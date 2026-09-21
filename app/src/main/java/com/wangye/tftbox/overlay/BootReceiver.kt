package com.wangye.tftbox.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wangye.tftbox.util.Perms
import com.wangye.tftbox.util.Prefs

/**
 * 开机 / 应用更新后，如果用户开了自启并且已经授权悬浮窗，就把悬浮球拉起来。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        if (!Prefs.isAutoStart(context)) return
        // 没给悬浮窗权限的话，起来了也只会失败弹个 toast，不如安静点
        if (!Perms.canDrawOverlay(context)) return

        runCatching { FloatingService.start(context) }
    }
}
