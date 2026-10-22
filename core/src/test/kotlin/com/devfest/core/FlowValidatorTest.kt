package com.devfest.core

import com.devfest.core.agent.FlowAgent
import com.devfest.core.model.Automation
import com.devfest.core.model.Step
import com.devfest.core.validation.FlowValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlowValidatorTest {

    private val validator = FlowValidator(knownPlaces = setOf("Home"))

    private fun flow(trigger: Step = Step(type = "manual"), vararg steps: Step) =
        Automation(title = "Test", trigger = trigger, steps = steps.toList())

    @Test
    fun `normalizes values, fills defaults and assigns ids`() {
        val result = validator.validate(
            flow(
                Step(type = "schedule", params = mapOf("time" to "7pm", "days" to "Mon, Tue, Wed, Thu, Fri")),
                Step(type = "vibrate"),
                Step(type = "volume", params = mapOf("stream" to "MEDIA", "percent" to "40.0"))
            )
        )
        assertTrue(result.isValid, result.errors.toString())
        val trigger = result.flow.trigger
        assertEquals("trigger", trigger.id)
        assertEquals(mapOf("time" to "19:00", "days" to "weekdays"), trigger.params)
        assertEquals(listOf("s1", "s2"), result.flow.steps.map { it.id })
        assertEquals("short", result.flow.steps[0].params["pattern"])
        assertEquals(mapOf("stream" to "media", "percent" to "40"), result.flow.steps[1].params)
    }

    @Test
    fun `rejects unknown blocks, misplaced triggers and bad values`() {
        val result = validator.validate(
            flow(
                Step(type = "notify"),
                Step(type = "teleport"),
                Step(type = "manual"),
                Step(type = "volume", params = mapOf("percent" to "150")),
                Step(type = "send_sms", params = mapOf("to" to "call mom", "message" to "hi"))
            )
        )
        val messages = result.errors.map { it.message }
        assertTrue(messages.any { "is not a trigger" in it })
        assertTrue(messages.any { "Unknown block \"teleport\"" in it })
        assertTrue(messages.any { "can only be the flow's trigger" in it })
        assertTrue(messages.any { "volume.percent" in it })
        assertTrue(messages.any { "send_sms.to" in it })
    }

    @Test
    fun `variables must be set before they are used`() {
        val ok = validator.validate(
            flow(
                Step(type = "sms_received"),
                Step(type = "get_location"),
                Step(type = "send_sms", params = mapOf("to" to "{{sms.from}}", "message" to "I'm at {{location.maps_url}}"))
            )
        )
        assertTrue(ok.isValid, ok.errors.toString())

        val bad = validator.validate(
            flow(
                Step(type = "manual"),
                Step(type = "notify", params = mapOf("title" to "Hi", "message" to "{{ai.reply}}")),
                Step(type = "ask_ai", params = mapOf("prompt" to "joke"))
            )
        )
        assertFalse(bad.isValid)
        assertTrue("{{ai.reply}}" in bad.errors.single().message)
    }

    @Test
    fun `outputs set inside a branch are not available after it`() {
        val result = validator.validate(
            flow(
                Step(type = "manual"),
                Step(
                    type = "is_charging",
                    then = listOf(Step(type = "get_location"))
                ),
                Step(type = "notify", params = mapOf("title" to "x", "message" to "{{location.lat}}"))
            )
        )
        assertFalse(result.isValid)
        assertEquals("s3", result.errors.single().stepId)
    }

    @Test
    fun `only conditions can branch, places must be saved, empty flows fail`() {
        val result = validator.validate(
            flow(
                Step(type = "location", params = mapOf("place" to "Office")),
                Step(type = "vibrate", then = listOf(Step(type = "vibrate")))
            )
        )
        val messages = result.errors.map { it.message }
        assertTrue(messages.any { "Only conditions" in it })
        assertTrue(messages.any { "Place \"Office\" is not saved" in it })

        assertFalse(validator.validate(flow(Step(type = "manual"))).isValid)
    }

    @Test
    fun `unknown params are errors so a misspelled filter is not silently dropped`() {
        val result = validator.validate(
            flow(Step(type = "wifi", params = mapOf("network" to "Home")), Step(type = "vibrate"))
        )
        assertFalse(result.isValid)
        assertTrue("no parameter \"network\"" in result.errors.single().message)
        assertTrue("ssid" in result.errors.single().message)
    }

    @Test
    fun `every monday means monday`() {
        val result = validator.validate(
            flow(Step(type = "schedule", params = mapOf("time" to "8:00", "days" to "every Monday")), Step(type = "vibrate"))
        )
        assertEquals("mon", result.flow.trigger.params["days"])
        assertEquals("daily", validator.validate(
            flow(Step(type = "schedule", params = mapOf("time" to "8:00", "days" to "every day")), Step(type = "vibrate"))
        ).flow.trigger.params["days"])
    }

    @Test
    fun `numbers out of range or not finite are rejected`() {
        listOf("4294967346", "NaN", "-1", "101").forEach { value ->
            val result = validator.validate(flow(Step(type = "manual"), Step(type = "volume", params = mapOf("percent" to value))))
            assertFalse(result.isValid, value)
        }
    }

    @Test
    fun `battery range must not be inverted`() {
        val result = validator.validate(
            flow(Step(type = "manual"), Step(type = "battery_between", params = mapOf("min" to "80", "max" to "20")))
        )
        assertTrue("above max" in result.errors.single().message)
    }

    @Test
    fun `a URL that is entirely a variable is checked at run time`() {
        val result = validator.validate(
            flow(Step(type = "manual"), Step(type = "get_location"), Step(type = "open_url", params = mapOf("url" to "{{location.maps_url}}")))
        )
        assertTrue(result.isValid, result.errors.toString())
    }

    @Test
    fun `camera in a background flow warns`() {
        val result = validator.validate(flow(Step(type = "unlock_failed"), Step(type = "take_photo")))
        assertTrue(result.isValid)
        assertTrue(result.warnings.any { "camera" in it.message })
    }

    @Test
    fun `accepts numbers, booleans and arrays from the model`() {
        val raw = """
            {"title": "Night", "trigger": {"type": "schedule", "params": {"time": "22:30", "days": ["sat", "sun"]}},
             "steps": [{"type": "do_not_disturb", "params": {"on": true}}, {"type": "brightness", "params": {"percent": 10}}]}
        """
        val flow = FlowAgent.DefaultJson.decodeFromString(Automation.serializer(), raw)
        val result = validator.validate(flow)
        assertTrue(result.isValid, result.errors.toString())
        assertEquals("weekends", result.flow.trigger.params["days"])
        assertEquals("true", result.flow.steps[0].params["on"])
        assertEquals("10", result.flow.steps[1].params["percent"])
    }
}
