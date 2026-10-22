package com.devfest.core.engine

import com.devfest.core.catalog.Values
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

/** Handlers that need only a clock. Android-specific handlers live in the app. */
object PureHandlers {

    fun create(clock: Clock = Clock.systemDefaultZone()): Map<String, StepHandler> = mapOf(
        "time_between" to StepHandler { _, params, _ ->
            val start = Values.time(params.getValue("start"))!!
            val end = Values.time(params.getValue("end"))!!
            val now = LocalTime.now(clock).withSecond(0).withNano(0)
            val inside = if (start <= end) now in start..end else now >= start || now <= end
            StepOutcome.Check(inside, "Now ${Values.formatTime(now)}, window ${params["start"]}-${params["end"]}")
        },
        "day_is" to StepHandler { _, params, _ ->
            val days = Values.days(params.getValue("days"))!!
            val today = LocalDate.now(clock).dayOfWeek
            StepOutcome.Check(today in days, "Today is ${today.name.lowercase()}")
        },
        "text_contains" to StepHandler { _, params, _ ->
            val needle = params.getValue("contains")
            val found = params.getValue("text").contains(needle, ignoreCase = true)
            StepOutcome.Check(found, if (found) "Found \"$needle\"" else "\"$needle\" not found")
        },
        "wait" to StepHandler { _, params, _ ->
            val seconds = params.getValue("seconds").toInt()
            delay(seconds * 1000L)
            StepOutcome.Done("Waited ${seconds}s")
        }
    )
}
