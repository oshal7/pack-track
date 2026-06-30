package com.packtrack.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface GpsCacheDao {
    @Insert
    suspend fun insert(entry: GpsCacheEntry)

    @Query("SELECT * FROM gps_cache WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsynced(): List<GpsCacheEntry>

    @Query("UPDATE gps_cache SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Int>)

    @Query("DELETE FROM gps_cache WHERE synced = 1 AND timestamp < :cutoff")
    suspend fun pruneOld(cutoff: Long)
}
