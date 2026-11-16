package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private fun calmLightScheme(palette: CalmPalette): ColorScheme = when (palette) {
    CalmPalette.SAGE -> lightColorScheme(
        primary = SagePrimaryLight,
        onPrimary = SageOnPrimaryLight,
        primaryContainer = SageContainerLight,
        onPrimaryContainer = SageOnContainerLight,
        secondary = SageSecondaryLight,
        onSecondary = SlateOnPrimaryLight,
        secondaryContainer = SageSecondaryContainerLight,
        onSecondaryContainer = SageOnContainerLight,
        tertiary = SageTertiaryLight,
        background = SurfaceLight,
        surface = SurfaceLight,
        surfaceContainerLow = SurfaceContainerLowLight,
        surfaceContainer = SurfaceContainerLight,
        surfaceContainerHigh = SurfaceContainerHighLight,
        onBackground = OnSurfaceLight,
        onSurface = OnSurfaceLight,
        onSurfaceVariant = OnSurfaceVariantLight,
        outline = OutlineLight,
    )
    CalmPalette.SAND -> lightColorScheme(
        primary = SandPrimaryLight,
        onPrimary = SandOnPrimaryLight,
        primaryContainer = SandContainerLight,
        onPrimaryContainer = SandOnContainerLight,
        secondary = SandSecondaryLight,
        onSecondary = SlateOnPrimaryLight,
        secondaryContainer = SandSecondaryContainerLight,
        onSecondaryContainer = SandOnContainerLight,
        tertiary = SandTertiaryLight,
        background = SurfaceLight,
        surface = SurfaceLight,
        surfaceContainerLow = SurfaceContainerLowLight,
        surfaceContainer = SurfaceContainerLight,
        surfaceContainerHigh = SurfaceContainerHighLight,
        onBackground = OnSurfaceLight,
        onSurface = OnSurfaceLight,
        onSurfaceVariant = OnSurfaceVariantLight,
        outline = OutlineLight,
    )
    else -> lightColorScheme(
        primary = SlatePrimaryLight,
        onPrimary = SlateOnPrimaryLight,
        primaryContainer = SlateContainerLight,
        onPrimaryContainer = SlateOnContainerLight,
        secondary = SlateSecondaryLight,
        onSecondary = SlateOnPrimaryLight,
        secondaryContainer = SlateSecondaryContainerLight,
        onSecondaryContainer = SlateOnContainerLight,
        tertiary = SlateTertiaryLight,
        background = SurfaceLight,
        surface = SurfaceLight,
        surfaceContainerLow = SurfaceContainerLowLight,
        surfaceContainer = SurfaceContainerLight,
        surfaceContainerHigh = SurfaceContainerHighLight,
        onBackground = OnSurfaceLight,
        onSurface = OnSurfaceLight,
        onSurfaceVariant = OnSurfaceVariantLight,
        outline = OutlineLight,
    )
}

private fun calmDarkScheme(palette: CalmPalette): ColorScheme = when (palette) {
    CalmPalette.SAGE -> darkColorScheme(
        primary = SagePrimaryDark,
        onPrimary = SageOnPrimaryDark,
        primaryContainer = SageContainerDark,
        onPrimaryContainer = SageOnContainerDark,
        secondary = SageSecondaryDark,
        onSecondary = SageOnPrimaryDark,
        secondaryContainer = SageSecondaryContainerDark,
        onSecondaryContainer = SageOnContainerDark,
        tertiary = SageTertiaryDark,
        background = SurfaceDark,
        surface = SurfaceDark,
        surfaceContainerLow = SurfaceContainerLowDark,
        surfaceContainer = SurfaceContainerDark,
        surfaceContainerHigh = SurfaceContainerHighDark,
        onBackground = OnSurfaceDark,
        onSurface = OnSurfaceDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        outline = OutlineDark,
    )
    CalmPalette.SAND -> darkColorScheme(
        primary = SandPrimaryDark,
        onPrimary = SandOnPrimaryDark,
        primaryContainer = SandContainerDark,
        onPrimaryContainer = SandOnContainerDark,
        secondary = SandSecondaryDark,
        onSecondary = SandOnPrimaryDark,
        secondaryContainer = SandSecondaryContainerDark,
        onSecondaryContainer = SandOnContainerDark,
        tertiary = SandTertiaryDark,
        background = SurfaceDark,
        surface = SurfaceDark,
        surfaceContainerLow = SurfaceContainerLowDark,
        surfaceContainer = SurfaceContainerDark,
        surfaceContainerHigh = SurfaceContainerHighDark,
        onBackground = OnSurfaceDark,
        onSurface = OnSurfaceDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        outline = OutlineDark,
    )
    else -> darkColorScheme(
        primary = SlatePrimaryDark,
        onPrimary = SlateOnPrimaryDark,
        primaryContainer = SlateContainerDark,
        onPrimaryContainer = SlateOnContainerDark,
        secondary = SlateSecondaryDark,
        onSecondary = SlateOnPrimaryDark,
        secondaryContainer = SlateSecondaryContainerDark,
        onSecondaryContainer = SlateOnContainerDark,
        tertiary = SlateTertiaryDark,
        background = SurfaceDark,
        surface = SurfaceDark,
        surfaceContainerLow = SurfaceContainerLowDark,
        surfaceContainer = SurfaceContainerDark,
        surfaceContainerHigh = SurfaceContainerHighDark,
        onBackground = OnSurfaceDark,
        onSurface = OnSurfaceDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        outline = OutlineDark,
    )
}

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun RabtaContactsTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    palette: CalmPalette = CalmPalette.SLATE,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        palette == CalmPalette.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDark -> calmDarkScheme(palette)
        else -> calmLightScheme(palette)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
