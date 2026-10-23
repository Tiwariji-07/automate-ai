package com.devfest.automation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devfest.automation.AutomateApp
import com.devfest.automation.store.FlowStatus
import com.devfest.automation.store.SavedFlow
import com.devfest.automation.trigger.Triggers
import com.devfest.automation.ui.theme.App
import com.devfest.automation.ui.theme.Type
import com.devfest.core.agent.ChatMessage
import com.devfest.core.model.Automation
import com.devfest.core.model.Step
import com.devfest.core.model.forEachStep
import com.devfest.core.validation.FlowValidator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(private val app: AutomateApp, startId: String?, saved: SavedStateHandle) : ViewModel() {

    /** Saved, so a new chat finds its draft again after Android kills the app in the background. */
    private val flowId = saved.getMutableStateFlow(KEY_FLOW, startId)

    val flow = flowId.flatMapLatest { id -> id?.let(app.store::observeFlow) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val messages = flowId.flatMapLatest { id -> id?.let(app.store::observeChat) ?: emptyFlow() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    /** The draft before the last reply changed it, for Undo and the change marks. */
    val previous = MutableStateFlow<Automation?>(null)

    /** Validator warnings by step id, shown on the step they are about. */
    val warnings = MutableStateFlow<Map<String, String>>(emptyMap())

    fun send(text: String) {
        if (text.isBlank() || busy.value) return
        busy.value = true
        error.value = null
        // On the app scope, so a reply that arrives after the screen closes is still saved.
        app.scope.launch {
            try {
                val id = flowId.value ?: app.store.createDraft().also { flowId.value = it }
                app.store.addChat(id, ChatMessage(ChatMessage.Role.USER, text.trim()))
                val current = app.store.get(id)?.automation
                val places = app.store.places().map { it.name }
                val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd HH:mm"))
                val result = app.agent.respond(app.store.chat(id), current, places, now)

                val reply = buildString {
                    append(result.message.ifBlank { if (result.flow != null) "Here is the flow." else "I could not build that." })
                    if (result.flow == null && result.errors.isNotEmpty()) {
                        append("\n\nProblems: ")
                        append(result.errors.joinToString("; ") { it.message })
                    }
                }
                app.store.addChat(id, ChatMessage(ChatMessage.Role.ASSISTANT, reply))
                result.flow?.let { flow ->
                    previous.value = current
                    val wasOn = app.store.get(id)?.status == FlowStatus.ENABLED
                    app.store.save(id, flow)
                    if (wasOn) Triggers.sync(app)
                }
            } catch (e: Exception) {
                error.value = e.message ?: "Something went wrong"
            } finally {
                busy.value = false
            }
        }
    }

    fun undo() {
        val id = flowId.value ?: return
        val before = previous.value ?: return
        previous.value = null
        app.scope.launch {
            val wasOn = app.store.get(id)?.status == FlowStatus.ENABLED
            app.store.save(id, before)
            app.store.addChat(id, ChatMessage(ChatMessage.Role.ASSISTANT, "Undid the last change."))
            if (wasOn) Triggers.sync(app)
        }
    }


    /** Re-checked on every change of the draft, so Undo never leaves warnings of the newer version. */
    fun revalidateWarnings(flow: Automation) = viewModelScope.launch {
        val places = app.store.places().map { it.name }.toSet()
        warnings.value = FlowValidator(places).validate(flow).warnings
            .filter { it.stepId != null }.associate { it.stepId!! to it.message }
    }

    private companion object {
        const val KEY_FLOW = "flow"
    }
}

@Composable
fun ChatScreen(flowId: String?, initialPrompt: String?, onBack: () -> Unit, onReview: (String) -> Unit) {
    val app = appInstance()
    val vm = viewModel { ChatViewModel(app, flowId, createSavedStateHandle()) }
    val flow by vm.flow.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val previous by vm.previous.collectAsStateWithLifecycle()
    val warnings by vm.warnings.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var sentInitial by rememberSaveable { mutableStateOf(false) }
    val list = rememberLazyListState()
    val colors = App.colors

    LaunchedEffect(Unit) {
        if (!sentInitial && !initialPrompt.isNullOrBlank()) {
            sentInitial = true
            vm.send(initialPrompt)
        }
    }
    LaunchedEffect(flow?.automation) { flow?.automation?.let(vm::revalidateWarnings) }
    LaunchedEffect(messages.size, busy, flow?.automation) {
        val count = list.layoutInfo.totalItemsCount
        if (count > 0) list.animateScrollToItem(count - 1)
    }

    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        TopBar(flow?.title ?: "New flow", onBack)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = list,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty() && !busy) {
                item {
                    Text(
                        "Describe what should happen and when. For example: \"When I get home, turn the ringer on\".",
                        style = Type.body, color = colors.muted, modifier = Modifier.padding(6.dp)
                    )
                }
            }
            items(messages) { message -> Bubble(message) }
            if (busy) item { Text("Thinking…", style = Type.small, color = colors.faint, modifier = Modifier.padding(4.dp)) }
            error?.let { item { ErrorNote(it) } }
            flow?.automation?.let { automation ->
                item(key = "draft") {
                    DraftCard(
                        flow = flow!!,
                        automation = automation,
                        changed = changes(previous, automation),
                        warnings = warnings,
                        canUndo = previous != null && !busy,
                        onUndo = vm::undo,
                        onReview = { onReview(flow!!.id) }
                    )
                }
            }
        }
        Composer(
            value = input,
            onChange = { input = it },
            placeholder = if (flow?.automation == null) "Describe an automation" else "Ask for a change",
            onSend = { vm.send(input); input = "" },
            busy = busy
        )
    }
}

