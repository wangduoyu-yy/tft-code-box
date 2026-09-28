package com.wangye.tftbox.nonogram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 拿**真实的游戏截图**跑整条识别链路：自动定位棋盘 → 读格子 → 读线索。
 *
 * 图片在 `src/test/resources/nonogram_sample.png`，是一张 15×15 的实际对局截图，
 * 当时棋盘还是空的（既没填色也没画 ×）。
 *
 * 这组测试是最有价值的 —— 求解器再对，输入的棋盘读错了也白搭。
 */
class BoardReaderTest {

    private val onFilled = NonogramSolver.FILLED
    private val onEmpty = NonogramSolver.EMPTY

    /** 手工量出来的棋盘像素框，用来验证自动定位准不准 */
    private val trueRect = IntRect(181, 629, 918, 1366)

    private val expectedRows = listOf(
        intArrayOf(4, 3), intArrayOf(2, 3, 3), intArrayOf(2, 1, 2, 2), intArrayOf(3, 3, 2),
        intArrayOf(8, 2), intArrayOf(2, 5, 1), intArrayOf(2, 6, 2), intArrayOf(2, 7, 2),
        intArrayOf(1, 11), intArrayOf(1, 11), intArrayOf(1, 11), intArrayOf(13),
        intArrayOf(13), intArrayOf(12), intArrayOf(9),
    )

    private val expectedCols = listOf(
        intArrayOf(4), intArrayOf(8, 2), intArrayOf(1, 11), intArrayOf(2, 1, 4),
        intArrayOf(3, 11), intArrayOf(1, 12), intArrayOf(12), intArrayOf(13),
        intArrayOf(3, 10), intArrayOf(2, 9), intArrayOf(2, 8), intArrayOf(1, 7),
        intArrayOf(3, 6), intArrayOf(10), intArrayOf(6),
    )

    /**
     * 读样例图。
     *
     * 得用反射调 `javax.imageio` —— Android 的编译 classpath 是 `android.jar`，
     * 它把 JDK 的 `java.desktop` 顶掉了，直接 import 编译不过；
     * 但单元测试实际跑在真 JDK 上，运行时这个类是有的。
     */
    private fun loadSample(): ScreenImage? = loadImage("nonogram_sample.png")

    /** 三星手机（1440×3120）上的实拍，验证换分辨率也能认</summary> */
    private fun loadSamsung(): ScreenImage? = loadImage("nonogram_samsung.png")

    private fun loadImage(name: String): ScreenImage? = runCatching {
        val stream = javaClass.classLoader?.getResourceAsStream(name)
            ?: return null
        val imageIO = Class.forName("javax.imageio.ImageIO")
        val read = imageIO.getMethod("read", java.io.InputStream::class.java)
        val img = stream.use { read.invoke(null, it) } ?: return null

        val cls = img.javaClass
        val w = cls.getMethod("getWidth").invoke(img) as Int
        val h = cls.getMethod("getHeight").invoke(img) as Int
        val px = IntArray(w * h)
        cls.getMethod(
            "getRGB",
            Int::class.java, Int::class.java, Int::class.java, Int::class.java,
            IntArray::class.java, Int::class.java, Int::class.java,
        ).invoke(img, 0, 0, w, h, px, 0, w)
        ScreenImage(px, w, h)
    }.getOrNull()

    private fun loadTemplates(): DigitTemplates? {
        val bytes = javaClass.classLoader?.getResourceAsStream("digit_templates.bin")
            ?.use { it.readBytes() } ?: return null
        return DigitTemplates.fromBytes(bytes)
    }

    // ── 自动定位 ────────────────────────────────────────────────

    /**
     * 8×8 的小盘，而且玩家把大半格子划掉了（奶油色底 + 深色叉）。
     *
     * 这盘踩过两个坑：
     * 1. 掩码只算「底色 ∪ 青色」时，因为大半格子是奶油色，列数只数出一半
     * 2. 自相关把 2 倍周期当成主周期（好几行都是「×实××××实×」重复图案），
     *    格数直接砍半
     */
    @Test
    fun `8x8 小盘也能准确定位`() {
        val img = loadImage("nonogram_8x8.png") ?: return
        val det = GridScanner.autoDetect(img) ?: run {
            throw AssertionError("8x8 定位失败")
        }
        assertEquals("列数", 8, det.cols)
        assertEquals("行数", 8, det.rows)
    }

