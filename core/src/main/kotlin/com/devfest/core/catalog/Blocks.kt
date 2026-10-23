package com.devfest.core.catalog

import com.devfest.core.model.Automation
import com.devfest.core.model.forEachStep

enum class Category { TRIGGER, CONDITION, ACTION }

enum class ParamType { TEXT, INT, NUMBER, BOOL, TIME, DAYS, CHOICE, PHONE, URL, PLACE, APP }

/** Things the phone must grant before a block can run. The app maps each to a permission or settings screen. */
enum class Requirement {
    NOTIFICATIONS,
    SEND_SMS,
    RECEIVE_SMS,
    CAMERA,
    LOCATION,
    BACKGROUND_LOCATION,
    EXACT_ALARMS,
    DEVICE_ADMIN,
    NOTIFICATION_ACCESS,
    DND_ACCESS,
    WRITE_SETTINGS,
    OVERLAY,
    LISTENER_SERVICE,
    AI_KEY
}

data class ParamSpec(
    val name: String,
    val type: ParamType,
    val description: String,
    val required: Boolean = true,
    val default: String? = null,
    val choices: List<String> = emptyList(),
    val min: Int? = null,
    val max: Int? = null
)

data class BlockSpec(
    val type: String,
    val category: Category,
    val title: String,
    val description: String,
    val params: List<ParamSpec> = emptyList(),
    /** Template variables this block sets, usable later as `{{name}}`. */
    val outputs: List<String> = emptyList(),
    val requires: Set<Requirement> = emptySet(),
    /** Sends data off the phone or records the owner. The review screen shows these params in full. */
    val sensitive: Boolean = false,
    val maxSeconds: Int = 60
) {
    fun param(name: String): ParamSpec? = params.firstOrNull { it.name == name }
}

private fun text(name: String, description: String, required: Boolean = true, default: String? = null) =
    ParamSpec(name, ParamType.TEXT, description, required, default)

private fun choice(name: String, description: String, choices: List<String>, default: String? = null) =
    ParamSpec(name, ParamType.CHOICE, description, required = default == null, default = default, choices = choices)

private fun int(name: String, description: String, min: Int, max: Int, default: String? = null) =
    ParamSpec(name, ParamType.INT, description, required = default == null, default = default, min = min, max = max)

object Blocks {

