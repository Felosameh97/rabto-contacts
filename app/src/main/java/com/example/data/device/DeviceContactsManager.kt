package com.example.data.device

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.domain.model.ContactRecord
import com.example.util.ContactDeduplicator
import com.example.util.ContactPhotoStorage
import com.example.util.PhoneUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeviceContactsManager(private val context: Context) {

    fun hasPermissions(): Boolean = hasReadPermission()

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
            val nickByCid = hashMapOf<Long, String>()
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
                        ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE -> {
                            nickByCid.putIfAbsent(cid, d1.take(200))
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
                val nick = nickByCid[cid]
                val rawLabels = labelsByCid[cid]?.toList().orEmpty() + listOf("الهاتف (مستورد)")

                val detId = ContactDeduplicator.deterministicContactId(phones, emails, displayName)
                val cachedPhoto = readDeviceContactPhotoPath(cid, detId)

                val rawRecord = ContactRecord(
                    id = detId,
                    displayName = displayName.take(200),
                    nickname = nick,
                    primaryPhone = phones.firstOrNull().orEmpty(),
                    phones = phones.take(10),
                    emails = emails.take(5),
                    categoryPaths = rawLabels,
                    organization = orgPair?.first,
                    jobTitle = orgPair?.second,
                    notes = notes,
                    address = address,
                    photoUri = cachedPhoto,
                    syncedToDevice = false, // Keep isolated inside Felo Contacts unless explicitly flagged
                    isFavorite = starredSet.contains(cid),
                )
                val enriched = ContactDeduplicator.enrichSingleContact(rawRecord)
                enriched.categoryPaths.forEach { cat ->
                    val parts = cat.split(">").map { it.trim() }.filter { it.isNotBlank() }
                    var cur = ""
                    parts.forEach { part ->
                        cur = if (cur.isEmpty()) part else "$cur > $part"
                        discoveredCats += cur
                    }
                }
                records += enriched
            }

            records to discoveredCats
        }
    }

    private suspend fun readDeviceContactPhotoPath(contactId: Long, hintId: String): String? {
        val bytes = runCatching {
            val contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
            ContactsContract.Contacts.openContactPhotoInputStream(context.contentResolver, contactUri, true)
                ?.use { it.readBytes() }
        }.getOrNull() ?: return null
        return ContactPhotoStorage.savePhotoBytes(context, bytes, hintId)
    }

    /**
     * One-way read of contact photos from Android's local Contacts provider matched by normalized phone key.
     * Never writes to or modifies ContactsContract.
     */
    suspend fun readDevicePhotosByPhoneKey(): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            if (!hasReadPermission()) throw SecurityException("صلاحية قراءة جهات الاتصال غير ممنوحة")
            val contactIdsWithPhoto = HashSet<Long>()
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.PHOTO_URI),
                "${ContactsContract.Contacts.PHOTO_URI} IS NOT NULL",
                null,
                null,
            )?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                while (c.moveToNext()) {
                    contactIdsWithPhoto += c.getLong(idIdx)
                }
            }
            if (contactIdsWithPhoto.isEmpty()) return@runCatching emptyMap()

            val phonesByCid = HashMap<Long, MutableList<String>>()
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null,
                null,
                null,
            )?.use { c ->
                val cidIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val numIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (c.moveToNext()) {
                    val cid = c.getLong(cidIdx)
                    if (!contactIdsWithPhoto.contains(cid)) continue
                    val key = PhoneUtils.phoneSearchKey(c.getString(numIdx).orEmpty())
                    if (key.length >= 6) {
                        phonesByCid.getOrPut(cid) { mutableListOf() }.add(key)
                    }
                }
            }

            val result = HashMap<String, String>()
            for ((cid, keys) in phonesByCid) {
                if (keys.isEmpty()) continue
                val savedPath = readDeviceContactPhotoPath(cid, "dev_$cid") ?: continue
                keys.forEach { key ->
                    result.putIfAbsent(key, savedPath)
                }
            }
            result
        }
    }

    /**
     * Complete Sandbox Isolation: writing contacts to Android's native ContactsContract is strictly prohibited
     * so contacts never leak to WhatsApp, Telegram, or the system phonebook.
     */
    suspend fun syncContactToPhonebook(@Suppress("UNUSED_PARAMETER") contact: ContactRecord): Result<Boolean> =
        Result.failure(
            UnsupportedOperationException(
                "وضع العزل الكامل مفعّل: جهات الاتصال معزولة داخل التطبيق فقط ولا تُزامن مع سجل الهاتف أو واتساب."
            )
        )

    /**
     * No-op because app contacts are strictly sandboxed and never written to the native phonebook.
     */
    suspend fun removeContactFromPhonebook(@Suppress("UNUSED_PARAMETER") contact: ContactRecord): Result<Boolean> =
        Result.success(false)
}
