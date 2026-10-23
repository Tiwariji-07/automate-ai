package com.devfest.automation.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.devfest.automation.R

/** Colours from `docs/design/mockup.html`. Amber When, violet Only if, teal Then. */
@Immutable
data class AppColors(
    val bg: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val line: Color,
    val brand: Color,
    val onBrand: Color,
    val brandSoft: Color,
    val whenColor: Color,
    val whenSoft: Color,
    val ifColor: Color,
    val ifSoft: Color,
    val thenColor: Color,
    val thenSoft: Color,
    val ok: Color,
    val fail: Color,
    val failSoft: Color,
    val warn: Color,
    val idle: Color,
    val composer: Color
)

val LightColors = AppColors(
    bg = Color(0xFFF2F4F9), surface = Color.White, ink = Color(0xFF0F1630), muted = Color(0xFF4F576B),
    faint = Color(0xFF646B7E), line = Color(0xFFDCE1EB), brand = Color(0xFF1337EC), onBrand = Color.White,
    brandSoft = Color(0xFFE6EAFE), whenColor = Color(0xFFA86400), whenSoft = Color(0xFFFFF0D2),
    ifColor = Color(0xFF6537E6), ifSoft = Color(0xFFEEE7FF), thenColor = Color(0xFF087A6D), thenSoft = Color(0xFFD9F3EE),
    ok = Color(0xFF0E7A3C), fail = Color(0xFFC62F35), failSoft = Color(0xFFFDE8E8), warn = Color(0xFF8A5000),
    idle = Color(0xFFB4BACA), composer = Color(0xFF0F1630)
)

val DarkColors = AppColors(
    bg = Color.Black, surface = Color(0xFF14161D), ink = Color(0xFFEEF0F6), muted = Color(0xFFB0B6C5),
    faint = Color(0xFF9AA1B2), line = Color(0xFF262A35), brand = Color(0xFF6E86FF), onBrand = Color(0xFF06091A),
    brandSoft = Color(0xFF1A2050), whenColor = Color(0xFFFFB547), whenSoft = Color(0xFF2D2210),
    ifColor = Color(0xFFB49BFF), ifSoft = Color(0xFF231B3D), thenColor = Color(0xFF45D9C6), thenSoft = Color(0xFF0E2B28),
    ok = Color(0xFF4ADE80), fail = Color(0xFFFF6B6F), failSoft = Color(0xFF33161A), warn = Color(0xFFFFC266),
    idle = Color(0xFF474D5C), composer = Color(0xFF1D2030)
)

private val LocalColors = staticCompositionLocalOf { LightColors }

object App {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalColors.current
}

private fun bricolage(weight: Int) = Font(
    R.font.bricolage_grotesque, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

private fun instrument(weight: Int) = Font(
    R.font.instrument_sans, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

/** Flow titles, written as sentences. */
val Display = FontFamily(bricolage(500), bricolage(600), bricolage(700))

/** Everything else. */
val Body = FontFamily(instrument(400), instrument(500), instrument(600))

object Type {
    val wordmark = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.03).em)
    val hero = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 31.sp, lineHeight = 33.sp, letterSpacing = (-0.035).em)
    val bar = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, letterSpacing = (-0.01).em)
    val rowTitle = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 21.sp, letterSpacing = (-0.015).em)
    val big = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 28.sp, letterSpacing = (-0.02).em)
    val body = TextStyle(fontFamily = Body, fontSize = 15.sp, lineHeight = 21.sp)
    val bodyStrong = body.copy(fontWeight = FontWeight.SemiBold)
    val small = TextStyle(fontFamily = Body, fontSize = 13.5.sp, lineHeight = 18.sp)
    val smallStrong = small.copy(fontWeight = FontWeight.SemiBold)
    val tiny = TextStyle(fontFamily = Body, fontSize = 12.5.sp, lineHeight = 16.sp)
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.brand, onPrimary = colors.onBrand, background = colors.bg, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink, onSurfaceVariant = colors.muted, outline = colors.line,
            outlineVariant = colors.line, error = colors.fail, surfaceContainerHigh = colors.surface,
            surfaceContainer = colors.surface, primaryContainer = colors.brandSoft, onPrimaryContainer = colors.ink
        )
    } else {
        lightColorScheme(
            primary = colors.brand, onPrimary = colors.onBrand, background = colors.bg, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink, onSurfaceVariant = colors.muted, outline = colors.line,
            outlineVariant = colors.line, error = colors.fail, surfaceContainerHigh = colors.surface,
            surfaceContainer = colors.surface, primaryContainer = colors.brandSoft, onPrimaryContainer = colors.ink
        )
    }
    val base = Typography()
    val typography = Typography(
        bodyLarge = base.bodyLarge.copy(fontFamily = Body),
        bodyMedium = base.bodyMedium.copy(fontFamily = Body),
        bodySmall = base.bodySmall.copy(fontFamily = Body),
        labelLarge = base.labelLarge.copy(fontFamily = Body, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontFamily = Body),
        labelSmall = base.labelSmall.copy(fontFamily = Body),
        titleLarge = base.titleLarge.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = Body, fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontFamily = Display, fontWeight = FontWeight.Bold)
    )
    CompositionLocalProvider(LocalColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