    @Test
    fun `8x8 小盘 划掉的格子能读成空`() {
        val img = loadImage("nonogram_8x8.png") ?: return
        val tpl = loadTemplates() ?: return
        val read = BoardReader(tpl).read(img) ?: run {
            throw AssertionError("8x8 读不出来")
        }
        var empty = 0
        var filled = 0
        for (r in 0 until 8) for (c in 0 until 8) {
            when (read.state[r][c]) {
                NonogramSolver.EMPTY -> empty++
                NonogramSolver.FILLED -> filled++
            }
        }
        assertTrue("应该读出玩家划掉的格子", empty > 5)
        assertTrue("应该读出玩家填实的格子", filled > 5)
    }

    /**
     * 格子里画了 × 的局面。这张图踩的坑是：× 是深色笔画，扫到它那一列掩码像素变少，
     * 被当成了格子之间的暗缝 —— 算出的周期偏小，格数多一列。
     * 修法是把判定暗缝的阈值压低，只挑真正全黑的缝。
     */
    @Test
    fun `格子里画满叉也不影响定位`() {
        val img = loadImage("nonogram_hard.png") ?: return
        val det = GridScanner.autoDetect(img) ?: run {
            throw AssertionError("画了叉之后定位失败")
        }
        assertEquals("列数", 15, det.cols)
        assertEquals("行数", 15, det.rows)
    }

    @Test
    fun `自动定位棋盘 - 不需要用户标定`() {
        val img = loadSample() ?: return
        val det = GridScanner.autoDetect(img)
        assertNotNull("应该能自动找到棋盘", det)

        assertEquals("列数", 15, det!!.cols)
        assertEquals("行数", 15, det.rows)

        // 位置允许有几像素误差（靠暗缝反推，精度约 2~6px）
        val r = det.rect
        assertTrue("左边界偏差过大：${r.left} vs ${trueRect.left}",
            kotlin.math.abs(r.left - trueRect.left) <= 8)
        assertTrue("上边界偏差过大：${r.top} vs ${trueRect.top}",
            kotlin.math.abs(r.top - trueRect.top) <= 8)
        assertTrue("右边界偏差过大：${r.right} vs ${trueRect.right}",
            kotlin.math.abs(r.right - trueRect.right) <= 8)
        assertTrue("下边界偏差过大：${r.bottom} vs ${trueRect.bottom}",
            kotlin.math.abs(r.bottom - trueRect.bottom) <= 8)

        // 格子中心偏差才是真正要紧的：采样窗口就开在中心附近
        val pitch = r.width.toFloat() / det.cols
        val truePitch = trueRect.width.toFloat() / 15
        var worst = 0f
        for (i in 0 until 15) {
            val a = r.left + pitch * (i + 0.5f)
            val b = trueRect.left + truePitch * (i + 0.5f)
            worst = maxOf(worst, kotlin.math.abs(a - b))
        }
        assertTrue("格子中心最大偏差 $worst px，太大会采到隔壁格子", worst < 10f)
    }

    // ── 线索 ────────────────────────────────────────────────────

    @Test
    fun `15条行线索全部读对`() { checkRows(loadSample() ?: return) }

    @Test
    fun `15条行线索全部读对 - 三星手机分辨率`() { checkRows(loadSamsung() ?: return) }

    private fun checkRows(img: ScreenImage) {
        val tpl = loadTemplates() ?: return
        val read = BoardReader(tpl).read(img) ?: run {
            throw AssertionError("自动定位失败，没找到棋盘")
        }
        assertTrue("不该有读不出来的线索：${read.warnings}", read.warnings.isEmpty())
        for (r in expectedRows.indices) {
            assertEquals("第 ${r + 1} 行线索", expectedRows[r].toList(), read.rowClues[r].toList())
        }
    }

