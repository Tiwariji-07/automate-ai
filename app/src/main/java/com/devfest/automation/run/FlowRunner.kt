package com.devfest.automation.run

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.devfest.automation.AutomateApp
import com.devfest.automation.store.FlowStore
import com.devfest.automation.store.RunState
import com.devfest.core.engine.FlowEngine
import com.devfest.core.engine.RunStatus
import com.devfest.core.engine.StepStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Starts and cancels runs. Each run is a WorkManager job, so it survives the UI closing and is
 * allowed to start from a background trigger. One run per flow at a time: a trigger that fires
 * while the flow is still running is ignored.
 */
class FlowRunner(private val context: Context) {

    /** A [test] run swaps the steps that send data off the phone for ones that only describe it. */
    fun run(flowId: String, source: String, triggerOutputs: Map<String, String> = emptyMap(), test: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<RunWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(
                workDataOf(
                    KEY_FLOW to flowId,
                    KEY_SOURCE to source,
                    KEY_TEST to test,
                    KEY_OUTPUTS to json.encodeToString(outputsSerializer, triggerOutputs.mapValues { it.value.take(MAX_OUTPUT) })
                )
            )
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(flowId), ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(flowId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(flowId))
    }

    companion object {
        private const val TAG = "flow-run"
        private const val KEY_FLOW = "flow"
        private const val KEY_SOURCE = "source"
        private const val KEY_OUTPUTS = "outputs"
        private const val KEY_TEST = "test"
        /** WorkManager input is capped at 10 KB. */
        private const val MAX_OUTPUT = 2000
        private val json = Json
        private val outputsSerializer = MapSerializer(String.serializer(), String.serializer())

        private fun workName(flowId: String) = "run-$flowId"

        internal fun isTest(data: Data): Boolean = data.getBoolean(KEY_TEST, false)

        internal fun read(data: Data): Triple<String, String, Map<String, String>> = Triple(
            data.getString(KEY_FLOW).orEmpty(),
            data.getString(KEY_SOURCE).orEmpty(),
            data.getString(KEY_OUTPUTS)?.let { json.decodeFromString(outputsSerializer, it) }.orEmpty()
        )
    }
}

class RunWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as AutomateApp
        val (flowId, source, outputs) = FlowRunner.read(inputData)
        val engine = if (FlowRunner.isTest(inputData)) app.testEngine else app.engine
        execute(app.store, engine, flowId, source, outputs)
        // A failed flow is a recorded outcome, not a job to retry: retrying could send an SMS twice.
        return Result.success()
    }

    companion object {
        suspend fun execute(store: FlowStore, engine: FlowEngine, flowId: String, source: String, outputs: Map<String, String>) {
            val flow = store.get(flowId) ?: return
            val automation = flow.automation ?: return
            val runId = store.startRun(flow, source)
            try {
                val result = engine.run(automation, outputs) { store.addStep(runId, it) }
                val state = when (result.status) {
                    RunStatus.COMPLETED -> RunState.COMPLETED
                    RunStatus.STOPPED -> RunState.STOPPED
                    RunStatus.FAILED -> RunState.FAILED
                }
                val last = result.steps.last()
                val summary = when (result.status) {
                    RunStatus.COMPLETED -> "${result.steps.count { it.status == StepStatus.DONE } - 1} actions done"
                    RunStatus.STOPPED -> "Stopped: ${last.message.ifEmpty { "condition not met" }}"
                    RunStatus.FAILED -> last.message
                }
                store.finishRun(runId, state, summary)
            } catch (e: CancellationException) {
                withContext(NonCancellable) { store.finishRun(runId, RunState.CANCELLED, "Cancelled") }
                throw e
            }
        }
    }
}
