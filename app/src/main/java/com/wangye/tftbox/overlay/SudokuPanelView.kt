package com.wangye.tftbox.overlay

import android.content.Context
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.wangye.tftbox.hint.HintEngine

/** 面板跟宿主（FloatingService）之间的约定 */
interface SudokuHintHost {
    fun requestHint(onResult: (HintEngine.Outcome) -> Unit)
    fun openAccessibilitySettings()
    fun showHighlight(cells: List<HighlightCell>)
    fun hideHighlight()
}

/**
 * 数独模式的浮窗：点开就自动读一次棋盘，给出下一步。
 *
 * 识别和求解都跑在后台线程，这里只负责把状态显示出来。
 */
class SudokuPanelView(
    context: Context,
    colors: OverlayColors,
    onClose: () -> Unit,
    onStopService: () -> Unit,
    private val host: SudokuHintHost,
    /** 打开面板之前已经读好的结果。为 null 表示面板要自己去读一次 */
    private val preset: HintEngine.Outcome? = null,
) : BasePanelView(
    context = context,
    colors = colors,
    panelTitle = "数独提示",
    headerActions = emptyList(),
    onClose = onClose,
    onStopService = onStopService,
) {

    private val status: TextView
    private val detail: TextView
    private val actionRow: LinearLayout

    init {
        fun dp(v: Float) = ChipBackground.dp(context, v)

        status = TextView(context).apply {
            setTextColor(colors.textMain)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            text = "正在看棋盘…"
        }
        detail = TextView(context).apply {
            setTextColor(colors.textSub)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            gravity = Gravity.CENTER
            setLineSpacing(dp(4f).toFloat(), 1f)
            setPadding(0, dp(10f), 0, 0)
        }
        actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(16f), 0, 0)
        }

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18f), 0, dp(18f), 0)
        }
        column.addView(status)
        column.addView(detail)
        column.addView(actionRow)

        val scroller = ScrollView(context).apply { isFillViewport = true }
        scroller.addView(
            column,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        body.addView(
            scroller,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        // 有现成结果就直接显示；没有才自己去读。
        // 点球进来看下一步时，外面已经读好了才开面板 —— 因为**面板一挡住屏幕就读不出来了**。
        if (preset != null) render(preset) else startRead()
    }

    /** 自己去读一次。宿主会先把这个面板藏起来再截图 */
    fun startRead() {
        status.text = "正在看棋盘…"
        detail.text = ""
        actionRow.removeAllViews()
        host.hideHighlight()
        host.requestHint { outcome -> render(outcome) }
    }

    private fun render(outcome: HintEngine.Outcome) {
        actionRow.removeAllViews()

        when (outcome) {
            is HintEngine.Outcome.Ok -> renderHints(outcome)

            is HintEngine.Outcome.Conflict -> {
                status.text = "有格子填错了"
                val shown = outcome.suspects.take(MAX_CONFLICT_LISTED)
                detail.text = buildString {
                    if (outcome.suspects.size == 1) {
                        append("第 ${shown[0].first + 1} 行 第 ${shown[0].second + 1} 列 这格可能填错了")
                    } else {
                        append("${outcome.suspects.size} 处可疑：\n")
                        append(shown.joinToString("\n") {
                            "第 ${it.first + 1} 行 第 ${it.second + 1} 列"
                        })
                        if (outcome.suspects.size > shown.size) {
                            append("\n…还有 ${outcome.suspects.size - shown.size} 处")
                        }
                    }
                    append("\n\n已在游戏画面上标红，挨个改回来试试")
                }

                // 只标前几个，标太多整屏都是红的
                val targets = outcome.suspects.take(MAX_HIGHLIGHTED).map { (r, c) ->
                    HighlightCell(
                        RectF(
                            outcome.gridRect.left + c * outcome.cellPitch,
                            outcome.gridRect.top + r * outcome.cellPitch,
                            outcome.gridRect.left + (c + 1) * outcome.cellPitch,
                            outcome.gridRect.top + (r + 1) * outcome.cellPitch,
                        ),
                        conflict = true,
                    )
                }
                if (targets.isNotEmpty()) host.showHighlight(targets)

                addButton("重新读一次") { startRead() }
            }

            is HintEngine.Outcome.Failed -> {
                status.text = "读不出来"
                detail.text = outcome.message
                addButton("重试") { startRead() }
                addButton("开启截图服务") { host.openAccessibilitySettings() }
            }
        }
    }

    private fun renderHints(ok: HintEngine.Outcome.Ok) {
        when {
            // 注意这里判断的是**玩家**填完了没有。
            // 求解器能从空棋盘推出整盘，但那不代表玩家填了东西 ——
            // 早期版本把这个搞混了，一进新局就报「已经全填完了」。
            ok.solved -> {
                status.text = "已经全填完了"
                detail.text = "${ok.cols}×${ok.rows} 每一格都填了，没有可以走的地方了。"
            }

            ok.hints.isEmpty() && ok.ambiguous -> {
                status.text = "推不出来了"
                detail.text = "剩下的格子光靠线索定不下来，这一步得靠猜。\n" +
                    "随便挑一个格子试一下，走错了再退回来。"
            }

            ok.hints.isEmpty() -> {
                status.text = "暂时没有新进展"
                detail.text = "当前填法和线索不冲突，但也推不出新格子。"
            }

            else -> {
                // 只给一句话，具体看哪一格交给画面上的框 —— 一屏文字没人看
                status.text = if (ok.hints.size == 1) "下一步" else "共 ${ok.hints.size} 处可以确定"

                val shown = ok.hints.take(MAX_LISTED)
                detail.text = if (shown.size <= MAX_LISTED_AS_TEXT) {
                    // 只有一两处时，直接说出来比框更清楚
                    shown.joinToString("\n") { h ->
                        "第 ${h.row + 1} 行 第 ${h.col + 1} 列  →  ${if (h.filled) "填满" else "留空"}"
                    }
                } else {
                    "已框在游戏画面上\n＋ 是填满，× 是留空"
                }

                // 每格带上**自己**的填/空，不能所有格子共用一条提示的类型
                val targets = shown.take(MAX_HIGHLIGHTED).map { h ->
                    val pitch = ok.cellPitch
                    HighlightCell(
                        RectF(
                            ok.gridRect.left + h.col * pitch,
                            ok.gridRect.top + h.row * pitch,
                            ok.gridRect.left + (h.col + 1) * pitch,
                            ok.gridRect.top + (h.row + 1) * pitch,
                        ),
                        h.filled,
                    )
                }
                if (targets.isNotEmpty()) host.showHighlight(targets)
            }
        }

        addButton("重新读一次") { startRead() }
    }

    private fun addButton(label: String, onClick: () -> Unit) {
        actionRow.addView(
            TextView(context).apply {
                text = label
                setTextColor(colors.accent)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                gravity = Gravity.CENTER
                setPadding(
                    ChipBackground.dp(context, 14f), ChipBackground.dp(context, 9f),
                    ChipBackground.dp(context, 14f), ChipBackground.dp(context, 9f)
                )
                background = ChipBackground.create(context, colors.accent, colors.chipFill)
                isClickable = true
                setOnClickListener { onClick() }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = ChipBackground.dp(context, 8f) }
        )
    }

    fun onClosed() = host.hideHighlight()

    private companion object {
        /** 最多在画面上框几格。太多了整屏都是框，反而看不清 */
        const val MAX_HIGHLIGHTED = 6

        /** 超过这个数就只用文字说「多少处」，不再逐条列 */
        const val MAX_LISTED_AS_TEXT = 3

        /** 错格最多列几个坐标 */
        const val MAX_CONFLICT_LISTED = 6

        const val MAX_LISTED = 6
    }
}
