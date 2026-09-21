package com.wangye.tftbox.data

import kotlinx.coroutines.flow.Flow

/**
 * 薄薄一层，App 和悬浮服务都从这里取数据，保证走的是同一个数据库实例。
 */
class LineupRepository(private val dao: LineupDao) {

    fun observeAll(): Flow<List<Lineup>> = dao.observeAll()

    suspend fun getAll(): List<Lineup> = dao.getAll()

    suspend fun findById(id: Long): Lineup? = dao.findById(id)

    suspend fun findByCode(code: String): Lineup? = dao.findByCode(code)

    suspend fun insert(lineup: Lineup): Long = dao.insert(lineup)

    suspend fun update(lineup: Lineup) = dao.update(lineup)

    suspend fun delete(lineup: Lineup) = dao.delete(lineup)

    suspend fun markUsed(id: Long, now: Long = System.currentTimeMillis()) =
        dao.markUsed(id, now)

    /**
     * 导入：同一条阵容码已经存过就当更新（补名称和标签），没存过才新增。
     * 避免从网上反复复制同一套阵容时列表里堆一串重复项。
     */
    suspend fun upsertByCode(lineup: Lineup): Long {
        val existing = dao.findByCode(lineup.code)
        return if (existing == null) {
            dao.insert(lineup)
        } else {
            dao.update(
                existing.copy(
                    title = lineup.title,
                    tags = lineup.tags,
                    season = lineup.season,
                    note = lineup.note,
                )
            )
            existing.id
        }
    }
}
