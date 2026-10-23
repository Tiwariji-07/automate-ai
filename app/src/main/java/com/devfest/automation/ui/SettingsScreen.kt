package com.devfest.automation.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devfest.automation.AutomateApp
import com.devfest.automation.ai.AiConfig
import com.devfest.automation.ai.AiSettings
import com.devfest.automation.platform.Requirements
import com.devfest.automation.store.PlaceEntity
import com.devfest.automation.trigger.Triggers
import com.devfest.automation.ui.theme.App
import com.devfest.automation.ui.theme.ThemeMode
import com.devfest.automation.ui.theme.Type
import com.devfest.core.catalog.Requirement
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** First launch: connect a model before anything else. */
@Composable
fun SetupScreen(onDone: () -> Unit) {
    val colors = App.colors
    Column(
        Modifier.fillMaxSize().background(colors.bg).statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState())
    ) {
        Text("Connect an AI model", style = Type.hero.copy(fontSize = Type.hero.fontSize * 1.1f), color = colors.ink, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 8.dp))
        Text(
            "Automate turns what you type into flows. It needs an API key from any OpenAI-compatible service.",
            style = Type.body, color = colors.muted, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 18.dp)
        )
        AiForm(button = "Test and continue", onSaved = onDone)
        Column(
            Modifier.fillMaxWidth().padding(12.dp).padding(top = 10.dp)
                .background(colors.surface.copy(alpha = 0.5f), RoundedCornerShape(22.dp)).padding(14.dp)
        ) {
            Text("Try first", style = Type.tiny, color = colors.faint)
            Text("\"When I unplug the charger, tell me the battery level\"", style = Type.rowTitle, color = colors.ink, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Preset picker, key, model and (for Custom) base URL. Tests the key with `/models` before saving. */
@Composable
fun AiForm(button: String, onSaved: () -> Unit) {
    val app = appInstance()
    val context = LocalContext.current
    val saved = remember { app.aiSettings.load() }
    val presets = AiSettings.PRESETS
    // Saveable, so a rotation or a trip to the key website does not clear what was typed.
    var preset by rememberSaveable { mutableIntStateOf(presets.indexOfFirst { it.baseUrl == saved.baseUrl }.let { if (it < 0) presets.size else it }) }
    var baseUrl by rememberSaveable { mutableStateOf(saved.baseUrl) }
    var model by rememberSaveable { mutableStateOf(saved.model) }
    var key by rememberSaveable { mutableStateOf(saved.apiKey) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val colors = App.colors

    Segmented(presets.map { if (it.name == "Groq") "Groq (free)" else it.name } + "Custom", preset, { index ->
        preset = index
        presets.getOrNull(index)?.let {
            baseUrl = it.baseUrl
            model = it.suggestedModel
        }
        models = emptyList()
        message = null
    }, Modifier.padding(bottom = 6.dp))
    if (preset == presets.size) Field("Base URL", baseUrl, { baseUrl = it }, placeholder = "https://…/v1")
    Field("API key", key, { key = it }, placeholder = "Paste your key", secret = true)
    presets.getOrNull(preset)?.let { p ->
        Text(
            buildAnnotatedString {
                append("No key yet? ")
                withStyle(SpanStyle(color = colors.brand, fontWeight = FontWeight.SemiBold)) { append("Get one from ${p.name}") }
                append(", then paste it here.")
            },
            style = Type.tiny, color = colors.muted,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp).clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.keyUrl)))
            }
        )
    }
    Field("Model", model, { model = it }, Modifier.padding(top = 8.dp))
    if (models.isNotEmpty()) {
        Text("Models on this key. Tap one:", style = Type.tiny, color = colors.muted, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
        models.take(12).forEach { id ->
            Text(
                id, style = Type.small, color = if (id == model) colors.brand else colors.ink,
                modifier = Modifier.fillMaxWidth().clickable { model = id; message = null }.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }
    }
    message?.let { Text(it, style = Type.small, color = colors.fail, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) }
    AppButton(button, {
        val config = AiConfig(baseUrl.trim().trimEnd('/'), model.trim(), key.trim())
        if (!config.isComplete) {
            message = "Fill in the key and the model."
            return@AppButton
        }
        busy = true
        message = null
        scope.launch {
            try {
                val available = app.chatModel.listModels(config)
                if (available.isNotEmpty() && config.model !in available) {
                    models = available
                    message = "The key works, but \"${config.model}\" is not one of its models."
                } else {
                    app.aiSettings.save(config)
                    onSaved()
                }
            } catch (e: Exception) {
                message = e.message ?: "Could not reach ${config.baseUrl}"
            } finally {
                busy = false
            }
        }
    }, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), busy = busy)
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = appInstance()
    val context = LocalContext.current
    val theme by app.uiSettings.theme.collectAsStateWithLifecycle()
    val places by remember { app.store.observePlaces() }.collectAsStateWithLifecycle(emptyList())
    var checks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose { }
    }
    var savedNote by remember { mutableStateOf(false) }
    val colors = App.colors

    Column(
        Modifier.fillMaxSize().background(colors.bg).statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(bottom = 24.dp)
    ) {
        TopBar("Settings", onBack)

        SectionHeader("Look")
        Segmented(listOf("System", "Light", "Dark"), theme.ordinal, { app.uiSettings.setTheme(ThemeMode.entries[it]) })

        SectionHeader("AI model")
        AiForm(button = if (savedNote) "Saved ✓" else "Test and save", onSaved = { savedNote = true })

        SectionHeader("Places", places.size.toString())
        PlacesCard(app, places)

        SectionHeader("Keep running in the background")
        Card {
            val unrestricted = remember(checks) { Requirements.isGranted(context, Requirement.LISTENER_SERVICE) }
            SettingRow(
                "No battery restrictions",
                "Without it, the phone closes Automate and triggers stop.",
                if (unrestricted) null else "Allow"
            ) { Requirements.settingsIntent(context, Requirement.LISTENER_SERVICE)?.let(context::startActivity) }
            HorizontalDivider(color = colors.line)
            SettingRow(
                "Autostart",
                "iQOO: allow Automate to start in the background, or triggers stop after a reboot.",
                "Open"
            ) { openAutostart(context) }
        }
    }
}

