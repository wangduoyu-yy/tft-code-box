package com.wangye.tftbox.hint

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 识别失败时把那张截图存进相册。
 *
 * 识别出问题的时候，光看「第几行没读出来」根本判断不出原因 ——
 * 是格子数错了、还是某个数字认不出、还是被别的窗口挡住了，全都有可能。
 * 存一张原图出来，拿回开发机上跑一遍就能直接看到问题在哪。
 */
object DebugShot {

    private const val TAG = "TftCodeBox"
    private const val ALBUM = "TftBox"

    /** 存进相册，返回一个给用户看的说明；失败返回 null */
    fun save(context: Context, bitmap: Bitmap): String? = runCatching {
        val name = "tftbox_" +
            SimpleDateFormat("MMdd_HHmmss", Locale.US).format(Date()) + ".png"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/" + ALBUM
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null

        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        name
    }.onFailure { Log.e(TAG, "存调试截图失败", it) }.getOrNull()
}
