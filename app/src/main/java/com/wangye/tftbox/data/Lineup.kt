package com.wangye.tftbox.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一条阵容记录。
 *
 * [code] 是金铲铲里那串导入用的阵容码，原样保存不做解析——我们不知道它的编码格式，
 * 也不该去猜。其余字段纯粹是用户自己的标注，用来在列表里认得出是哪套阵容。
 */
@Entity(tableName = "lineups")
data class Lineup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** 阵容名，用户自己起，列表主标题 */
    val title: String,
    /** 阵容码原文 */
    val code: String,
    /** 标签，逗号分隔，比如「九五,双海克斯」 */
    val tags: String = "",
    /** 赛季/版本，比如「S13」 */
    val season: String = "",
    /** 备注 */
    val note: String = "",
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** 最近一次复制走的时间，用来做「最近使用」排序 */
    val lastUsedAt: Long = 0L,
    val useCount: Int = 0,
) {
    /** 列表里显示的副标题，把非空字段拼起来 */
    val subtitle: String
        get() = listOf(season, tags, note)
            .filter { it.isNotBlank() }
            .joinToString(" · ")

    /** 阵容码太长，列表里只展示头和尾 */
    val codePreview: String
        get() = if (code.length <= 24) code else "${code.take(12)}…${code.takeLast(8)}"
}
