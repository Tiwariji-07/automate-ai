package com.devfest.core.trigger

import com.devfest.core.catalog.Values
import java.time.ZonedDateTime

/** Decides whether an event matches a trigger's params. The Android listeners call these. */
object TriggerRules {

    /** Next time a `schedule` trigger fires, strictly after [now]. Null if params are invalid. */
    fun nextSchedule(params: Map<String, String>, now: ZonedDateTime): ZonedDateTime? {
        val time = params["time"]?.let(Values::time) ?: return null
        val days = Values.days(params["days"] ?: "daily") ?: return null
        var candidate = now.withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)
        repeat(8) {
            if (candidate.isAfter(now) && candidate.dayOfWeek in days) return candidate
            candidate = candidate.plusDays(1).withHour(time.hour).withMinute(time.minute)
        }
        return null
    }

    fun sms(params: Map<String, String>, from: String, body: String): Boolean {
        val wantFrom = params["from"].orEmpty()
        val fromOk = wantFrom.isEmpty() || samePhone(wantFrom, from) || from.contains(wantFrom, ignoreCase = true)
        val wantText = params["contains"].orEmpty()
        return fromOk && (wantText.isEmpty() || body.contains(wantText, ignoreCase = true))
    }

    fun notification(params: Map<String, String>, appLabel: String, packageName: String, title: String, text: String): Boolean {
        val app = params["app"].orEmpty()
        val appOk = app.isEmpty() || appLabel.equals(app, ignoreCase = true) || packageName.equals(app, ignoreCase = true) ||
            appLabel.contains(app, ignoreCase = true)
        val wanted = params["contains"].orEmpty()
        return appOk && (wanted.isEmpty() || title.contains(wanted, ignoreCase = true) || text.contains(wanted, ignoreCase = true))
    }

    /** `event` params are "connected"/"disconnected" or "enter"/"exit". */
    fun event(params: Map<String, String>, happened: String): Boolean =
        (params["event"] ?: return true).equals(happened, ignoreCase = true)

    fun wifi(params: Map<String, String>, event: String, ssid: String?): Boolean {
        if (!event(params, event)) return false
        val wanted = params["ssid"].orEmpty()
        return wanted.isEmpty() || ssid.equals(wanted, ignoreCase = true)
    }

    /**
     * Fires once when the level drops below the threshold. It re-arms only after the level is back
     * [REARM_MARGIN] points above it, so hovering around the threshold does not fire repeatedly.
     */
    class BatteryLow(private val below: Int) {
        private var armed = true

        fun update(level: Int): Boolean {
            if (level >= below + REARM_MARGIN) armed = true
            if (armed && level < below) {
                armed = false
                return true
            }
            return false
        }
    }

    /** Compares phone numbers by their last 10 digits so "+91 98765 43210" matches "9876543210". */
    fun samePhone(a: String, b: String): Boolean {
        val da = a.filter(Char::isDigit).takeLast(10)
        val db = b.filter(Char::isDigit).takeLast(10)
        return da.length >= 6 && da == db
    }

    const val REARM_MARGIN = 5
}