    val all: List<BlockSpec> = listOf(
        // Triggers
        BlockSpec(
            "manual", Category.TRIGGER, "Run button",
            "Runs when the owner taps Run, a Quick Settings tile or a widget."
        ),
        BlockSpec(
            "schedule", Category.TRIGGER, "At a time",
            "Runs at a clock time on chosen days.",
            params = listOf(
                ParamSpec("time", ParamType.TIME, "24h time, HH:mm"),
                ParamSpec("days", ParamType.DAYS, "daily, weekdays, weekends or a list like mon,wed,fri", required = false, default = "daily")
            ),
            requires = setOf(Requirement.EXACT_ALARMS)
        ),
        BlockSpec(
            "location", Category.TRIGGER, "Arrive or leave a place",
            "Runs when the phone enters or leaves a saved place.",
            params = listOf(
                ParamSpec("place", ParamType.PLACE, "name of a saved place"),
                choice("event", "enter or exit", listOf("enter", "exit"), default = "enter"),
                int("radius_m", "radius in meters", 100, 5000, default = "200")
            ),
            requires = setOf(Requirement.LOCATION, Requirement.BACKGROUND_LOCATION)
        ),
        BlockSpec(
            "unlock_failed", Category.TRIGGER, "Wrong unlock",
            "Runs when someone enters a wrong PIN, pattern or password.",
            params = listOf(int("attempts", "failed attempts in a row before running", 1, 10, default = "1")),
            requires = setOf(Requirement.DEVICE_ADMIN)
        ),
        BlockSpec(
            "sms_received", Category.TRIGGER, "SMS received",
            "Runs when a text message arrives.",
            params = listOf(
                text("from", "only from this number or sender name", required = false),
                text("contains", "only if the message contains this text", required = false)
            ),
            outputs = listOf("sms.from", "sms.body"),
            requires = setOf(Requirement.RECEIVE_SMS)
        ),
        BlockSpec(
            "notification_received", Category.TRIGGER, "Notification posted",
            "Runs when another app posts a notification.",
            params = listOf(
                ParamSpec("app", ParamType.APP, "app name, for example WhatsApp", required = false),
                text("contains", "only if the title or text contains this", required = false)
            ),
            outputs = listOf("notification.app", "notification.title", "notification.text"),
            requires = setOf(Requirement.NOTIFICATION_ACCESS)
        ),
        BlockSpec(
            "charger", Category.TRIGGER, "Charger plugged or unplugged",
            "Runs when the charger is connected or disconnected.",
            params = listOf(choice("event", "connected or disconnected", listOf("connected", "disconnected"), default = "connected")),
            outputs = listOf("battery.level"),
            requires = setOf(Requirement.LISTENER_SERVICE)
        ),
        BlockSpec(
            "battery_low", Category.TRIGGER, "Battery drops below",
            "Runs once when the battery falls below a level.",
            params = listOf(int("below", "battery percent", 1, 99, default = "20")),
            outputs = listOf("battery.level"),
            requires = setOf(Requirement.LISTENER_SERVICE)
        ),
        BlockSpec(
            "headphones", Category.TRIGGER, "Headphones plugged or unplugged",
            "Runs when wired or Bluetooth headphones connect or disconnect.",
            params = listOf(choice("event", "connected or disconnected", listOf("connected", "disconnected"), default = "connected")),
            requires = setOf(Requirement.LISTENER_SERVICE)
        ),
        BlockSpec(
            "wifi", Category.TRIGGER, "Wi-Fi connects or disconnects",
            "Runs when the phone joins or leaves a Wi-Fi network.",
            params = listOf(
                text("ssid", "network name; empty means any network", required = false),
                choice("event", "connected or disconnected", listOf("connected", "disconnected"), default = "connected")
            ),
            outputs = listOf("wifi.ssid"),
            requires = setOf(Requirement.LOCATION, Requirement.BACKGROUND_LOCATION)
        ),

        // Conditions
        BlockSpec(
            "time_between", Category.CONDITION, "Time is between",
            "Passes when the current time is inside the window. Windows may cross midnight.",
            params = listOf(
                ParamSpec("start", ParamType.TIME, "HH:mm"),
                ParamSpec("end", ParamType.TIME, "HH:mm")
            )
        ),
        BlockSpec(
            "day_is", Category.CONDITION, "Day is",
            "Passes on the chosen days.",
            params = listOf(ParamSpec("days", ParamType.DAYS, "weekdays, weekends or a list like mon,wed,fri"))
        ),
        BlockSpec(
            "battery_between", Category.CONDITION, "Battery is between",
            "Passes when the battery percent is in the range.",
            params = listOf(
                int("min", "lowest percent", 0, 100, default = "0"),
                int("max", "highest percent", 0, 100, default = "100")
            )
        ),
        BlockSpec(
            "is_charging", Category.CONDITION, "Is charging",
            "Passes when the phone is (or is not) charging.",
            params = listOf(ParamSpec("charging", ParamType.BOOL, "true for charging", required = false, default = "true"))
        ),
        BlockSpec(
            "wifi_is", Category.CONDITION, "Connected to Wi-Fi",
            "Passes when connected to Wi-Fi, optionally a specific network.",
            params = listOf(text("ssid", "network name; empty means any network", required = false)),
            requires = setOf(Requirement.LOCATION)
        ),
        BlockSpec(
            "text_contains", Category.CONDITION, "Text contains",
            "Passes when some text, usually a variable like {{sms.body}}, contains a word. Case is ignored.",
            params = listOf(
                text("text", "text to check, may use {{variables}}"),
                text("contains", "word or phrase to look for")
            )
        ),

        // Actions
        BlockSpec(
            "notify", Category.ACTION, "Show notification",
            "Shows a notification on this phone.",
            params = listOf(text("title", "title"), text("message", "body text")),
            requires = setOf(Requirement.NOTIFICATIONS)
        ),
        BlockSpec(
            "speak", Category.ACTION, "Speak text",
            "Reads text aloud with text-to-speech.",
            params = listOf(text("text", "what to say"))
        ),
        BlockSpec(
            "vibrate", Category.ACTION, "Vibrate",
            "Vibrates the phone.",
            params = listOf(choice("pattern", "short, long or pulse", listOf("short", "long", "pulse"), default = "short"))
        ),
        BlockSpec(
            "flashlight", Category.ACTION, "Flashlight",
            "Turns the flashlight on or off, or blinks it.",
            params = listOf(
                choice("mode", "on, off or blink", listOf("on", "off", "blink"), default = "blink"),
                int("seconds", "how long to blink", 1, 60, default = "5")
            )
        ),
        BlockSpec(
            "play_sound", Category.ACTION, "Play sound",
            "Plays the default notification, alarm or ringtone sound.",
            params = listOf(choice("sound", "notification, alarm or ringtone", listOf("notification", "alarm", "ringtone"), default = "notification"))
        ),
        BlockSpec(
            "ringer_mode", Category.ACTION, "Ringer mode",
            "Sets the ringer to normal, vibrate or silent.",
            params = listOf(choice("mode", "normal, vibrate or silent", listOf("normal", "vibrate", "silent"))),
            requires = setOf(Requirement.DND_ACCESS)
        ),
        BlockSpec(
            "do_not_disturb", Category.ACTION, "Do Not Disturb",
            "Turns Do Not Disturb on or off.",
            params = listOf(ParamSpec("on", ParamType.BOOL, "true to turn on")),
            requires = setOf(Requirement.DND_ACCESS)
        ),
        BlockSpec(
            "volume", Category.ACTION, "Set volume",
            "Sets a volume level.",
            params = listOf(
                choice("stream", "media, ring, alarm or notification", listOf("media", "ring", "alarm", "notification"), default = "media"),
                int("percent", "0 to 100", 0, 100)
            )
        ),
        BlockSpec(
            "brightness", Category.ACTION, "Set brightness",
            "Sets screen brightness. Turns off auto brightness.",
            params = listOf(int("percent", "0 to 100", 0, 100)),
            requires = setOf(Requirement.WRITE_SETTINGS)
        ),
        BlockSpec(
            "send_sms", Category.ACTION, "Send SMS",
            "Sends a text message from this phone.",
            params = listOf(
                ParamSpec("to", ParamType.PHONE, "phone number with country code"),
                text("message", "message text, may use {{variables}}")
            ),
            requires = setOf(Requirement.SEND_SMS),
            sensitive = true
        ),
        BlockSpec(
            "http_request", Category.ACTION, "Web request",
            "Calls a URL, for example a webhook, Home Assistant or ntfy.",
            params = listOf(
                ParamSpec("url", ParamType.URL, "http or https URL"),
                choice("method", "GET, POST or PUT", listOf("GET", "POST", "PUT"), default = "POST"),
                text("body", "request body, may use {{variables}}", required = false)
            ),
            outputs = listOf("http.status", "http.body"),
            sensitive = true,
            maxSeconds = 30
        ),
        BlockSpec(
            "take_photo", Category.ACTION, "Take photo",
            "Takes a photo silently and keeps it in the app's private gallery. Android may block the camera while the phone is locked or the app is closed.",
            params = listOf(choice("camera", "front or back", listOf("front", "back"), default = "front")),
            outputs = listOf("photo.path"),
            requires = setOf(Requirement.CAMERA),
            sensitive = true
        ),
        BlockSpec(
            "get_location", Category.ACTION, "Get location",
            "Reads the current location.",
            params = listOf(choice("accuracy", "high or balanced", listOf("high", "balanced"), default = "high")),
            outputs = listOf("location.lat", "location.lng", "location.maps_url"),
            requires = setOf(Requirement.LOCATION, Requirement.BACKGROUND_LOCATION),
            sensitive = true,
            maxSeconds = 30
        ),
        BlockSpec(
            "open_app", Category.ACTION, "Open app",
            "Opens an installed app.",
            params = listOf(ParamSpec("app", ParamType.APP, "app name, for example Spotify")),
            requires = setOf(Requirement.OVERLAY)
        ),
        BlockSpec(
            "open_url", Category.ACTION, "Open link",
            "Opens a web page.",
            params = listOf(ParamSpec("url", ParamType.URL, "http or https URL")),
            requires = setOf(Requirement.OVERLAY)
        ),
        BlockSpec(
            "set_alarm", Category.ACTION, "Set alarm",
            "Adds an alarm to the clock app.",
            params = listOf(
                ParamSpec("time", ParamType.TIME, "HH:mm"),
                text("label", "alarm label", required = false)
            )
        ),
        BlockSpec(
            "copy_text", Category.ACTION, "Copy to clipboard",
            "Copies text to the clipboard.",
            params = listOf(text("text", "text to copy, may use {{variables}}"))
        ),
        BlockSpec(
            "ask_ai", Category.ACTION, "Ask AI",
            "Sends a prompt to the AI model and stores the answer. Use it to summarize, translate or decide.",
            params = listOf(text("prompt", "instruction, may use {{variables}}")),
            outputs = listOf("ai.reply"),
            requires = setOf(Requirement.AI_KEY),
            sensitive = true,
            maxSeconds = 45
        ),
        BlockSpec(
            "wait", Category.ACTION, "Wait",
            "Pauses before the next step.",
            params = listOf(int("seconds", "1 to 600", 1, 600)),
            maxSeconds = 610
        )
    )

    private val byType = all.associateBy { it.type }

    operator fun get(type: String): BlockSpec? = byType[type]

    /** Always available in templates. */
    val builtInVariables = listOf("now.time", "now.date", "now.day")
}

/** Everything the phone must grant before [flow] can run. */
fun requirementsOf(flow: Automation): Set<Requirement> {
    val result = mutableSetOf<Requirement>()
    Blocks[flow.trigger.type]?.let { result += it.requires }
    forEachStep(flow.steps) { step -> Blocks[step.type]?.let { result += it.requires } }
    return result
}
