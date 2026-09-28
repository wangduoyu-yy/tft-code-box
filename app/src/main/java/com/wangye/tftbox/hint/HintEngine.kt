package com.wangye.tftbox.hint

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.wangye.tftbox.nonogram.BoardReader
import com.wangye.tftbox.nonogram.DigitTemplates
import com.wangye.tftbox.nonogram.GridScanner
import com.wangye.tftbox.nonogram.IntRect
import com.wangye.tftbox.nonogram.NonogramSolver
import com.wangye.tftbox.nonogram.ScreenImage
import com.wangye.tftbox.util.Prefs

/**
 * 提示引擎：截图 → 认棋盘 → 求解 → 给出下一步。
 *
 * 整个过程跑在后台线程，因为一张 15×15 的图要逐像素扫好几遍，
 * 放主线程会卡住浮窗。
 */
object HintEngine {

    sealed interface Outcome {
        data class Ok(
            val hints: List<NonogramSolver.Hint>,
            val gridRect: IntRect,
            val cols: Int,
            val rows: Int,
            val cellPitch: Float,
            /** 玩家已经把棋盘填满了 */
            val solved: Boolean,
            val ambiguous: Boolean,
        ) : Outcome

        /**
         * 当前局面自相矛盾，而且**定位到了可疑的格子**。
         *
         * 跟 [Failed] 分开：那种是「读不出来」，这种是「读出来了，但你之前有格子填错了」，
         * 处理方式完全不同 —— 后者要在画面上标红给用户看。
         */
        data class Conflict(
            val message: String,
            val suspects: List<Pair<Int, Int>>,
            val gridRect: IntRect,
            val cellPitch: Float,
        ) : Outcome

        data class Failed(val message: String) : Outcome
    }

    private val main = Handler(Looper.getMainLooper())
    private var templates: DigitTemplates? = null

    fun request(context: Context, onDone: (Outcome) -> Unit) {
        val app = context.applicationContext

        if (!HintAccessibilityService.isSupported()) {
            onDone(Outcome.Failed("这个功能需要 Android 11 及以上"))
            return
        }
        if (!HintAccessibilityService.isReady()) {
            onDone(Outcome.Failed("还没开启截图服务，去设置里打开「数独提示」"))
            return
        }

        HintAccessibilityService.captureWithRetry { result ->
            when (result) {
                is HintAccessibilityService.CaptureResult.Failed -> {
                    onDone(Outcome.Failed("截图失败：${result.reason}"))
                }

                is HintAccessibilityService.CaptureResult.Ok -> {
                    val bmp = result.bitmap
                    Thread {
                        val outcome = runCatching { process(app, bmp) }
                            .getOrElse { Outcome.Failed("识别出错：${it.message}") }

                        // 开了调试模式才存图。默认关着 —— 正常用不该往相册里塞东西。
                        // 排查问题时打开，能把失败时的原图留下来。
                        val saved = if (outcome is Outcome.Failed && Prefs.isDebugShot(app)) {
                            DebugShot.save(app, bmp)
                        } else {
                            null
                        }
                        bmp.recycle()

                        main.post {
                            onDone(
                                if (saved != null && outcome is Outcome.Failed) {
                                    Outcome.Failed(outcome.message + "\n\n已把这次截图存到相册的 $saved")
                                } else {
                                    outcome
                                }
                            )
                        }
                    }.start()
                }
            }
        }
    }

    private fun process(context: Context, bitmap: Bitmap): Outcome {
        val img = ScreenImage.of(bitmap)

        val tpl = templates ?: DigitTemplates.load(context)?.also { templates = it }
            ?: return Outcome.Failed("数字模板加载失败")

        val read = BoardReader(tpl).read(img)
            ?: return Outcome.Failed("没在屏幕上找到棋盘。确认棋盘完整可见、没被弹窗挡住再试")

        val badRow = read.rowClues.indexOfFirst { it.isEmpty() }
        val badCol = read.colClues.indexOfFirst { it.isEmpty() }
        if (badRow >= 0 || badCol >= 0) {
            val what = if (badRow >= 0) "第 ${badRow + 1} 行" else "第 ${badCol + 1} 列"
            return Outcome.Failed("$what 的线索没读出来，可能屏幕上有东西挡住，或者游戏换字体了")
        }

        val det = GridScanner.autoDetect(img)
        val rect = det?.rect ?: return Outcome.Failed("棋盘位置丢了，再试一次")
        val pitch = rect.width.toFloat() / read.cols

        val result = NonogramSolver.solve(read.rowClues, read.colClues, read.state)
        if (result.contradiction != null) {
            // 能定位到可疑格子就标出来 —— 只说一句「对不上」对用户没用，
            // 他填了几十格根本不知道该回头查哪里
            return if (result.conflictCells.isNotEmpty()) {
                Outcome.Conflict(result.contradiction, result.conflictCells, rect, pitch)
            } else {
                Outcome.Failed(result.contradiction)
            }
        }

        return Outcome.Ok(
            hints = result.hints,
            gridRect = rect,
            cols = read.cols,
            rows = read.rows,
            cellPitch = rect.width.toFloat() / read.cols,
            solved = result.solved,
            ambiguous = result.ambiguous,
        )
    }
}
