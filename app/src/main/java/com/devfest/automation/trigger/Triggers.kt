package com.devfest.automation.trigger

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.devfest.automation.AutomateApp
import com.devfest.automation.platform.Requirements
import com.devfest.automation.store.SavedFlow
import com.devfest.core.catalog.Requirement
import com.devfest.core.trigger.TriggerRules
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.time.ZonedDateTime

/**
 * Keeps Android registrations (alarms, geofences, the listener service) in line with the enabled
 * flows. Call [sync] after any flow is enabled, disabled, edited or deleted, and after boot.
 */
object Triggers {

    /** Trigger types the [ListenerService] watches. */
    val LISTENER_TYPES = setOf("charger", "battery_low", "headphones", "wifi")

    private const val TAG = "Triggers"
    private const val PREFS = "triggers"
    private const val KEY_ALARMS = "alarm_flow_ids"
    private val lock = Mutex()

    suspend fun sync(context: Context) = lock.withLock {
        val app = context.applicationContext as AutomateApp
        val enabled = app.store.enabled()
        syncAlarms(app, enabled.filter { it.automation?.trigger?.type == "schedule" })
        syncGeofences(app, enabled.filter { it.automation?.trigger?.type == "location" })
        ListenerService.update(app, enabled.any { it.automation?.trigger?.type in LISTENER_TYPES })
    }

    /** Runs every enabled flow with [type] whose trigger params pass [matches]. */
    suspend fun fire(
        context: Context,
        type: String,
        source: String,
        outputs: Map<String, String> = emptyMap(),
        matches: (Map<String, String>) -> Boolean = { true }
    ) {
        val app = context.applicationContext as AutomateApp
        app.store.enabledWithTrigger(type)
            .filter { flow -> flow.automation?.let { matches(it.trigger.params) } == true }
            .forEach { app.runner.run(it.id, source, outputs) }
    }

    // Schedules

    private fun syncAlarms(app: AutomateApp, flows: List<SavedFlow>) {
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getStringSet(KEY_ALARMS, emptySet()).orEmpty()
        val current = flows.map { it.id }.toSet()
        (previous - current).forEach { cancelAlarm(app, it) }
        flows.forEach { scheduleNext(app, it) }
        prefs.edit().putStringSet(KEY_ALARMS, current).apply()
    }

    fun scheduleNext(context: Context, flow: SavedFlow) {
        val params = flow.automation?.trigger?.params ?: return
        val next = TriggerRules.nextSchedule(params, ZonedDateTime.now()) ?: return
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (!alarms.canScheduleExactAlarms()) {
            Log.w(TAG, "Exact alarms not allowed; ${flow.title} will not fire")
            return
        }
        // setAlarmClock is the most reliable on Vivo: it is exempt from Doze and battery limits.
        alarms.setAlarmClock(AlarmManager.AlarmClockInfo(next.toInstant().toEpochMilli(), null), alarmIntent(context, flow.id))
    }

    private fun cancelAlarm(context: Context, flowId: String) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context, flowId))
    }

    private fun alarmIntent(context: Context, flowId: String): PendingIntent = PendingIntent.getBroadcast(
        context, flowId.hashCode(),
        Intent(context, TriggerReceiver::class.java).setAction(TriggerReceiver.ACTION_SCHEDULE).putExtra(TriggerReceiver.EXTRA_FLOW, flowId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    // Places

    @SuppressLint("MissingPermission")
    private suspend fun syncGeofences(app: AutomateApp, flows: List<SavedFlow>) {
        val client = LocationServices.getGeofencingClient(app)
        val intent = geofenceIntent(app)
        runCatching { client.removeGeofences(intent).await() }
        if (flows.isEmpty()) return
        if (Requirements.missing(app, listOf(Requirement.LOCATION, Requirement.BACKGROUND_LOCATION)).isNotEmpty()) {
            Log.w(TAG, "Background location missing; place triggers are off")
            return
        }
        val places = app.store.places().associateBy { it.name.lowercase() }
        val fences = flows.mapNotNull { flow ->
            val params = flow.automation?.trigger?.params ?: return@mapNotNull null
            val place = places[params["place"].orEmpty().lowercase()] ?: return@mapNotNull null
            Geofence.Builder()
                .setRequestId(flow.id)
                .setCircularRegion(place.latitude, place.longitude, (params["radius_m"]?.toFloatOrNull() ?: place.radiusM.toFloat()))
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .build()
        }
        if (fences.isEmpty()) return
        val request = GeofencingRequest.Builder().setInitialTrigger(0).addGeofences(fences).build()
        runCatching { client.addGeofences(request, intent).await() }
            .onFailure { Log.w(TAG, "Could not add geofences", it) }
    }

    private fun geofenceIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 1,
        Intent(context, TriggerReceiver::class.java).setAction(TriggerReceiver.ACTION_GEOFENCE),
        // Play services fills in the transition, so this one must be mutable.
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}

/** Runs [block] on the app scope while keeping the broadcast alive until it finishes. */
fun BroadcastReceiver.handleAsync(context: Context, block: suspend () -> Unit) {
    val pending = goAsync()
    (context.applicationContext as AutomateApp).scope.launch {
        try {
            block()
        } catch (e: Exception) {
            Log.e("TriggerReceiver", "Trigger handling failed", e)
        } finally {
            pending.finish()
        }
    }
}

internal fun startForegroundSafely(context: Context, intent: Intent) {
    try {
        ContextCompat.startForegroundService(context, intent)
    } catch (e: IllegalStateException) {
        // Android refuses foreground service starts from some background states; the next sync retries.
        Log.w("Triggers", "Could not start ${intent.component?.shortClassName}", e)
    }
}
