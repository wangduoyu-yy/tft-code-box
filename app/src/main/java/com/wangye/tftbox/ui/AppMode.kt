package com.wangye.tftbox.ui

/**
 * App 的两副面孔。
 *
 * 每个模式有自己独立的界面和浮窗内容；主题、权限、悬浮球开关这些是共用的。
 */
enum class AppMode(
    val id: String,
    val label: String,
    val blurb: String,
    val emoji: String,
) {
    TFT(
        id = "tft",
        label = "金铲铲",
        blurb = "存阵容码，游戏里点悬浮球一键复制",
        emoji = "🎮",
    ),
    SUDOKU(
        id = "sudoku",
        label = "数独",
        blurb = "纯粹的九宫格，随时来一局",
        emoji = "🧩",
    );

    companion object {
        fun from(id: String?): AppMode = entries.firstOrNull { it.id == id } ?: TFT
    }
}
