package com.devfest.automation.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devfest.automation.AutomateApp
import com.devfest.automation.platform.Requirements
import com.devfest.automation.store.FlowStatus
import com.devfest.automation.store.RunEntity
import com.devfest.automation.store.RunState
import com.devfest.automation.store.SavedFlow
import com.devfest.automation.ui.theme.App
import com.devfest.automation.ui.theme.Type
import com.devfest.core.catalog.Requirement
import com.devfest.core.trigger.TriggerRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

data class HomeItem(
    val flow: SavedFlow,
    /** The first thing the owner typed, for drafts the model has not turned into a flow yet. */
    val firstMessage: String?,
    val lastRun: RunEntity?,
    val missing: List<Requirement>,
    val nextRunAt: Long?
) {
    val needsYou: Boolean
        get() = flow.status == FlowStatus.ENABLED && (missing.isNotEmpty() || lastRun?.state == RunState.FAILED)
}

data class HomeState(val loaded: Boolean = false, val items: List<HomeItem> = emptyList()) {
    val needsYou get() = items.filter { it.needsYou }
    val on get() = items.filter { it.flow.status == FlowStatus.ENABLED && !it.needsYou }
    val off get() = items.filter { it.flow.status == FlowStatus.OFF }
    val drafts get() = items.filter { it.flow.status == FlowStatus.DRAFT }
    val next get() = items.filter { it.flow.status == FlowStatus.ENABLED && it.nextRunAt != null }.minByOrNull { it.nextRunAt!! }
}

class HomeViewModel(private val app: AutomateApp) : ViewModel() {

    /** Bumped on resume, so revoked permissions and "next run" times are re-read. */
    private val tick = MutableStateFlow(0)

    val state = combine(app.store.observeFlows(), app.store.observeLatestRuns(), tick) { flows, runs, _ ->
        val latest = runs.associateBy { it.flowId }
        val now = ZonedDateTime.now()
        HomeState(
            loaded = true,
            items = flows.map { flow ->
                val trigger = flow.automation?.trigger
                HomeItem(
                    flow = flow,
                    firstMessage = if (flow.automation == null) app.store.chat(flow.id).firstOrNull()?.content else null,
                    lastRun = latest[flow.id],
                    missing = app.control.missing(flow),
                    nextRunAt = if (trigger?.type == "schedule") TriggerRules.nextSchedule(trigger.params, now)?.toInstant()?.toEpochMilli() else null
                )
            }
        )
    }.flowOn(Dispatchers.Default) // permission checks read the Keystore
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun refresh() { tick.value++ }

    fun run(id: String) = app.runner.run(id, "Run button")

    /** Calls [needsReview] when the flow is missing permissions, so the caller opens the review screen. */
    fun setEnabled(id: String, on: Boolean, needsReview: () -> Unit) = viewModelScope.launch {
        if (!on) {
            app.control.turnOff(id)
        } else if (app.control.enable(id).isNotEmpty()) {
            needsReview()
        }
    }
}

@Composable
fun HomeScreen(
    onOpenFlow: (SavedFlow) -> Unit,
    onReview: (String) -> Unit,
    onNewChat: (String) -> Unit,
    onSettings: () -> Unit
) {
    val app = appInstance()
    val vm = viewModel { HomeViewModel(app) }
    val state by vm.state.collectAsStateWithLifecycle()
    var prompt by rememberSaveable { mutableStateOf("") }
    val colors = App.colors

    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    Box(Modifier.fillMaxSize().background(colors.bg)) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Automate", style = Type.wordmark, color = colors.ink, modifier = Modifier.weight(1f))
                    IconCircle(Icons.Rounded.Tune, "Settings", onSettings)
                }
                state.next?.let { next ->
                    Text(
                        buildAnnotatedString {
                            append("Next: ")
                            withStyle(SpanStyle(color = colors.ink, fontWeight = FontWeight.SemiBold)) { append(next.flow.title) }
                            append(" at ${clock(next.nextRunAt!!)}, in ${inTime(next.nextRunAt - System.currentTimeMillis())}")
                        },
                        style = Type.small, color = colors.muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                    )
                }
            }
            if (state.loaded && state.items.isEmpty()) {
                item { EmptyHome(onPick = { prompt = it }) }
            }
            section("Needs you", state.needsYou, attention = true) { item ->
                FlowRow(item, trailing = {
                    Text(
                        "Fix", style = Type.smallStrong, color = colors.brand,
                        modifier = Modifier.clickable { onReview(item.flow.id) }.padding(horizontal = 8.dp, vertical = 12.dp)
                    )
                }, onClick = { onOpenFlow(item.flow) })
            }
            section("On", state.on, count = true) { item ->
                FlowRow(item, trailing = {
                    if (item.flow.automation?.trigger?.type == "manual") {
                        RunButton { vm.run(item.flow.id); onOpenFlow(item.flow) }
                    } else {
                        FlowSwitch(true) { vm.setEnabled(item.flow.id, it) { onReview(item.flow.id) } }
                    }
                }, onClick = { onOpenFlow(item.flow) })
            }
            section("Off", state.off) { item ->
                FlowRow(item, trailing = {
                    FlowSwitch(false) { vm.setEnabled(item.flow.id, it) { onReview(item.flow.id) } }
                }, onClick = { onOpenFlow(item.flow) })
            }
            section("Drafts", state.drafts) { item ->
                FlowRow(item, trailing = {}, onClick = { onOpenFlow(item.flow) })
            }
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(120.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, colors.bg, colors.bg)))
        )
        Composer(
            value = prompt,
            onChange = { prompt = it },
            placeholder = "Describe an automation",
            onSend = { onNewChat(prompt.trim()); prompt = "" },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding()
        )
    }
}

