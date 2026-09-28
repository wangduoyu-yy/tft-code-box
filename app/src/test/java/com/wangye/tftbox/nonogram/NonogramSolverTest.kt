package com.wangye.tftbox.nonogram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 求解器的单元测试。
 *
 * 用的第一组数据是**真实的一盘游戏**：从截图里读出来的 15×15 线索，
 * 以及玩家当时的状态（只有最后一行填了）。这盘的特征是第 5 列 `[3,11]`
 * 在 15 格里刚好排满，所以应该能一路推到底、完全不需要猜。
 */
class NonogramSolverTest {

    private val U = NonogramSolver.UNKNOWN
    private val F = NonogramSolver.FILLED
    private val E = NonogramSolver.EMPTY

    private fun rows(vararg r: IntArray) = r.toList()

    // ── 真实那盘 15×15 ──────────────────────────────────────────

    private val realRows = rows(
        intArrayOf(4, 3), intArrayOf(2, 3, 3), intArrayOf(2, 1, 2, 2), intArrayOf(3, 3, 2),
        intArrayOf(8, 2), intArrayOf(2, 5, 1), intArrayOf(2, 6, 2), intArrayOf(2, 7, 2),
        intArrayOf(1, 11), intArrayOf(1, 11), intArrayOf(1, 11), intArrayOf(13),
        intArrayOf(13), intArrayOf(12), intArrayOf(9),
    )

    private val realCols = rows(
        intArrayOf(4), intArrayOf(8, 2), intArrayOf(1, 11), intArrayOf(2, 1, 4),
        intArrayOf(3, 11), intArrayOf(1, 12), intArrayOf(12), intArrayOf(13),
        intArrayOf(3, 10), intArrayOf(2, 9), intArrayOf(2, 8), intArrayOf(1, 7),
        intArrayOf(3, 6), intArrayOf(10), intArrayOf(6),
    )

    /** 正确的解，由独立实现（Python 版）算出并逐条核对过 */
    private val realSolution = arrayOf(
        "011110001110000",
        "110111001110000",
        "110010011001100",
        "111001110000110",
        "111111110000110",
        "011011111000010",
        "011011111100011",
        "011011111110011",
        "001011111111111",
        "001011111111111",
        "001011111111111",
        "001111111111111",
        "011111111111110",
        "011111111111100",
        "000111111111000",
    )

    private fun boardFrom(rowsOf: Array<String>): Array<IntArray> =
        Array(rowsOf.size) { r -> IntArray(rowsOf[r].length) { c -> if (rowsOf[r][c] == '1') F else E } }

    private fun emptyBoard(n: Int) = Array(n) { IntArray(n) { U } }

    @Test
    fun `真实那盘 - 只给最后一行 应该全部推出来`() {
        // 玩家的状态：第 15 行 = 3空 + 9实 + 3空
        val initial = emptyBoard(15)
        for (c in 0 until 15) initial[14][c] = if (c in 3..11) F else E

        val res = NonogramSolver.solve(realRows, realCols, initial)

        assertNull("不该出现矛盾", res.contradiction)
        assertTrue("应该整盘都能推出来", res.fullyDetermined)
        assertTrue("不该有歧义", !res.ambiguous)

        val expected = boardFrom(realSolution)
        for (r in 0 until 15) for (c in 0 until 15) {
            assertEquals("第${r + 1}行第${c + 1}列", expected[r][c], res.board[r][c])
        }
    }

    @Test
    fun `真实那盘 - 空棋盘起步 应该给出大量提示且都正确`() {
        val res = NonogramSolver.solve(realRows, realCols, emptyBoard(15))
        assertNull(res.contradiction)

        val expected = boardFrom(realSolution)
        assertTrue("空棋盘也该推出不少格子", res.hints.size > 100)
        for (h in res.hints) {
            val truth = expected[h.row][h.col] == F
            assertEquals("提示 第${h.row + 1}行第${h.col + 1}列 与真解不符", truth, h.filled)
        }
    }

    @Test
    fun `真实那盘 - 填错一格应该报矛盾`() {
        val initial = emptyBoard(15)
        for (c in 0 until 15) initial[14][c] = if (c in 3..11) F else E

        // 第 15 行的线索是 [9]，也就是必须正好 9 个连续实心。
        // 挖掉中间一个 → 变成 5+3 两段，和 [9] 直接冲突。
        initial[14][7] = E

        val res = NonogramSolver.solve(realRows, realCols, initial)
        assertNotNull("第15行被改坏了，应该报矛盾", res.contradiction)
    }

    @Test
    fun `真实那盘 - 额外多填一格也应该报矛盾`() {
        val initial = emptyBoard(15)
        for (c in 0 until 15) initial[14][c] = if (c in 3..11) F else E
        initial[14][0] = F      // 第15行会变成 10 个实心，与 [9] 冲突

        val res = NonogramSolver.solve(realRows, realCols, initial)
        assertNotNull("多填一格应该报矛盾", res.contradiction)
    }

