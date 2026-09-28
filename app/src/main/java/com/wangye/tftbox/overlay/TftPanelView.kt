package com.wangye.tftbox.overlay

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
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
 * 金铲铲模式的浮窗：搜索框 + 阵容列表。点整行就把阵容码复制走并自动收起。
 */
class TftPanelView(
    context: Context,
    colors: OverlayColors,
    private val onCopy: (Lineup) -> Unit,
    onClose: () -> Unit,
    onAddFromClipboard: () -> Unit,
    onStopService: () -> Unit,
) : BasePanelView(
    context = context,
    colors = colors,
    panelTitle = "阵容",
    headerActions = listOf(
        // ＋：把剪贴板里刚复制的阵容码直接存进来，不用退出游戏开 App
        HeaderAction("＋", "从剪贴板新增阵容", onAddFromClipboard)
    ),
    onClose = onClose,
    onStopService = onStopService,
) {

    private val adapter = LineupAdapter(colors) { onCopy(it) }
    private val search: EditText
    private val empty: TextView
    private var all: List<Lineup> = emptyList()

    init {
        fun dp(v: Float) = ChipBackground.dp(context, v)

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
        body.addView(
            search,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(14f), 0, dp(14f), dp(8f)) }
        )

        val list = RecyclerView(context).apply {
            id = R.id.panel_list
            layoutManager = LinearLayoutManager(context)
            adapter = this@TftPanelView.adapter
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
        body.addView(
            container,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
    }

    fun setData(list: List<Lineup>) {
        all = list
        applyFilter()
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
}
