package com.wangye.tftbox.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import com.wangye.tftbox.R
import kotlin.math.hypot

/**
 * 悬浮球本体。
 *
 * 拖动逻辑直接写在这里，而不是丢回 Service——球自己就持有 windowManager 和
 * 自己的 LayoutParams，更新位置是它自己的事，Service 只需要知道「停哪了」。
 *
 * 用 Gravity.TOP or Gravity.START，这样 params.x 就是距屏幕左边的距离，换算简单。
 */
class BallView(
    context: Context,
    colors: OverlayColors,
    private val params: WindowManager.LayoutParams,
    // 转屏后宽高互换，Service 会直接改这两个值（见 FloatingService.onScreenChanged）
    var screenWidth: Int,
    var screenHeight: Int,
    private val onTap: () -> Unit,
    private val onPositionChanged: (x: Int, y: Int) -> Unit,
    /** 长按 —— 数独模式下「点一下」是直接给提示不弹面板，「长按」才开面板 */
    private val onLongPress: () -> Unit = {},
) : View(context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE61A1A22.toInt()
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = colors.accent
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colors.accent
        textSize = 19f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private var downRawX = 0f
    private var downRawY = 0f
    private var downTime = 0L
    private var startX = 0
    private var startY = 0
    private var dragging = false
    private var snapAnimator: ValueAnimator? = null

    init {
        isClickable = true
        contentDescription = "悬浮球，点开查看阵容"
    }

    /** 球里那张图。加载失败就退回原来的深色底 +「阵」字，不至于变成一个空圈。 */
    private val ballBitmap: Bitmap? by lazy {
        runCatching { BitmapFactory.decodeResource(resources, R.drawable.ball_face) }.getOrNull()
    }
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val clipPath = Path()
    private val dstRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) / 2f - ringPaint.strokeWidth

        val bitmap = ballBitmap
        if (bitmap != null) {
            // 把方图裁成圆贴上来 —— 圆外自然就没东西了，
            // 所以不需要真的给图片抠一个透明背景出来。
            clipPath.reset()
            clipPath.addCircle(cx, cy, radius, Path.Direction.CW)
            val saved = canvas.save()
            canvas.clipPath(clipPath)
            dstRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
            canvas.drawBitmap(bitmap, null, dstRect, imagePaint)
            canvas.restoreToCount(saved)
        } else {
            canvas.drawCircle(cx, cy, radius, bgPaint)
            val fm = textPaint.fontMetrics
            canvas.drawText("阵", cx, cy - (fm.ascent + fm.descent) / 2f, textPaint)
        }

        canvas.drawCircle(cx, cy, radius, ringPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                snapAnimator?.cancel()
                downRawX = event.rawX
                downRawY = event.rawY
                downTime = System.currentTimeMillis()
                startX = params.x
                startY = params.y
                dragging = false
                alpha = 0.7f
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && hypot(dx, dy) > touchSlop) dragging = true
                if (dragging) {
                    params.x = (startX + dx).toInt().coerceIn(0, (screenWidth - width).coerceAtLeast(0))
                    params.y = (startY + dy).toInt().coerceIn(0, (screenHeight - height).coerceAtLeast(0))
                    applyLayout()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                alpha = 1f
                if (dragging) {
                    snapToEdge()
                } else {
                    performClick()
                    val held = System.currentTimeMillis() - downTime
                    if (held >= LONG_PRESS_MS) onLongPress() else onTap()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                alpha = 1f
                if (dragging) snapToEdge()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /** 松手后吸附到最近的一侧边缘，避免球停在屏幕中间挡视线 */
    private fun snapToEdge() {
        val margin = (6 * density).toInt()
        val leftTarget = margin
        val rightTarget = (screenWidth - width - margin).coerceAtLeast(0)
        val target = if (params.x + width / 2 < screenWidth / 2) leftTarget else rightTarget

        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(params.x, target).apply {
            duration = 180
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                params.x = it.animatedValue as Int
                applyLayout()
            }
            start()
        }
        onPositionChanged(target, params.y)
    }

    private fun applyLayout() {
        runCatching { windowManager?.updateViewLayout(this, params) }
    }

    private companion object {
        const val LONG_PRESS_MS = 450L
    }
}
