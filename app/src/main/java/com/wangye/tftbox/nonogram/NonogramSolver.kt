package com.wangye.tftbox.nonogram

/**
 * Nonogram（数织 / 像素拼图，游戏里叫「数独」）求解器。
 *
 * 规则：每行/每列给出一串数字，表示该行/列**连续实心块**的长度，按顺序排列，
 * 块与块之间至少隔一个空格。比如 `[2,3,3]` 表示「2个实心、至少1空、3个实心、至少1空、3个实心」。
 *
 * 求两条路，按代价从低到高：
 *
 * 1. **约束传播** —— 对每行每列枚举所有可行摆法，取交集。反复迭代到不动点。
 *    这就是「人能做出来的推导」，推出来的格子一定是逻辑必然的。
 *
 * 2. **可满足性判定** —— 传播推不动时，对每个未知格分别假设「实心」和「空」，
 *    各自传播一次看会不会矛盾。只有一个假设能活 → 那格是确定的。
 *    这等价于「所有解里这格都一样」，所以**结论 100% 安全，不是猜**。
 *
 * 两个假设都能活 → 题目在这格上有歧义，会如实报告「得猜」，不糊弄。
 */
object NonogramSolver {

    const val UNKNOWN = -1
    const val EMPTY = 0
    const val FILLED = 1

    /** 单行最多枚举多少种摆法，防止大棋盘把内存吃爆 */
    private const val MAX_PLACEMENTS = 200_000

    /** 排查错格时最多试多少次。每次都要整盘传播一遍，太多会卡 */
    private const val MAX_CONFLICT_TRIES = 400

    data class Hint(
        val row: Int,
        val col: Int,
        val filled: Boolean,
        /** true = 传播推不出来，靠可满足性判定得到（结论一样可靠，只是人脑难推） */
        val bySearch: Boolean,
    )

    data class Result(
        /** 传播后的棋盘 */
        val board: Array<IntArray>,
        val hints: List<Hint>,
        /** 非 null 表示读到的棋盘和线索矛盾（识别错了，或者玩家填错了） */
        val contradiction: String? = null,
        /**
         * 矛盾时，能定位到的**可疑格子**。
         *
         * 做法是逐个把玩家填过的格子「撤销」再试：撤掉哪一格之后整盘就通了，
         * 哪一格就可疑。比只说一句「对不上」有用得多 ——
         * 用户几十格填下来，根本不知道该回头查哪里。
         */
        val conflictCells: List<Pair<Int, Int>> = emptyList(),
        /** 玩家已经把每一格都填了 —— 注意这是**玩家的进度**，不是求解器的能力 */
        val solved: Boolean = false,
        /**
         * 求解器把每一格都推出来了。
         *
         * 跟 [solved] 是两回事：空棋盘上纯推导往往就能确定全部格子，
         * 那时 [fullyDetermined] 为 true 但 [solved] 为 false ——
         * 玩家一个都还没填。早期版本把这两个搞混了，导致一进游戏就报「已经全填完了」。
         */
        val fullyDetermined: Boolean = false,
        /** 去掉这些提示格之后，剩下的仍有多个解 —— 有些格子得猜 */
        val ambiguous: Boolean = false,
    )

    // ── 一条线的枚举 ────────────────────────────────────────────

    /** 枚举满足线索、且不与 [known] 冲突的所有摆法。null = 无解 */
    private fun placements(clue: IntArray, n: Int, known: IntArray): List<IntArray>? {
        if (clue.isEmpty()) return null
        val out = ArrayList<IntArray>()
        val cur = IntArray(n)

        // needAfter[ci]：第 ci 块之后还需要多少格（含块与块之间的空隙）
        val needAfter = IntArray(clue.size)
        for (i in clue.size - 2 downTo 0) {
            needAfter[i] = 1 + clue[i + 1] + needAfter[i + 1]
        }

        fun rec(ci: Int, pos: Int) {
            if (out.size > MAX_PLACEMENTS) return
            if (ci == clue.size) {
                for (j in pos until n) if (known[j] == FILLED) return
                for (j in pos until n) cur[j] = EMPTY
                out.add(cur.copyOf())
                return
            }
            val block = clue[ci]
            val maxStart = n - block - needAfter[ci]
            var s = pos
            while (s <= maxStart) {
                // pos..s-1 必须为空；撞到已知实心就该停了
                var ok = true
                for (j in pos until s) if (known[j] == FILLED) { ok = false; break }
                if (!ok) break

                for (j in s until s + block) {
                    if (known[j] == EMPTY) { ok = false; break }
                }
                if (ok && ci < clue.size - 1 && known[s + block] == FILLED) ok = false

                if (ok) {
                    for (j in pos until s) cur[j] = EMPTY
                    for (j in s until s + block) cur[j] = FILLED
                    if (ci < clue.size - 1) {
                        cur[s + block] = EMPTY
                        rec(ci + 1, s + block + 1)
                    } else {
                        rec(ci + 1, s + block)
                    }
                }
                s++
            }
        }

        rec(0, 0)
        if (out.size > MAX_PLACEMENTS) return null
        return out.ifEmpty { null }
    }

    /** 对一条线做一次传播，结果写进 [out]。返回 false = 无解 */
    private fun propagateLine(line: IntArray, clue: IntArray, out: IntArray): Boolean {
        val opts = placements(clue, line.size, line) ?: return false
        for (i in line.indices) {
            if (line[i] != UNKNOWN) { out[i] = line[i]; continue }
            var allFilled = true
            var allEmpty = true
            for (o in opts) {
                if (o[i] == FILLED) allEmpty = false else allFilled = false
                if (!allFilled && !allEmpty) break
            }
            out[i] = when {
                allFilled -> FILLED
                allEmpty -> EMPTY
                else -> UNKNOWN
            }
        }
        return true
    }

