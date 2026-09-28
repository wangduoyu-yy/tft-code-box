package com.wangye.tftbox.nonogram

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 从一张截图里读出整个棋盘：格子状态 + 行线索 + 列线索。
 *
 * 三步：
 * 1. **格子状态** —— 每格算「青色像素占比」。不能用单点取色：空格上画着一个大大的 ×，
 *    正中心恰好是叉的交点，取单点必然读错。
 * 2. **字形切分** —— 线索数字是白/橙的亮字，二值化后跑连通域。橙色（该段已满足）
 *    和白色都要认，所以判据是「够亮」而不是「是白色」。
 * 3. **数字识别** —— 每个字形按高度归一化后跟模板做互相关。实测 72 个字形全对。
 */
class BoardReader(private val templates: DigitTemplates) {

    data class ReadResult(
        val cols: Int,
        val rows: Int,
        val rowClues: List<IntArray>,
        val colClues: List<IntArray>,
        val state: Array<IntArray>,
        /** 非致命的问题，比如某条线索没读出来 */
        val warnings: List<String> = emptyList(),
    )

    /** 自动找棋盘并读出来。找不到棋盘返回 null */
    fun read(img: ScreenImage): ReadResult? {
        val det = GridScanner.autoDetect(img) ?: return null

        val warnings = ArrayList<String>()
        val state = readCells(img, det)
        val (rowClues, colClues) = readClues(img, det, warnings)

        return ReadResult(det.cols, det.rows, rowClues, colClues, state, warnings)
    }

    // ── 格子状态 ────────────────────────────────────────────────

    private fun readCells(img: ScreenImage, det: GridScanner.Detection): Array<IntArray> {
        val rect = det.rect
        val pitchX = rect.width.toFloat() / det.cols
        val pitchY = rect.height.toFloat() / det.rows
        val out = Array(det.rows) { IntArray(det.cols) { NonogramSolver.UNKNOWN } }

        for (r in 0 until det.rows) {
            for (c in 0 until det.cols) {
                val x0 = rect.left + c * pitchX
                val y0 = rect.top + r * pitchY
                // 往里缩到中间 50%：自动定位有 3~6px 误差，窗口收窄点才不会被
                // 挤到隔壁格子或暗缝上去
                val ix0 = (x0 + pitchX * 0.25f).toInt()
                val ix1 = (x0 + pitchX * 0.75f).toInt()
                val iy0 = (y0 + pitchY * 0.25f).toInt()
                val iy1 = (y0 + pitchY * 0.75f).toInt()

                var teal = 0
                var bright = 0
                var total = 0
                for (y in iy0..iy1) {
                    for (x in ix0..ix1) {
                        if (!img.inBounds(x, y)) continue
                        total++
                        if (img.isFilledColor(x, y)) teal++
                        if (img.isBright(x, y)) bright++
                    }
                }
                val tealRatio = if (total > 0) teal.toFloat() / total else 0f
                val brightRatio = if (total > 0) bright.toFloat() / total else 0f

                // 三种状态必须分清：
                //   画了青色  = 实心
                //   画了 ×    = 玩家**确定**这里是空的（是给求解器的约束）
                //   什么都没画 = 未知（不是约束）
                // 把「没画」当成「确定为空」会让求解器立刻矛盾 —— 新开一局全空白，
                // 线索又要求必须填，就会误报「识别错了」。
                out[r][c] = when {
                    tealRatio > 0.5f -> NonogramSolver.FILLED
                    brightRatio > MARKED_EMPTY_RATIO -> NonogramSolver.EMPTY
                    else -> NonogramSolver.UNKNOWN
                }
            }
        }
        return out
    }

    // ── 线索 ────────────────────────────────────────────────────

