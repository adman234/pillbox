package com.adman234.pillbox.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MedDao {
    @Query("SELECT * FROM medication ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Medication>>

    @Query("SELECT * FROM medication")
    suspend fun getAll(): List<Medication>

    @Query("SELECT * FROM medication WHERE id = :id")
    suspend fun get(id: Long): Medication?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(m: Medication): Long

    @Update
    suspend fun update(m: Medication)

    @Delete
    suspend fun delete(m: Medication)

    @Query("DELETE FROM medication")
    suspend fun clear()
}

@Dao
interface DoseDao {
    @Query("SELECT * FROM dose_event WHERE scheduledAt >= :from AND scheduledAt < :to ORDER BY scheduledAt")
    fun observeRange(from: Long, to: Long): Flow<List<DoseEvent>>

    @Query("SELECT * FROM dose_event WHERE status != 'PENDING' ORDER BY COALESCE(actedAt, scheduledAt) DESC")
    fun observeHistory(): Flow<List<DoseEvent>>

    @Query("SELECT * FROM dose_event")
    suspend fun all(): List<DoseEvent>

    @Query("SELECT * FROM dose_event WHERE status = 'PENDING'")
    suspend fun pending(): List<DoseEvent>

    @Query("SELECT * FROM dose_event WHERE id = :id")
    suspend fun get(id: Long): DoseEvent?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(e: DoseEvent): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReplace(e: DoseEvent): Long

    @Update
    suspend fun update(e: DoseEvent)

    @Query("DELETE FROM dose_event WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT id FROM dose_event WHERE medId = :medId AND status = 'PENDING' AND extra = 0 AND scheduledAt >= :from")
    suspend fun futurePendingIds(medId: Long, from: Long): List<Long>

    @Query("SELECT id FROM dose_event WHERE status = 'PENDING' AND extra = 0 AND scheduledAt >= :from")
    suspend fun allFuturePendingIds(from: Long): List<Long>

    @Query("SELECT id FROM dose_event WHERE medId = :medId AND status = 'PENDING'")
    suspend fun pendingIdsForMed(medId: Long): List<Long>

    @Query("DELETE FROM dose_event WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)

    @Query("SELECT medId, MAX(actedAt) AS at FROM dose_event WHERE status = 'TAKEN' GROUP BY medId")
    fun observeLastTaken(): Flow<List<LastTaken>>

    @Query("SELECT MAX(actedAt) FROM dose_event WHERE medId = :medId AND status = 'TAKEN'")
    suspend fun lastTaken(medId: Long): Long?

    @Query("DELETE FROM dose_event")
    suspend fun clear()
}

@Database(entities = [Medication::class, DoseEvent::class], version = 1, exportSchema = false)
abstract class PillboxDb : RoomDatabase() {
    abstract fun meds(): MedDao
    abstract fun doses(): DoseDao

    companion object {
        fun create(ctx: Context): PillboxDb =
            Room.databaseBuilder(ctx, PillboxDb::class.java, "pillbox.db").build()
    }
}
