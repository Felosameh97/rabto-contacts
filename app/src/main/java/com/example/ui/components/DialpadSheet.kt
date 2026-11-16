package com.example.ui.components

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.domain.model.ContactRecord
import com.example.ui.theme.WhatsAppGreen
import com.example.util.PhoneUtils
import com.example.util.SystemIntents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialpadSheet(
    initialDigits: String = "",
    matches: List<ContactRecord>,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onOpenContact: (ContactRecord) -> Unit,
    onWhatsApp: (ContactRecord, String) -> Unit,
    onSaveNewNumber: (String) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var digits by remember(initialDigits) { mutableStateOf(initialDigits) }

    val callPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        if (digits.isNotBlank()) SystemIntents.dialPhone(context, digits)
    }

    LaunchedEffect(digits) {
        onQueryChange(digits)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Matching contacts preview
            if (matches.isNotEmpty() && digits.isNotBlank()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(matches, key = { it.id }) { contact ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenContact(contact) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(contact.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        contact.primaryPhone,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val p = contact.primaryPhone.ifBlank { digits }
                                        SystemIntents.dialPhone(context, p)
                                    }
                                ) {
                                    Icon(Icons.Rounded.Call, contentDescription = "اتصال", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }

            // Number display bar
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clipText = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                        val cleaned = clipText.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
                        if (cleaned.isNotBlank()) digits = cleaned
                    }) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = "لصق رقم")
                    }

                    Text(
                        text = digits.ifBlank { "أدخل الرقم للاتصال..." },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (digits.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).testTag("dialpad_display_text"),
                    )

                    if (digits.isNotBlank()) {
                        IconButton(onClick = { digits = digits.dropLast(1) }) {
                            Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "مسح")
                        }
                    }
                }
            }

            // Keypad Grid
            val keypadRows = listOf(
                listOf("1" to "", "2" to "أ ب ج", "3" to "د هـ و"),
                listOf("4" to "ز ح ط", "5" to "ي ك ل", "6" to "م ن س"),
                listOf("7" to "ع ف ص", "8" to "ق ر ش", "9" to "ت ث خ"),
                listOf("*" to "", "0" to "+", "#" to ""),
            )

            keypadRows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    row.forEach { (digit, sub) ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .clickable { digits += digit }
                                .testTag("dialpad_key_$digit"),
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(digit, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                if (sub.isNotBlank()) {
                                    Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // Call + WhatsApp + Save buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = {
                        if (digits.isNotBlank()) onSaveNewNumber(digits)
                    },
                    enabled = digits.isNotBlank(),
                    modifier = Modifier.size(52.dp).testTag("dialpad_save_btn"),
                ) {
                    Icon(Icons.Rounded.PersonAdd, contentDescription = "حفظ الرقم")
                }

                Button(
                    onClick = {
                        if (digits.isBlank()) return@Button
                        val hasCallPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                        if (hasCallPerm) {
                            SystemIntents.dialPhone(context, digits)
                        } else {
                            callPermLauncher.launch(Manifest.permission.CALL_PHONE)
                        }
                    },
                    enabled = digits.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f).height(52.dp).testTag("dialpad_call_btn"),
                ) {
                    Icon(Icons.Rounded.Call, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("اتصال الآن")
                }

                FilledTonalIconButton(
                    onClick = {
                        if (digits.isNotBlank() && !PhoneUtils.isUssdOrShortCode(digits)) {
                            val dummy = ContactRecord(
                                id = "dial_${digits.hashCode()}",
                                displayName = digits,
                                primaryPhone = digits,
                                phones = listOf(digits),
                            )
                            onWhatsApp(dummy, digits)
                        }
                    },
                    enabled = digits.isNotBlank() && !PhoneUtils.isUssdOrShortCode(digits),
                    modifier = Modifier.size(52.dp).testTag("dialpad_wa_btn"),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = "واتساب", tint = WhatsAppGreen)
                }
            }
        }
    }
}
