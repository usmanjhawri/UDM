package com.nexonic.downloadmanager.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.nexonic.downloadmanager.core.FileCategory

/** A restrained cobalt accent, paired with neutral surfaces rather than saturated panels. */
val Brand = Color(0xFF3B53D6)
val BrandDark = Color(0xFFA9B8FF)
val Success = Color(0xFF16A34A)
val Warning = Color(0xFFD97706)

/** Shared shape scale for screens, cards, controls, and bottom sheets. */
private val NexonicShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val Light = lightColorScheme(
    primary = Brand, onPrimary = Color.White,
    primaryContainer = Color(0xFFE7ECFF), onPrimaryContainer = Color(0xFF1C316D),
    secondary = Color(0xFF566079), onSecondary = Color.White,
    background = Color(0xFFF7F8FC), onBackground = Color(0xFF19202E),
    surface = Color.White, onSurface = Color(0xFF19202E),
    surfaceVariant = Color(0xFFEFF1F7), onSurfaceVariant = Color(0xFF4E596B),
    outline = Color(0xFFBFC6D3), outlineVariant = Color(0xFFE2E6EE),
)
private val Dark = darkColorScheme(
    primary = BrandDark, onPrimary = Color(0xFF101D43),
    primaryContainer = Color(0xFF2A3C69), onPrimaryContainer = Color(0xFFE0E8FF),
    secondary = Color(0xFFACB9D3), onSecondary = Color(0xFF172034),
    background = Color(0xFF0D111A), onBackground = Color(0xFFE9EDF6),
    surface = Color(0xFF171D29), onSurface = Color(0xFFE9EDF6),
    surfaceVariant = Color(0xFF252D3B), onSurfaceVariant = Color(0xFFB2BCD0),
    outline = Color(0xFF657084), outlineVariant = Color(0xFF3C4658),
)

@Composable
fun NexonicTheme(content: @Composable () -> Unit) {
    val scheme = com.nexonic.downloadmanager.core.ColorSchemeChoice.named(Appearance.scheme)
    val dark = scheme?.dark ?: when (Appearance.choice) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val colors = when {
        scheme == null -> if (dark) Dark else Light
        // A colour scheme: its accent, background and panels over the light or dark base.
        dark -> Dark.copy(primary = Color(scheme.accent), onPrimary = Color.Black, background = Color(scheme.background),
            surface = Color(scheme.surface), primaryContainer = Color(scheme.accent).copy(alpha = 0.25f).compositeOver(Color(scheme.surface)),
            onPrimaryContainer = Color.White)
        else -> Light.copy(primary = Color(scheme.accent), onPrimary = Color.White, background = Color(scheme.background),
            surface = Color(scheme.surface), primaryContainer = Color(scheme.accent).copy(alpha = 0.15f).compositeOver(Color.White),
            onPrimaryContainer = Color.Black)
    }
    MaterialTheme(colorScheme = colors, shapes = NexonicShapes,
        typography = typography(Appearance.font, Appearance.textScale), content = content)
}

/** The chosen font family and text size applied to every text style of the app. */
private fun typography(font: String, scale: Int): androidx.compose.material3.Typography {
    val base = androidx.compose.material3.Typography()
    val family = when (font) {
        "sans" -> androidx.compose.ui.text.font.FontFamily.SansSerif
        "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
        "mono" -> androidx.compose.ui.text.font.FontFamily.Monospace
        else -> null
    }
    if (family == null && scale == 100) return base
    val f = scale.coerceIn(85, 150) / 100f
    fun androidx.compose.ui.text.TextStyle.adjust() = copy(
        fontFamily = family ?: fontFamily,
        fontSize = fontSize * f,
        lineHeight = if (lineHeight.isSp) lineHeight * f else lineHeight,
    )
    return base.copy(
        displayLarge = base.displayLarge.adjust(), displayMedium = base.displayMedium.adjust(), displaySmall = base.displaySmall.adjust(),
        headlineLarge = base.headlineLarge.adjust(), headlineMedium = base.headlineMedium.adjust(), headlineSmall = base.headlineSmall.adjust(),
        titleLarge = base.titleLarge.adjust(), titleMedium = base.titleMedium.adjust(), titleSmall = base.titleSmall.adjust(),
        bodyLarge = base.bodyLarge.adjust(), bodyMedium = base.bodyMedium.adjust(), bodySmall = base.bodySmall.adjust(),
        labelLarge = base.labelLarge.adjust(), labelMedium = base.labelMedium.adjust(), labelSmall = base.labelSmall.adjust(),
    )
}

/** Settings → Appearance: the same as the phone, light or dark, and a colour scheme (changes right away). */
object Appearance {
    var choice by androidx.compose.runtime.mutableStateOf("system")
    var scheme by androidx.compose.runtime.mutableStateOf("")
    var font by androidx.compose.runtime.mutableStateOf("system")
    var textScale by androidx.compose.runtime.mutableStateOf(100)
    /** Changes when the size, speed or date units change, so the list is drawn again. */
    var units by androidx.compose.runtime.mutableStateOf(0)
}

fun categoryColor(c: FileCategory): Color = when (c) {
    FileCategory.VIDEO -> Color(0xFF7C3AED)
    FileCategory.MUSIC -> Color(0xFFDB2777)
    FileCategory.COMPRESSED -> Color(0xFFD97706)
    FileCategory.DOCUMENTS -> Color(0xFF2563EB)
    FileCategory.PROGRAMS -> Color(0xFF059669)
    FileCategory.IMAGES -> Color(0xFF0891B2)
    FileCategory.MODELS -> Color(0xFF9333EA)
    FileCategory.OTHER -> Color(0xFF64748B)
}
