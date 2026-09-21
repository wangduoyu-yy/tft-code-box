package com.wangye.tftbox.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/** 明暗模式 */
enum class ThemeMode(val id: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    companion object {
        fun from(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/**
 * 一套配色。只定几个种子色，具体方案的其余颜色由下面推导，
 * 这样加一套新主题只要填 8 个颜色。
 */
data class Palette(
    val id: String,
    val name: String,
    /** 强调色：按钮、选中态、浮窗描边 */
    val primary: Color,
    /** 次要强调色 */
    val secondary: Color,
    val darkBg: Color,
    val darkSurface: Color,
    val darkSurfaceVariant: Color,
    val lightBg: Color,
    val lightSurface: Color,
    val lightSurfaceVariant: Color,
)

val Palettes: List<Palette> = listOf(
    Palette(
        id = "gold", name = "金铲铲金",
        primary = Color(0xFFFFD54F), secondary = Color(0xFF7FB2F0),
        darkBg = Color(0xFF121216), darkSurface = Color(0xFF1C1C24), darkSurfaceVariant = Color(0xFF26262F),
        lightBg = Color(0xFFFFFCF5), lightSurface = Color(0xFFFFFFFF), lightSurfaceVariant = Color(0xFFF3EFE6),
    ),
    Palette(
        id = "ocean", name = "深海蓝",
        primary = Color(0xFF4FC3F7), secondary = Color(0xFFFFB74D),
        darkBg = Color(0xFF0C1319), darkSurface = Color(0xFF15212B), darkSurfaceVariant = Color(0xFF1E2D3A),
        lightBg = Color(0xFFF6FBFE), lightSurface = Color(0xFFFFFFFF), lightSurfaceVariant = Color(0xFFE4F0F7),
    ),
    Palette(
        id = "sakura", name = "樱花粉",
        primary = Color(0xFFF48FB1), secondary = Color(0xFF81C7F5),
        darkBg = Color(0xFF17131A), darkSurface = Color(0xFF221B26), darkSurfaceVariant = Color(0xFF2D2331),
        lightBg = Color(0xFFFFF8FA), lightSurface = Color(0xFFFFFFFF), lightSurfaceVariant = Color(0xFFF9E8EE),
    ),
    Palette(
        id = "forest", name = "森野绿",
        primary = Color(0xFF81C784), secondary = Color(0xFFFFB74D),
        darkBg = Color(0xFF0F1611), darkSurface = Color(0xFF19241C), darkSurfaceVariant = Color(0xFF223027),
        lightBg = Color(0xFFF6FCF7), lightSurface = Color(0xFFFFFFFF), lightSurfaceVariant = Color(0xFFE6F2E8),
    ),
    Palette(
        id = "violet", name = "紫夜",
        primary = Color(0xFFB39DDB), secondary = Color(0xFF4DD0E1),
        darkBg = Color(0xFF13111C), darkSurface = Color(0xFF1D1A29), darkSurfaceVariant = Color(0xFF272234),
        lightBg = Color(0xFFFAF8FF), lightSurface = Color(0xFFFFFFFF), lightSurfaceVariant = Color(0xFFEDE8F8),
    ),
)

fun paletteById(id: String?): Palette =
    Palettes.firstOrNull { it.id == id } ?: Palettes.first()

/** 当前配色，给需要拿到原始调色板的组件用（比如设置页里的色卡） */
val LocalPalette = staticCompositionLocalOf { Palettes.first() }

private fun darkScheme(p: Palette) = darkColorScheme(
    primary = p.primary,
    onPrimary = Color(0xFF1A1A1A),
    primaryContainer = p.primary.copy(alpha = 0.20f).compositeOver(p.darkSurface),
    onPrimaryContainer = p.primary,
    secondary = p.secondary,
    onSecondary = Color(0xFF10161F),
    background = p.darkBg,
    onBackground = Color(0xFFEDEDF2),
    surface = p.darkSurface,
    onSurface = Color(0xFFEDEDF2),
    surfaceVariant = p.darkSurfaceVariant,
    onSurfaceVariant = Color(0xFF9A9AA8),
    outline = Color(0xFF3A3A45),
    error = Color(0xFFFF6B6B),
    errorContainer = Color(0xFF4A2222),
    onErrorContainer = Color(0xFFFFD9D9),
)

private fun lightScheme(p: Palette) = lightColorScheme(
    primary = p.primary,
    onPrimary = Color(0xFF201A00),
    primaryContainer = p.primary.copy(alpha = 0.28f).compositeOver(p.lightSurface),
    onPrimaryContainer = Color(0xFF3A2E00),
    secondary = p.secondary,
    onSecondary = Color(0xFFFFFFFF),
    background = p.lightBg,
    onBackground = Color(0xFF1B1B1F),
    surface = p.lightSurface,
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = p.lightSurfaceVariant,
    onSurfaceVariant = Color(0xFF6B6B75),
    outline = Color(0xFFC9C4BA),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410E0B),
)

@Composable
fun TftTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    paletteId: String? = null,
    content: @Composable () -> Unit,
) {
    val palette = paletteById(paletteId)
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = if (dark) darkScheme(palette) else lightScheme(palette),
            content = content,
        )
    }
}
