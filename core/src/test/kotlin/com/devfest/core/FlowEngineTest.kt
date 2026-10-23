package com.devfest.core

import com.devfest.core.engine.FlowEngine
import com.devfest.core.engine.PureHandlers
import com.devfest.core.engine.RunStatus
import com.devfest.core.engine.StepHandler
import com.devfest.core.engine.StepOutcome
import com.devfest.core.engine.StepStatus
import com.devfest.core.model.Automation
import com.devfest.core.model.Step
import com.devfest.core.validation.FlowValidator
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlowEngineTest {

    private val calls = mutableListOf<String>()

    /** Records each action call; conditions pass when their "pass" param is "true". */
    private fun handlers(extra: Map<String, StepHandler> = emptyMap()): Map<String, StepHandler> {
        val action = StepHandler { step, params, _ ->
            calls += "${step.type}:${params.values.joinToString()}"
            StepOutcome.Done("ok")
        }
        val condition = StepHandler { step, _, _ ->
            calls += step.type
            StepOutcome.Check(step.id.endsWith("pass"))
        }
        return mapOf(
            "notify" to action, "vibrate" to action, "send_sms" to action,
            "is_charging" to condition, "wifi_is" to condition
        ) + extra
    }

    private fun flow(vararg steps: Step) = Automation("Test", trigger = Step("trigger", "manual"), steps = steps.toList())

    @Test
    fun `failed guard runs nothing after it`() = runTest {
        val result = FlowEngine(handlers()).run(
            flow(
                Step("guard-fail", "is_charging"),
                Step("sms", "send_sms", mapOf("to" to "+1555", "message" to "hi"))
            )
        )
        assertEquals(RunStatus.STOPPED, result.status)
        assertEquals(listOf("is_charging"), calls)
        assertEquals(StepStatus.NOT_PASSED, result.steps.last().status)
    }

    @Test
    fun `branches pick then or else and the list continues`() = runTest {
        val result = FlowEngine(handlers()).run(
            flow(
                Step(
                    "check-fail", "is_charging",
                    then = listOf(Step("a", "notify", mapOf("m" to "then"))),
                    otherwise = listOf(Step("b", "notify", mapOf("m" to "else")))
                ),
                Step("c", "vibrate", mapOf("p" to "after"))
            )
        )
        assertEquals(RunStatus.COMPLETED, result.status)
        assertEquals(listOf("is_charging", "notify:else", "vibrate:after"), calls)
    }

    @Test
    fun `guard inside a branch ends only that branch`() = runTest {
        val result = FlowEngine(handlers()).run(
            flow(
                Step(
                    "outer-pass", "is_charging",
                    then = listOf(Step("inner-fail", "wifi_is"), Step("skipped", "notify", mapOf("m" to "no")))
                ),
                Step("c", "vibrate", mapOf("p" to "after"))
            )
        )
        assertEquals(RunStatus.COMPLETED, result.status)
        assertEquals(listOf("is_charging", "wifi_is", "vibrate:after"), calls)
    }

    @Test
    fun `failed or throwing step stops the run`() = runTest {
        val boom = StepHandler { _, _, _ -> error("camera busy") }
        val result = FlowEngine(handlers(mapOf("take_photo" to boom))).run(
            flow(Step("p", "take_photo"), Step("n", "notify", mapOf("m" to "never")))
        )
        assertEquals(RunStatus.FAILED, result.status)
        assertEquals("camera busy", result.steps.last().message)
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `missing handler fails instead of skipping`() = runTest {
        val result = FlowEngine(handlers()).run(flow(Step("x", "flashlight")))
        assertEquals(RunStatus.FAILED, result.status)
        assertEquals(StepStatus.FAILED, result.steps.last().status)
    }

    @Test
    fun `slow step times out`() = runTest {
        val slow = StepHandler { _, _, _ -> delay(10 * 60_000); StepOutcome.Done() }
        val result = FlowEngine(handlers(mapOf("speak" to slow))).run(flow(Step("s", "speak")))
        assertEquals(RunStatus.FAILED, result.status)
        assertEquals("Timed out after 60s", result.steps.last().message)
    }

    @Test
    fun `outputs fill templates in later steps`() = runTest {
        val location = StepHandler { _, _, _ -> StepOutcome.Done(outputs = mapOf("location.maps_url" to "https://maps/x")) }
        val validated = FlowValidator().validate(
            Automation(
                "Where", trigger = Step(type = "sms_received"),
                steps = listOf(
                    Step(type = "get_location"),
                    Step(type = "send_sms", params = mapOf("to" to "{{sms.from}}", "message" to "At {{location.maps_url}} ({{now.day}})"))
                )
            )
        )
        val clock = Clock.fixed(LocalDateTime.of(2026, 10, 22, 9, 0).toInstant(ZoneOffset.UTC), ZoneId.of("UTC"))
        FlowEngine(handlers(mapOf("get_location" to location)), clock)
            .run(validated.flow, triggerOutputs = mapOf("sms.from" to "+15550001"))
        assertEquals(listOf("send_sms:+15550001, At https://maps/x (thursday)"), calls)
    }

    @Test
    fun `empty filled-in value fails instead of matching everything`() = runTest {
        val validated = FlowValidator().validate(
            Automation(
                "Check", trigger = Step(type = "sms_received"),
                steps = listOf(
                    Step(type = "ask_ai", params = mapOf("prompt" to "is this spam: {{sms.body}}")),
                    Step(type = "text_contains", params = mapOf("text" to "{{sms.body}}", "contains" to "{{ai.reply}}")),
                    Step(type = "send_sms", params = mapOf("to" to "{{sms.from}}", "message" to "ok"))
                )
            )
        )
        val emptyAi = StepHandler { _, _, _ -> StepOutcome.Done(outputs = mapOf("ai.reply" to "")) }
        val result = FlowEngine(PureHandlers.create() + handlers(mapOf("ask_ai" to emptyAi)))
            .run(validated.flow, mapOf("sms.from" to "+15550001", "sms.body" to "hello"))
        assertEquals(RunStatus.FAILED, result.status)
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `filled-in sender name is not a phone number`() = runTest {
        val validated = FlowValidator().validate(
            Automation(
                "Reply", trigger = Step(type = "sms_received"),
                steps = listOf(Step(type = "send_sms", params = mapOf("to" to "{{sms.from}}", "message" to "hi")))
            )
        )
        val result = FlowEngine(handlers()).run(validated.flow, mapOf("sms.from" to "VM-BANK", "sms.body" to "x"))
        assertEquals(RunStatus.FAILED, result.status)
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `cancelling during wait stops before the next step`() = runTest {
        val job = launch {
            FlowEngine(PureHandlers.create() + handlers()).run(
                flow(Step("w", "wait", mapOf("seconds" to "30")), Step("s", "send_sms", mapOf("to" to "+1555", "message" to "late")))
            )
        }
        advanceTimeBy(5_000)
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `time window works across midnight`() = runTest {
        fun at(hour: Int) = Clock.fixed(LocalDateTime.of(2026, 10, 22, hour, 0).toInstant(ZoneOffset.UTC), ZoneId.of("UTC"))
        val flow = flow(Step("w", "time_between", mapOf("start" to "22:00", "end" to "06:00")))
        suspend fun status(hour: Int) = FlowEngine(PureHandlers.create(at(hour)), at(hour)).run(flow).status
        assertEquals(RunStatus.COMPLETED, status(23))
        assertEquals(RunStatus.COMPLETED, status(3))
        assertEquals(RunStatus.STOPPED, status(12))
    }
}