    private fun readClues(
        img: ScreenImage,
        det: GridScanner.Detection,
        warnings: MutableList<String>,
    ): Pair<List<IntArray>, List<IntArray>> {
        val rect = det.rect
        val pitch = rect.width.toFloat() / det.cols

        // 行线索：网格左边那一竖条
        val rowRegion = IntRect(
            left = max(0, rect.left - det.clueWidth),
            top = rect.top,
            right = rect.left,
            bottom = rect.bottom,
        )
        // 列线索：网格上边那一横条
        val colRegion = IntRect(
            left = rect.left,
            top = max(0, rect.top - det.clueHeight),
            right = rect.right,
            bottom = rect.top,
        )

        var rowGlyphs = findGlyphs(img, rowRegion, pitch)
        var colGlyphs = findGlyphs(img, colRegion, pitch)

        // 按位置归到各自的线索框
        val pitchY = rect.height.toFloat() / det.rows
        val byRow = Array(det.rows) { ArrayList<Glyph>() }
        for (g in rowGlyphs) {
            val idx = (((g.cy - rect.top) / pitchY).toInt()).coerceIn(0, det.rows - 1)
            byRow[idx].add(g)
        }
        val byCol = Array(det.cols) { ArrayList<Glyph>() }
        for (g in colGlyphs) {
            val idx = (((g.cx - rect.left) / pitch).toInt()).coerceIn(0, det.cols - 1)
            byCol[idx].add(g)
        }

        val rowClues = ArrayList<IntArray>(det.rows)
        for (r in 0 until det.rows) {
            val nums = groupRowClue(byRow[r].sortedBy { it.x })
            if (nums == null) warnings.add("第 ${r + 1} 行的线索没读懂")
            rowClues.add(nums ?: intArrayOf())
        }

        val colClues = ArrayList<IntArray>(det.cols)
        for (c in 0 until det.cols) {
            val nums = groupColClue(byCol[c], pitch)
            if (nums == null) warnings.add("第 ${c + 1} 列的线索没读懂")
            colClues.add(nums ?: intArrayOf())
        }

        return rowClues to colClues
    }

    /** 行线索：整行从左到右，数字之间靠较大间距切分 */
    private fun groupRowClue(glyphs: List<Glyph>): IntArray? {
        if (glyphs.isEmpty()) return null
        val out = ArrayList<Int>()
        var cur = StringBuilder()
        var prevEnd: Int? = null
        for (g in glyphs) {
            val ch = g.digit ?: return null
            if (prevEnd != null && g.x - prevEnd > g.h * 0.55) {
                out.add(cur.toString().toIntOrNull() ?: return null)
                cur = StringBuilder()
            }
            cur.append(ch)
            prevEnd = g.x + g.w
        }
        if (cur.isNotEmpty()) out.add(cur.toString().toIntOrNull() ?: return null)
        return out.toIntArray().takeIf { it.isNotEmpty() }
    }

    /** 列线索：数字是竖着堆的，同一数字内的字形 y 接近，不同数字 y 差得开 */
    private fun groupColClue(glyphs: List<Glyph>, pitch: Float): IntArray? {
        if (glyphs.isEmpty()) return null
        val lines = ArrayList<ArrayList<Glyph>>()
        for (g in glyphs.sortedBy { it.cy }) {
            val last = lines.lastOrNull()
            if (last != null && abs(g.cy - last.last().cy) < pitch * 0.35f) last.add(g)
            else lines.add(arrayListOf(g))
        }
        val out = ArrayList<Int>()
        for (ln in lines) {
            val sb = StringBuilder()
            for (g in ln.sortedBy { it.x }) {
                val ch = g.digit ?: return null
                sb.append(ch)
            }
            out.add(sb.toString().toIntOrNull() ?: return null)
        }
        return out.toIntArray().takeIf { it.isNotEmpty() }
    }

    // ── 字形切分 ────────────────────────────────────────────────

    private class Glyph(
        val x: Int, val y: Int, val w: Int, val h: Int,
        val cx: Int, val cy: Int,
        val digit: Char?,
    )

