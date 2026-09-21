package com.wangye.tftbox.util

import android.util.Base64

/**
 * 解析从抖音/小红书/贴吧复制过来的整段阵容分享文本。
 *
 * 实际见过的形态长这样：
 * ```
 * 【阵容码】#【抖音大鱼】天降重炮大嘴#MjE5OTkxMDM0MzQ1MDYzODIxNzg5NjY0NTI3OTY3
 *    ↑标记        ↑作者      ↑阵容名              ↑真正的码
 * ```
 * 各家发的格式不统一，所以这里只做「尽量抠出有用的东西」，
 * 抠不出来就退化成「整段当码」，绝不因为格式不认识就拒绝保存。
 */
object LineupShareParser {

    /** 【作者】这种方括号包裹的片段 */
    private val BRACKET = Regex("【(.+?)】")

    /** 候选码：一段连续的、不含空格的字母数字 + base64 常用符号 */
    private val TOKEN = Regex("[A-Za-z0-9+/=_-]{16,}")

    private const val MARKER = "阵容码"

    /**
     * 官方格式把阵容名夹在一对 ## 之间，是最可靠的来源：
     * ```
     * 【阵容码】##兔铲铲-重装枪手##【运营思路】@@@@@@@@@@@@@@H4sIAAAAAAAAA...
     * ```
     */
    private val TITLE_IN_HASHES = Regex("##\\s*([^#]{1,60}?)\\s*##")

    /** 方括号里出现这些词说明是段落标题，不是作者名 */
    private val SECTION_WORDS = listOf(
        "阵容码", "运营思路", "出装", "站位", "装备", "羁绊", "思路", "海克斯", "强化", "过渡"
    )

    data class Parsed(
        val code: String,
        val title: String? = null,
        val author: String? = null,
    )

    fun parse(raw: String?): Parsed? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null

        val code = extractCode(text) ?: return null

        // 官方格式优先：名字明明白白夹在一对 ## 里，没有歧义
        TITLE_IN_HASHES.find(text)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return Parsed(code = code, title = it.take(60), author = null) }

        // 作者和阵容名都在「阵容码」标记和码本体之间这一小段里。
        // 抖音分享文案前后会夹一堆「复制口令打开APP领福利」之类的废话，
        // 只取这个区间，噪声就进不来了 —— 而且噪声往往比真名字还长，
        // 不划范围的话「取最长的一段」必定选错。
        val codeAt = text.indexOf(code)
        val markerAt = text.indexOf(MARKER)
        val start = if (markerAt in 0 until codeAt) markerAt + MARKER.length else 0
        val segment = text.substring(start, codeAt.coerceAtLeast(start))
            .trimStart('】', '#', ':', '：', ' ', '\n', '\t')

        val author = BRACKET.find(segment)
            ?.groupValues?.get(1)?.trim()
            ?.takeIf { name ->
                name.isNotBlank() && SECTION_WORDS.none { name.contains(it) }
            }

        val title = segment
            .replace(BRACKET, " ")
            .split('#', '\n')
            .map { it.trim() }
            // 必须含字母或数字，否则「@@@@@@@@」这种填充物会因为够长而被选中
            .filter { it.isNotBlank() && it.any { c -> c.isLetterOrDigit() } }
            .filter { !it.contains(MARKER) }
            .maxByOrNull { it.length }
            ?.take(60)

        return Parsed(code = code, title = title, author = author)
    }

    /**
     * 从一段文本里挑出最像阵容码的那一段。
     *
     * 分两档：
     *  1. 能 base64 解出「纯 ASCII 数字」的 —— 这是确认过的金铲铲格式，最强证据
     *  2. 退而求其次，够长、够像 token 的
     */
    private fun extractCode(text: String): String? {
        val candidates = TOKEN.findAll(text).map { it.value }.toList()
        if (candidates.isEmpty()) return null

        // 第一档：base64 → 纯数字
        candidates.firstOrNull { decodesToDigits(it) }?.let { return it }

        // 第二档：够长的裸 token。整段文本本身就是码的情况走这里。
        candidates.firstOrNull { it.length >= 24 }?.let { return it }

        // 第三档：整段就是一个短 token
        if (candidates.size == 1 && looksLikeCode(text)) return candidates.first()

        return null
    }

    private fun decodesToDigits(token: String): Boolean {
        if (token.length < 12) return false
        return try {
            val padded = token + "=".repeat((4 - token.length % 4) % 4)
            val bytes = Base64.decode(padded, Base64.DEFAULT)
            bytes.isNotEmpty() && bytes.all { it.toInt() in 48..57 }
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * 粗判一段文本像不像阵容码。
     *
     * 没去解析格式，只排除明显不是的：太短、太长、带换行或空格的整段正文。
     * 宁可漏判也不要误判 —— 误判会在用户复制一句普通文字时弹「要保存吗」，很烦。
     */
    fun looksLikeCode(text: String?): Boolean {
        val t = text?.trim().orEmpty()
        if (t.length < 16 || t.length > 4096) return false
        if (t.contains('\n') || t.contains(' ')) return false
        return t.any { it.isLetterOrDigit() }
    }
}
