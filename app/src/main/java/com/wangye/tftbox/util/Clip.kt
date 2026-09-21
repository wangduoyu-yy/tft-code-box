package com.wangye.tftbox.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast

object Clip {

    fun copy(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("阵容码", text))
    }

    fun read(context: Context): String? = runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!cm.hasPrimaryClip()) return null
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        clip.getItemAt(0).coerceToText(context)?.toString()
    }.getOrNull()

    /** Android 13 起系统自己会弹「已复制」气泡，不需要我们再 toast 一次 */
    fun toastCopied(context: Context, label: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            toast(context, "已复制：$label")
        }
    }

    /** Toast 在部分 ROM 的后台会被拦，包一层免得把主流程带崩 */
    fun toast(context: Context, text: String) {
        runCatching { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }

    // 判断「像不像阵容码」的逻辑在 LineupShareParser 里，
    // 那边还要负责从整段分享文案中抠出码、名字、作者，放一起才好维护。
}
