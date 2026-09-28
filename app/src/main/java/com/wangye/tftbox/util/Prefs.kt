package com.wangye.tftbox.util

import android.content.Context

/**
 * 一点点设置项，用 SharedPreferences 就够，不值得上 DataStore。
 */
object Prefs {

    private const val FILE = "settings"
    private const val KEY_WATCH_CLIPBOARD = "watch_clipboard"
    private const val KEY_AUTO_START = "auto_start"
    private const val KEY_AUTO_BALL = "auto_ball"
    private const val KEY_DEBUG_SHOT = "debug_shot"
    private const val KEY_BALL_X = "ball_x"
    private const val KEY_BALL_Y = "ball_y"
    private const val KEY_APP_MODE = "app_mode"
    private const val KEY_MODE_CHOSEN = "mode_chosen"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_PALETTE = "palette_id"
    private const val UNSET = Int.MIN_VALUE

    private const val DEFAULT_THEME_MODE = "dark"
    private const val DEFAULT_PALETTE = "violet"

    private fun sp(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 打开 App 时自动检查剪贴板里有没有疑似阵容码 */
    fun isWatchClipboard(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_WATCH_CLIPBOARD, true)

    fun setWatchClipboard(ctx: Context, value: Boolean) =
        sp(ctx).edit().putBoolean(KEY_WATCH_CLIPBOARD, value).apply()

    /**
     * 调试模式：识别失败时把那张截图存进相册，方便排查问题。
     * 默认关 —— 平时用不着往相册里塞东西。
     */
    fun isDebugShot(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_DEBUG_SHOT, false)

    fun setDebugShot(ctx: Context, value: Boolean) =
        sp(ctx).edit().putBoolean(KEY_DEBUG_SHOT, value).apply()

    /** 打开 App 就自动把悬浮球挂起来。默认开。 */
    fun isAutoBall(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_AUTO_BALL, true)

    fun setAutoBall(ctx: Context, value: Boolean) =
        sp(ctx).edit().putBoolean(KEY_AUTO_BALL, value).apply()

    /** 开机自动拉起悬浮球 */
    fun isAutoStart(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_AUTO_START, false)

    fun setAutoStart(ctx: Context, value: Boolean) =
        sp(ctx).edit().putBoolean(KEY_AUTO_START, value).apply()

    /** 当前模式：tft / sudoku。默认金铲铲。 */
    fun appMode(ctx: Context): String =
        sp(ctx).getString(KEY_APP_MODE, "tft") ?: "tft"

    fun setAppMode(ctx: Context, value: String) =
        sp(ctx).edit().putString(KEY_APP_MODE, value).apply()

    /** 是否已经选过一次模式。没选过就先弹选择页。 */
    fun isModeChosen(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_MODE_CHOSEN, false)

    fun setModeChosen(ctx: Context, value: Boolean) =
        sp(ctx).edit().putBoolean(KEY_MODE_CHOSEN, value).apply()

    /** 明暗模式：system / light / dark。默认深色。 */
    fun themeMode(ctx: Context): String =
        sp(ctx).getString(KEY_THEME_MODE, DEFAULT_THEME_MODE) ?: DEFAULT_THEME_MODE

    fun setThemeMode(ctx: Context, value: String) =
        sp(ctx).edit().putString(KEY_THEME_MODE, value).apply()

    /** 配色方案 id。默认紫夜。 */
    fun paletteId(ctx: Context): String =
        sp(ctx).getString(KEY_PALETTE, DEFAULT_PALETTE) ?: DEFAULT_PALETTE

    fun setPaletteId(ctx: Context, value: String) =
        sp(ctx).edit().putString(KEY_PALETTE, value).apply()

    /** 记住悬浮球停在哪，下次过来还在原位 */
    fun ballPos(ctx: Context, defaultX: Int, defaultY: Int): Pair<Int, Int> {
        val s = sp(ctx)
        val x = s.getInt(KEY_BALL_X, UNSET)
        val y = s.getInt(KEY_BALL_Y, UNSET)
        return if (x == UNSET || y == UNSET) defaultX to defaultY else x to y
    }

    fun saveBallPos(ctx: Context, x: Int, y: Int) {
        sp(ctx).edit().putInt(KEY_BALL_X, x).putInt(KEY_BALL_Y, y).apply()
    }
}
