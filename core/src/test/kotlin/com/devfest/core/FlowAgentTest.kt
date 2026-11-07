package com.devfest.core

import com.devfest.core.agent.AgentPrompt
import com.devfest.core.agent.ChatMessage
import com.devfest.core.agent.ChatModel
import com.devfest.core.agent.FlowAgent
import com.devfest.core.catalog.Blocks
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlowAgentTest {

    private class ScriptedModel(vararg replies: String) : ChatModel {
        val queue = ArrayDeque(replies.toList())
        val requests = mutableListOf<List<ChatMessage>>()
        override suspend fun complete(messages: List<ChatMessage>, json: Boolean): String {
            requests += messages
            return queue.removeFirst()
        }
    }

    private val user = listOf(ChatMessage(ChatMessage.Role.USER, "vibrate when I tap run"))

    @Test
    fun `reads a fenced reply`() = runTest {
        val model = ScriptedModel(
            """```json
            {"message": "Done", "flow": {"title": "Buzz", "trigger": {"type": "manual"}, "steps": [{"type": "vibrate"}]}}
            ```"""
        )
        val result = FlowAgent(model).respond(user, null, emptyList(), "09:00")
        assertNotNull(result.flow)
        assertEquals("s1", result.flow!!.steps.single().id)
        assertEquals(1, model.requests.size)
    }

    @Test
    fun `sends validation errors back once and uses the fix`() = runTest {
        val model = ScriptedModel(
            """{"message": "ok", "flow": {"title": "Buzz", "trigger": {"type": "manual"}, "steps": [{"type": "buzz"}]}}""",
            """{"message": "fixed", "flow": {"title": "Buzz", "trigger": {"type": "manual"}, "steps": [{"type": "vibrate"}]}}"""
        )
        val result = FlowAgent(model).respond(user, null, emptyList(), "09:00")
        assertEquals("fixed", result.message)
        assertNotNull(result.flow)
        assertTrue("Unknown block \"buzz\"" in model.requests[1].last().content)
    }

    @Test
    fun `malformed JSON gets one repair`() = runTest {
        val model = ScriptedModel(
            "Sure! Here you go: {not json",
            """{"message": "ok", "flow": {"title": "Buzz", "trigger": {"type": "manual"}, "steps": [{"type": "vibrate"}]}}"""
        )
        val result = FlowAgent(model).respond(user, null, emptyList(), "09:00")
        assertNotNull(result.flow)
        assertTrue("not valid JSON" in model.requests[1].last().content)
    }

    @Test
    fun `a bare flow object is accepted`() = runTest {
        val result = FlowAgent(ScriptedModel("""{"title": "Buzz", "trigger": {"type": "manual"}, "steps": [{"type": "vibrate"}]}"""))
            .respond(user, null, emptyList(), "09:00")
        assertNotNull(result.flow)
    }

    @Test
    fun `gives up after one repair and reports errors`() = runTest {
        val bad = """{"message": "ok", "flow": {"title": "Buzz", "trigger": {"type": "manual"}, "steps": [{"type": "buzz"}]}}"""
        val result = FlowAgent(ScriptedModel(bad, bad)).respond(user, null, emptyList(), "09:00")
        assertNull(result.flow)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `a question has no flow and no errors`() = runTest {
        val result = FlowAgent(ScriptedModel("""{"message": "Which number should I text?", "flow": null}"""))
            .respond(user, null, emptyList(), "09:00")
        assertNull(result.flow)
        assertTrue(result.errors.isEmpty())
        assertEquals("Which number should I text?", result.message)
    }

    @Test
    fun `prompt lists every block`() {
        val prompt = AgentPrompt.system()
        Blocks.all.forEach { assertTrue("- ${it.type}:" in prompt, it.type) }
    }

    @Test
    fun `the trace shows one clean attempt`() = runTest {
        val model = ScriptedModel("""{"message": "ok", "flow": {"title": "Buzz", "trigger": {"type": "manual"}, "steps": [{"type": "vibrate"}]}}""")
        val result = FlowAgent(model).respond(user, null, emptyList(), "09:00")
        assertEquals(1, result.attempts.size)
        assertNull(result.attempts.single().problem)
        assertEquals(false, result.repaired)
    }

    @Test
    fun `the trace records why the first reply was repaired`() = runTest {
        var tick = 0L
        val model = ScriptedModel(
            """{"message": "ok", "flow": {"title": "Buzz", "trigger": {"type": "manual"}, "steps": [{"type": "buzz"}]}}""",
            """{"message": "fixed", "flow": {"title": "Buzz", "trigger": {"type": "manual"}, "steps": [{"type": "vibrate"}]}}"""
        )
        val result = FlowAgent(model, nanoTime = { tick += 5_000_000; tick }).respond(user, null, emptyList(), "09:00")
        assertEquals(listOf("1 validation errors", null), result.attempts.map { it.problem })
        assertEquals(listOf(5L, 5L), result.attempts.map { it.durationMs })
        assertTrue(result.repaired)
    }

    @Test
    fun `the trace marks a failed repair`() = runTest {
        val result = FlowAgent(ScriptedModel("{not json", "still not json")).respond(user, null, emptyList(), "09:00")
        assertEquals(listOf("invalid JSON", "invalid JSON"), result.attempts.map { it.problem })
        assertEquals(false, result.repaired)
    }
}
