package com.wangye.tftbox.overlay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wangye.tftbox.R
import com.wangye.tftbox.data.Lineup

/**
 * 浮窗里的阵容列表。
 *
 * 用经典 View 手写，不引 XML 布局 —— 行结构很简单，而且浮窗跑在 Service 上下文里，
 * 没有 Activity 主题，手写反而好控制。
 */
class LineupAdapter(
    private val colors: OverlayColors,
    private val onClick: (Lineup) -> Unit,
) : ListAdapter<Lineup, LineupAdapter.RowHolder>(RowDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder =
        RowHolder(buildRow(parent.context, colors))

    override fun onBindViewHolder(holder: RowHolder, position: Int) {
        val item = getItem(position)
        holder.title.text = item.title
        holder.subtitle.text = item.subtitle.ifBlank { "未标注" }
        holder.code.text = item.codePreview
        holder.favorite.visibility = if (item.favorite) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener { onClick(item) }
    }

    class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.row_title)
        val subtitle: TextView = view.findViewById(R.id.row_subtitle)
        val code: TextView = view.findViewById(R.id.row_code)
        val favorite: TextView = view.findViewById(R.id.row_favorite)
    }

    private object RowDiff : DiffUtil.ItemCallback<Lineup>() {
        override fun areItemsTheSame(old: Lineup, new: Lineup) = old.id == new.id
        override fun areContentsTheSame(old: Lineup, new: Lineup) = old == new
    }

    companion object {

        fun buildRow(context: Context, colors: OverlayColors): View {
            fun dp(value: Float) = ChipBackground.dp(context, value)

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14f), dp(11f), dp(14f), dp(11f))
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                background = RippleDrawable(
                    ColorStateList.valueOf(colors.rowRipple),
                    ColorDrawable(Color.TRANSPARENT),
                    null
                )
            }

            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            root.addView(
                column,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )

            val titleRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            column.addView(titleRow)

            titleRow.addView(
                TextView(context).apply {
                    id = R.id.row_title
                    setTextColor(colors.textMain)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )

            titleRow.addView(
                TextView(context).apply {
                    id = R.id.row_favorite
                    text = "★"
                    setTextColor(colors.accent)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    visibility = View.GONE
                    setPadding(dp(6f), 0, 0, 0)
                }
            )

            column.addView(
                TextView(context).apply {
                    id = R.id.row_subtitle
                    setTextColor(colors.textSub)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(2f), 0, 0)
                }
            )

            column.addView(
                TextView(context).apply {
                    id = R.id.row_code
                    setTextColor(colors.textHint)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    typeface = Typeface.MONOSPACE
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(3f), 0, 0)
                }
            )

            root.addView(
                TextView(context).apply {
                    text = "复制"
                    setTextColor(colors.accent)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    gravity = Gravity.CENTER
                    setPadding(dp(13f), dp(7f), dp(13f), dp(7f))
                    background = ChipBackground.create(context, colors.accent, colors.chipFill)
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(12f) }
            )

            return root
        }
    }
}
