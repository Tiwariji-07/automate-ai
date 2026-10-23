package com.devfest.automation.run

import com.devfest.automation.AutomateApp
import com.devfest.automation.platform.Requirements
import com.devfest.automation.store.FlowStatus
import com.devfest.automation.store.SavedFlow
import com.devfest.automation.trigger.Triggers
import com.devfest.core.catalog.Requirement
import com.devfest.core.catalog.requirementsOf
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * The one place that turns flows on and off. The Home switch, the review screen and delete all go
 * through here, so a flow is never enabled without its permissions and triggers always match.
 * Each change and its trigger sync finish together, even if the screen that started them closes.
 */
class FlowControl(private val app: AutomateApp) {

    fun missing(flow: SavedFlow): List<Requirement> =
        flow.automation?.let { Requirements.missing(app, requirementsOf(it)) }.orEmpty()

    /** Turns the flow on when nothing is missing. Returns what is still missing. */
    suspend fun enable(id: String): List<Requirement> = withContext(NonCancellable) {
        val flow = app.store.get(id)
        val missing = flow?.automation?.let { missing(flow) } ?: return@withContext emptyList()
        if (missing.isEmpty()) {
            app.store.setStatus(id, FlowStatus.ENABLED)
            Triggers.sync(app)
        }
        missing
    }

    suspend fun turnOff(id: String) = withContext(NonCancellable) {
        app.store.setStatus(id, FlowStatus.OFF)
        Triggers.sync(app)
    }

    suspend fun delete(id: String) = withContext(NonCancellable) {
        app.runner.cancel(id)
        app.store.delete(id)
        Triggers.sync(app)
    }
}
