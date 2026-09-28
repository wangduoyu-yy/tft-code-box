package com.wangye.tftbox.hint

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi

/**
 * 一个「只会截图」的无障碍服务。
 *
 * 为什么非要用无障碍：数独提示需要**随时**看一眼屏幕。MediaProjection 每次都要弹窗让用户
 * 确认，玩一局点十几次，根本没法用。无障碍服务授权一次之后 `takeScreenshot()` 随时可用。
 *
 * 这个服务不读任何界面内容、不模拟任何操作，只做截图这一件事。
 */
class HintAccessibilityService : AccessibilityService() {

    /** 截图结果。失败要带上具体原因，否则用户只看到「失败了」没法排查 */
    sealed interface CaptureResult {
        data class Ok(val bitmap: Bitmap) : CaptureResult
        data class Failed(val reason: String) : CaptureResult
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "无障碍服务已连接，可以截图了")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        Log.i(TAG, "无障碍服务已断开")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /** 用不到事件，但必须实现 */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = "TftCodeBox"

        @Volatile
        private var instance: HintAccessibilityService? = null

        /** 系统版本够、且用户已经开了无障碍 */
        fun isReady(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && instance != null

        /** 系统支不支持这个能力（跟用户开没开无关） */
        fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

        fun capture(onResult: (CaptureResult) -> Unit) {
            val svc = instance
            if (svc == null) {
                onResult(CaptureResult.Failed("截图服务没连上，去「辅助功能」里确认开关是开的"))
                return
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                onResult(CaptureResult.Failed("系统版本太低，需要 Android 11 及以上"))
                return
            }
            svc.captureInternal(onResult)
        }

        private val handler = android.os.Handler(android.os.Looper.getMainLooper())

        /**
         * 带重试的截图。
         *
         * 「两次截图间隔太短」是系统强制的频率限制，很容易撞上 ——
         * 比如面板一打开就截了一次，紧接着标定完又截一次。等一会儿再试就好了。
         */
        fun captureWithRetry(attempts: Int = 3, onResult: (CaptureResult) -> Unit) {
            capture { result ->
                val failed = result as? CaptureResult.Failed
                if (failed != null && attempts > 1) {
                    Log.i(TAG, "截图失败（${failed.reason}），稍后重试")
                    handler.postDelayed({ captureWithRetry(attempts - 1, onResult) }, 700)
                } else {
                    onResult(result)
                }
            }
        }

        /**
         * 截图失败码翻译成人话。
         * 这几个常量定义在 AccessibilityService 里。
         */
        private fun describeError(code: Int): String = when (code) {
            ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR -> "系统内部错误（$code）"
            ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "系统没有授予截图权限（$code）"
            ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "两次截图间隔太短，稍等一下再试（$code）"
            ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "显示器无效（$code）"
            ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> "屏幕上有受保护的内容，系统禁止截屏（$code）"
            else -> "未知错误（$code）"
        }

        internal fun describeErrorPublic(code: Int) = describeError(code)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureInternal(onResult: (CaptureResult) -> Unit) {
        val started = runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        // 注意：走到这里只代表「系统给了一张图」，
                        // 后面转成能读像素的位图还可能失败，要分开报
                        val converted = runCatching {
                            val hw = Bitmap.wrapHardwareBuffer(
                                result.hardwareBuffer, result.colorSpace
                            ) ?: return@runCatching null
                            val soft = hw.copy(Bitmap.Config.ARGB_8888, false)
                            hw.recycle()
                            soft
                        }
                        runCatching { result.hardwareBuffer.close() }

                        val bmp = converted.getOrNull()
                        if (bmp == null) {
                            val err = converted.exceptionOrNull()
                            Log.e(TAG, "截图拿到了但转位图失败", err)
                            onResult(
                                CaptureResult.Failed(
                                    "截图拿到了，但转成可读位图失败" +
                                        (err?.let { "：${it.javaClass.simpleName} ${it.message}" } ?: "")
                                )
                            )
                        } else {
                            onResult(CaptureResult.Ok(bmp))
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        val why = describeError(errorCode)
                        Log.w(TAG, "截图失败：$why")
                        onResult(CaptureResult.Failed(why))
                    }
                }
            )
        }

        started.onFailure {
            Log.e(TAG, "调用截图接口抛异常", it)
            onResult(CaptureResult.Failed("调用截图接口失败：${it.javaClass.simpleName} ${it.message}"))
        }
    }
}