@Composable
private fun SettingRow(title: String, detail: String, action: String?, onAction: () -> Unit) {
    val colors = App.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = Type.body, color = colors.ink)
            Text(detail, style = Type.tiny, color = colors.muted)
        }
        if (action == null) {
            Text("✓ Done", style = Type.smallStrong, color = colors.ok)
        } else {
            Text(
                action, style = Type.smallStrong, color = colors.onBrand,
                modifier = Modifier.background(colors.brand, RoundedCornerShape(12.dp)).clickable(onClick = onAction)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun PlacesCard(app: AutomateApp, places: List<PlaceEntity>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val colors = App.colors

    fun addHere() {
        val label = name.trim()
        if (label.isEmpty()) {
            error = "Give the place a name first, for example Home."
            return
        }
        busy = true
        error = null
        scope.launch {
            try {
                val location = LocationServices.getFusedLocationProviderClient(context)
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                if (location == null) {
                    error = "Could not get a location fix. Try again outside or with Wi-Fi on."
                } else {
                    app.store.savePlace(PlaceEntity(label, location.latitude, location.longitude))
                    Triggers.sync(app)
                    name = ""
                }
            } catch (e: Exception) {
                error = e.message ?: "Location failed"
            } finally {
                busy = false
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) addHere() else error = "Precise location is needed to save a place."
    }

    Card {
        places.forEach { place ->
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(place.name, style = Type.bodyStrong, color = colors.ink)
                    Text("%.5f, %.5f · %d m".format(place.latitude, place.longitude, place.radiusM), style = Type.tiny, color = colors.muted)
                }
                Text(
                    "Remove", style = Type.smallStrong, color = colors.fail,
                    modifier = Modifier.clickable { scope.launch { app.store.deletePlace(place.name); Triggers.sync(app) } }.padding(12.dp)
                )
            }
            HorizontalDivider(color = colors.line)
        }
        Text(
            "Save where you are now, then use the name in a flow: \"when I arrive at Home\".",
            style = Type.tiny, color = colors.muted, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
        )
        Box(Modifier.padding(vertical = 4.dp)) { Field("Name", name, { name = it }, placeholder = "Home") }
        error?.let { Text(it, style = Type.small, color = colors.fail, modifier = Modifier.padding(horizontal = 16.dp)) }
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton("Save current location", {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) addHere()
                else launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }, Modifier.fillMaxWidth(), kind = ButtonKind.QUIET, busy = busy)
        }
    }
}

/** Vivo/iQOO hide autostart in their own security app; the screen name changed between versions. */
private fun openAutostart(context: Context) {
    val candidates = listOf(
        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
    )
    for (component in candidates) {
        val intent = Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
}