private fun LazyListScope.section(
    title: String,
    items: List<HomeItem>,
    attention: Boolean = false,
    count: Boolean = false,
    row: @Composable (HomeItem) -> Unit
) {
    if (items.isEmpty()) return
    item(key = "header-$title") { SectionHeader(title, if (count) items.size.toString() else null) }
    item(key = "card-$title") {
        val colors = App.colors
        Card(Modifier.animateContentSize(), border = if (attention) colors.fail.copy(alpha = 0.35f) else colors.line) {
            items.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(color = colors.line)
                row(item)
            }
        }
    }
}

@Composable
private fun FlowRow(item: HomeItem, trailing: @Composable () -> Unit, onClick: () -> Unit) {
    val colors = App.colors
    val flow = item.flow
    val active = flow.status != FlowStatus.OFF
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 12.dp, top = 13.dp, bottom = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        MiniWire(flow.automation, active)
        Column(Modifier.weight(1f)) {
            Text(flow.title, style = Type.rowTitle, color = colors.ink)
            (flow.automation?.summary ?: item.firstMessage?.let { "\"$it\"" })?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = Type.small, color = colors.muted, maxLines = 2, modifier = Modifier.padding(top = 3.dp))
            }
            val (text, color) = rowState(item)
            Text(text, style = Type.tiny.copy(fontWeight = FontWeight.SemiBold), color = color, modifier = Modifier.padding(top = 5.dp))
        }
        Box(Modifier.align(Alignment.CenterVertically)) { trailing() }
    }
}

@Composable
private fun rowState(item: HomeItem): Pair<String, Color> {
    val colors = App.colors
    val run = item.lastRun
    return when {
        item.flow.status == FlowStatus.DRAFT && item.flow.automation == null -> "Not finished" to colors.faint
        item.flow.status == FlowStatus.DRAFT && item.missing.isNotEmpty() ->
            "Needs ${Requirements.label(item.missing.first())}" to colors.warn
        item.flow.status == FlowStatus.DRAFT -> "Ready to review" to colors.brand
        item.flow.status == FlowStatus.ENABLED && item.missing.isNotEmpty() ->
            "Blocked: needs ${Requirements.label(item.missing.first())}" to colors.fail
        run?.state == RunState.RUNNING -> "● Running now" to colors.brand
        run?.state == RunState.FAILED -> "✕ Failed ${ago(run.startedAt)}: ${run.summary}" to colors.fail
        item.flow.status == FlowStatus.OFF -> "Off" to colors.faint
        item.flow.automation?.trigger?.type == "manual" && run == null -> "Tap ▶ to run" to colors.faint
        item.nextRunAt != null -> "Next run in ${inTime(item.nextRunAt - System.currentTimeMillis())}" to colors.faint
        run != null && run.state == RunState.COMPLETED -> "✓ Ran ${ago(run.startedAt)}" to colors.ok
        run != null -> "Last run ${ago(run.startedAt)}: ${run.summary}" to colors.faint
        else -> "Waiting for the trigger" to colors.faint
    }
}

@Composable
private fun RunButton(onClick: () -> Unit) {
    val colors = App.colors
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(colors.brand)
            .clickable(onClickLabel = "Run now") {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.PlayArrow, "Run now", tint = colors.onBrand)
    }
}

private val EXAMPLES = listOf(
    "When I unplug the charger, tell me the battery level",
    "Weekdays at 23:00, Do Not Disturb on and brightness 10%",
    "When an SMS has an OTP, copy the code",
    "After 3 wrong unlocks, take a front photo and text me my location"
)

@Composable
private fun EmptyHome(onPick: (String) -> Unit) {
    val colors = App.colors
    Column(Modifier.padding(top = 24.dp)) {
        Text(
            "Say what should happen, and when.",
            style = Type.hero, color = colors.ink, modifier = Modifier.padding(horizontal = 20.dp)
        )
        Text(
            "Automate turns it into a flow you can check before it runs.",
            style = Type.body, color = colors.muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        SectionHeader("Try one")
        EXAMPLES.forEach { example ->
            Text(
                "\"$example\"",
                style = Type.rowTitle, color = colors.ink,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                    .border(1.dp, colors.line, RoundedCornerShape(22.dp))
                    .clickable { onPick(example) }
                    .padding(14.dp)
            )
        }
        Spacer(Modifier.width(1.dp))
    }
}

fun clock(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime().toString().take(5)
