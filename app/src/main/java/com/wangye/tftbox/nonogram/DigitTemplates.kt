package com.wangye.tftbox.nonogram

import android.content.Context
import com.wangye.tftbox.R

/**
 * 线索数字的字形模板。
 *
 * 从真实游戏截图里切出 72 个字形、用已知答案打上标签、逐类平均得到 10 个模板，
 * 打包在 `res/raw/digit_templates.bin`（10 × 24 × 30 字节，每字节 0 或 1）。
 *
 * 游戏用的是固定字体，所以模板匹配比通用 OCR 又快又准：
 * 实测 72 个字形全部识别正确，最低匹配得分 0.883（阈值 0.55，余量很足）。
 *
 * 万一以后游戏换字体导致识别不准，需要重新采样模板。
 */
class DigitTemplates private constructor(
    /** 10 个模板，每个是 [H]×[W] 的灰度向量（这里只有 0/1） */
    private val templates: Array<FloatArray>,
) {

    /**
     * 认出这是哪个数字。低于 [minScore] 就返回 null，宁可说认不出，也不给错答案。
     */
    fun match(vec: FloatArray, minScore: Float = MIN_SCORE): Char? {
        val best = bestScore(vec)
        val digit = bestDigit(vec)
        return if (digit >= 0 && best >= minScore) ('0' + digit) else null
    }

    /** 跟最像的那个模板有多像（0~1）。用来判断一块形状到底像不像一个数字 */
    fun bestScore(vec: FloatArray): Float {
        var best = -1f
        for (d in templates.indices) {
            val s = correlation(vec, templates[d])
            if (s > best) best = s
        }
        return best
    }

    fun bestDigit(vec: FloatArray): Int {
        var bestDigit = -1
        var best = -1f
        for (d in templates.indices) {
            val s = correlation(vec, templates[d])
            if (s > best) {
                best = s
                bestDigit = d
            }
        }
        return bestDigit
    }

    /** 零均值归一化互相关：对整体明暗变化不敏感，只看形状 */
    private fun correlation(a: FloatArray, b: FloatArray): Float {
        var ma = 0f
        var mb = 0f
        for (i in a.indices) { ma += a[i]; mb += b[i] }
        ma /= a.size
        mb /= b.size
        var num = 0f
        var da = 0f
        var db = 0f
        for (i in a.indices) {
            val x = a[i] - ma
            val y = b[i] - mb
            num += x * y
            da += x * x
            db += y * y
        }
        val den = kotlin.math.sqrt(da) * kotlin.math.sqrt(db)
        return if (den > 1e-6f) num / den else 0f
    }

    companion object {
        const val W = 30
        const val H = 24

        /**
         * 实测最低分 0.883，取 0.55 作阈值留足余量：
         * 正常情况绝不会误判，遇到根本不认识的字体则会老实返回 null。
         */
        private const val MIN_SCORE = 0.55f

        fun load(context: Context): DigitTemplates? = runCatching {
            context.resources.openRawResource(R.raw.digit_templates)
                .use { it.readBytes() }
                .let { fromBytes(it) }
        }.getOrNull()

        /**
         * 直接从字节建。抽出来是为了让单元测试能喂固定的模板文件，
         * 不必依赖 Android 的 Context。
         */
        fun fromBytes(bytes: ByteArray): DigitTemplates? {
            val need = 10 * W * H
            if (bytes.size < need) return null
            val arr = Array(10) { d ->
                FloatArray(W * H) { i ->
                    if (bytes[d * W * H + i].toInt() != 0) 1f else 0f
                }
            }
            return DigitTemplates(arr)
        }
    }
}
