package dev.fquo.liftwear.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WorkoutCacheEntity::class,
        OutboxEntity::class,
        HistoryRecordEntity::class,
        FinishResultEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class LiftWearDatabase : RoomDatabase() {

    abstract fun workoutCacheDao(): WorkoutCacheDao
    abstract fun outboxDao(): OutboxDao
    abstract fun historyDao(): HistoryDao
    abstract fun finishResultDao(): FinishResultDao

    companion object {
        fun create(context: Context): LiftWearDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                LiftWearDatabase::class.java,
                "liftwear.db",
            )
                // The cache and history are re-fetchable, but the outbox is not: it can hold
                // the only copy of sets logged in a basement. Destructive migration would
                // throw those away silently, so a schema change must come with a migration.
                .build()
    }
}
