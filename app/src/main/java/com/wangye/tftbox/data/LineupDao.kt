package com.wangye.tftbox.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LineupDao {

    /** 收藏置顶，然后按最近使用、最后按创建时间倒序 */
    @Query(
        """
        SELECT * FROM lineups
        ORDER BY favorite DESC, lastUsedAt DESC, createdAt DESC
        """
    )
    fun observeAll(): Flow<List<Lineup>>

    /** 悬浮面板是一次性读取，不需要 Flow */
    @Query(
        """
        SELECT * FROM lineups
        ORDER BY favorite DESC, lastUsedAt DESC, createdAt DESC
        """
    )
    suspend fun getAll(): List<Lineup>

    @Query("SELECT * FROM lineups WHERE id = :id")
    suspend fun findById(id: Long): Lineup?

    @Query("SELECT * FROM lineups WHERE code = :code LIMIT 1")
    suspend fun findByCode(code: String): Lineup?

    @Insert
    suspend fun insert(lineup: Lineup): Long

    @Update
    suspend fun update(lineup: Lineup)

    @Delete
    suspend fun delete(lineup: Lineup)

    @Query("DELETE FROM lineups")
    suspend fun deleteAll()

    /** 复制走一次，记一笔使用 */
    @Query("UPDATE lineups SET lastUsedAt = :now, useCount = useCount + 1 WHERE id = :id")
    suspend fun markUsed(id: Long, now: Long)
}
