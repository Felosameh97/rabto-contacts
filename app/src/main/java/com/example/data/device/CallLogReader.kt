package com.example.data.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.example.data.local.ContactDao
import com.example.domain.model.CallLogEntry
import com.example.domain.model.ContactRecord
import com.example.util.PhoneUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CallLogReader(
    private val context: Context,
    private val contactDao: ContactDao,
) {
    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CALL_LOG
    ) == PackageManager.PERMISSION_GRANTED

    suspend fun loadFrequentAndRecentCalls(limit: Int = 250): Pair<List<CallLogEntry>, List<CallLogEntry>> =
        withContext(Dispatchers.IO) {
            if (!hasPermission()) return@withContext emptyList<CallLogEntry>() to emptyList()

            val entries = mutableListOf<CallLogEntry>()
            val projection = arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
            )

            runCatching {
                context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${CallLog.Calls.DATE} DESC"
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
                    val numIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                    val nameIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                    val typeIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                    val dateIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                    val durIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)

                    var count = 0
                    while (cursor.moveToNext() && count < limit) {
                        val rawNum = cursor.getString(numIdx).orEmpty()
                        if (rawNum.isBlank()) continue
                        val normNum = PhoneUtils.normalizePhone(rawNum)
                        entries += CallLogEntry(
                            id = cursor.getString(idIdx),
                            number = normNum.ifBlank { rawNum },
                            cachedName = cursor.getString(nameIdx)?.takeIf { it.isNotBlank() },
                            callType = cursor.getInt(typeIdx),
                            timestampMs = cursor.getLong(dateIdx),
                            durationSec = cursor.getLong(durIdx),
                            callCount = 1,
                        )
                        count++
                    }
                }
            }

            if (entries.isEmpty()) return@withContext emptyList<CallLogEntry>() to emptyList()

            // Group by normalized key first
            val grouped = linkedMapOf<String, MutableList<CallLogEntry>>()
            for (e in entries) {
                val key = PhoneUtils.phoneSearchKey(e.number).ifBlank { e.number }
                grouped.getOrPut(key) { mutableListOf() }.add(e)
            }

            // Single indexed batch query instead of 100+ LIKE scans
            val validKeys = grouped.keys.filter { it.length >= 4 }.distinct()
            val matchedByKey = mutableMapOf<String, ContactRecord>()
            for (chunk in validKeys.chunked(400)) {
                val matches = contactDao.findAllByPhoneKeys(chunk)
                for (m in matches) {
                    matchedByKey.putIfAbsent(m.phoneKey, m.contact.toDomain())
                }
            }

            val enrichedByKey = mutableMapOf<String, CallLogEntry>()
            for ((key, list) in grouped) {
                val latest = list.first()
                enrichedByKey[key] = latest.copy(
                    callCount = list.size,
                    matchedContact = matchedByKey[key],
                )
            }

            val frequent = enrichedByKey.values
                .sortedWith(compareByDescending<CallLogEntry> { it.callCount }.thenByDescending { it.timestampMs })
                .take(12)

            val recent = entries.take(20).map { entry ->
                val key = PhoneUtils.phoneSearchKey(entry.number).ifBlank { entry.number }
                entry.copy(matchedContact = enrichedByKey[key]?.matchedContact)
            }

            frequent to recent
        }
}
