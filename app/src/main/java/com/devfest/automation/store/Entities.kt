package com.devfest.automation.store

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class FlowStatus { DRAFT, ENABLED, OFF }

@Entity(tableName = "flows")
data class FlowEntity(
    @PrimaryKey val id: String,
    val title: String,
    /** `Automation` JSON, already validated. Null while the chat has not produced a flow yet. */
    @ColumnInfo(name = "automation_json") val automationJson: String?,
    val status: FlowStatus,
    @ColumnInfo(name = "trigger_type") val triggerType: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [ForeignKey(FlowEntity::class, ["id"], ["flow_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("flow_id")]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "flow_id") val flowId: String,
    /** "user" or "assistant". */
    val role: String,
    val content: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

enum class RunState { RUNNING, COMPLETED, STOPPED, FAILED, CANCELLED }

@Entity(
    tableName = "runs",
    foreignKeys = [ForeignKey(FlowEntity::class, ["id"], ["flow_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("flow_id"), Index("started_at")]
)
data class RunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "flow_id") val flowId: String,
    @ColumnInfo(name = "flow_title") val flowTitle: String,
    /** What started it, for example "manual" or "sms from +91…". */
    val source: String,
    val state: RunState,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "finished_at") val finishedAt: Long? = null,
    val summary: String = ""
)

@Entity(
    tableName = "run_steps",
    foreignKeys = [ForeignKey(RunEntity::class, ["id"], ["run_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("run_id")]
)
data class RunStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "run_id") val runId: Long,
    @ColumnInfo(name = "step_id") val stepId: String,
    val type: String,
    /** `StepStatus` name. */
    val status: String,
    val message: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long
)

@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey val name: String,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "radius_m") val radiusM: Int = 200
)
