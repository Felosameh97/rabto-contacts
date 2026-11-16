package com.example.data.device

import android.Manifest
import android.content.ContentProviderOperation
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.domain.model.ContactRecord
import com.example.util.PhoneUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeviceContactsManager(private val context: Context) {

    fun hasPermissions(): Boolean {
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
        return read && write
    }

    suspend fun syncContactToPhonebook(contact: ContactRecord): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            if (!hasPermissions()) throw SecurityException("Contacts permission not granted")
            val primary = contact.primaryPhone.ifBlank { contact.phones.firstOrNull().orEmpty() }
            if (primary.isBlank()) return@runCatching false

            if (existsInPhonebook(primary)) {
                return@runCatching true
            }

            val ops = ArrayList<ContentProviderOperation>()
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build()
            )
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contact.displayName)
                    .build()
            )
            for (phone in contact.phones.ifEmpty { listOf(primary) }.distinct()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                        .build()
                )
            }
            for (email in contact.emails.distinct()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Email.DATA, email)
                        .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_WORK)
                        .build()
                )
            }
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        }
    }

    suspend fun removeContactFromPhonebook(contact: ContactRecord): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            if (!hasPermissions()) throw SecurityException("Contacts permission not granted")
            val suffix = PhoneUtils.phoneSearchKey(contact.primaryPhone.ifBlank { contact.phones.firstOrNull().orEmpty() })
            if (suffix.isBlank()) return@runCatching false

            val rawIds = mutableSetOf<Long>()
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null,
                null,
                null
            )?.use { cursor ->
                val rawIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID)
                val numIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val num = cursor.getString(numIdx).orEmpty()
                    if (PhoneUtils.phoneSearchKey(num) == suffix) {
                        rawIds += cursor.getLong(rawIdx)
                    }
                }
            }

            for (rawId in rawIds) {
                context.contentResolver.delete(
                    ContactsContract.RawContacts.CONTENT_URI,
                    "${ContactsContract.RawContacts._ID} = ?",
                    arrayOf(rawId.toString())
                )
            }
            rawIds.isNotEmpty()
        }
    }

    private fun existsInPhonebook(phone: String): Boolean {
        val key = PhoneUtils.phoneSearchKey(phone)
        if (key.length < 6) return false
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            null,
            null,
            null
        )?.use { cursor ->
            val idx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val num = cursor.getString(idx).orEmpty()
                if (PhoneUtils.phoneSearchKey(num) == key) return true
            }
        }
        return false
    }
}