    @Test
    fun `15条列线索全部读对 包含粘在一起的两位数`() { checkCols(loadSample() ?: return) }

    @Test
    fun `15条列线索全部读对 - 三星手机分辨率`() { checkCols(loadSamsung() ?: return) }

    private fun checkCols(img: ScreenImage) {
        val tpl = loadTemplates() ?: return
        val read = BoardReader(tpl).read(img) ?: run {
            throw AssertionError("自动定位失败，没找到棋盘")
        }
        for (c in expectedCols.indices) {
            assertEquals("第 ${c + 1} 列线索", expectedCols[c].toList(), read.colClues[c].toList())
        }
    }

    /**
     * 玩家填了一部分之后的局面。
     *
     * 这是真机上踩过的坑：原先「棋盘范围」是按格子底色算的，玩家填得越多、底色越少，
     * 填满一整行时那行直接从范围里消失，格数少算两行。修法是掩码取「底色 ∪ 青色」。
     */
    private fun loadPartial(): ScreenImage? = loadImage("nonogram_partial.png")

    @Test
    fun `填了一部分的棋盘也能准确定位`() {
        val img = loadPartial() ?: return
        val det = GridScanner.autoDetect(img) ?: run {
            throw AssertionError("填了格之后定位失败")
        }
        assertEquals("列数", 15, det.cols)
        assertEquals("行数", 15, det.rows)
    }

    @Test
    fun `填了一部分的棋盘 线索照样读对`() { checkCols(loadPartial() ?: return) }

    @Test
    fun `填了一部分的棋盘 能读出玩家填过的格子`() {
        val img = loadPartial() ?: return
        val tpl = loadTemplates() ?: return
        val read = BoardReader(tpl).read(img) ?: run {
            throw AssertionError("读不出来")
        }
        var known = 0
        for (r in 0 until 15) for (c in 0 until 15) {
            if (read.state[r][c] != NonogramSolver.UNKNOWN) known++
        }
        // 这张图里玩家填了不少格，不该全读成「未知」
        assertTrue("应该读出玩家填过的格子，实际只有 $known 格是已知的", known > 10)
    }

    @Test
    fun `自动定位棋盘 - 三星手机分辨率`() {
        val img = loadSamsung() ?: return
        val det = GridScanner.autoDetect(img) ?: run {
            throw AssertionError("三星分辨率的截图定位失败")
        }
        assertEquals("列数", 15, det.cols)
        assertEquals("行数", 15, det.rows)
        assertTrue("棋盘宽度应该占屏幕大部分：${det.rect.width} / ${img.width}",
            det.rect.width > img.width * 0.7f)
    }

    // ── 格子状态 ────────────────────────────────────────────────

    @Test
    fun `空白棋盘每格都读成未知 而不是空`() {
        val img = loadSample() ?: return
        val tpl = loadTemplates() ?: return
        val read = BoardReader(tpl).read(img) ?: return

        // 这张截图是新开的一局：既没填色，也没画 ×。
        // 必须读成「未知」—— 读成「空」的话求解器会拿它当约束，
        // 立刻跟线索冲突，新开一局就会误报「识别错了」。
        var bad = 0
        for (r in 0 until 15) for (c in 0 until 15) {
            if (read.state[r][c] != NonogramSolver.UNKNOWN) bad++
        }
        assertEquals("空白棋盘每一格都该是未知状态", 0, bad)
    }

    // ── 端到端 ──────────────────────────────────────────────────

    @Test
    fun `读到的东西喂给求解器 结果自洽且提示量合理`() {
        val img = loadSample() ?: return
        val tpl = loadTemplates() ?: return
        val read = BoardReader(tpl).read(img) ?: return

        val result = NonogramSolver.solve(read.rowClues, read.colClues, read.state)
        assertTrue("不该矛盾：${result.contradiction}", result.contradiction == null)
        assertTrue("空棋盘也该推出不少格子，实际 ${result.hints.size}", result.hints.size > 100)

        for (h in result.hints) {
            assertTrue("提示坐标越界", h.row in 0 until 15 && h.col in 0 until 15)
        }
    }
}
