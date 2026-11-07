package com.devfest.automation.ai

import com.devfest.core.agent.ChatMessage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OpenAiChatModelTest {

    private val config = AiConfig("https://ai.example/v1", "test-model", "key")
    private val messages = listOf(ChatMessage(ChatMessage.Role.USER, "hi"))

    private fun client(body: ResponseBody) = OkHttpClient.Builder()
        .addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body)
                .build()
        }
        .build()

    @Test
    fun `a connection that drops while reading the body ends the call with an error`() = runBlocking {
        val broken = object : ForwardingSource(Buffer().writeUtf8("{\"choices\": [")) {
            override fun read(sink: Buffer, byteCount: Long): Long = throw IOException("connection reset")
        }.buffer()
        val body = object : ResponseBody() {
            override fun contentType() = "application/json".toMediaType()
            override fun contentLength() = -1L
            override fun source(): BufferedSource = broken
        }
        val error = withTimeout(5_000) {
            assertFailsWith<IOException> { OpenAiChatModel({ config }, client(body)).complete(messages, json = true) }
        }
        assertEquals("connection reset", error.message)
    }

    @Test
    fun `a good reply still comes back`() = runBlocking {
        val json = """{"choices": [{"message": {"role": "assistant", "content": "{}"}, "finish_reason": "stop"}]}"""
        val body = object : ResponseBody() {
            override fun contentType() = "application/json".toMediaType()
            override fun contentLength() = -1L
            override fun source(): BufferedSource = Buffer().writeUtf8(json)
        }
        val reply = withTimeout(5_000) { OpenAiChatModel({ config }, client(body)).complete(messages, json = true) }
        assertEquals("{}", reply)
    }
}
