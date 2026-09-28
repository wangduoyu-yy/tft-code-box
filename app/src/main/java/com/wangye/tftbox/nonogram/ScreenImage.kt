package com.wangye.tftbox.nonogram

import android.graphics.Bitmap

/**
 * 把截图摊平成一维数组，方便反复取像素。
 *
 * `Bitmap.getPixel` 每次都要过 JNI，读一张棋盘要调几千次，直接卡死；
 * 先 `getPixels` 一次性拷出来，之后全是纯数组访问。
 */
class ScreenImage(val pixels: IntArray, val width: Int, val height: Int) {

    fun inBounds(x: Int, y: Int) = x in 0 until width && y in 0 until height

    fun red(x: Int, y: Int) = (pixels[y * width + x] shr 16) and 0xFF
    fun green(x: Int, y: Int) = (pixels[y * width + x] shr 8) and 0xFF
    fun blue(x: Int, y: Int) = pixels[y * width + x] and 0xFF
    fun rgbSum(x: Int, y: Int) = red(x, y) + green(x, y) + blue(x, y)

    /** 实心格的青色。实测游戏里是 (10,140,136) 左右 */
    fun isFilledColor(x: Int, y: Int): Boolean {
        val r = red(x, y); val g = green(x, y); val b = blue(x, y)
        return g > 110 && g - r > 60 && g - b < 25 && b > 90
    }

    /** 格子/线索框的底色。实测是 (48,58,70) 左右 */
    fun isCellBackground(x: Int, y: Int): Boolean {
        val r = red(x, y); val g = green(x, y); val b = blue(x, y)
        return kotlin.math.abs(r - 48) <= 14 &&
            kotlin.math.abs(g - 58) <= 14 &&
            kotlin.math.abs(b - 70) <= 14
    }

    /**
     * 够不够亮 —— 用来找字形（线索数字、以及玩家画的 ×）。
     * 白色线索、橙色线索（该段已满足）、灰色的 × 都过这条线。
     */
    fun isBright(x: Int, y: Int): Boolean = rgbSum(x, y) > BRIGHT_SUM

    companion object {
        const val BRIGHT_SUM = 330

        fun of(bitmap: Bitmap): ScreenImage {
            val w = bitmap.width
            val h = bitmap.height
            val px = IntArray(w * h)
            bitmap.getPixels(px, 0, w, 0, 0, w, h)
            return ScreenImage(px, w, h)
        }
    }
}
