package com.devfest.automation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devfest.automation.store.FlowStatus
import com.devfest.automation.store.RunEntity
import com.devfest.automation.store.RunState
import com.devfest.automation.store.RunStepEntity
import com.devfest.automation.ui.theme.App
import com.devfest.automation.ui.theme.Type
import com.devfest.core.catalog.Blocks
import com.devfest.core.catalog.Category
import com.devfest.core.engine.StepStatus
import com.devfest.core.model.Automation
import com.devfest.core.model.Step
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun FlowScreen(flowId: String, onBack: () -> Unit, onEdit: () -> Unit, onReview: () -> Unit) {
    val app = appInstance()
    val saved by remember(flowId) { app.store.observeFlow(flowId) }.collectAsStateWithLifecycle(null)
    val runs by remember(flowId) { app.store.observeRuns(flowId) }.collectAsStateWithLifecycle(emptyList())
    var selectedRunId by rememberSaveable { mutableStateOf<Long?>(null) }
    val run = runs.firstOrNull { it.id == selectedRunId } ?: runs.firstOrNull()
    val steps by remember(run?.id) { run?.let { app.store.observeRunSteps(it.id) } ?: flowOf(emptyList()) }
        .collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val colors = App.colors
    val flow = saved?.automation
    val running = runs.firstOrNull()?.state == RunState.RUNNING

    Box(Modifier.fillMaxSize().background(colors.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 110.dp)) {
            TopBar(saved?.title.orEmpty(), onBack) {
                Box {
                    IconCircle(Icons.Rounded.MoreVert, "More") { menu = true }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Change in chat") }, { menu = false; onEdit() })
                        DropdownMenuItem({ Text("Review and permissions") }, { menu = false; onReview() })
                        if (saved?.status == FlowStatus.ENABLED) {
                            DropdownMenuItem({ Text("Turn off") }, { menu = false; scope.launch { app.control.turnOff(flowId) } })
                        }
                        DropdownMenuItem({ Text("Delete", color = colors.fail) }, { menu = false; confirmDelete = true })
                    }
                }
            }
            if (flow == null) return@Column

            AnimatedContent(run?.state == RunState.RUNNING, label = "now") { live ->
                if (live && run != null) NowCard(flow, run, steps) else LastRunCard(saved?.status, run)
            }

            Panel(if (run == null) "Steps" else if (run.id == runs.firstOrNull()?.id) "Latest run" else "Run from ${ago(run.startedAt)}") {
                Wire(flow, statuses = if (run == null) emptyMap() else statuses(flow, steps, run.state == RunState.RUNNING))
            }

            if (runs.size > 1 || (runs.size == 1 && runs.first().state != RunState.RUNNING)) {
                SectionHeader("History", runs.size.toString())
                Card {
                    runs.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider(color = colors.line)
                        HistoryRow(item, selected = item.id == run?.id) { selectedRunId = item.id }
                    }
                }
            }
            if (running) {
                Text(
                    "Stopping skips the steps that have not started. A text that is already sent cannot be unsent.",
                    style = Type.tiny, color = colors.muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }

        BottomBar(Modifier.align(Alignment.BottomCenter)) {
            when {
                flow == null -> Unit
                running -> AppButton("Stop run", { app.runner.cancel(flowId) }, Modifier.weight(1f), kind = ButtonKind.DANGER)
                saved?.status == FlowStatus.DRAFT -> {
                    AppButton("Change in chat", onEdit, Modifier.weight(1f), kind = ButtonKind.QUIET)
                    AppButton("Review", onReview, Modifier.weight(1f), kind = ButtonKind.INK)
                }
                else -> {
                    AppButton("Test run", { selectedRunId = null; app.runner.run(flowId, "Test run", test = true) }, Modifier.weight(1f), kind = ButtonKind.QUIET)
                    AppButton("Run now", { selectedRunId = null; app.runner.run(flowId, "Run button") }, Modifier.weight(1f))
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${saved?.title}\"?") },
            text = { Text("Its chat and run history go too.") },
            confirmButton = {
                TextButton({
                    confirmDelete = false
                    scope.launch { app.control.delete(flowId); onBack() }
                }) { Text("Delete", color = colors.fail) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun NowCard(flow: Automation, run: RunEntity, steps: List<RunStepEntity>) {
    val colors = App.colors
    val rows = wireRows(flow)
    val statuses = statuses(flow, steps, running = true)
    val currentIndex = rows.indexOfFirst { statuses[it.step.id]?.state == NodeState.RUNNING }
    val current = rows.getOrNull(currentIndex)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(run.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val seconds = ((now - run.startedAt) / 1000).coerceAtLeast(0)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
            .background(colors.brand, RoundedCornerShape(24.dp)).padding(16.dp)
    ) {
        Text(
            if (currentIndex > 0) "Step $currentIndex of ${rows.size - 1}" else "Starting",
            style = Type.tiny, color = colors.onBrand.copy(alpha = 0.85f)
        )
        Text(current?.let { describe(it.step).title } ?: "Running", style = Type.big, color = colors.onBrand, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text(run.source, style = Type.small, color = colors.onBrand.copy(alpha = 0.9f), modifier = Modifier.weight(1f))
            Text("%d:%02d so far".format(seconds / 60, seconds % 60), style = Type.small, color = colors.onBrand.copy(alpha = 0.9f))
        }
    }
}

@Composable
private fun LastRunCard(status: FlowStatus?, run: RunEntity?) {
    val colors = App.colors
    val (headline, color) = when (run?.state) {
        null -> (if (status == FlowStatus.ENABLED) "Waiting for the trigger" else "Not run yet") to colors.muted
        RunState.COMPLETED -> "✓ Ran ${ago(run.startedAt)}" to colors.ok
        RunState.STOPPED -> "Ran ${ago(run.startedAt)}, a condition was not met" to colors.warn
        RunState.FAILED -> "✕ Failed ${ago(run.startedAt)}" to colors.fail
        RunState.CANCELLED -> "Stopped ${ago(run.startedAt)}" to colors.faint
        RunState.RUNNING -> "Running" to colors.brand
    }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(headline, style = Type.bodyStrong, color = color)
        run?.let {
            val detail = listOf(it.source, it.summary).filter(String::isNotBlank).joinToString(" · ")
            if (detail.isNotEmpty()) Text(detail, style = Type.small, color = colors.muted)
        }
    }
}

@Composable
private fun HistoryRow(run: RunEntity, selected: Boolean, onClick: () -> Unit) {
    val colors = App.colors
    val (mark, color) = when (run.state) {
        RunState.COMPLETED -> "✓" to colors.ok
        RunState.FAILED -> "✕" to colors.fail
        RunState.STOPPED -> "–" to colors.warn
        RunState.CANCELLED -> "■" to colors.faint
        RunState.RUNNING -> "●" to colors.brand
    }
    Row(
        Modifier.fillMaxWidth().background(if (selected) colors.brandSoft else Color.Transparent).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(mark, style = Type.bodyStrong, color = color, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(run.source, style = Type.small.copy(fontWeight = FontWeight.SemiBold), color = colors.ink)
            if (run.summary.isNotBlank()) Text(run.summary, style = Type.tiny, color = colors.muted, maxLines = 2)
        }
        Text(ago(run.startedAt), style = Type.tiny, color = colors.faint)
    }
}

/**
 * Turns saved step records into wire states. A step with no record is running (the first one,
 * while the run is live), waiting (after it), or skipped (a branch not taken, or after a stop).
 */
fun statuses(flow: Automation, records: List<RunStepEntity>, running: Boolean): Map<String, NodeStatus> {
    val byId = records.associateBy { it.stepId }
    val result = mutableMapOf<String, NodeStatus>()
    var runningAssigned = false

    fun record(step: Step): NodeState? = byId[step.id]?.let {
        when (runCatching { StepStatus.valueOf(it.status) }.getOrNull()) {
            StepStatus.DONE -> NodeState.DONE
            StepStatus.PASSED -> NodeState.PASSED
            StepStatus.NOT_PASSED -> NodeState.NOT_PASSED
            StepStatus.FAILED, null -> NodeState.FAILED
        }
    }

    fun pending(): NodeState = when {
        !running -> NodeState.SKIPPED
        !runningAssigned -> NodeState.RUNNING.also { runningAssigned = true }
        else -> NodeState.WAITING
    }

    /** Returns false when the rest of the list will not run. */
    fun walk(steps: List<Step>, reachable: Boolean): Boolean {
        var active = reachable
        for (step in steps) {
            if (!active) {
                result[step.id] = NodeStatus(NodeState.SKIPPED)
                walk(step.then, false)
                walk(step.otherwise, false)
                continue
            }
            val state = record(step)
            if (state == null) {
                result[step.id] = NodeStatus(pending())
                walk(step.then, running)
                walk(step.otherwise, running)
                if (!running) active = false
                continue
            }
            result[step.id] = NodeStatus(state, byId[step.id]?.message.orEmpty())
            val isCondition = Blocks[step.type]?.category == Category.CONDITION
            when {
                state == NodeState.FAILED -> {
                    walk(step.then, false)
                    walk(step.otherwise, false)
                    active = false
                }
                isCondition && step.hasBranches -> {
                    val passed = state == NodeState.PASSED
                    walk(step.then, passed)
                    walk(step.otherwise, !passed)
                }
                isCondition && state == NodeState.NOT_PASSED -> active = false
            }
        }
        return active
    }

    record(flow.trigger)?.let { result[flow.trigger.id] = NodeStatus(it) }
    walk(flow.steps, reachable = true)
    return result
}
