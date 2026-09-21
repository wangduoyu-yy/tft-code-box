package com.wangye.tftbox.overlay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.wangye.tftbox.TftApp
import com.wangye.tftbox.data.Lineup
import com.wangye.tftbox.util.Clip
import com.wangye.tftbox.util.LineupShareParser
import kotlinx.coroutines.launch

/**
 * 一个看不见的 Activity，专门处理跟剪贴板有关的脏活。它有两种模式：
 *
 * - 默认（复制）：把文本写进剪贴板，然后立刻 finish。
 * - [ACTION_IMPORT]：读剪贴板、解析阵容码、存进数据库。
 *
 * 为什么非要绕这一圈：悬浮面板是 Service 的窗口，不算「当前有焦点的应用」。
 * Android 10 起剪贴板读写都跟焦点绑定，而且国产 ROM 卡得更严 ——
 * 表现就是用户点了复制/新增毫无反应，且没有任何报错。
 * 借一个真 Activity 拿焦点是唯一可靠的办法；Activity 能启动，
 * 靠的是我们持有 SYSTEM_ALERT_WINDOW（后台启动 Activity 限制的豁免条件之一）。
 */
class ClipboardActivity : ComponentActivity() {

    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isImport = intent?.action == ACTION_IMPORT
        if (!isImport && intent?.getStringExtra(EXTRA_TEXT).isNullOrEmpty()) {
            finish()
            return
        }

        // onWindowFocusChanged 在某些 ROM 上可能不按预期触发，加一道超时兜底，
        // 保证这个 Activity 绝不会卡在屏幕上。
        window.decorView.postDelayed({ run() }, 350)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) run()
    }

    private fun run() {
        if (handled) return
        handled = true
        if (intent?.action == ACTION_IMPORT) importFromClipboard() else copyToClipboard()
    }

    // ── 写入剪贴板 ──────────────────────────────────────────────

    private fun copyToClipboard() {
        val body = intent?.getStringExtra(EXTRA_TEXT)
        if (!body.isNullOrEmpty()) {
            Clip.copy(this, body)
            intent?.getStringExtra(EXTRA_LABEL)?.let { Clip.toastCopied(this, it) }
        }
        finish()
    }

    // ── 读剪贴板并保存 ──────────────────────────────────────────

    private fun importFromClipboard() {
        val parsed = LineupShareParser.parse(Clip.read(this))
        if (parsed == null) {
            Clip.toast(this, "剪贴板里没找到阵容码，先去复制一条")
            finish()
            return
        }

        val repository = (application as TftApp).repository
        lifecycleScope.launch {
            val existing = runCatching { repository.findByCode(parsed.code) }.getOrNull()
            if (existing != null) {
                Clip.toast(this@ClipboardActivity, "这条已经存过了：${existing.title}")
            } else {
                val title = parsed.title ?: "未命名阵容"
                runCatching {
                    repository.insert(
                        Lineup(
                            title = title,
                            code = parsed.code,
                            note = parsed.author?.let { "来源：$it" }.orEmpty(),
                        )
                    )
                }.onSuccess {
                    Clip.toast(this@ClipboardActivity, "已保存：$title")
                }.onFailure {
                    Clip.toast(this@ClipboardActivity, "保存失败：${it.message}")
                }
            }
            finish()
        }
    }

    companion object {
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_LABEL = "extra_label"

        /** 带上这个 action 表示「读剪贴板存阵容」，不带就是「把文本写进剪贴板」 */
        const val ACTION_IMPORT = "com.wangye.tftbox.action.IMPORT_FROM_CLIPBOARD"
    }
}
