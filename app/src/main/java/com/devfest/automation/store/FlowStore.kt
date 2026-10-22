package com.devfest.automation.store

import com.devfest.core.agent.ChatMessage
import com.devfest.core.agent.FlowAgent
import com.devfest.core.engine.StepRecord
import com.devfest.core.model.Automation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

data class SavedFlow(
    val id: String,
    val title: String,
    val automation: Automation?,
    val status: FlowStatus,
    val updatedAt: Long
)

/** The only way the app reads and writes flows, chats, runs and places. */
class FlowStore(private val db: AppDatabase, private val now: () -> Long = System::currentTimeMillis) {

    private val json = FlowAgent.DefaultJson

    fun observeFlows(): Flow<List<SavedFlow>> = db.flows().observeAll().map { list -> list.map(::toSaved) }

    fun observeFlow(id: String): Flow<SavedFlow?> = db.flows().observe(id).map { it?.let(::toSaved) }

    suspend fun get(id: String): SavedFlow? = db.flows().get(id)?.let(::toSaved)

    suspend fun enabledWithTrigger(triggerType: String): List<SavedFlow> =
        db.flows().enabledWithTrigger(triggerType).map(::toSaved)

    suspend fun enabled(): List<SavedFlow> = db.flows().enabled().map(::toSaved)

    suspend fun createDraft(): String {
        val id = UUID.randomUUID().toString()
        val time = now()
        db.flows().upsert(FlowEntity(id, "New flow", null, FlowStatus.DRAFT, null, time, time))
        return id
    }

    /**
     * Saves a validated automation. Keeps the current status unless [status] is given, except that
     * an edited enabled flow goes back to draft: the change must pass review before it runs.
     */
    suspend fun save(id: String, automation: Automation, status: FlowStatus? = null) {
        val existing = db.flows().get(id)
        val time = now()
        db.flows().upsert(
            FlowEntity(
                id = id,
                title = automation.title,
                automationJson = json.encodeToString(Automation.serializer(), automation),
                status = status ?: existing?.status?.takeIf { it != FlowStatus.ENABLED } ?: FlowStatus.DRAFT,
                triggerType = automation.trigger.type,
                createdAt = existing?.createdAt ?: time,
                updatedAt = time
            )
        )
    }

    suspend fun setStatus(id: String, status: FlowStatus) = db.flows().setStatus(id, status, now())

    suspend fun delete(id: String) = db.flows().delete(id)

    fun observeChat(flowId: String): Flow<List<ChatMessage>> =
        db.flows().observeMessages(flowId).map { list -> list.map(::toMessage) }

    suspend fun chat(flowId: String): List<ChatMessage> = db.flows().messages(flowId).map(::toMessage)

    suspend fun addChat(flowId: String, message: ChatMessage) {
        db.flows().insertMessage(ChatMessageEntity(flowId = flowId, role = message.role.name.lowercase(), content = message.content, createdAt = now()))
    }

    suspend fun startRun(flow: SavedFlow, source: String): Long =
        db.runs().insert(RunEntity(flowId = flow.id, flowTitle = flow.title, source = source, state = RunState.RUNNING, startedAt = now()))

    suspend fun addStep(runId: Long, record: StepRecord) {
        db.runs().insertStep(RunStepEntity(runId = runId, stepId = record.stepId, type = record.type, status = record.status.name, message = record.message, durationMs = record.durationMs))
    }

    suspend fun finishRun(runId: Long, state: RunState, summary: String) {
        db.runs().finish(runId, state, now(), summary)
        db.runs().trim(keep = MAX_RUNS)
    }

    suspend fun cancelStaleRuns() = db.runs().cancelStale(now())

    fun observeRecentRuns(limit: Int = 50) = db.runs().observeRecent(limit)

    fun observeLatestRuns() = db.runs().observeLatestPerFlow()

    fun observeRuns(flowId: String, limit: Int = 20) = db.runs().observeForFlow(flowId, limit)

    fun observeRunSteps(runId: Long) = db.runs().observeSteps(runId)

    fun observePlaces() = db.places().observeAll()

    suspend fun places(): List<PlaceEntity> = db.places().all()

    suspend fun savePlace(place: PlaceEntity) = db.places().upsert(place)

    suspend fun deletePlace(name: String) = db.places().delete(name)

    private fun toSaved(entity: FlowEntity) = SavedFlow(
        id = entity.id,
        title = entity.title,
        automation = entity.automationJson?.let { runCatching { json.decodeFromString(Automation.serializer(), it) }.getOrNull() },
        status = entity.status,
        updatedAt = entity.updatedAt
    )

    private fun toMessage(entity: ChatMessageEntity) = ChatMessage(
        if (entity.role == "user") ChatMessage.Role.USER else ChatMessage.Role.ASSISTANT,
        entity.content
    )

    private companion object {
        const val MAX_RUNS = 500
    }
}
