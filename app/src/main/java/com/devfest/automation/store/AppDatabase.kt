package com.devfest.automation.store

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FlowDao {
    @Query("SELECT * FROM flows ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<FlowEntity>>

    @Query("SELECT * FROM flows WHERE id = :id")
    fun observe(id: String): Flow<FlowEntity?>

    @Query("SELECT * FROM flows WHERE id = :id")
    suspend fun get(id: String): FlowEntity?

    @Query("SELECT * FROM flows WHERE status = 'ENABLED'")
    suspend fun enabled(): List<FlowEntity>

    @Query("SELECT * FROM flows WHERE status = 'ENABLED' AND trigger_type = :triggerType")
    suspend fun enabledWithTrigger(triggerType: String): List<FlowEntity>

    @Upsert
    suspend fun upsert(flow: FlowEntity)

    @Query("UPDATE flows SET status = :status, updated_at = :now WHERE id = :id")
    suspend fun setStatus(id: String, status: FlowStatus, now: Long)

    @Query("DELETE FROM flows WHERE id = :id")
    suspend fun delete(id: String)

    @Insert
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages WHERE flow_id = :flowId ORDER BY id")
    fun observeMessages(flowId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE flow_id = :flowId ORDER BY id")
    suspend fun messages(flowId: String): List<ChatMessageEntity>
}

@Dao
interface RunDao {
    @Insert
    suspend fun insert(run: RunEntity): Long

    @Query("UPDATE runs SET state = :state, finished_at = :finishedAt, summary = :summary WHERE id = :id")
    suspend fun finish(id: Long, state: RunState, finishedAt: Long, summary: String)

    /** Runs left RUNNING by a killed process. */
    @Query("UPDATE runs SET state = 'CANCELLED', finished_at = :now, summary = 'Interrupted' WHERE state = 'RUNNING'")
    suspend fun cancelStale(now: Long)

    @Insert
    suspend fun insertStep(step: RunStepEntity)

    @Query("SELECT * FROM runs ORDER BY started_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RunEntity>>

    @Query("SELECT * FROM runs WHERE flow_id = :flowId ORDER BY started_at DESC LIMIT :limit")
    fun observeForFlow(flowId: String, limit: Int): Flow<List<RunEntity>>

    /** The newest run of each flow, however many runs other flows had since. */
    @Query("SELECT * FROM runs WHERE id IN (SELECT MAX(id) FROM runs GROUP BY flow_id)")
    fun observeLatestPerFlow(): Flow<List<RunEntity>>

    @Query("SELECT * FROM run_steps WHERE run_id = :runId ORDER BY id")
    fun observeSteps(runId: Long): Flow<List<RunStepEntity>>

    /** Keeps history bounded: drops runs older than the newest [keep]. */
    @Query("DELETE FROM runs WHERE id NOT IN (SELECT id FROM runs ORDER BY started_at DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places ORDER BY name")
    fun observeAll(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places")
    suspend fun all(): List<PlaceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(place: PlaceEntity)

    @Query("DELETE FROM places WHERE name = :name")
    suspend fun delete(name: String)
}

@Database(
    entities = [FlowEntity::class, ChatMessageEntity::class, RunEntity::class, RunStepEntity::class, PlaceEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun flows(): FlowDao
    abstract fun runs(): RunDao
    abstract fun places(): PlaceDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "automate.db").build()
    }
}
