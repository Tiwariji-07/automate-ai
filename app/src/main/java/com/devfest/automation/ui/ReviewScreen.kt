package com.devfest.automation.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devfest.automation.AutomateApp
import com.devfest.automation.platform.Requirements
import com.devfest.automation.store.FlowStatus
import com.devfest.automation.ui.theme.App
import com.devfest.automation.ui.theme.Type
import com.devfest.core.catalog.Blocks
import com.devfest.core.catalog.Requirement
import com.devfest.core.catalog.requirementsOf
import com.devfest.core.model.Automation
import com.devfest.core.model.Step
import com.devfest.core.model.forEachStep
import kotlinx.coroutines.launch

@Composable
fun ReviewScreen(flowId: String, onBack: () -> Unit, onTestStarted: () -> Unit, onTurnedOn: () -> Unit) {
    val app = appInstance()
    val context = LocalContext.current
    val saved by remember(flowId) { app.store.observeFlow(flowId) }.collectAsStateWithLifecycle(null)
    val flow = saved?.automation
    val scope = rememberCoroutineScope()
    val colors = App.colors

    // Bumped when the owner comes back from a permission dialog or settings screen.
    var checks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose { }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { checks++ }
    val activity = LocalActivity.current
    // Permissions already asked for here. Android stops showing the dialog after two denials.
    val asked = remember { mutableSetOf<String>() }

    val requirements = remember(flow) { flow?.let { requirementsOf(it).sortedBy(Requirement::ordinal) }.orEmpty() }
    val granted = remember(requirements, checks) { requirements.associateWith { Requirements.isGranted(context, it) } }
    val allGranted = granted.values.all { it }

    fun allow(requirement: Requirement) {
        // Background location is only offered once foreground location is granted.
        val target = if (requirement == Requirement.BACKGROUND_LOCATION && granted[Requirement.LOCATION] == false) Requirement.LOCATION else requirement
        val permissions = when (target) {
            // Android 12+ wants fine and coarse in one request.
            Requirement.LOCATION -> listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            else -> listOfNotNull(Requirements.permission(target))
        }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        val deniedForGood = permissions.any { it in asked && activity?.shouldShowRequestPermissionRationale(it) == false }
        if (permissions.isNotEmpty() && !deniedForGood) {
            asked += permissions
            permissionLauncher.launch(permissions.toTypedArray())
            return
        }
        // Denied for good, granted but switched off (notifications), or a settings-only permission.
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        val intent = if (permissions.isNotEmpty()) details else Requirements.settingsIntent(context, target) ?: details
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(details)
        }
    }

    @Composable
    fun NeedsPanel() = Panel(if (allGranted) "It uses" else "First, allow these") {
        requirements.forEachIndexed { index, requirement ->
            if (index > 0) HorizontalDivider(color = colors.line)
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(Requirements.label(requirement), style = Type.body, color = colors.ink)
                    why(requirement)?.let { Text(it, style = Type.tiny, color = colors.muted) }
                }
                if (granted[requirement] == true) {
                    Text("✓ Allowed", style = Type.smallStrong, color = colors.ok)
                } else {
                    Text(
                        "Allow", style = Type.smallStrong, color = colors.onBrand,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(colors.brand)
                            .clickable { allow(requirement) }.padding(horizontal = 18.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(colors.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
            TopBar("", onBack)
            if (flow == null) return@Column
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(flow.title, style = Type.hero, color = colors.ink)
                Text(runsWhen(flow), style = Type.small, color = colors.muted, modifier = Modifier.padding(top = 6.dp))
            }

            // Missing permissions block turning on, so they come first.
            if (requirements.isNotEmpty() && !allGranted) NeedsPanel()

            val outgoing = remember(flow) { sensitiveSteps(flow) }
            if (outgoing.isNotEmpty()) {
                Panel("It will send or record") {
                    outgoing.forEachIndexed { index, step ->
                        if (index > 0) HorizontalDivider(color = colors.line, modifier = Modifier.padding(vertical = 8.dp))
                        SensitiveDetail(step, app)
                    }
                }
            }

            Panel("What it does") { Wire(flow) }

            if (requirements.isNotEmpty() && allGranted) NeedsPanel()

            val testNote = if (outgoing.any { it.type in TEST_FAKED }) {
                "A test run does every step, except that texts, web requests and AI prompts only show what they would send."
            } else {
                "A test run does every step now, so you can check the result."
            }
            Text(testNote, style = Type.tiny, color = colors.muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        }

        BottomBar(Modifier.align(Alignment.BottomCenter)) {
            AppButton("Test run", {
                app.runner.run(flowId, "Test run", test = true)
                onTestStarted()
            }, Modifier.weight(1f), kind = ButtonKind.QUIET, enabled = flow != null)
            val on = saved?.status == FlowStatus.ENABLED
            AppButton(
                if (on) "Keep on" else if (allGranted) "Turn on" else "Allow all first",
                {
                    scope.launch {
                        if (app.control.enable(flowId).isEmpty()) onTurnedOn()
                    }
                },
                Modifier.weight(1f),
                enabled = flow != null && allGranted
            )
        }
    }
}

private val TEST_FAKED = setOf("send_sms", "http_request", "ask_ai")

private fun runsWhen(flow: Automation): String = when (flow.trigger.type) {
    "manual" -> "Runs when you tap Run."
    "schedule" -> "Runs on its own at the set time, even when the screen is off."
    else -> "Runs in the background, even when the screen is locked."
}

private fun sensitiveSteps(flow: Automation): List<Step> = buildList {
    forEachStep(flow.steps) { if (Blocks[it.type]?.sensitive == true) add(it) }
}

@Composable
private fun SensitiveDetail(step: Step, app: AutomateApp) {
    val colors = App.colors
    val p = step.params
    val (headline, body) = when (step.type) {
        "send_sms" -> "SMS to ${p["to"]}" to pretty(p["message"])
        "http_request" -> "${p["method"] ?: "POST"} ${p["url"]}" to p["body"]?.let(::pretty)
        "take_photo" -> "A ${p["camera"] ?: "front"} camera photo, kept in the app" to null
        "get_location" -> "Your location, for the steps after it" to null
        "ask_ai" -> {
            // load() decrypts the key; only the host is needed, so read it once.
            val host = remember { Uri.parse(app.aiSettings.load().baseUrl).host }
            "Prompt to ${host ?: "the AI service"}" to pretty(p["prompt"])
        }
        else -> describe(step).title to null
    }
    Text(headline, style = Type.bodyStrong.copy(fontSize = Type.small.fontSize), color = colors.ink)
    body?.takeIf { it.isNotBlank() }?.let {
        Text(
            it, style = Type.small, color = colors.ink,
            modifier = Modifier.padding(top = 4.dp).fillMaxWidth().background(colors.bg, RoundedCornerShape(12.dp)).padding(10.dp)
        )
    }
}

private fun why(requirement: Requirement): String? = when (requirement) {
    Requirement.LISTENER_SERVICE -> "Stops the phone from closing Automate in the background"
    Requirement.BACKGROUND_LOCATION -> "Choose \"Allow all the time\""
    Requirement.EXACT_ALARMS -> "So the flow runs on the minute"
    Requirement.DEVICE_ADMIN -> "Only used to count wrong unlocks"
    Requirement.AI_KEY -> "Set it in Settings"
    else -> null
}
