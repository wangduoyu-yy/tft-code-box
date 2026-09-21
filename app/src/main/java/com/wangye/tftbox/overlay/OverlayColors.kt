package com.wangye.tftbox.overlay

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.toArgb
import com.wangye.tftbox.ui.ThemeMode
import com.wangye.tftbox.ui.paletteById
import com.wangye.tftbox.util.Prefs

/**
 * 浮窗和悬浮球是经典 View，吃不到 Compose 的 MaterialTheme，
 * 所以把当前主题换算成一组 ARGB 整数传进去，保证浮窗跟 App 里一个风格。
 */
data class OverlayColors(
    val dark: Boolean,
    val accent: Int,
    val panelFill: Int,
    val panelStroke: Int,
    val textMain: Int,
    val textSub: Int,
    val textHint: Int,
    val rowRipple: Int,
    val chipFill: Int,
    /** 关闭悬浮球按钮的警示色，不需要跟主题走 */
    val danger: Int = 0xFFFF6B6B.toInt(),
) {
    companion object {

        fun resolve(context: Context): OverlayColors {
            val palette = paletteById(Prefs.paletteId(context))
            val accent = palette.primary.toArgb()
            val dark = when (ThemeMode.from(Prefs.themeMode(context))) {
                ThemeMode.SYSTEM -> isSystemDark(context)
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            return if (dark) {
                OverlayColors(
                    dark = true,
                    accent = accent,
                    panelFill = withAlpha(palette.darkSurface.toArgb(), 0xF0),
                    panelStroke = withAlpha(accent, 0x44),
                    textMain = 0xFFEDEDF2.toInt(),
                    textSub = 0xFF9A9AA8.toInt(),
                    textHint = 0xFF6E6E7A.toInt(),
                    rowRipple = withAlpha(accent, 0x30),
                    chipFill = 0x14FFFFFF,
                )
            } else {
                OverlayColors(
                    dark = false,
                    accent = accent,
                    panelFill = withAlpha(palette.lightSurface.toArgb(), 0xF7),
                    panelStroke = withAlpha(accent, 0x77),
                    textMain = 0xFF1B1B1F.toInt(),
                    textSub = 0xFF6B6B75.toInt(),
                    textHint = 0xFF9A9AA8.toInt(),
                    rowRipple = withAlpha(accent, 0x30),
                    chipFill = 0x12000000,
                )
            }
        }

        private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)

        private fun isSystemDark(context: Context): Boolean =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }
}