    /** 反复传播到不动点。false = 矛盾无解 */
    private fun propagate(board: Array<IntArray>, rows: List<IntArray>, cols: List<IntArray>): Boolean {
        val n = board.size
        if (n == 0) return true
        val m = board[0].size
        val buf = IntArray(maxOf(n, m))
        var changed = true
        while (changed) {
            changed = false
            for (r in 0 until n) {
                if (!propagateLine(board[r], rows[r], buf)) return false
                for (c in 0 until m) {
                    if (board[r][c] == UNKNOWN && buf[c] != UNKNOWN) {
                        board[r][c] = buf[c]; changed = true
                    }
                }
            }
            for (c in 0 until m) {
                val line = IntArray(n) { board[it][c] }
                if (!propagateLine(line, cols[c], buf)) return false
                for (r in 0 until n) {
                    if (board[r][c] == UNKNOWN && buf[r] != UNKNOWN) {
                        board[r][c] = buf[r]; changed = true
                    }
                }
            }
        }
        return true
    }

    private fun copyBoard(b: Array<IntArray>) = Array(b.size) { b[it].copyOf() }

    // ── 主入口 ──────────────────────────────────────────────────

    fun solve(rows: List<IntArray>, cols: List<IntArray>, initial: Array<IntArray>): Result {
        val n = rows.size
        val m = if (n > 0) cols.size else 0
        if (n == 0 || m == 0) return Result(initial, emptyList(), "线索为空")

        val work = copyBoard(initial)
        if (!propagate(work, rows, cols)) {
            val suspects = findConflictCells(rows, cols, initial)
            return Result(
                work, emptyList(),
                if (suspects.isEmpty()) {
                    "读到的棋盘和线索对不上 —— 可能识别错了"
                } else {
                    "有格子填错了，一共 ${suspects.size} 处可疑（已在画面上标红）"
                },
                conflictCells = suspects,
            )
        }

        // 两个概念要分清：
        //   playerDone      —— 玩家自己把棋盘填满了（进度）
        //   fullyDetermined —— 求解器把每一格都推出来了（能力）
        // 空棋盘上纯推导常常就能确定全部格子，这时候 playerDone 是 false。
        val playerDone = initial.all { row -> row.all { it != UNKNOWN } }
        val fullyDetermined = work.all { row -> row.all { it != UNKNOWN } }

        if (playerDone) return Result(work, emptyList(), solved = true, fullyDetermined = true)

        // 第一档：传播新确定的格子（人脑能推出来的那部分）
        val propagationHints = ArrayList<Hint>()
        for (r in 0 until n) for (c in 0 until m) {
            if (initial[r][c] == UNKNOWN && work[r][c] != UNKNOWN) {
                propagationHints.add(Hint(r, c, work[r][c] == FILLED, bySearch = false))
            }
        }
        if (propagationHints.isNotEmpty()) {
            return Result(work, propagationHints, fullyDetermined = fullyDetermined)
        }

        val unknown = ArrayList<Pair<Int, Int>>()
        for (r in 0 until n) for (c in 0 until m) if (work[r][c] == UNKNOWN) unknown.add(r to c)
        if (unknown.isEmpty()) return Result(work, emptyList(), fullyDetermined = true)

        // 第二档：传播推不动，用可满足性判定找「怎么走都得一样」的格子
        val hints = ArrayList<Hint>()
        var ambiguous = false
        for ((r, c) in unknown) {
            val canFill = satisfies(work, rows, cols, r, c, FILLED)
            val canEmpty = satisfies(work, rows, cols, r, c, EMPTY)
            when {
                canFill && canEmpty -> ambiguous = true
                canFill -> hints.add(Hint(r, c, true, bySearch = true))
                canEmpty -> hints.add(Hint(r, c, false, bySearch = true))
            }
        }
        return Result(work, hints, null, fullyDetermined = fullyDetermined, ambiguous = ambiguous)
    }

    /**
     * 找出可能是「填错的那一格」。
     *
     * 逐个撤销玩家填过的格子，看撤销之后整盘能不能通：
     * 撤销某一格就通了 → 那一格（或与它冲突的那几格）就是问题所在。
     *
     * 注意这只能找到**单独撤销就起作用**的格子。如果错在好几格的组合上，
     * 可能一个都找不出来 —— 那种情况会返回空，界面就老实说「定位不到」。
     */
    private fun findConflictCells(
        rows: List<IntArray>,
        cols: List<IntArray>,
        initial: Array<IntArray>,
    ): List<Pair<Int, Int>> {
        val played = ArrayList<Pair<Int, Int>>()
        for (r in initial.indices) for (c in initial[r].indices) {
            if (initial[r][c] != UNKNOWN) played.add(r to c)
        }
        if (played.isEmpty()) return emptyList()   // 一盘空格子还对不上，那是识别问题
        if (played.size > MAX_CONFLICT_TRIES) return emptyList()

        val suspects = ArrayList<Pair<Int, Int>>()
        for ((r, c) in played) {
            val test = copyBoard(initial)
            test[r][c] = UNKNOWN
            if (propagate(test, rows, cols)) suspects.add(r to c)
        }
        return suspects
    }

    /** 在 [base] 基础上假设某格为 [value]，传播后是否不矛盾 */
    private fun satisfies(
        base: Array<IntArray>,
        rows: List<IntArray>,
        cols: List<IntArray>,
        r: Int,
        c: Int,
        value: Int,
    ): Boolean {
        val test = copyBoard(base)
        test[r][c] = value
        return propagate(test, rows, cols)
    }
}
