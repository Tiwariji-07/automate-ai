package com.devfest.automation.trigger

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.provider.Telephony
import com.devfest.automation.AutomateApp
import com.devfest.core.trigger.TriggerRules
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

/** Alarms, geofences, boot and clock changes. */
class TriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) = handleAsync(context) {
        val app = context.applicationContext as AutomateApp
        when (intent.action) {
            ACTION_SCHEDULE -> {
                val flowId = intent.getStringExtra(EXTRA_FLOW) ?: return@handleAsync
                val flow = app.store.get(flowId) ?: return@handleAsync
                if (flow.status == com.devfest.automation.store.FlowStatus.ENABLED) {
                    app.runner.run(flow.id, "schedule ${flow.automation?.trigger?.params?.get("time").orEmpty()}")
                    Triggers.scheduleNext(app, flow)
                }
            }
            ACTION_GEOFENCE -> {
                val event = GeofencingEvent.fromIntent(intent) ?: return@handleAsync
                if (event.hasError()) return@handleAsync
                val happened = when (event.geofenceTransition) {
                    Geofence.GEOFENCE_TRANSITION_ENTER -> "enter"
                    Geofence.GEOFENCE_TRANSITION_EXIT -> "exit"
                    else -> return@handleAsync
                }
                val ids = event.triggeringGeofences.orEmpty().map { it.requestId }.toSet()
                for (flow in app.store.enabledWithTrigger("location").filter { it.id in ids }) {
                    val params = flow.automation?.trigger?.params ?: continue
                    if (TriggerRules.event(params, happened)) app.runner.run(flow.id, "$happened ${params["place"]}")
                }
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" -> Triggers.sync(app)
        }
    }

    companion object {
        const val ACTION_SCHEDULE = "com.devfest.automation.SCHEDULE"
        const val ACTION_GEOFENCE = "com.devfest.automation.GEOFENCE"
        const val EXTRA_FLOW = "flow"
    }
}

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent).orEmpty()
        val from = messages.firstOrNull()?.displayOriginatingAddress ?: return
        // Long messages arrive in parts.
        val body = messages.joinToString("") { it.displayMessageBody.orEmpty() }
        handleAsync(context) {
            Triggers.fire(context, "sms_received", "SMS from $from", mapOf("sms.from" to from, "sms.body" to body)) {
                TriggerRules.sms(it, from, body)
            }
        }
    }
}

/** Device admin: told about failed unlock attempts. */
class UnlockWatcher : DeviceAdminReceiver() {
    override fun onPasswordFailed(context: Context, intent: Intent, user: UserHandle) {
        val attempts = context.getSystemService(DevicePolicyManager::class.java).currentFailedPasswordAttempts
        handleAsync(context) {
            // Fires once, when the count reaches the flow's threshold.
            Triggers.fire(context, "unlock_failed", "Wrong unlock ($attempts)") {
                (it["attempts"]?.toIntOrNull() ?: 1) == attempts
            }
        }
    }
}
