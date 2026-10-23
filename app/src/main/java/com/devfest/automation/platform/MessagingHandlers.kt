package com.devfest.automation.platform

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.devfest.automation.ai.OpenAiChatModel
import com.devfest.core.agent.ChatMessage
import com.devfest.core.catalog.Requirement
import com.devfest.core.catalog.Values
import com.devfest.core.engine.StepHandler
import com.devfest.core.engine.StepOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** SMS, web requests, the AI, and handing off to other apps. */
class MessagingHandlers(
    private val context: Context,
    private val ai: OpenAiChatModel,
    private val http: OkHttpClient = defaultHttp
) {

    fun create(): Map<String, StepHandler> = mapOf(
        "send_sms" to StepHandler { _, params, _ ->
            needs(Requirement.SEND_SMS)?.let { return@StepHandler it }
            val to = Values.phone(params.getValue("to"))
                ?: return@StepHandler StepOutcome.Failed("\"${params["to"]}\" is not a phone number")
            sendSms(to, params.getValue("message"))
        },
        "http_request" to StepHandler { _, params, _ -> httpRequest(params) },
        "ask_ai" to StepHandler { _, params, _ ->
            val reply = ai.complete(
                listOf(
                    ChatMessage(ChatMessage.Role.SYSTEM, "You run inside a phone automation. Answer in plain text, under 80 words, no markdown."),
                    ChatMessage(ChatMessage.Role.USER, params.getValue("prompt"))
                ),
                json = false
            ).trim()
            StepOutcome.Done(reply.take(120), mapOf("ai.reply" to reply))
        },
        "open_app" to StepHandler { _, params, _ ->
            val name = params.getValue("app")
            val intent = Apps.find(context, name)?.let { context.packageManager.getLaunchIntentForPackage(it.packageName) }
                ?: return@StepHandler StepOutcome.Failed("No installed app called \"$name\"")
            launch(intent, "Opened $name")
        },
        "open_url" to StepHandler { _, params, _ ->
            launch(Intent(Intent.ACTION_VIEW, Uri.parse(params.getValue("url"))), "Opened link")
        },
        "set_alarm" to StepHandler { _, params, _ ->
            val time = Values.time(params.getValue("time"))!!
            val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, time.hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, time.minute)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .apply { params["label"]?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) } }
            launch(intent, "Alarm set for ${params["time"]}")
        }
    )

    private fun needs(requirement: Requirement): StepOutcome.Failed? =
        if (Requirements.isGranted(context, requirement)) null
        else StepOutcome.Failed("Needs \"${Requirements.label(requirement)}\"")

    /**
     * Android 14 silently ignores activity starts from the background unless the app may draw
     * over other apps. Fail clearly instead of reporting a launch that never happened.
     */
    private fun launch(intent: Intent, done: String): StepOutcome {
        val foreground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (!foreground && !Requirements.isGranted(context, Requirement.OVERLAY)) {
            return StepOutcome.Failed("Needs \"${Requirements.label(Requirement.OVERLAY)}\" to open apps from the background")
        }
        if (intent.resolveActivity(context.packageManager) == null) return StepOutcome.Failed("No app can handle this")
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return StepOutcome.Done(done)
    }

    private suspend fun sendSms(to: String, message: String): StepOutcome {
        val sms = context.getSystemService(SmsManager::class.java)
        val parts = sms.divideMessage(message)
        val action = "${context.packageName}.SMS_SENT.${requestCodes.incrementAndGet()}"
        val results = mutableListOf<Int>()
        val done = CompletableDeferred<List<Int>>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                results += resultCode
                if (results.size == parts.size) done.complete(results.toList())
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        val outcome = try {
            val sent = ArrayList(parts.map {
                PendingIntent.getBroadcast(
                    context, requestCodes.incrementAndGet(),
                    Intent(action).setPackage(context.packageName),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
                )
            })
            sms.sendMultipartTextMessage(to, null, parts, sent, null)
            withTimeoutOrNull(30_000) { done.await() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return StepOutcome.Failed("The phone refused to send the SMS: ${e.message}")
        } finally {
            context.unregisterReceiver(receiver)
        }
        return when {
            outcome == null -> StepOutcome.Failed("No confirmation from the SIM after 30s; the SMS may not have been sent")
            outcome.all { it == Activity.RESULT_OK } -> StepOutcome.Done("Sent to $to")
            else -> StepOutcome.Failed("SMS failed (code ${outcome.first { it != Activity.RESULT_OK }}). Check signal and SIM.")
        }
    }

    private suspend fun httpRequest(params: Map<String, String>): StepOutcome = withContext(Dispatchers.IO) {
        val method = params.getValue("method")
        val body = params["body"].orEmpty()
        val type = if (body.trimStart().startsWith("{") || body.trimStart().startsWith("[")) "application/json" else "text/plain"
        val request = Request.Builder().url(params.getValue("url")).apply {
            when (method) {
                "GET" -> get()
                else -> method(method, body.toRequestBody("$type; charset=utf-8".toMediaType()))
            }
        }.build()
        http.newCall(request).execute().use { response ->
            val text = response.body.string().take(MAX_BODY)
            val outputs = mapOf("http.status" to response.code.toString(), "http.body" to text)
            if (response.isSuccessful) StepOutcome.Done("HTTP ${response.code}", outputs)
            else StepOutcome.Failed("HTTP ${response.code}: ${text.take(120)}")
        }
    }

    private companion object {
        const val MAX_BODY = 4000
        val requestCodes = AtomicInteger(5000)
        val defaultHttp: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

/** Finds installed launchable apps by label. */
object Apps {
    data class App(val label: String, val packageName: String)

    fun installed(context: Context): List<App> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
            .map { App(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** Exact label first, then package name, then the closest label containing the text. */
    fun find(context: Context, name: String): App? {
        val apps = installed(context)
        val wanted = name.trim()
        return apps.firstOrNull { it.label.equals(wanted, ignoreCase = true) }
            ?: apps.firstOrNull { it.packageName.equals(wanted, ignoreCase = true) }
            ?: apps.filter { it.label.contains(wanted, ignoreCase = true) }.minByOrNull { it.label.length }
    }
}
