package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.util.AppLockSecurity
import com.example.util.BiometricVaultHelper

@Composable
fun AppLockScreen(
    pinHash: String,
    biometricEnabled: Boolean,
    onUnlocked: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var enteredPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val canBiometric = remember(context) { BiometricVaultHelper.isDeviceSecuredOrBiometricAvailable(context) }

    BackHandler(enabled = true) {
        // Prevent back press from bypassing the app lock
    }

    fun triggerBiometric() {
        BiometricVaultHelper.authenticate(
            context = context,
            title = "فتح Felo Contacts",
            subtitle = "استخدم البصمة أو قفل الجهاز للمتابعة",
            onSuccess = onUnlocked,
            onError = { msg -> errorMsg = msg },
        )
    }

    LaunchedEffect(biometricEnabled, canBiometric) {
        if (biometricEnabled && canBiometric) {
            triggerBiometric()
        }
    }

    fun submitPin() {
        if (AppLockSecurity.verifyPin(enteredPin, pinHash)) {
            errorMsg = null
            onUnlocked()
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            errorMsg = "رمز PIN غير صحيح، حاول مرة أخرى"
            enteredPin = ""
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            .testTag("app_lock_screen"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 380.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(72.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "التطبيق مقفل",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Felo Contacts محمي",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "أدخل رمز المرور (4–6 أرقام) للوصول إلى جهات الاتصال والتسجيلات",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                // PIN Dots Indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .testTag("app_lock_pin_dots"),
                ) {
                    val slotCount = maxOf(4, enteredPin.length.coerceAtMost(6))
                    for (i in 0 until slotCount) {
                        val filled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (filled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                        )
                    }
                }

                errorMsg?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }

                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            row.forEach { d ->
                                PinDigitKey(
                                    digit = d,
                                    onClick = {
                                        if (enteredPin.length < 6) {
                                            errorMsg = null
                                            enteredPin += d
                                            if (enteredPin.length in 4..6 && AppLockSecurity.verifyPin(enteredPin, pinHash)) {
                                                onUnlocked()
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        FilledTonalIconButton(
                            onClick = { if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1) },
                            modifier = Modifier
                                .size(68.dp)
                                .testTag("app_lock_backspace_btn"),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "مسح")
                        }
                        PinDigitKey(
                            digit = "0",
                            onClick = {
                                if (enteredPin.length < 6) {
                                    errorMsg = null
                                    enteredPin += "0"
                                    if (enteredPin.length in 4..6 && AppLockSecurity.verifyPin(enteredPin, pinHash)) {
                                        onUnlocked()
                                    }
                                }
                            },
                        )
                        FilledTonalIconButton(
                            onClick = { submitPin() },
                            enabled = enteredPin.length >= 4,
                            modifier = Modifier
                                .size(68.dp)
                                .testTag("app_lock_submit_btn"),
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = "تأكيد الرمز")
                        }
                    }
                }

                if (biometricEnabled && canBiometric) {
                    FilledTonalButton(
                        onClick = { triggerBiometric() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("app_lock_biometric_btn"),
                    ) {
                        Icon(Icons.Rounded.Fingerprint, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("فتح بالبصمة / قفل الهاتف")
                    }
                }
            }
        }
    }
}

@Composable
private fun PinDigitKey(
    digit: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag("app_lock_key_$digit"),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = digit,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun SetPinDialog(
    hasExistingPin: Boolean,
    onDismiss: () -> Unit,
    onConfirmPin: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
        title = { Text(if (hasExistingPin) "تغيير رمز قفل التطبيق" else "تعيين رمز قفل التطبيق (PIN)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "اختر رمزاً من 4 إلى 6 أرقام لحماية بياناتك ومكالماتك.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { next ->
                        pin = next.filter { it.isDigit() }.take(6)
                        validationError = null
                    },
                    label = { Text("الرمز الجديد (4–6 أرقام)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().testTag("set_pin_input"),
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { next ->
                        confirmPin = next.filter { it.isDigit() }.take(6)
                        validationError = null
                    },
                    label = { Text("تأكيد الرمز") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().testTag("set_pin_confirm_input"),
                )
                validationError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        !AppLockSecurity.isValidPinFormat(pin) ->
                            validationError = "يجب أن يتكون الرمز من 4 إلى 6 أرقام"
                        pin != confirmPin ->
                            validationError = "الرمزان غير متطابقين"
                        else -> onConfirmPin(pin)
                    }
                },
                modifier = Modifier.testTag("set_pin_save_btn"),
            ) {
                Text("حفظ القفل")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        },
    )
}
