package com.devfest.automation.trigger

import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.devfest.automation.AutomateApp
import com.devfest.automation.R
import com.devfest.automation.platform.Notifications
import com.devfest.core.trigger.TriggerRules
import kotlinx.coroutines.launch

/**
 * Watches events that Android only delivers to a running app: charger, battery level,
 * headphones and Wi-Fi. Runs as a quiet foreground service while any enabled flow needs it.
 */
class ListenerService : Service() {

    private val app get() = applicationContext as AutomateApp
    private val main = Handler(Looper.getMainLooper())
    private var batteryRules = mapOf<String, TriggerRules.BatteryLow>()
    private var lastSsid: String? = null
    private var wifiConnected = false
    private var startedAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        val notification = NotificationCompat.Builder(this, Notifications.SERVICE)
            .setSmallIcon(R.drawable.ic_stat_flow)
            .setContentTitle("Watching for triggers")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        ContextCompat.registerReceiver(this, power, IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        getSystemService(AudioManager::class.java).registerAudioDeviceCallback(audio, main)
        getSystemService(ConnectivityManager::class.java).registerNetworkCallback(
            NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
            wifi
        )
        startedAt = System.currentTimeMillis()
        refreshBatteryRules()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        refreshBatteryRules()
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(power)
        getSystemService(AudioManager::class.java).unregisterAudioDeviceCallback(audio)
        runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(wifi) }
        super.onDestroy()
    }

    private fun refreshBatteryRules() = app.scope.launch {
        val flows = app.store.enabledWithTrigger("battery_low")
        val rules = flows.associate { flow ->
            val below = flow.automation?.trigger?.params?.get("below")?.toIntOrNull() ?: 20
            flow.id to (batteryRules[flow.id] ?: TriggerRules.BatteryLow(below))
        }
        main.post { batteryRules = rules }
    }

    private fun fire(type: String, source: String, outputs: Map<String, String> = emptyMap(), matches: (Map<String, String>) -> Boolean) {
        app.scope.launch { Triggers.fire(app, type, source, outputs, matches) }
    }

    private val power = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = app.device.batteryLevel()
            val outputs = mapOf("battery.level" to level.toString())
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> fire("charger", "Charger connected", outputs) { TriggerRules.event(it, "connected") }
                Intent.ACTION_POWER_DISCONNECTED -> fire("charger", "Charger disconnected", outputs) { TriggerRules.event(it, "disconnected") }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val due = batteryRules.filter { (_, rule) -> rule.update(level) }.keys
                    if (due.isNotEmpty()) {
                        app.scope.launch { due.forEach { app.runner.run(it, "Battery $level%", outputs) } }
                    }
                }
            }
        }
    }

    private val audio = object : AudioDeviceCallback() {
        // Android reports the devices already connected right after registering; skip that first call.
        private var ready = false

        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
            if (ready && added.any(::isHeadphones)) fire("headphones", "Headphones connected") { TriggerRules.event(it, "connected") }
            ready = true
        }

        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
            if (removed.any(::isHeadphones)) fire("headphones", "Headphones disconnected") { TriggerRules.event(it, "disconnected") }
        }

        private fun isHeadphones(device: AudioDeviceInfo) = device.isSink && device.type in HEADPHONE_TYPES
    }

    private val wifi = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val ssid = (caps.transportInfo as? WifiInfo)?.ssid?.trim('"')?.takeIf { it != WifiManagerUnknown }
            if (wifiConnected && ssid == lastSsid) return
            wifiConnected = true
            lastSsid = ssid
            // The callback reports the network already connected at registration; that is not an event.
            if (System.currentTimeMillis() - startedAt < STARTUP_GRACE_MS) return
            fire("wifi", "Wi-Fi ${ssid ?: "connected"}", mapOf("wifi.ssid" to ssid.orEmpty())) { TriggerRules.wifi(it, "connected", ssid) }
        }

        override fun onLost(network: Network) {
            val ssid = lastSsid
            wifiConnected = false
            lastSsid = null
            fire("wifi", "Wi-Fi ${ssid ?: ""} lost", mapOf("wifi.ssid" to ssid.orEmpty())) { TriggerRules.wifi(it, "disconnected", ssid) }
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 7
        private const val STARTUP_GRACE_MS = 3_000L
        private const val WifiManagerUnknown = "<unknown ssid>"
        private val HEADPHONE_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET
        )

        fun update(context: Context, needed: Boolean) {
            val intent = Intent(context, ListenerService::class.java)
            if (needed) startForegroundSafely(context, intent) else context.stopService(intent)
        }
    }
}

/** Notification access: reacts to notifications from other apps. */
class NotificationWatcher : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val notification = sbn.notification
        if (notification.flags and (Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_ONGOING_EVENT) != 0) return
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        val app = applicationContext as AutomateApp
        app.scope.launch {
            Triggers.fire(
                app, "notification_received", "Notification from $label",
                mapOf("notification.app" to label, "notification.title" to title, "notification.text" to text)
            ) { TriggerRules.notification(it, label, sbn.packageName, title, text) }
        }
    }
}
