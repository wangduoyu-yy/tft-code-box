package com.wangye.tftbox.nonogram

import kotlin.math.abs

data class IntRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

/**
 * 从整张截图里**自动找出棋盘**，不需要用户标定。
 *
 * 思路分两步：
 *
 * 1. **按颜色定位。** 棋盘格子的底色是固定的 (48,58,70)，而且和游戏其他地方的背景
 *    分得很开（线索框的颜色都跟它不一样）。所以「匹配这个颜色的像素」基本就只落在棋盘上。
 *
 * 2. **按暗缝精修。** 光靠颜色范围会差几个像素：格子有圆角，边上的格子匹配到的像素少一截。
 *    但格子之间的暗缝中心**就是格子边界**，用它反推能达到 2~4px 的精度 ——
 *    对 49px 宽的格子来说完全够用。
 *
 * 为什么不用「取颜色区域的包围盒」：截图里 x=172 有一根 428 像素高的孤立竖线，
 * 会把包围盒整个带偏 9 像素，累积到最后几列就采到隔壁格子去了。
 */
object GridScanner {

    data class Detection(
        /** 棋盘的像素框 */
        val rect: IntRect,
        val cols: Int,
        val rows: Int,
        /** 线索区在网格左侧/上方延伸多远（像素） */
        val clueWidth: Int,
        val clueHeight: Int,
    )

    private const val MIN_CELLS = 4
    private const val MAX_CELLS = 40
    private const val MIN_PITCH = 10f

    /** 比这更宽的「低谷」不算格子之间的暗缝，是画面本身的空白 */
    private const val MAX_GAP_WIDTH = 40

    /**
     * 判定「暗缝」用的阈值系数。
     * 真暗缝几乎全黑，格子里的 × 只是让像素变少 —— 阈值压低才能只挑出真暗缝。
     */
    private const val GAP_LEVEL = 0.15f

    /** 格子底色的匹配容差。实测必须很小，否则线索框会被一起算进来 */
    private const val COLOR_TOL = 3

    private const val CELL_R = 48
    private const val CELL_G = 58
    private const val CELL_B = 70

    /** 玩家划掉（×）的格子底色 */
    private const val MARK_R = 243
    private const val MARK_G = 243
    private const val MARK_B = 231

    fun autoDetect(img: ScreenImage): Detection? {
        val mask = buildCellMask(img)

        val colProfile = FloatArray(img.width)
        val rowProfile = FloatArray(img.height)
        for (x in 0 until img.width) {
            var n = 0
            for (y in 0 until img.height) if (mask[y * img.width + x]) n++
            colProfile[x] = n.toFloat()
        }
        for (y in 0 until img.height) {
            var n = 0
            val base = y * img.width
            for (x in 0 until img.width) if (mask[base + x]) n++
            rowProfile[y] = n.toFloat()
        }

        val gx = detectAxis(colProfile) ?: return null
        val gy = detectAxis(rowProfile) ?: return null

        if (gx.count !in MIN_CELLS..MAX_CELLS || gy.count !in MIN_CELLS..MAX_CELLS) return null

        val left = gx.start
        val top = gy.start
        val right = left + gx.pitch * gx.count
        val bottom = top + gy.pitch * gy.count
        if (right > img.width || bottom > img.height) return null

        val rect = IntRect(
            left.toInt(), top.toInt(),
            right.toInt(), bottom.toInt(),
        )

        // 线索区范围：复用「底色占比」的办法，能顺带避开游戏上方那些撤销/重做按钮
        val cell = gx.pitch
        return Detection(
            rect = rect,
            cols = gx.count,
            rows = gy.count,
            clueWidth = findClueExtent(img, rect, cell, horizontal = true),
            clueHeight = findClueExtent(img, rect, cell, horizontal = false),
        )
    }

