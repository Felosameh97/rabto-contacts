package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

data class SimSlotInfo(
    val slotIndex: Int, // 0 = SIM 1, 1 = SIM 2
    val label: String,
    val carrierName: String,
    val accountHandle: PhoneAccountHandle?,
)

enum class SimPreference(val labelAr: String) {
    ASK_OR_BOTH("إظهار SIM 1 و SIM 2"),
    SIM_1("SIM 1 دائماً"),
    SIM_2("SIM 2 دائماً"),
}

object SimUtils {

    fun hasPhoneStatePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun getAvailableSims(context: Context): List<SimSlotInfo> {
        val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager

        val carrierNamesBySlot = LinkedHashMap<Int, String>()
        if (hasPhoneStatePermission(context)) {
            runCatching {
                val activeSubs = sm?.activeSubscriptionInfoList.orEmpty()
                for (sub in activeSubs) {
                    val slot = sub.simSlotIndex
                    val carrier = sub.carrierName?.toString()?.trim().orEmpty()
                    if (slot >= 0) {
                        carrierNamesBySlot[slot] = carrier
                    }
                }
            }
        }

        val handles: List<PhoneAccountHandle> = if (hasPhoneStatePermission(context)) {
            runCatching { tm?.callCapablePhoneAccounts.orEmpty() }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        if (handles.isNotEmpty()) {
            return handles.take(2).mapIndexed { index, handle ->
                val carrier = carrierNamesBySlot[index].orEmpty().ifBlank {
                    runCatching {
                        val acct = tm?.getPhoneAccount(handle)
                        acct?.label?.toString()?.trim().orEmpty()
                    }.getOrDefault("")
                }
                val cleanCarrier = carrier.take(16)
                val label = if (cleanCarrier.isNotBlank()) {
                    "SIM ${index + 1} • $cleanCarrier"
                } else {
                    "SIM ${index + 1}"
                }
                SimSlotInfo(
                    slotIndex = index,
                    label = label,
                    carrierName = cleanCarrier,
                    accountHandle = handle,
                )
            }
        }

        // Fallback if permission not granted yet or single slot: expose SIM 1 & SIM 2 UI slots safely
        val count = carrierNamesBySlot.size.coerceAtLeast(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) 2 else 1)
        return (0 until count.coerceAtMost(2)).map { idx ->
            val carrier = carrierNamesBySlot[idx].orEmpty()
            SimSlotInfo(
                slotIndex = idx,
                label = if (carrier.isNotBlank()) "SIM ${idx + 1} • $carrier" else "SIM ${idx + 1}",
                carrierName = carrier,
                accountHandle = null,
            )
        }
    }
}