    // ── 小棋盘 ──────────────────────────────────────────────────

    @Test
    fun `简单 5x5 全推`() {
        // 一个十字：
        //   ..#..
        //   ..#..
        //   #####
        //   ..#..
        //   ..#..
        // 行和 = 1+1+5+1+1 = 9，列和也是 9，自洽
        val r = rows(intArrayOf(1), intArrayOf(1), intArrayOf(5), intArrayOf(1), intArrayOf(1))
        val c = rows(intArrayOf(1), intArrayOf(1), intArrayOf(5), intArrayOf(1), intArrayOf(1))
        val res = NonogramSolver.solve(r, c, emptyBoard(5))

        assertNull("这题自洽，不该报矛盾", res.contradiction)
        assertTrue("应该能推完", res.fullyDetermined)
        assertTrue("应该给出提示", res.hints.isNotEmpty())
        // 第 3 行第 3 列一定是实心
        val mid = res.hints.find { it.row == 2 && it.col == 2 }
        assertNotNull("中间格应该被推出", mid)
        assertTrue("中间格是实心", mid!!.filled)
    }

    @Test
    fun `已经填满的线不会产生多余提示`() {
        val r = rows(intArrayOf(1), intArrayOf(1))
        val c = rows(intArrayOf(1), intArrayOf(1))
        val initial = arrayOf(intArrayOf(F, E), intArrayOf(E, F))
        val res = NonogramSolver.solve(r, c, initial)
        assertTrue("已经解完了", res.solved)
        assertTrue(res.hints.isEmpty())
    }

    @Test
    fun `线索为空时不崩`() {
        val res = NonogramSolver.solve(emptyList(), emptyList(), emptyArray())
        assertNotNull(res.contradiction)
    }

    // ── 「解出来了」和「玩家填完了」是两回事 ──────────────────────

    @Test
    fun `空棋盘上求解器能推完 但不能报告玩家填完了`() {
        // 这是真机上踩过的坑：新开一局棋盘全空，求解器纯推导就把整盘算出来了，
        // 于是界面显示「已经全填完了」——玩家一个格子都还没填呢。
        val res = NonogramSolver.solve(realRows, realCols, emptyBoard(15))

        assertTrue("求解器应该能把整盘推出来", res.fullyDetermined)
        assertTrue("但玩家没填过任何格子，不能算填完", !res.solved)
        assertTrue("应该给出大量提示，而不是说没得走", res.hints.size > 100)
    }

    @Test
    fun `填错一格时能定位到那一格`() {
        // 拿正确解，故意把一个实心格改成空 —— 模拟玩家画错了
        val board = boardFrom(realSolution)
        val badRow = 0
        val badCol = 1                     // 解里 (1,2) 是实心
        assertEquals(F, board[badRow][badCol])
        board[badRow][badCol] = E

        val res = NonogramSolver.solve(realRows, realCols, board)

        assertNotNull("应该报矛盾", res.contradiction)
        assertTrue("应该定位到可疑格子", res.conflictCells.isNotEmpty())
        assertTrue(
            "改错的那格 (${badRow + 1},${badCol + 1}) 应该在可疑列表里，实际 ${res.conflictCells.map { it.first + 1 to it.second + 1 }}",
            res.conflictCells.contains(badRow to badCol)
        )
    }

    @Test
    fun `解完全正确时不该误报错格`() {
        val res = NonogramSolver.solve(realRows, realCols, boardFrom(realSolution))
        assertNull("正确解不该有矛盾", res.contradiction)
        assertTrue("也不该报可疑格子", res.conflictCells.isEmpty())
        assertTrue("玩家填满了", res.solved)
    }

    @Test
    fun `玩家真填满了才报告填完`() {
        val full = boardFrom(realSolution)
        val res = NonogramSolver.solve(realRows, realCols, full)
        assertTrue("玩家填满了", res.solved)
        assertTrue("也给不出新提示了", res.hints.isEmpty())
    }

    // ── 歧义检测 ────────────────────────────────────────────────

    @Test
    fun `有歧义的题不该硬给答案`() {
        // 3x3，每行每列都是 [1]。这是个经典的多解题：(0,0) 和 (0,2) 都能放
        val r = rows(intArrayOf(1), intArrayOf(1), intArrayOf(1))
        val c = rows(intArrayOf(1), intArrayOf(1), intArrayOf(1))
        val res = NonogramSolver.solve(r, c, emptyBoard(3))
        assertNull(res.contradiction)
        if (!res.solved) {
            assertTrue("这题多个解，应该报告有歧义", res.ambiguous)
        }
    }
}