    /**
     * 棋盘掩码 = **格子底色 ∪ 实心青色 ∪ 划掉的奶油色**。
     *
     * 一个格子在这游戏里只有这三种样子之一：
     * - 没动过：(48,58,70) 深灰底
     * - 填实了：青色
     * - 划掉了：奶油色 (243,243,231) 底 + 一个深色叉
     *
     * **三种必须都算进来**。少算一种，那种格子多的行/列就会从「棋盘范围」里掉出去 ——
     * 之前在 15×15 上漏了青色，填满的行整行消失、格数少算两行；
     * 在 8×8 上漏了奶油色，因为大半格子都被划掉了，列数直接只数出一半。
     *
     * 奶油色的容差必须很小：放宽到 8 就会把白色文字和广告也匹配进来，
     * 棋盘范围被拉到屏幕底部。
     */
    private fun buildCellMask(img: ScreenImage): BooleanArray {
        val out = BooleanArray(img.width * img.height)
        val px = img.pixels
        for (i in px.indices) {
            val c = px[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF

            val isCellBg = abs(r - CELL_R) <= COLOR_TOL &&
                abs(g - CELL_G) <= COLOR_TOL &&
                abs(b - CELL_B) <= COLOR_TOL

            // 实心格子的青色，跟 ScreenImage.isFilledColor 同一套判据
            val isFilled = g > 110 && g - r > 60 && g - b < 25 && b > 90

            // 划掉的格子（浅奶油色）
            val isMarked = abs(r - MARK_R) <= COLOR_TOL &&
                abs(g - MARK_G) <= COLOR_TOL &&
                abs(b - MARK_B) <= COLOR_TOL

            if (isCellBg || isFilled || isMarked) out[i] = true
        }
        return out
    }

    private class Axis(val start: Float, val pitch: Float, val count: Int)

    /**
     * 在一条投影上找棋盘的起止和格子数。
     *
     * 两种信息各取所长：
     * - **周期**用自相关在实心区里算 —— 对零星的噪声不敏感。试过「数暗缝间距取中位数」，
     *   容易多算一条或少算一条，格数就错了。
     * - **位置**用暗缝做最小二乘拟合 —— 自相关只能给出整数周期，直接拿它的中点当边界
     *   会偏 7~8 像素；而「暗缝中心 = 左边界 + i×周期」这个关系能把误差压到 1~5 像素。
     */
    private fun detectAxis(profile: FloatArray): Axis? {
        val max = profile.maxOrNull() ?: return null
        if (max < 20f) return null

        // 1) 实心区（格子内部）：投影明显高的一段
        var lo = -1
        var hi = -1
        val solidLevel = max * 0.5f
        for (i in profile.indices) {
            if (profile[i] > solidLevel) {
                if (lo < 0) lo = i
                hi = i
            }
        }
        if (lo < 0 || hi - lo < 30) return null

        // 2) 定周期。
        //    优先用「暗缝的相邻间距」—— 相邻两条暗缝永远相距正好一个周期，
        //    所以中位数就是**基频**，不会被谐波骗。
        //    自相关虽然在大多数盘上没问题，但遇到有重复图案的盘（比如 8×8 那局，
        //    好几行都是「×实××实×」来回重复）会把 2 倍周期当成主周期，
        //    格数直接少一半。
        //
        //    这里的阈值必须比范围判定低得多：真正的暗缝是几乎全黑的（掩码像素≈0），
        //    而格子里画的「×」只是让那一列像素少一些。用 0.5 的话每个 × 都会被
        //    当成一条暗缝，间距里混进一堆 20~30px 的假值，把中位数从 77 拉到 75，
        //    格数就多算了一列。
        var pitch = pitchFromGaps(profile, max * GAP_LEVEL, lo, hi) ?: 0f
        if (pitch < MIN_PITCH) {
            pitch = estimatePitch(profile, lo, hi) ?: return null
        }
        if (pitch < MIN_PITCH) return null

        val count = Math.round((hi - lo + 1) / pitch)
        if (count < MIN_CELLS || count > MAX_CELLS) return null

        var start = (lo + hi) / 2f - count * pitch / 2f

        // 3) 用暗缝精修位置
        val refined = refineByGaps(profile, max * 0.35f, lo, hi, start, pitch, count)
        if (refined != null) start = refined

        if (start < 0) return null
        return Axis(start, pitch, count)
    }

    /**
     * 用暗缝之间的间距求周期。相邻暗缝永远相距一个周期，所以取中位数就是基频。
     * 找不到足够的暗缝就返回 null，交给自相关兜底。
     */
    private fun pitchFromGaps(
        profile: FloatArray,
        threshold: Float,
        lo: Int,
        hi: Int,
    ): Float? {
        val from = maxOf(0, lo - 40)
        val to = minOf(profile.size - 1, hi + 40)
        val centers = ArrayList<Float>()
        var start = -1

        for (i in from..to) {
            val low = profile[i] < threshold
            if (low && start < 0) start = i
            if (!low && start >= 0) {
                if (i - start <= MAX_GAP_WIDTH) centers.add((start + i - 1) / 2f)
                start = -1
            }
        }
        if (start >= 0 && to - start + 1 <= MAX_GAP_WIDTH) {
            centers.add((start + to) / 2f)
        }

        if (centers.size < 3) return null
        val spacings = FloatArray(centers.size - 1) { centers[it + 1] - centers[it] }
        return spacings.sorted()[spacings.size / 2]
    }

    /** 在 [lo]~[hi] 这段里用自相关找重复周期 */
    private fun estimatePitch(profile: FloatArray, lo: Int, hi: Int): Float? {
        val n = hi - lo + 1
        if (n < 20) return null
        var mean = 0f
        for (i in lo..hi) mean += profile[i]
        mean /= n
        val centered = FloatArray(n) { profile[lo + it] - mean }

        val maxLag = n / 2
        if (maxLag < 6) return null
        val ac = FloatArray(maxLag)
        for (lag in 1 until maxLag) {
            var s = 0f
            for (i in 0 until n - lag) s += centered[i] * centered[i + lag]
            ac[lag] = s
        }

        var bestLag = -1
        var bestVal = 0f
        val from = maxOf(4, (n * 0.02f).toInt())
        for (lag in from until maxLag - 1) {
            if (ac[lag] >= ac[lag - 1] && ac[lag] >= ac[lag + 1] && ac[lag] > 0f && ac[lag] > bestVal) {
                bestVal = ac[lag]
                bestLag = lag
            }
        }
        return if (bestLag > 0) bestLag.toFloat() else null
    }

    /**
     * 用暗缝位置反推左边界。
     *
     * 每条暗缝的中心都该落在 `left + i × pitch` 上（i 从 1 数到 count-1），
     * 所以把 `g_i - i×pitch` 平均一下就是最靠谱的左边界估计。
     */
    private fun refineByGaps(
        profile: FloatArray,
        threshold: Float,
        lo: Int,
        hi: Int,
        startGuess: Float,
        pitch: Float,
        count: Int,
    ): Float? {
        val maxGapWidth = pitch * 0.6f
        val offsets = ArrayList<Float>()

        var s = -1
        for (i in lo..hi + 1) {
            val low = i <= hi && profile[i] < threshold
            if (low && s < 0) s = i
            if (!low && s >= 0) {
                val end = i - 1
                val width = end - s + 1
                // 暗缝不可能比格子还宽；太宽的是画面本身的空白
                if (width <= maxGapWidth) {
                    val center = (s + end) / 2f
                    val idx = Math.round((center - startGuess) / pitch)
                    if (idx in 1 until count) {
                        offsets.add(center - idx * pitch)
                    }
                }
                s = -1
            }
        }

        if (offsets.size < 3) return null
        var sum = 0f
        for (o in offsets) sum += o
        return sum / offsets.size
    }

    /**
     * 线索区从网格往外延伸多远。
     *
     * 线索框的底色和格子接近（容差放宽才匹配得上），所以「这片颜色在网格跨度上的占比」
     * 一越过线索区边界就会掉下去。靠这个能避开游戏上方那些撤销/重做按钮。
     */
    private fun findClueExtent(
        img: ScreenImage,
        rect: IntRect,
        cell: Float,
        horizontal: Boolean,
    ): Int {
        val maxExtent = (cell * 5).toInt()
        var lastGood = 0
        var d = 0
        while (d < maxExtent) {
            val at = if (horizontal) rect.left - d else rect.top - d
            val ratio = clueRatioAt(img, rect, at, horizontal)
            if (ratio >= 0.5f) lastGood = d
            else if (d - lastGood > cell * 0.6f) break
            d += 2
        }
        return lastGood.coerceIn((cell * 1.2f).toInt(), maxExtent)
    }

    private fun clueRatioAt(img: ScreenImage, rect: IntRect, at: Int, horizontal: Boolean): Float {
        if (at < 0) return 0f
        var hit = 0
        var cnt = 0
        if (horizontal) {
            for (y in rect.top until rect.bottom) {
                if (!img.inBounds(at, y)) continue
                cnt++
                if (img.isCellBackground(at, y)) hit++
            }
        } else {
            for (x in rect.left until rect.right) {
                if (!img.inBounds(x, at)) continue
                cnt++
                if (img.isCellBackground(x, at)) hit++
            }
        }
        return if (cnt > 0) hit.toFloat() / cnt else 0f
    }
}