    private fun findGlyphs(img: ScreenImage, region: IntRect, pitch: Float): List<Glyph> {
        val w = region.width
        val h = region.height
        if (w <= 2 || h <= 2) return emptyList()

        val bright = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val px = region.left + x
                val py = region.top + y
                if (img.inBounds(px, py) && img.isBright(px, py)) {
                    bright[y * w + x] = true
                }
            }
        }

        val labels = IntArray(w * h)
        val out = ArrayList<Glyph>()
        val stack = IntArray(w * h)
        var next = 0

        for (start in 0 until w * h) {
            if (!bright[start] || labels[start] != 0) continue
            next++
            var sp = 0
            stack[sp++] = start
            labels[start] = next
            var minX = w; var maxX = -1; var minY = h; var maxY = -1
            var count = 0
            while (sp > 0) {
                val cur = stack[--sp]
                val cy = cur / w
                val cx = cur % w
                count++
                if (cx < minX) minX = cx
                if (cx > maxX) maxX = cx
                if (cy < minY) minY = cy
                if (cy > maxY) maxY = cy
                // 4 邻域。每个像素最多入栈一次，栈开 w*h 够用
                if (cx > 0) {
                    val n = cur - 1
                    if (bright[n] && labels[n] == 0) { labels[n] = next; stack[sp++] = n }
                }
                if (cx < w - 1) {
                    val n = cur + 1
                    if (bright[n] && labels[n] == 0) { labels[n] = next; stack[sp++] = n }
                }
                if (cy > 0) {
                    val n = cur - w
                    if (bright[n] && labels[n] == 0) { labels[n] = next; stack[sp++] = n }
                }
                if (cy < h - 1) {
                    val n = cur + w
                    if (bright[n] && labels[n] == 0) { labels[n] = next; stack[sp++] = n }
                }
            }
            if (count < 16) continue

            val gw = maxX - minX + 1
            val gh = maxY - minY + 1
            // 高度过滤：太矮是噪点，太高是线索框的边框线
            if (gh < pitch * 0.18f || gh > pitch * 0.62f) continue
            if (gw > gh * 2.6f) continue

            val mask = BooleanArray(gw * gh)
            for (yy in 0 until gh) for (xx in 0 until gw) {
                mask[yy * gw + xx] = labels[(minY + yy) * w + (minX + xx)] == next
            }
            emitGlyphs(out, mask, gw, gh, region.left + minX, region.top + minY, pitch)
        }
        return out
    }

    /**
     * 把一块连通域变成一到两个字形。
     *
     * 两位数（比如 `13`、`10`）挨得紧的时候**底部的笔画会粘在一起**，整块变成
     * 一个宽连通域，直接拿去匹配必然失败 —— 表现为整条线索读不出来。
     *
     * 修法：宽高比超过 1 就试着一刀切开，**判据是「切开后左右两边都能被认出来」**，
     * 取总分最高的切点。不能简单地切「墨最少的那一列」——
     * `0` 中间的空心墨也很少，切在那儿两边都认不出数字，会被这个判据排除掉。
     */
    private fun emitGlyphs(
        out: MutableList<Glyph>,
        mask: BooleanArray, gw: Int, gh: Int,
        originX: Int, originY: Int,
        pitch: Float,
    ) {
        // 单个数字的宽高比不会超过 1 多少，超过就认为粘了两个
        if (gw <= gh * 1.05f) {
            emitOne(out, mask, gw, gh, originX, originY, pitch, 0, gw)
            return
        }

        var bestSplit = -1
        var bestTotal = -1f
        val lo = maxOf(2, (gw * 0.22f).toInt())
        val hi = minOf(gw - 3, (gw * 0.78f).toInt())
        for (sx in lo..hi) {
            val sl = pieceScore(mask, gw, gh, 0, sx) ?: continue
            val sr = pieceScore(mask, gw, gh, sx, gw) ?: continue
            if (sl + sr > bestTotal) {
                bestTotal = sl + sr
                bestSplit = sx
            }
        }

        if (bestSplit < 0) {
            // 切不开，就当一个整体认认看（认不出自然会返回 null）
            emitOne(out, mask, gw, gh, originX, originY, pitch, 0, gw)
            return
        }
        emitOne(out, mask, gw, gh, originX, originY, pitch, 0, bestSplit)
        emitOne(out, mask, gw, gh, originX, originY, pitch, bestSplit, gw)
    }

    /** 这一段的形状有多像一个数字；太窄了直接判为不可能 */
    private fun pieceScore(mask: BooleanArray, gw: Int, gh: Int, x0: Int, x1: Int): Float? {
        val pw = x1 - x0
        if (pw < gh * 0.22f || pw > gh * 1.8f) return null
        return templates.bestScore(cut(mask, gw, gh, x0, x1))
    }

    private fun emitOne(
        out: MutableList<Glyph>,
        mask: BooleanArray, gw: Int, gh: Int,
        originX: Int, originY: Int,
        pitch: Float,
        x0: Int, x1: Int,
    ) {
        val pw = x1 - x0
        if (pw < pitch * 0.06f || pw > pitch * 0.62f) return
        if (pw > gh * 1.8f) return
        val vec = cut(mask, gw, gh, x0, x1)
        out.add(
            Glyph(
                x = originX + x0, y = originY, w = pw, h = gh,
                cx = originX + x0 + pw / 2, cy = originY + gh / 2,
                digit = templates.match(vec),
            )
        )
    }

    /** 从大掩码里裁出一段并归一化成模板尺寸 */
    private fun cut(mask: BooleanArray, gw: Int, gh: Int, x0: Int, x1: Int): FloatArray {
        val pw = x1 - x0
        val sub = BooleanArray(pw * gh)
        for (yy in 0 until gh) for (xx in 0 until pw) {
            sub[yy * pw + xx] = mask[yy * gw + x0 + xx]
        }
        return normalize(sub, pw, gh)
    }

    /** 按高度缩放到 24px、水平居中放进 30×24，跟模板同样的处理方式 */
    private fun normalize(mask: BooleanArray, gw: Int, gh: Int): FloatArray {
        val targetH = DigitTemplates.H
        val targetW = DigitTemplates.W
        val scaledW = max(1, (gw.toFloat() * targetH / gh).roundToInt())

        val canvas = FloatArray(targetH * targetW)
        val offX = (targetW - scaledW) / 2

        for (ty in 0 until targetH) {
            // 映射回原图坐标（双线性）
            val sy = (ty + 0.5f) * gh / targetH - 0.5f
            val y0 = kotlin.math.floor(sy).toInt()
            val fy = sy - y0
            for (tx in 0 until scaledW) {
                val sx = (tx + 0.5f) * gw / scaledW - 0.5f
                val x0 = kotlin.math.floor(sx).toInt()
                val fx = sx - x0

                fun at(xx: Int, yy: Int): Float =
                    if (xx in 0 until gw && yy in 0 until gh && mask[yy * gw + xx]) 1f else 0f

                val v = at(x0, y0) * (1 - fx) * (1 - fy) +
                    at(x0 + 1, y0) * fx * (1 - fy) +
                    at(x0, y0 + 1) * (1 - fx) * fy +
                    at(x0 + 1, y0 + 1) * fx * fy

                val destX = tx + offX
                if (destX in 0 until targetW) canvas[ty * targetW + destX] = v
            }
        }
        return canvas
    }

    private companion object {
        /**
         * 一格里的亮像素占比超过这个值，就认为玩家在上面画了 ×。
         * 空白格几乎是纯底色，占比接近 0；× 是粗线条，占比高得多，两者分得很开。
         */
        const val MARKED_EMPTY_RATIO = 0.03f
    }
}
