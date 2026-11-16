package com.example.ui.theme

import androidx.compose.ui.graphics.Color

enum class ThemeMode(val labelAr: String) {
    SYSTEM("تلقائي"),
    LIGHT("فاتح"),
    DARK("داكن"),
}

enum class CalmPalette(val labelAr: String) {
    SLATE("نيلي هادئ"),
    SAGE("أخضر مريح"),
    SAND("رملي دافئ"),
    DYNAMIC("ألوان الجهاز"),
}

// 1. Calm Slate Indigo (Low-eye-strain refined blue-slate)
val SlatePrimaryLight = Color(0xFF34597E)
val SlateOnPrimaryLight = Color(0xFFFFFFFF)
val SlateContainerLight = Color(0xFFD7E5F5)
val SlateOnContainerLight = Color(0xFF102A43)
val SlateSecondaryLight = Color(0xFF476A6F)
val SlateSecondaryContainerLight = Color(0xFFD5E8EA)
val SlateTertiaryLight = Color(0xFF6B5B7B)

val SlatePrimaryDark = Color(0xFF9EC1E6)
val SlateOnPrimaryDark = Color(0xFF0B253E)
val SlateContainerDark = Color(0xFF213F5C)
val SlateOnContainerDark = Color(0xFFD7E5F5)
val SlateSecondaryDark = Color(0xFFA7C9CE)
val SlateSecondaryContainerDark = Color(0xFF2D4B4F)
val SlateTertiaryDark = Color(0xFFCBBADF)

// 2. Soft Sage Emerald
val SagePrimaryLight = Color(0xFF2F6B5D)
val SageOnPrimaryLight = Color(0xFFFFFFFF)
val SageContainerLight = Color(0xFFCFEBE3)
val SageOnContainerLight = Color(0xFF0E3028)
val SageSecondaryLight = Color(0xFF4E635D)
val SageSecondaryContainerLight = Color(0xFFD7E5E0)
val SageTertiaryLight = Color(0xFF54627A)

val SagePrimaryDark = Color(0xFF97CFC1)
val SageOnPrimaryDark = Color(0xFF0A332A)
val SageContainerDark = Color(0xFF1D4D42)
val SageOnContainerDark = Color(0xFFCFEBE3)
val SageSecondaryDark = Color(0xFFB3C7C1)
val SageSecondaryContainerDark = Color(0xFF354943)
val SageTertiaryDark = Color(0xFFB4C5E0)

// 3. Warm Sand Taupe
val SandPrimaryLight = Color(0xFF7C5A3B)
val SandOnPrimaryLight = Color(0xFFFFFFFF)
val SandContainerLight = Color(0xFFF3E2D2)
val SandOnContainerLight = Color(0xFF33200F)
val SandSecondaryLight = Color(0xFF655D54)
val SandSecondaryContainerLight = Color(0xFFEBE2D8)
val SandTertiaryLight = Color(0xFF4C665B)

val SandPrimaryDark = Color(0xFFE2BE9D)
val SandOnPrimaryDark = Color(0xFF3B2410)
val SandContainerDark = Color(0xFF5A3E25)
val SandOnContainerDark = Color(0xFFF3E2D2)
val SandSecondaryDark = Color(0xFFCEC4B9)
val SandSecondaryContainerDark = Color(0xFF48413A)
val SandTertiaryDark = Color(0xFFAECAC0)

// Shared serene neutrals
val SurfaceLight = Color(0xFFF8F9FB)
val SurfaceContainerLowLight = Color(0xFFF1F3F6)
val SurfaceContainerLight = Color(0xFFE9EDF2)
val SurfaceContainerHighLight = Color(0xFFE1E6ED)
val OnSurfaceLight = Color(0xFF1B2028)
val OnSurfaceVariantLight = Color(0xFF58606E)
val OutlineLight = Color(0xFF88919F)

val SurfaceDark = Color(0xFF101318)
val SurfaceContainerLowDark = Color(0xFF171B22)
val SurfaceContainerDark = Color(0xFF1D222B)
val SurfaceContainerHighDark = Color(0xFF262C37)
val OnSurfaceDark = Color(0xFFE5E8EF)
val OnSurfaceVariantDark = Color(0xFFA9B1BF)
val OutlineDark = Color(0xFF707887)

// Brand action colors (calm toned)
val WhatsAppGreen = Color(0xFF259E56)
val TelegramBlue = Color(0xFF2E95C9)
val InstaPayPurple = Color(0xFF6C4F94)
val ErrorRed = Color(0xFFC64B4B)
