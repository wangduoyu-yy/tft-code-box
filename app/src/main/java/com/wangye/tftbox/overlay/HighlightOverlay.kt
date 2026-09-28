package com.wangye.tftbox.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * 画面上要框的一格。
 *
 * [conflict] 为 true 表示这格**可能是填错的**，用红色标出来 ——
 * 跟「该填/该空」的提示不是一回事，所以单独一个标志。
 */
data class HighlightCell(
    val rect: RectF,
    val filled: Boolean = false,
    val conflict: Boolean = false,
)

/**
 * 直接在游戏画面上把「该填的格子」框出来 —— 比看文字快得多。
 *
 * 不抢触摸（FLAG_NOT_TOUCHABLE），所以框亮着的时候游戏照样能操作。
 */
class HighlightOverlay(
    private val context: Context,
    private val accent: Int,
) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: HighlightView? = null

    /** 现在画面上有没有框 —— 用来实现「点一下框出来、再点一下关掉」 */
    val isShowing: Boolean get() = view != null

    fun show(cells: List<HighlightCell>) {
        dismiss()
        if (cells.isEmpty()) return

        val v = HighlightView(context, accent)
        v.setTargets(cells)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        runCatching { windowManager.addView(v, params) }.onSuccess { view = v }
    }

    fun dismiss() {
        val v = view ?: return
        view = null
        runCatching { windowManager.removeView(v) }
    }
}

private class HighlightView(context: Context, private val accent: Int) : View(context) {

    private val density = resources.displayMetrics.density
    private var cells: List<HighlightCell> = emptyList()
    private var alpha = 1f
    private val locOnScreen = IntArray(2)

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
        color = accent
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    private val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = Color.WHITE
    }
    /** 「该填」用的实心块。用强调色但半透明，底下的格子还看得见 */
    private val solidFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = accent
    }
    private val badGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WARN }
    private val badStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
        color = WARN
    }

    private var animator: ValueAnimator? = null

    init {
        // 呼吸效果，静态的框在花哨的游戏画面上容易被忽略
        animator = ValueAnimator.ofFloat(0.45f, 1f).apply {
            duration = 700
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                alpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun setTargets(targets: List<HighlightCell>) {
        cells = targets
        invalidate()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        glow.alpha = (alpha * 60).toInt().coerceIn(0, 255)
        stroke.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        cross.alpha = (alpha * 220).toInt().coerceIn(0, 255)
        // 半透明：底下的格子还能透出来，不至于把棋盘糊住
        solidFill.alpha = (alpha * 150).toInt().coerceIn(0, 255)

        // 要画的坐标是从**整屏截图**里算出来的（含状态栏），
        // 但浮窗的原点会往下偏一个状态栏 / 挖孔的高度 ——
        // 直接画的话整片框会下沉一两行。
        // 问一下自己在屏幕上的真实原点，把坐标系摆正。
        getLocationOnScreen(locOnScreen)
        val saved = canvas.save()
        canvas.translate(-locOnScreen[0].toFloat(), -locOnScreen[1].toFloat())

        // 每一格画**自己**的符号。之前所有格子共用第一条提示的类型，
        // 结果「留空 填满 填满 填满 填满 留空」被画成清一色六个叉，
        // 用户照着填就全错了。
        for (cell in cells) {
            val r = cell.rect

            if (cell.conflict) {
                // 可能是填错的格子，红色标出来
                canvas.drawRoundRect(r, 8f * density, 8f * density, badGlow)
                canvas.drawRoundRect(r, 8f * density, 8f * density, badStroke)
                continue
            }

            canvas.drawRoundRect(r, 8f * density, 8f * density, glow)
            canvas.drawRoundRect(r, 8f * density, 8f * density, stroke)

            val cx = r.centerX()
            val cy = r.centerY()
            val s = minOf(r.width(), r.height()) * 0.22f

            if (cell.filled) {
                // 该填 → 画一个**实心方块**。
                // 原来画的是「+」，跟「×」都是细线条，一眼扫过去分不清 ——
                // 实心块跟叉的区别一眼就能看出来，而且它本身就长得像「涂满」后的样子。
                val half = minOf(r.width(), r.height()) * 0.28f
                canvas.drawRoundRect(
                    cx - half, cy - half, cx + half, cy + half,
                    4f * density, 4f * density, solidFill,
                )
            } else {
                // 该空 → 画个叉
                canvas.drawLine(cx - s, cy - s, cx + s, cy + s, cross)
                canvas.drawLine(cx + s, cy - s, cx - s, cy + s, cross)
            }
        }

        canvas.restoreToCount(saved)
    }

    private companion object {
        /** 标错格用的警示色 */
        const val WARN = 0xFFFF5252.toInt()
    }
}