@Composable
private fun Bubble(message: ChatMessage) {
    val colors = App.colors
    if (message.role == ChatMessage.Role.USER) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                message.content, style = Type.body, color = colors.onBrand,
                modifier = Modifier.widthIn(max = 300.dp)
                    .background(colors.brand, RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    } else {
        Text(message.content, style = Type.body, color = colors.ink, modifier = Modifier.padding(horizontal = 2.dp))
    }
}

@Composable
private fun ErrorNote(text: String) {
    val colors = App.colors
    Text(
        text, style = Type.small, color = colors.fail,
        modifier = Modifier.fillMaxWidth().background(colors.failSoft, RoundedCornerShape(14.dp)).padding(12.dp)
    )
}

@Composable
private fun DraftCard(
    flow: SavedFlow,
    automation: Automation,
    changed: Map<String, String>,
    warnings: Map<String, String>,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onReview: () -> Unit
) {
    val colors = App.colors
    Column(
        Modifier.fillMaxWidth().animateContentSize()
            .background(colors.surface, RoundedCornerShape(22.dp))
            .border(1.dp, colors.line, RoundedCornerShape(22.dp))
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(automation.title, style = Type.rowTitle, color = colors.ink, modifier = Modifier.weight(1f))
            AnimatedVisibility(canUndo) {
                Text(
                    "Undo", style = Type.smallStrong, color = colors.brand,
                    modifier = Modifier.heightIn(min = 48.dp).clickable(onClick = onUndo).padding(horizontal = 12.dp, vertical = 14.dp)
                )
            }
        }
        if (automation.summary.isNotBlank()) {
            Text(automation.summary, style = Type.small, color = colors.muted, modifier = Modifier.padding(bottom = 6.dp))
        }
        Wire(automation, changed = changed, warnings = warnings)
        AppButton(
            "Review and turn on",
            onReview, Modifier.fillMaxWidth().padding(top = 12.dp), kind = ButtonKind.INK
        )
    }
}

/** Steps that are new (empty value) or changed (value is the old wording), by id. */
private fun changes(before: Automation?, after: Automation): Map<String, String> {
    if (before == null) return emptyMap()
    fun index(flow: Automation): Map<String, Step> = buildMap {
        put(flow.trigger.id, flow.trigger)
        forEachStep(flow.steps) { put(it.id, it) }
    }
    val old = index(before)
    return index(after).mapNotNull { (id, step) ->
        val was = old[id]
        when {
            was == null || was.type != step.type -> id to ""
            was.params != step.params -> id to describe(was).title
            else -> null
        }
    }.toMap()
}
