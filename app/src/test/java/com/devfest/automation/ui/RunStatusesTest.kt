package com.devfest.automation.ui

import com.devfest.automation.store.RunStepEntity
import com.devfest.core.model.Automation
import com.devfest.core.model.Step
import kotlin.test.Test
import kotlin.test.assertEquals

class RunStatusesTest {

    private val flow = Automation(
        title = "t",
        trigger = Step("trigger", "manual"),
        steps = listOf(
            Step(
                "c1", "is_charging",
                then = listOf(Step("yes", "vibrate")),
                otherwise = listOf(Step("no", "speak"))
            ),
            Step("guard", "is_charging"),
            Step("last", "vibrate")
        )
    )

    private fun record(id: String, status: String) = RunStepEntity(runId = 1, stepId = id, type = "", status = status, message = "", durationMs = 0)

    @Test
    fun `live run marks the next step running and the rest waiting`() {
        val s = statuses(flow, listOf(record("trigger", "DONE"), record("c1", "PASSED")), running = true)
        assertEquals(NodeState.RUNNING, s["yes"]?.state)
        assertEquals(NodeState.SKIPPED, s["no"]?.state)
        assertEquals(NodeState.WAITING, s["guard"]?.state)
        assertEquals(NodeState.WAITING, s["last"]?.state)
    }

    @Test
    fun `a guard that does not pass skips the rest`() {
        val records = listOf(record("trigger", "DONE"), record("c1", "NOT_PASSED"), record("no", "DONE"), record("guard", "NOT_PASSED"))
        val s = statuses(flow, records, running = false)
        assertEquals(NodeState.SKIPPED, s["yes"]?.state)
        assertEquals(NodeState.DONE, s["no"]?.state)
        assertEquals(NodeState.NOT_PASSED, s["guard"]?.state)
        assertEquals(NodeState.SKIPPED, s["last"]?.state)
    }

    @Test
    fun `a failed branching condition skips both branches`() {
        val s = statuses(flow, listOf(record("trigger", "DONE"), record("c1", "FAILED")), running = false)
        assertEquals(NodeState.FAILED, s["c1"]?.state)
        assertEquals(NodeState.SKIPPED, s["yes"]?.state)
        assertEquals(NodeState.SKIPPED, s["no"]?.state)
        assertEquals(NodeState.SKIPPED, s["last"]?.state)
    }
}
