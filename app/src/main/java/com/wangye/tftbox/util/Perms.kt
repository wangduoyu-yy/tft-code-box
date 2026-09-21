package com.wangye.tftbox.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object Perms {

    fun canDrawOverlay(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun overlaySettingsIntent(ctx: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${ctx.packageName}")
        )

    fun isIgnoringBatteryOptimizations(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    @SuppressLint("BatteryLife")
    fun batterySettingsIntent(ctx: Context): Intent =
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${ctx.packageName}")
        )

    fun hasNotificationPermission(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    /** 厂商机型判断，用来提示用户去开那些藏在设置深处的自启/后台弹出开关 */
    fun manufacturerName(): String = (Build.MANUFACTURER ?: "").lowercase()

    fun isXiaomi(): Boolean = manufacturerName().let { it.contains("xiaomi") || it.contains("redmi") }
    fun isHuawei(): Boolean = manufacturerName().let { it.contains("huawei") || it.contains("honor") }
    fun isOppo(): Boolean = manufacturerName().let { it.contains("oppo") || it.contains("realme") || it.contains("oneplus") }
    fun isVivo(): Boolean = manufacturerName().let { it.contains("vivo") || it.contains("iqoo") }

    /** MIUI 特有的「后台弹出界面」权限页 */
    fun xiaomiBackgroundStartIntent(): Intent = Intent().apply {
        setClassName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity"
        )
    }

    val isOemWithHiddenSettings: Boolean
        get() = isXiaomi() || isHuawei() || isOppo() || isVivo()
}
