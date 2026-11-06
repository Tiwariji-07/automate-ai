package com.devfest.automation.run

import com.devfest.automation.store.FlowStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class MayRunTest {

    @Test
    fun `a triggered run needs the flow to be enabled when the worker starts`() {
        assertEquals(true, RunWorker.mayRun(FlowStatus.ENABLED, requireEnabled = true))
        assertEquals(false, RunWorker.mayRun(FlowStatus.DRAFT, requireEnabled = true))
        assertEquals(false, RunWorker.mayRun(FlowStatus.OFF, requireEnabled = true))
    }

    @Test
    fun `a manual or test run uses the saved flow whatever its status`() {
        FlowStatus.entries.forEach { assertEquals(true, RunWorker.mayRun(it, requireEnabled = false)) }
    }
}
