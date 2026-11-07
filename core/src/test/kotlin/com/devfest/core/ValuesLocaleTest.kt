package com.devfest.core

import com.devfest.core.catalog.Values
import com.devfest.core.model.Automation
import com.devfest.core.model.Step
import com.devfest.core.trigger.TriggerRules
import com.devfest.core.validation.FlowValidator
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ValuesLocaleTest {

    private fun <T> withLocale(locale: Locale, block: () -> T): T {
        val saved = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            return block()
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun `stored times use ASCII digits in an Arabic locale`(): Unit = withLocale(Locale.forLanguageTag("ar-EG")) {
        assertEquals("07:30", Values.formatTime(LocalTime.of(7, 30)))
        val flow = Automation(
            title = "Morning",
            trigger = Step(type = "schedule", params = mapOf("time" to "7:30")),
            steps = listOf(Step(type = "vibrate"))
        )
        val trigger = FlowValidator().validate(flow).flow.trigger
        assertEquals("07:30", trigger.params["time"])
        assertNotNull(TriggerRules.nextSchedule(trigger.params, ZonedDateTime.of(2026, 11, 6, 6, 0, 0, 0, ZoneId.of("Asia/Kolkata"))))
    }
}
