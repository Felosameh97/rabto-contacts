package com.example.ui.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.ContactRecord
import com.example.ui.theme.WhatsAppGreen
import com.example.ui.viewmodel.AiAssistantUiState
import com.example.util.SystemIntents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantSheet(
    uiState: AiAssistantUiState,
    onRunCommand: (String) -> Unit,
    onRunBatchCategorize: () -> Unit,
    onRunBilingualEnrich: () -> Unit = {},
    onOpenContact: (ContactRecord) -> Unit,
    onWhatsAppWithMsg: (ContactRecord, String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var promptText by remember { mutableStateOf("") }

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                promptText = spoken
                onRunCommand(spoken)
            }
        }
    }

    val quickSuggestions = listOf(
        "هات أرقام كهربائي في المطرية",
        "افتح قسم دفعة 2023",
        "ابعت رسالة واتساب لـ ",
        "اتصل بـ ",
        "صنف غير المصنفين بذكاء",
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("مساعد رابطة الذكي (Gemini AI)", style = MaterialTheme.typography.titleLarge)
            }

            Text(
                "اكتب أو اتكلم بأي أمر: اتصال، رسالة واتساب، بحث بالمنطقة أو المهنة، إضافة رقم، أو تصنيف تلقائي ذكي.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                quickSuggestions.forEach { chip ->
                    AssistChip(
                        onClick = {
                            if (chip.endsWith(" ")) {
                                promptText = chip
                            } else {
                                promptText = chip
                                onRunCommand(chip)
                            }
                        },
                        label = { Text(chip.trim(), style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }

            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it },
                placeholder = { Text("مثال: اتصل بأحمد محمد أو دورلي على دكتور في مدينة نصر...") },
                minLines = 2,
                maxLines = 4,
                trailingIcon = {
                    Row {
                        IconButton(
                            onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث بالأمر المطلوب...")
                                }
                                runCatching { voiceLauncher.launch(intent) }
                            },
                            modifier = Modifier.testTag("ai_voice_btn"),
                        ) {
                            Icon(Icons.Rounded.Mic, contentDescription = "إدخال صوتي")
                        }
                        IconButton(
                            onClick = { if (promptText.isNotBlank()) onRunCommand(promptText) },
                            modifier = Modifier.testTag("ai_send_btn"),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "تنفيذ الأمر", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("ai_prompt_input"),
            )

            FilledTonalButton(
                onClick = onRunBatchCategorize,
                enabled = uiState !is AiAssistantUiState.Loading,
                modifier = Modifier.fillMaxWidth().testTag("ai_batch_categorize_btn"),
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("تصنيف ذكي تلقائي لـ 40 جهة غير مصنفة عبر Gemini")
            }

            FilledTonalButton(
                onClick = onRunBilingualEnrich,
                enabled = uiState !is AiAssistantUiState.Loading,
                modifier = Modifier.fillMaxWidth().testTag("ai_bilingual_enrich_btn"),
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("تحويل لـ English + حفظ Nickname عربي + Tags عبر Gemini")
            }

            when (uiState) {
                is AiAssistantUiState.Idle -> Unit
                is AiAssistantUiState.Loading -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("جاري الفهم والتنفيذ عبر Gemini...", style = MaterialTheme.typography.bodySmall)
                }
                is AiAssistantUiState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = uiState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                is AiAssistantUiState.Success -> {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = uiState.actionBadge,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = uiState.reply,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }

                    if (uiState.matchedContacts.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(uiState.matchedContacts, key = { it.id }) { contact ->
                                val phone = contact.primaryPhone.ifBlank { contact.phones.firstOrNull().orEmpty() }
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenContact(contact) },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(contact.displayName, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                listOfNotNull(
                                                    phone.takeIf { it.isNotBlank() },
                                                    contact.categoryPaths.firstOrNull(),
                                                ).joinToString(" • "),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (phone.isNotBlank()) {
                                            FilledTonalIconButton(
                                                onClick = { SystemIntents.dialPhone(context, phone) },
                                                modifier = Modifier.size(40.dp),
                                            ) {
                                                Icon(Icons.Rounded.Call, contentDescription = "اتصال", modifier = Modifier.size(18.dp))
                                            }
                                            FilledTonalIconButton(
                                                onClick = { onWhatsAppWithMsg(contact, phone, uiState.suggestedMessage) },
                                                modifier = Modifier.size(40.dp),
                                            ) {
                                                Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = "واتساب", tint = WhatsAppGreen, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
