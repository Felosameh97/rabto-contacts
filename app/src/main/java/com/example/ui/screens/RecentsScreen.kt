package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CallLog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.domain.model.CallLogEntry
import com.example.domain.model.CallTranscriptRecord
import com.example.domain.model.ContactRecord
import com.example.telecom.PostCallSummary
import com.example.ui.components.ContactAvatarBadge
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.WhatsAppGreen
import com.example.util.EgyptGeoLookup
import com.example.util.PhoneUtils
import com.example.util.SystemIntents
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class RecentsFilter(val labelAr: String) {
    ALL("الكل"),
    MISSED("فائتة"),
    RECORDED("مسجّلة / AI"),
}

private enum class DateBucket(val labelAr: String) {
    TODAY("اليوم"),
    YESTERDAY("أمس"),
    OLDER("الأقدم"),
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentsScreen(
    callLogs: List<CallLogEntry>,
    callTranscripts: List<CallTranscriptRecord>,
    hasCallLogPermission: Boolean,
    showContactPhotos: Boolean,
    preferredSimSlot: Int?,
    postCallSummary: PostCallSummary?,
    whatsappTemplates: List<String>,
    onRecordDial: (String, Boolean) -> Unit,
    onOpenContact: (ContactRecord) -> Unit,
    onSaveNumber: (String) -> Unit,
    onOpenTranscript: (CallTranscriptRecord) -> Unit,
    onDeleteCallLog: (CallLogEntry) -> Unit,
    onWhatsApp: (ContactRecord, String) -> Unit,
    onWhatsAppQuick: (String, String, String?, String?) -> Unit,
    onScheduleFollowUpQuick: (String, String, String?, Int?) -> Unit,
    onDismissPostCallSummary: () -> Unit,
    onOpenLiveTranscriber: () -> Unit,
    onRefreshCallLogs: () -> Unit,
    onPermissionsChanged: () -> Unit,
) {
    val context = LocalContext.current
    var activeFilter by remember { mutableStateOf(RecentsFilter.ALL) }
    var showPostCallWaTemplates by remember { mutableStateOf(false) }
    var swipeChoiceTarget by remember { mutableStateOf<Pair<CallLogEntry, CallTranscriptRecord>?>(null) }

    val callLogLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onPermissionsChanged()
    }

