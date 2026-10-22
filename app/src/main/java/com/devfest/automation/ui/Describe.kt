package com.devfest.automation.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.devfest.core.catalog.Blocks
import com.devfest.core.model.Step

/** A step in words: [title] is the line people read, [detail] the quoted text or URL under it. */
data class StepText(val title: String, val detail: String? = null)

fun icon(type: String): ImageVector = when (type) {
    "manual" -> Icons.Rounded.PlayArrow
    "schedule" -> Icons.Rounded.Schedule
    "location" -> Icons.Rounded.Place
    "unlock_failed" -> Icons.Rounded.Lock
    "sms_received" -> Icons.Rounded.Sms
    "notification_received" -> Icons.Rounded.NotificationsActive
    "charger" -> Icons.Rounded.BatteryChargingFull
    "battery_low" -> Icons.Rounded.BatteryAlert
    "headphones" -> Icons.Rounded.Headphones
    "wifi", "wifi_is" -> Icons.Rounded.Wifi
    "time_between" -> Icons.Rounded.AccessTime
    "day_is" -> Icons.Rounded.CalendarToday
    "battery_between", "is_charging" -> Icons.Rounded.BatteryStd
    "text_contains" -> Icons.Rounded.TextFields
    "notify" -> Icons.Rounded.Notifications
    "speak" -> Icons.Rounded.RecordVoiceOver
    "vibrate" -> Icons.Rounded.Vibration
    "flashlight" -> Icons.Rounded.FlashlightOn
    "play_sound" -> Icons.Rounded.MusicNote
    "ringer_mode", "volume" -> Icons.AutoMirrored.Rounded.VolumeUp
    "do_not_disturb" -> Icons.Rounded.DoNotDisturbOn
    "brightness" -> Icons.Rounded.BrightnessMedium
    "send_sms" -> Icons.AutoMirrored.Rounded.Message
    "http_request" -> Icons.Rounded.Public
    "take_photo" -> Icons.Rounded.PhotoCamera
    "get_location" -> Icons.Rounded.MyLocation
    "open_app" -> Icons.AutoMirrored.Rounded.OpenInNew
    "open_url" -> Icons.Rounded.Link
    "set_alarm" -> Icons.Rounded.Alarm
    "copy_text" -> Icons.Rounded.ContentCopy
    "ask_ai" -> Icons.Rounded.AutoAwesome
    "wait" -> Icons.Rounded.HourglassEmpty
    else -> Icons.Rounded.AutoAwesome
}

