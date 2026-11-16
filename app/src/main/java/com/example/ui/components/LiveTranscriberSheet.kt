package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.LocalTranscriptAnalyzer
import com.example.telecom.LocalSpeechTranscriber
import com.example.util.SystemIntents

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LiveTranscriberSheet(
    onSaveTranscript: (rawText: String, contactName: String, phone: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val transcriber = remember { LocalSpeechTranscriber(context) }
    val liveState by transcriber.state.collectAsStateWithLifecycle()

    var selectedLang by remember { mutableStateOf("ar-EG") }
    var editableTranscript by remember { mutableStateOf("") }
    var contactName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }

    LaunchedEffect(liveState.confirmedText, liveState.partialText) {
        val stream = liveState.fullText
        if (stream.isNotBlank()) {
            editableTranscript = stream
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            transcriber.stop()
        }
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            transcriber.start(selectedLang)
        }
    }

    val analysis = remember(editableTranscript, contactName, phoneNumber) {
        LocalTranscriptAnalyzer.analyze(
            rawTranscript = editableTranscript,
            contactName = contactName,
            phone = phoneNumber,
        )
    }

    val statusColor by animateColorAsState(
        targetValue = if (liveState.isListening) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        label = "live_status_color",
    )

    ModalBottomSheet(
        onDismissRequest = {
            transcriber.stop()
            onDismiss()
        },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(
                            "التفريغ الصوتي الفوري (مجاناً)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "تحويل كلامي مباشر بدون استهلاك رصيد Gemini",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor,
                ) {
                    Text(
                        text = if (liveState.isListening) "● مباشر" else "جاهز",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }

            // Language picker
            val languages = listOf(
                "ar-EG" to "عربي مصري",
                "ar-SA" to "عربي فصحى",
                "en-US" to "English",
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                languages.forEach { (code, label) ->
                    FilterChip(
                        selected = selectedLang == code,
                        onClick = {
                            selectedLang = code
                            if (liveState.isListening) {
                                transcriber.stop()
                                transcriber.start(code)
                            }
                        },
                        leadingIcon = if (selectedLang == code) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        label = { Text(label) },
                    )
                }
            }

            // Optional caller association fields
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = contactName,
                    onValueChange = { contactName = it },
                    label = { Text("اسم الشخص (اختياري)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).testTag("live_transcriber_name"),
                )
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("الرقم لربط السجل") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).testTag("live_transcriber_phone"),
                )
            }

            // Live stream text box
            OutlinedTextField(
                value = editableTranscript,
                onValueChange = { editableTranscript = it },
                label = { Text("النص المباشر (مناسب للمكالمة على السبيكر أو الاجتماعات)") },
                placeholder = { Text("اضغط بدء الاستماع وتحدث أو افتح السبيكر وسيظهر النص لحظياً هنا...") },
                minLines = 4,
                maxLines = 8,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .testTag("live_transcriber_text"),
            )

            liveState.errorNote?.takeIf { it.isNotBlank() }?.let { err ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }

            // Instant Local NLP highlights & calendar extraction preview
            if (analysis.highlights.isNotEmpty() || analysis.eventTitle.isNotBlank()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "الاستخراج الذكي الفوري (محلي):",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        if (analysis.highlights.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                analysis.highlights.forEach { hl ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                    ) {
                                        Text(
                                            text = "★ $hl",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                        if (analysis.eventTitle.isNotBlank()) {
                            FilledTonalButton(
                                onClick = {
                                    SystemIntents.insertCalendarEvent(
                                        context = context,
                                        title = analysis.eventTitle,
                                        description = "${contactName.ifBlank { phoneNumber }}\n${analysis.extractedNotes}",
                                        startEpochMs = analysis.eventEpochMs,
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("إضافة للتقويم: ${analysis.eventTitle}")
                            }
                        }
                    }
                }
            }

            // Controls: Start/Stop + Clear + Save
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        if (liveState.isListening) {
                            transcriber.stop()
                        } else {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                transcriber.start(selectedLang)
                            } else {
                                micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    colors = if (liveState.isListening) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                    modifier = Modifier.weight(1f).testTag("live_transcriber_toggle_btn"),
                ) {
                    Icon(
                        if (liveState.isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (liveState.isListening) "إيقاف" else "بدء التفريغ")
                }

                OutlinedButton(
                    onClick = {
                        transcriber.stop()
                        transcriber.clear()
                        editableTranscript = ""
                    },
                    enabled = editableTranscript.isNotBlank(),
                    modifier = Modifier.testTag("live_transcriber_clear_btn"),
                ) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = "مسح", modifier = Modifier.size(18.dp))
                }

                FilledTonalButton(
                    onClick = {
                        transcriber.stop()
                        onSaveTranscript(editableTranscript, contactName, phoneNumber)
                        onDismiss()
                    },
                    enabled = editableTranscript.isNotBlank(),
                    modifier = Modifier.weight(1f).testTag("live_transcriber_save_btn"),
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("حفظ واستخراج")
                }
            }
        }
    }
}
