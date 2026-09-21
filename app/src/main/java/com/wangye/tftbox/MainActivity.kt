package com.wangye.tftbox

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wangye.tftbox.overlay.FloatingService
import com.wangye.tftbox.ui.AppRoot
import com.wangye.tftbox.ui.MainViewModel
import com.wangye.tftbox.ui.ThemeMode
import com.wangye.tftbox.ui.TftTheme
import com.wangye.tftbox.util.Clip
import com.wangye.tftbox.util.Perms
import com.wangye.tftbox.util.Prefs
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** 从浏览器「分享」过来的文本 */
    private val sharedText = MutableStateFlow<String?>(null)

    /** 每次回到前台重新读一次剪贴板 */
    private val clipboardText = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readShareIntent(intent)
        autoStartFloatingBall()

        setContent {
            val viewModel: MainViewModel = viewModel(
                factory = MainViewModel.factory(application)
            )
            val themeMode by viewModel.themeMode.collectAsState()
            val paletteId by viewModel.paletteId.collectAsState()

            // 主题包在最外层，设置页一改全局立刻换肤
            TftTheme(mode = ThemeMode.from(themeMode), paletteId = paletteId) {
                AppRoot(
                    viewModel = viewModel,
                    sharedText = sharedText,
                    clipboardText = clipboardText,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readShareIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // 读剪贴板要求当前应用有焦点，onResume 正好满足。
        // 放在这里而不是 composition 里，是因为 Compose 首次组合可能早于窗口获得焦点。
        clipboardText.value = if (Prefs.isWatchClipboard(this)) {
            Clip.read(this)?.trim()
        } else {
            null
        }
    }

    private fun readShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        if (intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
        if (!text.isNullOrEmpty()) sharedText.value = text
    }

    /**
     * 冷启动 App 就把球挂起来，不用每次手动去设置页点开关。
     *
     * 只在 onCreate 里做，不在 onResume：用户要是刚在浮窗里点了「关闭悬浮球」，
     * 切个后台再回来不该又给他打开。下次冷启动才会重新自动开。
     * 相应地，App 被从后台任务里划掉时服务会自己停（见 FloatingService.onTaskRemoved）。
     */
    private fun autoStartFloatingBall() {
        if (!Prefs.isAutoBall(this)) return
        if (!Perms.canDrawOverlay(this)) return
        if (FloatingService.isRunning) return
        runCatching { FloatingService.start(this) }
    }
}