    val postCallNotifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        postCallSummary?.let { s ->
            onScheduleFollowUpQuick(s.contactName, s.phone, s.contactId, 60)
        }
    }

    val transcriptsById = remember(callTranscripts) {
        callTranscripts.associateBy { it.id }
    }

    fun resolveTranscript(entry: CallLogEntry): CallTranscriptRecord? {
        entry.transcriptId?.let { id ->
            transcriptsById[id]?.let { return it }
        }
        val key = PhoneUtils.phoneSearchKey(entry.number)
        if (key.length < 6) return null
        return callTranscripts.firstOrNull { tr ->
            PhoneUtils.phoneSearchKey(tr.phone) == key &&
                kotlin.math.abs(tr.timestampMs - entry.timestampMs) <= 300_000L
        }
    }

    val filteredLogs = remember(callLogs, activeFilter, callTranscripts) {
        when (activeFilter) {
            RecentsFilter.ALL -> callLogs
            RecentsFilter.MISSED -> callLogs.filter { it.callType == CallLog.Calls.MISSED_TYPE }
            RecentsFilter.RECORDED -> callLogs.filter { it.hasRecording || it.transcriptId != null || resolveTranscript(it) != null }
        }
    }

    val groupedByDate = remember(filteredLogs) {
        val todayCal = Calendar.getInstance()
        val yestCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val logCal = Calendar.getInstance()

        fun sameDay(a: Calendar, b: Calendar): Boolean =
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

        filteredLogs.groupBy { entry ->
            logCal.timeInMillis = entry.timestampMs
            when {
                sameDay(logCal, todayCal) -> DateBucket.TODAY
                sameDay(logCal, yestCal) -> DateBucket.YESTERDAY
                else -> DateBucket.OLDER
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Filter Strip + Refresh
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RecentsFilter.entries.forEach { filter ->
                            val count = when (filter) {
                                RecentsFilter.ALL -> callLogs.size
                                RecentsFilter.MISSED -> callLogs.count { it.callType == CallLog.Calls.MISSED_TYPE }
                                RecentsFilter.RECORDED -> callLogs.count { it.hasRecording || it.transcriptId != null }
                            }
                            FilterChip(
                                selected = activeFilter == filter,
                                onClick = { activeFilter = filter },
                                label = { Text("${filter.labelAr} ($count)", maxLines = 1) },
                                modifier = Modifier.testTag("recents_filter_${filter.name}"),
                            )
                        }
                    }
                    IconButton(
                        onClick = onRefreshCallLogs,
                        modifier = Modifier.size(48.dp).testTag("recents_refresh_btn"),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "تحديث السجل")
                    }
                }
            }

            // Call Log Permission Card
            if (!hasCallLogPermission) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text(
                                    text = "تفعيل سجل مكالمات الهاتف",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                            Text(
                                text = "امنح صلاحية سجل المكالمات لدمج مكالمات الشرائح وربطها بجهات اتصالك المعزولة بدون إضافتها لهاتفك.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                            )
                            Button(
                                onClick = { callLogLauncher.launch(Manifest.permission.READ_CALL_LOG) },
                                modifier = Modifier.testTag("recents_grant_permission_btn"),
                            ) {
                                Text(stringResource(R.string.grant_call_log))
                            }
                        }
                    }
                }
            }

            // Post-Call Quick Actions Banner
            postCallSummary?.let { summary ->
                val linkedTranscript = summary.transcriptId?.let { transcriptsById[it] }
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        modifier = Modifier.fillMaxWidth().testTag("recents_post_call_card"),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "انتهت مكالمة ${summary.contactName}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                    val meta = buildString {
                                        append(summary.phone)
                                        if (summary.durationSec > 0) append(" • ${summary.durationSec}ث")
                                        if (summary.hasRecording) append(" • تسجيل محفوظ محلياً")
                                    }
                                    Text(
                                        text = meta,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                                    )
                                    val aiLine = linkedTranscript?.oneLineSummary?.takeIf { it.isNotBlank() }
                                        ?: summary.oneLineSummary?.takeIf { it.isNotBlank() }
                                        ?: when {
                                            linkedTranscript?.status == "PROCESSING" || linkedTranscript?.status == "PENDING_AI" ->
                                                "جاري تفريغ وتلخيص المكالمة بالذكاء الاصطناعي..."
                                            summary.aiRequested && summary.hasRecording ->
                                                "تم تجهيز التسجيل للتفريغ والملخص الذكي"
                                            else -> null
                                        }
                                    if (!aiLine.isNullOrBlank()) {
                                        Text(
                                            text = "✦ $aiLine",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                IconButton(onClick = onDismissPostCallSummary, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Rounded.Close, contentDescription = "إغلاق", modifier = Modifier.size(18.dp))
                                }
                            }
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (linkedTranscript != null) {
                                    Button(
                                        onClick = { onOpenTranscript(linkedTranscript) },
                                        modifier = Modifier.testTag("post_call_open_transcript_btn"),
                                    ) {
                                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            if (linkedTranscript.status == "READY") "ملخص AI والمشغّل"
                                            else "التسجيل والتفريغ"
                                        )
                                    }
                                }
                                FilledTonalButton(
                                    onClick = {
                                        onRecordDial(summary.phone, true)
                                        SystemIntents.dialPhoneWithSimSlot(context, summary.phone, preferredSimSlot)
                                    },
                                ) {
                                    Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("معاودة")
                                }
                                if (!PhoneUtils.isUssdOrShortCode(summary.phone)) {
                                    FilledTonalButton(onClick = { showPostCallWaTemplates = true }) {
                                        Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("واتساب")
                                    }
                                }
                                FilledTonalButton(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            postCallNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            onScheduleFollowUpQuick(summary.contactName, summary.phone, summary.contactId, 60)
                                        }
                                    },
                                ) {
                                    Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("تذكير ساعة")
                                }
                                OutlinedButton(onClick = onOpenLiveTranscriber) {
                                    Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("تفريغ ملاحظة")
                                }
                            }
                        }
                    }
                }
            }

            if (filteredLogs.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(40.dp),
                            )
                            Text(
                                text = when (activeFilter) {
                                    RecentsFilter.ALL -> "سجل المكالمات فارغ حالياً"
                                    RecentsFilter.MISSED -> "لا توجد مكالمات فائتة"
                                    RecentsFilter.RECORDED -> "لا توجد مكالمات مسجلة أو مفرغة بعد"
                                },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "أي مكالمات واردة أو صادرة ستظهر هنا مجمّعة حسب التاريخ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                DateBucket.entries.forEach { bucket ->
                    val rows = groupedByDate[bucket].orEmpty()
                    if (rows.isNotEmpty()) {
                        stickyHeader(key = "bucket_${bucket.name}") {
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "${bucket.labelAr} (${rows.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                )
                            }
                        }

                        items(rows, key = { it.id }) { entry ->
                            val transcript = remember(entry.id, callTranscripts) { resolveTranscript(entry) }
                            RecentCallLogRow(
                                entry = entry,
                                bucket = bucket,
                                matchedTranscript = transcript,
                                showPhoto = showContactPhotos,
                                onOpen = {
                                    val matched = entry.matchedContact
                                    if (matched != null) {
                                        onOpenContact(matched)
                                    } else {
                                        onSaveNumber(entry.number)
                                    }
                                },
                                onDial = {
                                    onRecordDial(entry.number, true)
                                    SystemIntents.dialPhoneWithSimSlot(context, entry.number, preferredSimSlot)
                                },
                                onOpenTranscript = {
                                    if (transcript != null) onOpenTranscript(transcript)
                                },
                                onSwipeLeft = {
                                    if (transcript != null) {
                                        swipeChoiceTarget = entry to transcript
                                    } else {
                                        onDeleteCallLog(entry)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    swipeChoiceTarget?.let { (entry, transcript) ->
        AlertDialog(
            onDismissRequest = { swipeChoiceTarget = null },
            icon = { Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(entry.matchedContact?.displayName ?: entry.cachedName ?: entry.number) },
            text = { Text("هل تريد عرض ملخص الذكاء الاصطناعي والتسجيل أو حذف سجل المكالمة؟") },
            confirmButton = {
                Button(
                    onClick = {
                        swipeChoiceTarget = null
                        onOpenTranscript(transcript)
                    },
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("عرض الملخص والتفريغ")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        swipeChoiceTarget = null
                        onDeleteCallLog(entry)
                    },
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp))
                    Text("حذف السجل", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }

    if (showPostCallWaTemplates && postCallSummary != null) {
        AlertDialog(
            onDismissRequest = { showPostCallWaTemplates = false },
            title = { Text("رسالة واتساب سريعة لـ ${postCallSummary.contactName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    whatsappTemplates.forEach { tpl ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onWhatsAppQuick(
                                        postCallSummary.phone,
                                        postCallSummary.contactName,
                                        postCallSummary.contactId,
                                        tpl,
                                    )
                                    showPostCallWaTemplates = false
                                    onDismissPostCallSummary()
                                },
                        ) {
                            Text(tpl, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onWhatsAppQuick(postCallSummary.phone, postCallSummary.contactName, postCallSummary.contactId, null)
                    showPostCallWaTemplates = false
                    onDismissPostCallSummary()
                }) { Text("محادثة فارغة") }
            },
            dismissButton = {
                TextButton(onClick = { showPostCallWaTemplates = false }) { Text("إلغاء") }
            },
        )
    }
}

@Composable
private fun RecentCallLogRow(
    entry: CallLogEntry,
    bucket: DateBucket,
    matchedTranscript: CallTranscriptRecord?,
    showPhoto: Boolean,
    onOpen: () -> Unit,
    onDial: () -> Unit,
    onOpenTranscript: () -> Unit,
    onSwipeLeft: () -> Unit,
) {
    val label = entry.matchedContact?.displayName
        ?.takeIf { it.isNotBlank() }
        ?: entry.cachedName?.takeIf { it.isNotBlank() }
        ?: entry.number
    val isMissed = entry.callType == CallLog.Calls.MISSED_TYPE || entry.callType == CallLog.Calls.REJECTED_TYPE
    val callIcon = when (entry.callType) {
        CallLog.Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Rounded.CallMade
        CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> Icons.AutoMirrored.Rounded.CallMissed
        else -> Icons.AutoMirrored.Rounded.CallReceived
    }
    val callIconTint = if (isMissed) ErrorRed else MaterialTheme.colorScheme.primary
    val fmt = remember(bucket) {
        val pattern = if (bucket == DateBucket.TODAY || bucket == DateBucket.YESTERDAY) "hh:mm a" else "dd/MM • hh:mm a"
        SimpleDateFormat(pattern, Locale.getDefault())
    }
    val timeText = fmt.format(Date(entry.timestampMs))
    val isRecordedOrAi = entry.hasRecording || matchedTranscript != null

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onDial()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onSwipeLeft()
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = entry.number.isNotBlank(),
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val dir = dismissState.dismissDirection
            val bgColor = when (dir) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                SwipeToDismissBoxValue.EndToStart -> if (matchedTranscript != null) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }
                SwipeToDismissBoxValue.Settled -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(bgColor)
                    .padding(horizontal = 20.dp),
            ) {
                if (dir == SwipeToDismissBoxValue.StartToEnd) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterStart),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("اتصال", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                } else if (dir == SwipeToDismissBoxValue.EndToStart) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = if (matchedTranscript != null) "تفريغ / حذف" else "حذف السجل",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (matchedTranscript != null) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.error,
                        )
                        Icon(
                            imageVector = if (matchedTranscript != null) Icons.Rounded.GraphicEq else Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = if (matchedTranscript != null) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .testTag("recent_call_card_${entry.id}"),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ContactAvatarBadge(
                    displayName = entry.matchedContact?.displayName ?: entry.cachedName.orEmpty(),
                    phone = entry.number,
                    photoUri = entry.matchedContact?.photoUri,
                    colorSeed = entry.matchedContact?.avatarColorSeed ?: 0,
                    showPhoto = showPhoto,
                    size = 44.dp,
                    isBlocked = entry.matchedContact?.blocked == true,
                    isMuted = entry.matchedContact?.muted == true,
                )

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = callIcon,
                            contentDescription = null,
                            tint = callIconTint,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = if (entry.callCount > 1) "$label (${entry.callCount})" else label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isMissed) ErrorRed else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }

                    val geoHint = remember(entry.number, entry.matchedContact) {
                        if (entry.matchedContact == null) EgyptGeoLookup.inferCallerInsight(entry.number) else null
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = buildString {
                                append(entry.number)
                                append(" • ")
                                append(timeText)
                                if (entry.durationSec > 0L) append(" • ${entry.durationSec}ث")
                                if (!geoHint.isNullOrBlank()) append(" • $geoHint")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (entry.simSlot != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Icon(Icons.Rounded.SimCard, contentDescription = null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text("${entry.simSlot + 1}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    if (isRecordedOrAi) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = matchedTranscript != null, onClick = onOpenTranscript)
                                .testTag("recent_ai_badge_${entry.id}"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = "مكالمة مسجلة",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = when {
                                        matchedTranscript?.oneLineSummary?.isNotBlank() == true -> matchedTranscript.oneLineSummary
                                        matchedTranscript?.status == "READY" -> "تفريغ وملخص AI جاهز"
                                        else -> "مكالمة مسجلة"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                FilledTonalIconButton(
                    onClick = onDial,
                    modifier = Modifier.size(48.dp).testTag("recent_call_btn_${entry.id}"),
                ) {
                    Icon(Icons.Rounded.Call, contentDescription = "اتصال")
                }
            }
        }
    }
}