fun describe(step: Step): StepText {
    val p = step.params
    fun on(event: String?, yes: String, no: String) = if (event == "disconnected" || event == "exit") no else yes
    return when (step.type) {
        "manual" -> StepText("I tap Run")
        "schedule" -> StepText("${days(p["days"])} at ${p["time"]}")
        "location" -> StepText(on(p["event"], "Arrive at ${p["place"]}", "Leave ${p["place"]}"))
        "unlock_failed" -> {
            val n = p["attempts"]?.toIntOrNull() ?: 1
            StepText(if (n == 1) "Wrong unlock" else "Wrong unlock, $n attempts")
        }
        "sms_received" -> StepText(
            "SMS" + (p["from"]?.let { " from $it" } ?: "") + (p["contains"]?.let { " with \"$it\"" } ?: "")
        )
        "notification_received" -> StepText(
            (p["app"]?.let { "$it notification" } ?: "Any notification") + (p["contains"]?.let { " with \"$it\"" } ?: "")
        )
        "charger" -> StepText(on(p["event"], "Charger plugged in", "Charger unplugged"))
        "battery_low" -> StepText("Battery below ${p["below"]}%")
        "headphones" -> StepText(on(p["event"], "Headphones connected", "Headphones disconnected"))
        "wifi" -> StepText(on(p["event"], "Joins ", "Leaves ") + (p["ssid"]?.let { "\"$it\"" } ?: "any Wi-Fi"))

        "time_between" -> StepText("${p["start"]} to ${p["end"]}")
        "day_is" -> StepText(days(p["days"]))
        "battery_between" -> StepText("Battery ${p["min"]}–${p["max"]}%")
        "is_charging" -> StepText(if (p["charging"] == "false") "Not charging" else "Charging")
        "wifi_is" -> StepText("On " + (p["ssid"]?.let { "\"$it\"" } ?: "any Wi-Fi"))
        "text_contains" -> StepText("${pretty(p["text"])} has \"${p["contains"]}\"")

        "notify" -> StepText("Notify: ${pretty(p["title"])}", p["message"]?.let(::pretty))
        "speak" -> StepText("Say out loud", quote(p["text"]))
        "vibrate" -> StepText("Vibrate, ${p["pattern"]}")
        "flashlight" -> StepText(
            when (p["mode"]) {
                "on" -> "Flashlight on"
                "off" -> "Flashlight off"
                else -> "Blink flashlight"
            },
            if (p["mode"] == "blink" || p["mode"] == null) "${p["seconds"] ?: 5} seconds" else null
        )
        "play_sound" -> StepText("Play ${p["sound"]} sound")
        "ringer_mode" -> StepText("Ringer to ${p["mode"]}")
        "do_not_disturb" -> StepText(if (p["on"] == "false") "Do Not Disturb off" else "Do Not Disturb on")
        "volume" -> StepText("${p["stream"]?.replaceFirstChar(Char::uppercase)} volume ${p["percent"]}%")
        "brightness" -> StepText("Brightness ${p["percent"]}%")
        "send_sms" -> StepText("Text ${p["to"]}", quote(p["message"]))
        "http_request" -> StepText("Web request", "${p["method"] ?: "POST"} ${p["url"]}")
        "take_photo" -> StepText("Take ${p["camera"] ?: "front"} photo")
        "get_location" -> StepText("Get location")
        "open_app" -> StepText("Open ${p["app"]}")
        "open_url" -> StepText("Open link", p["url"])
        "set_alarm" -> StepText("Alarm at ${p["time"]}", p["label"])
        "copy_text" -> StepText("Copy to clipboard", quote(p["text"]))
        "ask_ai" -> StepText("Ask AI", quote(p["prompt"]))
        "wait" -> StepText("Wait ${duration(p["seconds"])}")
        else -> StepText(Blocks[step.type]?.title ?: step.type)
    }
}

/** Phrase for the trigger in Home rows and titles, for example "Weekdays at 23:00". */
fun days(value: String?): String = when (val v = value?.lowercase()?.trim()) {
    null, "", "daily" -> "Every day"
    "weekdays" -> "Weekdays"
    "weekends" -> "Weekends"
    else -> v.split(",").joinToString(", ") { it.trim().replaceFirstChar(Char::uppercase) }
}

private fun duration(seconds: String?): String {
    val s = seconds?.toIntOrNull() ?: return "a moment"
    return if (s >= 60 && s % 60 == 0) "${s / 60} min" else "$s s"
}

private val VARIABLE = Regex("""\{\{\s*([a-zA-Z0-9_.]+)\s*\}\}""")

private val VARIABLE_NAMES = mapOf(
    "sms.from" to "sender", "sms.body" to "SMS text", "notification.app" to "app", "notification.title" to "title",
    "notification.text" to "notification text", "battery.level" to "battery %", "wifi.ssid" to "Wi-Fi name",
    "http.status" to "status", "http.body" to "response", "photo.path" to "photo", "location.lat" to "latitude",
    "location.lng" to "longitude", "location.maps_url" to "map link", "ai.reply" to "AI answer",
    "now.time" to "time", "now.date" to "date", "now.day" to "day"
)

/** `{{location.maps_url}}` reads as `[map link]`. */
fun pretty(text: String?): String =
    text.orEmpty().replace(VARIABLE) { "[" + (VARIABLE_NAMES[it.groupValues[1]] ?: it.groupValues[1]) + "]" }

private fun quote(text: String?): String? = text?.let { "\"${pretty(it)}\"" }
