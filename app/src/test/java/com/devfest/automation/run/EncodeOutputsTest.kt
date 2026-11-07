package com.devfest.automation.run

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EncodeOutputsTest {

    private fun writeUtfSize(text: String): Int {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).writeUTF(text)
        return bytes.size() - 2
    }

    @Test
    fun `short outputs are kept as they are`() {
        val text = FlowRunner.encodeOutputs(mapOf("sms.from" to "+919876543210", "sms.body" to "Hello"))
        val values = Json.parseToJsonElement(text).jsonObject
        assertEquals("Hello", values["sms.body"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a long notification in Hindi fits in the WorkManager limit`() {
        val hindi = "नमस्ते ".repeat(1000)
        val outputs = mapOf("notification.app" to "WhatsApp", "notification.title" to hindi, "notification.text" to hindi)
        val text = FlowRunner.encodeOutputs(outputs)
        assertTrue(writeUtfSize(text) <= FlowRunner.MAX_OUTPUT_BYTES, "encoded size ${writeUtfSize(text)}")
        val values = Json.parseToJsonElement(text).jsonObject
        assertEquals("WhatsApp", values["notification.app"]?.jsonPrimitive?.content)
        assertTrue(values["notification.text"]!!.jsonPrimitive.content.isNotEmpty())
    }

    @Test
    fun `the length count matches DataOutputStream`() {
        val sample = "a\u0000é नमस्ते 😀"
        assertEquals(writeUtfSize(sample), FlowRunner.modifiedUtf8Length(sample))
    }

    @Test
    fun `an emoji is not cut in half`() {
        val text = FlowRunner.encodeOutputs(mapOf("t" to "😀".repeat(3000)), maxBytes = 100)
        val value = Json.parseToJsonElement(text).jsonObject["t"]!!.jsonPrimitive.content
        assertTrue(value.isEmpty() || !value.last().isHighSurrogate())
    }
}
