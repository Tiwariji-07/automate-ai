package com.devfest.core

import com.devfest.core.trigger.TriggerRules
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TriggerRulesTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    // Thursday 22 Oct 2026, 09:00
    private val now = ZonedDateTime.of(2026, 10, 22, 9, 0, 0, 0, zone)

    @Test
    fun `schedule picks later today, tomorrow, or the next allowed day`() {
        assertEquals(now.withHour(18), TriggerRules.nextSchedule(mapOf("time" to "18:00"), now))
        assertEquals(now.plusDays(1).withHour(7), TriggerRules.nextSchedule(mapOf("time" to "07:00"), now))
        // 09:00 exactly is not "after now"
        assertEquals(now.plusDays(1), TriggerRules.nextSchedule(mapOf("time" to "09:00"), now))
        val weekend = TriggerRules.nextSchedule(mapOf("time" to "10:00", "days" to "weekends"), now)!!
        assertEquals(DayOfWeek.SATURDAY, weekend.dayOfWeek)
        assertEquals(now.plusDays(7).withHour(8), TriggerRules.nextSchedule(mapOf("time" to "08:00", "days" to "thu"), now))
    }

    @Test
    fun `sms filters match number formats and text`() {
        assertTrue(TriggerRules.sms(mapOf("from" to "+91 98765 43210"), "9876543210", "hi"))
        assertTrue(TriggerRules.sms(mapOf("from" to "HDFC"), "VM-HDFCBK", "x"))
        assertFalse(TriggerRules.sms(mapOf("contains" to "OTP"), "x", "hello"))
        assertTrue(TriggerRules.sms(emptyMap(), "x", "anything"))
    }

    @Test
    fun `notification filters on app and text`() {
        assertTrue(TriggerRules.notification(mapOf("app" to "whatsapp"), "WhatsApp", "com.whatsapp", "Mom", "hi"))
        assertFalse(TriggerRules.notification(mapOf("app" to "WhatsApp", "contains" to "urgent"), "WhatsApp", "com.whatsapp", "Mom", "hi"))
        assertFalse(TriggerRules.notification(mapOf("app" to "Gmail"), "WhatsApp", "com.whatsapp", "Mom", "hi"))
    }

    @Test
    fun `battery low fires once until it recovers`() {
        val rule = TriggerRules.BatteryLow(below = 20)
        assertEquals(listOf(false, true, false, false, false, false, true), listOf(25, 19, 18, 21, 19, 26, 19).map(rule::update))
    }
}
