package com.wangye.tftbox.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wangye.tftbox.R
import com.wangye.tftbox.data.Lineup

/**
 * 悬浮面板：搜索框 + 阵容列表。点整行就把阵容码复制走并自动收起。
 *
 * 这是 Service 的窗口，不是 Activity，所以全手写 View、不依赖任何主题属性；
 * 配色由 [OverlayColors] 从用户选的主题算出来。
 */
class PanelView(
    context: Context,
    private val colors: OverlayColors,
    private val onCopy: (Lineup) -> Unit,
    private val onClose: () -> Unit,
    private val onAddFromClipboard: () -> Unit,
    private val onStopService: () -> Unit,
) : LinearLayout(context) {

    private val adapter = LineupAdapter(colors) { onCopy(it) }
    private val search: EditText
    private val empty: TextView
    private lateinit var stopButton: TextView
    private var all: List<Lineup> = emptyList()

    /** 「关闭悬浮球」要点两次才生效，避免手滑 */
    private var stopArmed = false
    private val disarmStop = Runnable {
        stopArmed = false
        if (::stopButton.isInitialized) {
            stopButton.text = "关闭悬浮球"
            stopButton.setTextColor(colors.textSub)
        }
    }

    init {
        fun dp(v: Float) = ChipBackground.dp(context, v)

        orientation = VERTICAL
        background = ChipBackground.rounded(context, colors.panelFill, 18f, colors.panelStroke)

        // 关键：让面板自己先拿到焦点，否则焦点会自动落到 EditText 上，
        // 面板一弹出来输入法就跟着弹出来糊住半个屏幕。
        isFocusable = true
        isFocusableInTouchMode = true

        // 点面板外面的区域 → 收起。需要窗口带 FLAG_WATCH_OUTSIDE_TOUCH。
        setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                onClose()
                true
            } else {
                false
            }
        }

        // 返回键收起面板（面板窗口有焦点，能收到按键）
        setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                onClose()
                true
            } else {
                false
            }
        }

        // ── 标题栏 ──────────────────────────────────────────────
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(12f), dp(10f), dp(8f))
        }
        header.addView(
            TextView(context).apply {
                text = "阵容"
                setTextColor(colors.textMain)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                typeface = Typeface.DEFAULT_BOLD
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        // ＋：把剪贴板里刚复制的阵容码直接存进来，不用退出游戏开 App
        header.addView(
            TextView(context).apply {
                text = "＋"
                setTextColor(colors.accent)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                gravity = Gravity.CENTER
                setPadding(dp(12f), dp(2f), dp(12f), dp(2f))
                isClickable = true
                contentDescription = "从剪贴板新增阵容"
                setOnClickListener { onAddFromClipboard() }
            }
        )
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

        // ── 搜索框 ──────────────────────────────────────────────
        search = EditText(context).apply {
            id = R.id.panel_search
            hint = "搜名称 / 标签 / 赛季"
            setHintTextColor(colors.textHint)
            setTextColor(colors.textMain)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(12f), dp(9f), dp(12f), dp(9f))
            background = ChipBackground.rounded(context, colors.chipFill, 10f)
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) = applyFilter()
        })
        addView(
            search,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(14f), 0, dp(14f), dp(8f)) }
        )

        // ── 列表 ────────────────────────────────────────────────
        val list = RecyclerView(context).apply {
            id = R.id.panel_list
            layoutManager = LinearLayoutManager(context)
            adapter = this@PanelView.adapter
            setPadding(0, 0, 0, dp(8f))
            setBackgroundColor(Color.TRANSPARENT)
        }
        empty = TextView(context).apply {
            id = R.id.panel_empty
            text = "还没有阵容，先去 App 里加几条"
            setTextColor(colors.textSub)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        val container = FrameLayout(context).apply {
            addView(list, FrameLayout.LayoutParams(-1, -1))
            addView(empty, FrameLayout.LayoutParams(-1, -1))
        }
        addView(
            container,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        // ── 底部：关闭悬浮球 ────────────────────────────────────
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

    fun setData(list: List<Lineup>) {
        all = list
        applyFilter()
    }

    fun clearSearch() {
        search.setText("")
    }

    private fun applyFilter() {
        val q = search.text.toString().trim()
        val filtered = if (q.isEmpty()) {
            all
        } else {
            all.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.tags.contains(q, ignoreCase = true) ||
                    it.season.contains(q, ignoreCase = true) ||
                    it.note.contains(q, ignoreCase = true) ||
                    it.code.contains(q, ignoreCase = true)
            }
        }
        adapter.submitList(filtered)
        empty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        empty.text = if (all.isEmpty()) {
            "还没有阵容，先去 App 里加几条"
        } else {
            "没有匹配「${TextUtils.ellipsize(q, search.paint, 120f, TextUtils.TruncateAt.END)}」的阵容"
        }
    }

    private companion object {
        const val TAG = "TftCodeBox"
    }
}
