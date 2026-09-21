package com.wangye.tftbox.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue

/** 浮窗里那些小圆角背景，全部按当前主题色生成 */
object ChipBackground {

    /** 「复制」小胶囊：淡填充 + 半透明描边圆角矩形 */
    fun create(context: Context, accent: Int, fill: Int): GradientDrawable {
        val density = context.resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 100f * density
            setColor(fill)
            setStroke(
                (1.5f * density).toInt().coerceAtLeast(1),
                Color.argb(0xAA, Color.red(accent), Color.green(accent), Color.blue(accent))
            )
        }
    }

    /** 面板/搜索框这类圆角背景 */
    fun rounded(context: Context, fill: Int, radiusDp: Float, strokeColor: Int = 0): GradientDrawable {
        val density = context.resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * density
            setColor(fill)
            if (strokeColor != 0) setStroke((1f * density).toInt().coerceAtLeast(1), strokeColor)
        }
    }

    fun dp(context: Context, value: Float): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics
        ).toInt()
}
