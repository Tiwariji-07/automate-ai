package com.devfest.core.engine

import com.devfest.core.catalog.BlockSpec
import com.devfest.core.catalog.Blocks
import com.devfest.core.catalog.Values
import com.devfest.core.catalog.Category
import com.devfest.core.catalog.ParamType
import com.devfest.core.model.Automation
import com.devfest.core.model.Step
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** What one step reports back. */
sealed interface StepOutcome {
    /** An action finished. [outputs] become template variables for later steps. */
    data class Done(val message: String = "", val outputs: Map<String, String> = emptyMap()) : StepOutcome

    /** A condition was evaluated. */
    data class Check(val passed: Boolean, val message: String = "") : StepOutcome

    /** The step could not do its job. The run stops here. */
    data class Failed(val message: String) : StepOutcome
}

/** Runs one block type on the phone. [params] are validated and have `{{variables}}` filled in. */
fun interface StepHandler {
    suspend fun run(step: Step, params: Map<String, String>, variables: Map<String, String>): StepOutcome
}

enum class StepStatus { DONE, PASSED, NOT_PASSED, FAILED }

enum class RunStatus {
    COMPLETED,
    /** A top-level condition did not pass, so the remaining steps were skipped. */
    STOPPED,
    FAILED
}

data class StepRecord(
    val stepId: String,
    val type: String,
    val status: StepStatus,
    val message: String,
    val durationMs: Long
)

data class RunResult(val status: RunStatus, val steps: List<StepRecord>)

/**
 * Runs a validated [Automation]. One run is sequential. Cancelling the calling coroutine cancels the run
 * and throws [CancellationException]; steps already reported through `onStep` stay reported.
 */
class FlowEngine(
    private val handlers: Map<String, StepHandler>,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    suspend fun run(
        flow: Automation,
        triggerOutputs: Map<String, String> = emptyMap(),
        onStep: suspend (StepRecord) -> Unit = {}
    ): RunResult {
        val run = Run(onStep)
        run.variables += builtIns()
        run.variables += triggerOutputs
        run.record(flow.trigger, StepStatus.DONE, "Triggered", 0)
        val status = try {
            if (run.list(flow.steps)) RunStatus.COMPLETED else RunStatus.STOPPED
        } catch (_: StopRun) {
            RunStatus.FAILED
        }
        return RunResult(status, run.records)
    }

    private class StopRun : Exception()

    private inner class Run(private val onStep: suspend (StepRecord) -> Unit) {
        val variables = mutableMapOf<String, String>()
        val records = mutableListOf<StepRecord>()

        suspend fun record(step: Step, status: StepStatus, message: String, durationMs: Long) {
            val record = StepRecord(step.id, step.type, status, message, durationMs)
            records += record
            onStep(record)
        }

        /** Returns false when a guard condition stopped this list. */
        suspend fun list(steps: List<Step>): Boolean {
            for (step in steps) {
                if (!step(step)) return false
            }
            return true
        }

        private suspend fun step(step: Step): Boolean {
            val spec = Blocks[step.type]
            val handler = handlers[step.type]
            if (spec == null || handler == null) {
                record(step, StepStatus.FAILED, "\"${step.type}\" is not supported on this phone", 0)
                throw StopRun()
            }
            val params = step.params.mapValues { (name, value) ->
                if (spec.param(name)?.type in TEMPLATE_TYPES) Templates.render(value, variables) else value
            }.toMutableMap()
            renderProblem(spec, step.params, params)?.let {
                record(step, StepStatus.FAILED, it, 0)
                throw StopRun()
            }
            val started = clock.millis()
            val outcome = try {
                withTimeoutOrNull(spec.maxSeconds * 1000L) {
                    handler.run(step, params, variables.toMap())
                } ?: StepOutcome.Failed("Timed out after ${spec.maxSeconds}s")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                StepOutcome.Failed(e.message ?: e::class.simpleName ?: "Error")
            }
            val elapsed = clock.millis() - started

            return when (outcome) {
                is StepOutcome.Done -> {
                    if (spec.category == Category.CONDITION) {
                        record(step, StepStatus.FAILED, "Condition returned an action result", elapsed)
                        throw StopRun()
                    }
                    variables += outcome.outputs
                    record(step, StepStatus.DONE, outcome.message, elapsed)
                    true
                }
                is StepOutcome.Check -> {
                    if (spec.category != Category.CONDITION) {
                        record(step, StepStatus.FAILED, "Action returned a condition result", elapsed)
                        throw StopRun()
                    }
                    record(step, if (outcome.passed) StepStatus.PASSED else StepStatus.NOT_PASSED, outcome.message, elapsed)
                    when {
                        step.hasBranches -> {
                            list(if (outcome.passed) step.then else step.otherwise)
                            true
                        }
                        else -> outcome.passed
                    }
                }
                is StepOutcome.Failed -> {
                    record(step, StepStatus.FAILED, outcome.message, elapsed)
                    throw StopRun()
                }
            }
        }
    }

    /**
     * Values filled in at run time skip the validator, so check them here: an empty `{{ai.reply}}`
     * must not make `text_contains` match everything, and `{{sms.from}}` may be a sender name.
     * Normalizes [rendered] in place.
     */
    private fun renderProblem(spec: BlockSpec, raw: Map<String, String>, rendered: MutableMap<String, String>): String? {
        for ((name, value) in rendered) {
            if (raw[name] == value) continue
            val param = spec.param(name) ?: continue
            val label = "${spec.type}.$name"
            if (value.isBlank()) {
                if (param.required) return "$label is empty after filling in ${raw[name]}"
                continue
            }
            when (param.type) {
                ParamType.PHONE -> rendered[name] = Values.phone(value) ?: return "$label \"$value\" is not a phone number"
                ParamType.URL -> rendered[name] = Values.url(value) ?: return "$label \"$value\" is not a valid URL"
                else -> Unit
            }
        }
        return null
    }

    private fun builtIns(): Map<String, String> {
        val now = LocalDateTime.now(clock)
        return mapOf(
            "now.time" to now.format(DateTimeFormatter.ofPattern("HH:mm")),
            "now.date" to now.toLocalDate().toString(),
            "now.day" to now.dayOfWeek.name.lowercase()
        )
    }

    private companion object {
        val TEMPLATE_TYPES = setOf(ParamType.TEXT, ParamType.URL, ParamType.PHONE)
    }
}
