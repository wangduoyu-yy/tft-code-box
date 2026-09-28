package com.wangye.tftbox.overlay

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 浮窗的外壳：圆角背景、标题栏、右下角「关闭悬浮球」、点外面收起、返回键收起。
 *
 * 两个模式的内容不一样，但外壳完全一样 —— 抽出来是为了将来修关闭按钮之类的 bug
 * 只用改一处。子类往 [body] 里塞自己的内容就行。
 *
 * 这是 Service 窗口不是 Activity，全程手写 View，不依赖任何主题属性；
 * 配色由 [OverlayColors] 按用户选的主题算出来。
 */
abstract class BasePanelView(
    context: Context,
    protected val colors: OverlayColors,
    panelTitle: String,
    headerActions: List<HeaderAction>,
    protected val onClose: () -> Unit,
    private val onStopService: () -> Unit,
) : LinearLayout(context) {

    /** 标题栏上的小按钮，比如金铲铲模式那个「＋」 */
    data class HeaderAction(
        val label: String,
        val description: String,
        val onClick: () -> Unit,
    )

    private lateinit var stopButton: TextView
    private var stopArmed = false
    private val disarmStop = Runnable {
        stopArmed = false
        if (::stopButton.isInitialized) {
            stopButton.text = "关闭悬浮球"
            stopButton.setTextColor(colors.textSub)
        }
    }

    /** 子类的内容容器。基类构造完成后就可以往里加 View。 */
    protected lateinit var body: LinearLayout
        private set

    init {
        fun dp(v: Float) = ChipBackground.dp(context, v)

        orientation = VERTICAL
        background = ChipBackground.rounded(context, colors.panelFill, 18f, colors.panelStroke)

        // 让面板自己先拿到焦点，否则焦点会自动落到 EditText 上，
        // 面板一弹出来输入法就跟着弹出来糊住半个屏幕。
        isFocusable = true
        isFocusableInTouchMode = true

        // 点面板外面 → 收起。需要窗口带 FLAG_WATCH_OUTSIDE_TOUCH。
        setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                onClose()
                true
            } else {
                false
            }
        }

        // 返回键收起（面板窗口有焦点，能收到按键）
        setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                onClose()
                true
            } else {
                false
            }
        }

        buildHeader(panelTitle, headerActions, ::dp)

        body = LinearLayout(context).apply { orientation = VERTICAL }
        addView(
            body,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        buildFooter(::dp)
    }

    private fun buildHeader(
        panelTitle: String,
        headerActions: List<HeaderAction>,
        dp: (Float) -> Int,
    ) {
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(12f), dp(10f), dp(8f))
        }

        header.addView(
            TextView(context).apply {
                text = panelTitle
                setTextColor(colors.textMain)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                typeface = Typeface.DEFAULT_BOLD
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        headerActions.forEach { action ->
            header.addView(
                TextView(context).apply {
                    text = action.label
                    setTextColor(colors.accent)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    gravity = Gravity.CENTER
                    setPadding(dp(12f), dp(2f), dp(12f), dp(2f))
                    isClickable = true
                    contentDescription = action.description
                    setOnClickListener { action.onClick() }
                }
            )
        }

        header.addView(
            TextView(context).apply {
                text = "✕"
                setTextColor(colors.textSub)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                gravity = Gravity.CENTER
                setPadding(dp(12f), dp(2f), dp(12f), dp(2f))
                isClickable = true
                contentDescription = "收起面板"
                setOnClickListener { onClose() }
            }
        )

        addView(header)
    }

    private fun buildFooter(dp: (Float) -> Int) {
        // 以前只能去通知栏关，通知要是被系统折叠或没给通知权限，就彻底没辙。
        // 放这儿等于多一个随时能按的出口。
        val footer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dp(8f), 0, dp(8f), dp(4f))
        }

        stopButton = TextView(context).apply {
            text = "关闭悬浮球"
            setTextColor(colors.textSub)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(dp(14f), dp(9f), dp(14f), dp(9f))
            isClickable = true
            setOnClickListener {
                Log.i(TAG, "点击「关闭悬浮球」 armed=$stopArmed")
                if (!stopArmed) {
                    stopArmed = true
                    text = "再点一次确认关闭"
                    setTextColor(colors.danger)
                    postDelayed(disarmStop, 3000L)
                } else {
                    removeCallbacks(disarmStop)
                    onStopService()
                }
            }
        }

        footer.addView(stopButton)
        addView(footer)
    }

    private companion object {
        const val TAG = "TftCodeBox"
    }
}
