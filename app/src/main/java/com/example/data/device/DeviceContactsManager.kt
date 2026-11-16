package com.example.data.device

import android.Manifest
import android.content.ContentProviderOperation
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.domain.model.ContactRecord
import com.example.util.EgyptGeoLookup
import com.example.util.PhoneUtils
import com.example.util.SmartCategoryEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class DeviceContactsManager(private val context: Context) {

    fun hasPermissions(): Boolean {
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
        return read && write
    }

    fun hasReadPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Reads ALL contacts from the user's phonebook / Google People app on device (with phones, emails,
     * organizations, addresses, notes, and group labels) and auto-classifies them.
     */
    suspend fun readAllDeviceContacts(): Result<Pair<List<ContactRecord>, Set<String>>> = withContext(Dispatchers.IO) {
        runCatching {
            if (!hasReadPermission()) throw SecurityException("صلاحية قراءة جهات الاتصال غير ممنوحة")

            val namesMap = linkedMapOf<Long, String>()
            val starredSet = hashSetOf<Long>()
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.STARRED,
                ),
                null,
                null,
                null
            )?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                val nameIdx = c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                val starIdx = c.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED)
                while (c.moveToNext()) {
                    val cid = c.getLong(idIdx)
                    val name = c.getString(nameIdx).orEmpty().trim()
                    if (name.isNotBlank()) namesMap[cid] = name
                    if (c.getInt(starIdx) == 1) starredSet += cid
                }
            }

            // Read group titles (labels in Android Contacts / People)
            val groupTitleById = hashMapOf<Long, String>()
            context.contentResolver.query(
                ContactsContract.Groups.CONTENT_URI,
                arrayOf(ContactsContract.Groups._ID, ContactsContract.Groups.TITLE),
                null,
                null,
                null
            )?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(ContactsContract.Groups._ID)
                val tIdx = c.getColumnIndexOrThrow(ContactsContract.Groups.TITLE)
                while (c.moveToNext()) {
                    val title = c.getString(tIdx).orEmpty().trim()
                    if (title.isNotBlank()) groupTitleById[c.getLong(idIdx)] = title
                }
            }

            val phonesByCid = hashMapOf<Long, LinkedHashSet<String>>()
            val emailsByCid = hashMapOf<Long, LinkedHashSet<String>>()
            val addressByCid = hashMapOf<Long, String>()
            val orgByCid = hashMapOf<Long, Pair<String?, String?>>()
            val notesByCid = hashMapOf<Long, String>()
            val labelsByCid = hashMapOf<Long, LinkedHashSet<String>>()

            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.Data.CONTACT_ID,
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.Data.DATA1,
                    ContactsContract.Data.DATA4,
                ),
                null,
                null,
                null
            )?.use { c ->
                val cidIdx = c.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
                val mimeIdx = c.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE)
                val d1Idx = c.getColumnIndexOrThrow(ContactsContract.Data.DATA1)
                val d4Idx = c.getColumnIndexOrThrow(ContactsContract.Data.DATA4)

                while (c.moveToNext()) {
                    val cid = c.getLong(cidIdx)
                    val mime = c.getString(mimeIdx).orEmpty()
                    val d1 = c.getString(d1Idx).orEmpty().trim()
                    if (d1.isBlank()) continue

                    when (mime) {
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                            val norm = PhoneUtils.normalizePhone(d1)
                            if (norm.isNotBlank()) phonesByCid.getOrPut(cid) { LinkedHashSet() }.add(norm)
                        }
                        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                            if (d1.contains("@")) emailsByCid.getOrPut(cid) { LinkedHashSet() }.add(d1)
                        }
                        ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> {
                            addressByCid.putIfAbsent(cid, d1)
                        }
                        ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                            val title = c.getString(d4Idx)?.trim()?.takeIf { it.isNotBlank() }
                            orgByCid.putIfAbsent(cid, d1 to title)
                        }
                        ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE -> {
                            notesByCid.putIfAbsent(cid, d1.take(1000))
                        }
                        ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE -> {
                            val gid = d1.toLongOrNull()
                            val gTitle = gid?.let { groupTitleById[it] }
                            if (!gTitle.isNullOrBlank()) {
                                labelsByCid.getOrPut(cid) { LinkedHashSet() }.add(gTitle)
                            }
                        }
                    }
                }
            }

            val allIds = (namesMap.keys + phonesByCid.keys + emailsByCid.keys).distinct()
            val records = ArrayList<ContactRecord>(allIds.size)
            val discoveredCats = LinkedHashSet<String>()

            for (cid in allIds) {
                val phones = phonesByCid[cid]?.toList().orEmpty()
                val emails = emailsByCid[cid]?.toList().orEmpty()
                if (phones.isEmpty() && emails.isEmpty()) continue

                val displayName = namesMap[cid]
                    ?: phones.firstOrNull()
                    ?: emails.firstOrNull()
                    ?: continue
                val orgPair = orgByCid[cid]
                val address = addressByCid[cid]
                val notes = notesByCid[cid]
                val rawLabels = labelsByCid[cid]?.toList().orEmpty() + listOf("الهاتف (مستورد)")

                val (nestedCats, starred) = SmartCategoryEngine.normalizeAndNestLabels(
                    rawLabels = rawLabels,
                    displayName = displayName,
                    phones = phones,
                    emails = emails,
                    organization = orgPair?.first,
                    jobTitle = orgPair?.second,
                    notes = notes,
                    address = address,
                )
                nestedCats.forEach { cat ->
                    val parts = cat.split(">").map { it.trim() }.filter { it.isNotBlank() }
                    var cur = ""
                    parts.forEach { part ->
                        cur = if (cur.isEmpty()) part else "$cur > $part"
                        discoveredCats += cur
                    }
                }

                val geo = EgyptGeoLookup.inferLocation(address, displayName, notes, rawLabels.joinToString(" "))
                    ?: phones.firstOrNull()?.let { EgyptGeoLookup.inferLocationFromPhone(it) }

                records += ContactRecord(
                    id = "dev_${cid}_${UUID.nameUUIDFromBytes(displayName.toByteArray()).toString().take(6)}",
                    displayName = displayName.take(200),
                    primaryPhone = phones.firstOrNull().orEmpty(),
                    phones = phones.take(10),
                    emails = emails.take(5),
                    categoryPaths = nestedCats,
                    organization = orgPair?.first,
                    jobTitle = orgPair?.second,
                    notes = notes,
                    address = address,
                    district = geo?.key,
                    latitude = geo?.lat,
                    longitude = geo?.lng,
                    syncedToDevice = true,
                    isFavorite = starred || starredSet.contains(cid),
                )
            }

            records to discoveredCats
        }
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
