package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File
import kotlin.math.abs

private data class AvatarTone(
    val containerLight: Color,
    val contentLight: Color,
    val containerDark: Color,
    val contentDark: Color,
)

private val avatarPalette = listOf(
    AvatarTone(Color(0xFFD9E6F7), Color(0xFF1A3A5C), Color(0xFF213C5B), Color(0xFFCCE1FA)), // Slate Indigo
    AvatarTone(Color(0xFFD2EBE2), Color(0xFF104234), Color(0xFF1B4539), Color(0xFFC5EBDC)), // Sage Mint
    AvatarTone(Color(0xFFF3E0D0), Color(0xFF532F14), Color(0xFF52351F), Color(0xFFF6DEC9)), // Warm Sand
    AvatarTone(Color(0xFFE6DCF5), Color(0xFF3E2663), Color(0xFF3B285B), Color(0xFFE5D9FA)), // Soft Plum
    AvatarTone(Color(0xFFD6EEF2), Color(0xFF124652), Color(0xFF19424C), Color(0xFFC9EEF5)), // Ocean Teal
    AvatarTone(Color(0xFFF7DCE0), Color(0xFF631E29), Color(0xFF59232C), Color(0xFFF8D6DC)), // Muted Rose
    AvatarTone(Color(0xFFECE6CC), Color(0xFF4B4112), Color(0xFF473F19), Color(0xFFF2EBC9)), // Olive Gold
    AvatarTone(Color(0xFFDCE4EC), Color(0xFF233240), Color(0xFF293542), Color(0xFFD7E2EC)), // Steel Blue
)

fun initialsForName(name: String, fallbackPhone: String = ""): String {
    val clean = name.trim()
    if (clean.isEmpty()) {
        return fallbackPhone.trim().takeLast(2).ifBlank { "#" }
    }
    val parts = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
    return if (parts.size >= 2) {
        val first = parts.first().firstOrNull()?.uppercaseChar() ?: '#'
        val second = parts.last().firstOrNull()?.uppercaseChar() ?: ' '
        if (first.isLetter() && second.isLetter()) "$first$second" else first.toString()
    } else {
        clean.firstOrNull()?.uppercase() ?: "#"
    }
}

@Composable
fun ContactAvatarBadge(
    displayName: String,
    phone: String = "",
    photoUri: String? = null,
    colorSeed: Int = 0,
    showPhoto: Boolean = true,
    size: Dp = 46.dp,
    isBlocked: Boolean = false,
    isMuted: Boolean = false,
) {
    val isDark = isSystemInDarkTheme()
    val seedIdx = rememberSeedIndex(colorSeed, displayName, phone)
    val tone = avatarPalette[seedIdx]
    val bgColor = if (isBlocked) {
        MaterialTheme.colorScheme.errorContainer
    } else if (isDark) {
        tone.containerDark
    } else {
        tone.containerLight
    }
    val fgColor = if (isBlocked) {
        MaterialTheme.colorScheme.onErrorContainer
    } else if (isDark) {
        tone.contentDark
    } else {
        tone.contentLight
    }

    val hasPhoto = showPhoto && !photoUri.isNullOrBlank() && File(photoUri).exists()
    Box(modifier = Modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center,
        ) {
            if (hasPhoto) {
                AsyncImage(
                    model = File(photoUri!!),
                    contentDescription = displayName.ifBlank { phone },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                val initials = initialsForName(displayName, phone)
                val fontSize = when {
                    size >= 60.dp -> 20.sp
                    size >= 44.dp -> 15.sp
                    else -> 13.sp
                }
                Text(
                    text = initials,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    color = fgColor,
                    maxLines = 1,
                )
            }
        }

        if (isBlocked || isMuted) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isBlocked) Icons.Rounded.Block else Icons.Rounded.NotificationsOff,
                    contentDescription = if (isBlocked) "محظور" else "مكتوم",
                    tint = Color.White,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
    }
}

private fun rememberSeedIndex(colorSeed: Int, name: String, phone: String): Int {
    val raw = if (colorSeed > 0) colorSeed else abs((name.ifBlank { phone }).hashCode())
    return abs(raw) % avatarPalette.size
}
