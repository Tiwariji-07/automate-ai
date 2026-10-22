package com.devfest.automation

import android.app.Application
import com.devfest.automation.ai.AiSettings
import com.devfest.automation.ai.OpenAiChatModel
import com.devfest.automation.platform.DeviceHandlers
import com.devfest.automation.platform.MessagingHandlers
import com.devfest.automation.platform.Notifications
import com.devfest.automation.platform.SensorHandlers
import com.devfest.automation.run.FlowControl
import com.devfest.automation.run.FlowRunner
import com.devfest.automation.ui.UiSettings
import com.devfest.automation.store.AppDatabase
import com.devfest.automation.store.FlowStore
import com.devfest.core.agent.FlowAgent
import com.devfest.core.engine.FlowEngine
import com.devfest.core.engine.PureHandlers
import com.devfest.core.engine.StepHandler
import com.devfest.core.engine.StepOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Holds the app's single instances. Everything else gets them from here. */
class AutomateApp : Application() {

    /** For work that must outlive a screen, such as saving after a chat reply. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val store by lazy { FlowStore(AppDatabase.build(this)) }
    val aiSettings by lazy { AiSettings(this) }
    val chatModel by lazy { OpenAiChatModel(aiSettings::load) }
    val agent by lazy { FlowAgent(chatModel) }
    val runner by lazy { FlowRunner(this) }
    val device by lazy { DeviceHandlers(this) }

    private val handlers by lazy {
        PureHandlers.create() +
            device.create() +
            MessagingHandlers(this, chatModel).create() +
            SensorHandlers(this).create()
    }

    val engine by lazy { FlowEngine(handlers) }

    /** Same as [engine], except steps that send data off the phone only report what they would send. */
    val testEngine by lazy {
        FlowEngine(
            handlers + mapOf(
                "send_sms" to StepHandler { _, params, _ -> StepOutcome.Done("Test: would text ${params["to"]}: \"${params["message"]}\"") },
                "http_request" to StepHandler { _, params, _ -> StepOutcome.Done("Test: would ${params["method"] ?: "POST"} ${params["url"]}") },
                "ask_ai" to StepHandler { _, params, _ ->
                    StepOutcome.Done("Test: would ask the AI \"${params["prompt"]}\"", mapOf("ai.reply" to "(test answer)"))
                }
            )
        )
    }

    val control by lazy { FlowControl(this) }
    val uiSettings by lazy { UiSettings(this) }

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        scope.launch { store.cancelStaleRuns() }
    }
}
