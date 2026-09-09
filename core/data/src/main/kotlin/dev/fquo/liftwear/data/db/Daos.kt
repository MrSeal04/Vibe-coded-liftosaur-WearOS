package dev.fquo.liftwear.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutCacheDao {

    @Query("SELECT * FROM workout_cache WHERE id = ${WorkoutCacheEntity.SINGLE_ROW}")
    fun observe(): Flow<WorkoutCacheEntity?>

    @Query("SELECT * FROM workout_cache WHERE id = ${WorkoutCacheEntity.SINGLE_ROW}")
    suspend fun get(): WorkoutCacheEntity?

    @Query("SELECT * FROM workout_cache WHERE id = ${WorkoutCacheEntity.PREVIEW_ROW}")
    fun observePreview(): Flow<WorkoutCacheEntity?>

    @Query("SELECT * FROM workout_cache WHERE id = ${WorkoutCacheEntity.PREVIEW_ROW}")
    suspend fun getPreview(): WorkoutCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: WorkoutCacheEntity)

    /** The live row only. Throwing away the preview as well would blank the Tile. */
    @Query("DELETE FROM workout_cache WHERE id = ${WorkoutCacheEntity.SINGLE_ROW}")
    suspend fun clear()

    @Query("DELETE FROM workout_cache")
    suspend fun clearAll()
}

@Dao
interface OutboxDao {

    /**
     * Insertion order is the send order. Rows are never reordered, because logging set 3
     * before set 2 would let a progression script compute from the wrong state.
     */
    @Query("SELECT * FROM outbox WHERE status = '${OutboxEntity.STATUS_PENDING}' ORDER BY id ASC")
    suspend fun pending(): List<OutboxEntity>

    @Query("SELECT * FROM outbox ORDER BY id ASC")
    fun observeAll(): Flow<List<OutboxEntity>>

    @Query("SELECT * FROM outbox ORDER BY id ASC")
    suspend fun observeAllOnce(): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM outbox WHERE status = '${OutboxEntity.STATUS_PENDING}'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox WHERE status = '${OutboxEntity.STATUS_PARKED}'")
    fun observeParkedCount(): Flow<Int>

    @Insert
    suspend fun insert(entity: OutboxEntity): Long

    @Query("DELETE FROM outbox WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("UPDATE outbox SET attempts = attempts + 1, lastError = :error WHERE id IN (:ids)")
    suspend fun recordFailure(ids: List<Long>, error: String?)

    @Query(
        "UPDATE outbox SET status = '${OutboxEntity.STATUS_PARKED}', lastError = :error " +
            "WHERE id IN (:ids)"
    )
    suspend fun park(ids: List<Long>, error: String?)

    /** Unparking is the user saying "try again"; it does not change the payloads. */
    @Query("UPDATE outbox SET status = '${OutboxEntity.STATUS_PENDING}', attempts = 0 WHERE status = '${OutboxEntity.STATUS_PARKED}'")
    suspend fun unparkAll()

    @Query("DELETE FROM outbox")
    suspend fun clear()

    @Query("DELETE FROM outbox WHERE workoutStartTime = :startTime")
    suspend fun clearWorkout(startTime: Long)
}

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history_cache ORDER BY id DESC LIMIT :limit")
    fun observe(limit: Int = 50): Flow<List<HistoryRecordEntity>>

    @Query("SELECT * FROM history_cache WHERE id = :id")
    suspend fun get(id: Long): HistoryRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAll(records: List<HistoryRecordEntity>)

    @Query("DELETE FROM history_cache")
    suspend fun clear()
}

@Dao
interface FinishResultDao {

    @Query("SELECT * FROM finish_result WHERE id = ${FinishResultEntity.SINGLE_ROW}")
    fun observe(): Flow<FinishResultEntity?>

    @Query("SELECT * FROM finish_result WHERE id = ${FinishResultEntity.SINGLE_ROW}")
    suspend fun observeOnce(): FinishResultEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: FinishResultEntity)

    @Query("DELETE FROM finish_result")
    suspend fun clear()
}
