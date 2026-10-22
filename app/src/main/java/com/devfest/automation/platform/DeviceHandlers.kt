package com.devfest.automation.platform

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.devfest.automation.R
import com.devfest.core.catalog.Requirement
import com.devfest.core.engine.StepHandler
import com.devfest.core.engine.StepOutcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/** Handlers for things the phone does by itself, without network or other apps. */
class DeviceHandlers(private val context: Context) {

    fun create(): Map<String, StepHandler> = mapOf(
        "notify" to StepHandler { step, params, _ ->
            needs(Requirement.NOTIFICATIONS)?.let { return@StepHandler it }
            Notifications.ensureChannels(context)
            val notification = NotificationCompat.Builder(context, Notifications.FLOWS)
                .setSmallIcon(R.drawable.ic_stat_flow)
                .setContentTitle(params["title"])
                .setContentText(params["message"])
                .setStyle(NotificationCompat.BigTextStyle().bigText(params["message"]))
                .setAutoCancel(true)
                .build()
            @SuppressLint("MissingPermission")
            NotificationManagerCompat.from(context).notify(Notifications.nextId(), notification)
            StepOutcome.Done("Shown")
        },
        "speak" to StepHandler { _, params, _ -> speak(params.getValue("text")) },
        "vibrate" to StepHandler { _, params, _ ->
            val effect = when (params["pattern"]) {
                "long" -> VibrationEffect.createOneShot(1200, VibrationEffect.DEFAULT_AMPLITUDE)
                "pulse" -> VibrationEffect.createWaveform(longArrayOf(0, 120, 120, 120, 120, 120), -1)
                else -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            }
            context.getSystemService(VibratorManager::class.java).vibrate(CombinedVibration.createParallel(effect))
            StepOutcome.Done()
        },
        "flashlight" to StepHandler { _, params, _ -> flashlight(params.getValue("mode"), params.getValue("seconds").toInt()) },
        "play_sound" to StepHandler { _, params, _ ->
            val type = when (params["sound"]) {
                "alarm" -> RingtoneManager.TYPE_ALARM
                "ringtone" -> RingtoneManager.TYPE_RINGTONE
                else -> RingtoneManager.TYPE_NOTIFICATION
            }
            val ringtone = RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(type))
                ?: return@StepHandler StepOutcome.Failed("No default ${params["sound"]} sound is set")
            ringtone.play()
            // Alarm and ringtone sounds loop; stop them after a few seconds.
            if (type != RingtoneManager.TYPE_NOTIFICATION) {
                delay(6_000)
                ringtone.stop()
            }
            StepOutcome.Done("Played ${params["sound"]}")
        },
        "ringer_mode" to StepHandler { _, params, _ ->
            needs(Requirement.DND_ACCESS)?.let { return@StepHandler it }
            audio.ringerMode = when (params["mode"]) {
                "silent" -> AudioManager.RINGER_MODE_SILENT
                "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
                else -> AudioManager.RINGER_MODE_NORMAL
            }
            StepOutcome.Done("Ringer ${params["mode"]}")
        },
        "do_not_disturb" to StepHandler { _, params, _ ->
            needs(Requirement.DND_ACCESS)?.let { return@StepHandler it }
            val on = params["on"] == "true"
            context.getSystemService(NotificationManager::class.java).setInterruptionFilter(
                if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL
            )
            StepOutcome.Done(if (on) "Do Not Disturb on" else "Do Not Disturb off")
        },
        "volume" to StepHandler { _, params, _ ->
            val stream = when (params["stream"]) {
                "ring" -> AudioManager.STREAM_RING
                "alarm" -> AudioManager.STREAM_ALARM
                "notification" -> AudioManager.STREAM_NOTIFICATION
                else -> AudioManager.STREAM_MUSIC
            }
            val percent = params.getValue("percent").toInt()
            val max = audio.getStreamMaxVolume(stream)
            try {
                audio.setStreamVolume(stream, (max * percent / 100.0).roundToInt(), 0)
            } catch (_: SecurityException) {
                return@StepHandler StepOutcome.Failed("Changing this volume needs Do Not Disturb access")
            }
            StepOutcome.Done("${params["stream"]} volume $percent%")
        },
        "brightness" to StepHandler { _, params, _ ->
            needs(Requirement.WRITE_SETTINGS)?.let { return@StepHandler it }
            val percent = params.getValue("percent").toInt()
            val resolver = context.contentResolver
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, (255 * percent / 100.0).roundToInt().coerceIn(1, 255))
            StepOutcome.Done("Brightness $percent%")
        },
        "copy_text" to StepHandler { _, params, _ ->
            context.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("Automate", params.getValue("text")))
            StepOutcome.Done("Copied")
        },
        "battery_between" to StepHandler { _, params, _ ->
            val level = batteryLevel()
            val min = params.getValue("min").toInt()
            val max = params.getValue("max").toInt()
            StepOutcome.Check(level in min..max, "Battery $level%")
        },
        "is_charging" to StepHandler { _, params, _ ->
            val charging = context.getSystemService(BatteryManager::class.java).isCharging
            StepOutcome.Check(charging == (params["charging"] != "false"), if (charging) "Charging" else "Not charging")
        },
        "wifi_is" to StepHandler { _, params, _ ->
            val ssid = currentSsid()
            val wanted = params["ssid"].orEmpty()
            val passed = ssid != null && (wanted.isEmpty() || ssid.equals(wanted, ignoreCase = true))
            StepOutcome.Check(passed, ssid?.let { "On $it" } ?: "Not on Wi-Fi")
        }
    )

    private val audio get() = context.getSystemService(AudioManager::class.java)

    private fun needs(requirement: Requirement): StepOutcome.Failed? =
        if (Requirements.isGranted(context, requirement)) null
        else StepOutcome.Failed("Needs \"${Requirements.label(requirement)}\"")

    fun batteryLevel(): Int =
        context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    /** Null when not on Wi-Fi. "unknown" when connected but location is off, so the name is hidden. */
    @Suppress("DEPRECATION")
    fun currentSsid(): String? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        val raw = context.getSystemService(WifiManager::class.java).connectionInfo?.ssid
        return raw?.trim('"')?.takeIf { it.isNotEmpty() && it != WifiManager.UNKNOWN_SSID.trim('<', '>') && it != "<unknown ssid>" }
            ?: "unknown"
    }

    private suspend fun speak(text: String): StepOutcome {
        var engine: TextToSpeech? = null
        try {
            val ready = suspendCancellableCoroutine { cont ->
                engine = TextToSpeech(context) { status -> if (cont.isActive) cont.resume(status == TextToSpeech.SUCCESS) }
            }
            val tts = engine ?: return StepOutcome.Failed("Text-to-speech is not available")
            if (!ready) return StepOutcome.Failed("Text-to-speech is not available")
            suspendCancellableCoroutine { cont ->
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { if (cont.isActive) cont.resume(Unit) }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { if (cont.isActive) cont.resume(Unit) }
                })
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "flow")
                cont.invokeOnCancellation { tts.stop() }
            }
            return StepOutcome.Done("Spoke ${text.length} characters")
        } finally {
            engine?.shutdown()
        }
    }

    private suspend fun flashlight(mode: String, seconds: Int): StepOutcome {
        val cameras = context.getSystemService(CameraManager::class.java)
        val id = cameras.cameraIdList.firstOrNull {
            cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return StepOutcome.Failed("This phone has no flashlight")
        when (mode) {
            "on" -> cameras.setTorchMode(id, true)
            "off" -> cameras.setTorchMode(id, false)
            else -> try {
                repeat(seconds * 2) {
                    cameras.setTorchMode(id, true)
                    delay(250)
                    cameras.setTorchMode(id, false)
                    delay(250)
                }
            } finally {
                runCatching { cameras.setTorchMode(id, false) }
            }
        }
        return StepOutcome.Done("Flashlight $mode")
    }
}
