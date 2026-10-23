package com.devfest.core.catalog

import java.net.URI
import java.time.DayOfWeek
import java.time.LocalTime

/** Parses and normalizes param values. Each function returns null when the value is invalid. */
object Values {

    private val timePattern = Regex("""^(\d{1,2})(?::(\d{2}))?\s*(am|pm)?$""", RegexOption.IGNORE_CASE)

    /** Accepts "7:30", "07:30", "7pm", "7:30 pm". */
    fun time(raw: String): LocalTime? {
        val match = timePattern.matchEntire(raw.trim()) ?: return null
        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].ifEmpty { "0" }.toInt()
        val half = match.groupValues[3].lowercase()
        if (half.isNotEmpty()) {
            if (hour !in 1..12) return null
            hour = hour % 12 + if (half == "pm") 12 else 0
        }
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    fun formatTime(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

    private val dayNames = mapOf(
        "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thu" to DayOfWeek.THURSDAY, "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY,
        "sun" to DayOfWeek.SUNDAY
    )
    private val weekdays = DayOfWeek.entries.take(5).toSet()
    private val weekends = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    /** Accepts "daily", "weekdays", "weekends", or a list like "mon,wed,fri" / "Monday Tuesday". */
    fun days(raw: String): Set<DayOfWeek>? {
        val tokens = raw.lowercase().split(',', ' ', ';', '/').map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        val result = mutableSetOf<DayOfWeek>()
        for (token in tokens) {
            when (token) {
                "every", "on", "and" -> Unit
                "daily", "everyday", "day", "days", "all" -> result += DayOfWeek.entries
                "weekdays", "weekday" -> result += weekdays
                "weekends", "weekend" -> result += weekends
                else -> result += dayNames[token.take(3)] ?: return null
            }
        }
        return result.takeIf { it.isNotEmpty() }
    }

    fun formatDays(days: Set<DayOfWeek>): String = when (days) {
        DayOfWeek.entries.toSet() -> "daily"
        weekdays -> "weekdays"
        weekends -> "weekends"
        else -> DayOfWeek.entries.filter { it in days }.joinToString(",") { it.name.take(3).lowercase() }
    }

    /** Whole number within [min]..[max]; "40.0" is accepted; "NaN" and out-of-range values are not. */
    fun int(raw: String, min: Int?, max: Int?): Int? {
        val number = raw.trim().toBigDecimalOrNull() ?: return null
        if (min != null && number < min.toBigDecimal()) return null
        if (max != null && number > max.toBigDecimal()) return null
        if (number.abs() > Int.MAX_VALUE.toBigDecimal()) return null
        return number.setScale(0, java.math.RoundingMode.HALF_UP).toInt()
    }

    fun bool(raw: String): Boolean? = when (raw.trim().lowercase()) {
        "true", "yes", "on", "1" -> true
        "false", "no", "off", "0" -> false
        else -> null
    }

    private val phonePattern = Regex("""^\+?[0-9 ()\-]{3,20}$""")

    fun phone(raw: String): String? {
        val trimmed = raw.trim()
        if (!phonePattern.matches(trimmed)) return null
        val digits = trimmed.count { it.isDigit() }
        if (digits !in 3..15) return null
        return trimmed.filter { it.isDigit() || it == '+' }
    }

    fun url(raw: String): String? {
        val trimmed = raw.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        return trimmed
    }
}
