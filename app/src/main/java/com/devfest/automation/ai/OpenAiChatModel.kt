package com.devfest.automation.ai

import com.devfest.core.agent.ChatMessage
import com.devfest.core.agent.ChatModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AiException(message: String) : IOException(message)

/** Chat completions against any OpenAI-compatible API (OpenAI, Groq, OpenRouter, a local server). */
class OpenAiChatModel(
    private val config: () -> AiConfig,
    private val client: OkHttpClient = defaultClient
) : ChatModel {

    override suspend fun complete(messages: List<ChatMessage>, json: Boolean): String {
        val current = config()
        if (!current.isComplete) throw AiException("Add an API key in Settings first")
        val body = ChatRequest(
            model = current.model,
            messages = messages.map { WireMessage(it.role.name.lowercase(), it.content) },
            responseFormat = if (json) ResponseFormat("json_object") else null
        )
        val response = post(current, "/chat/completions", Wire.encodeToString(ChatRequest.serializer(), body))
        val parsed = Wire.decodeFromString(ChatResponse.serializer(), response)
        val choice = parsed.choices.firstOrNull() ?: throw AiException("The model returned no answer")
        if (choice.finishReason == "length") throw AiException("The answer was cut off. Try a shorter request")
        return choice.message.content?.takeIf { it.isNotBlank() }
            ?: throw AiException(choice.message.refusal ?: "The model returned an empty answer")
    }

    /** Model ids the key can use, for the settings picker. */
    suspend fun listModels(using: AiConfig = config()): List<String> {
        val response = get(using, "/models")
        return Wire.decodeFromString(ModelList.serializer(), response).data.map { it.id }.sorted()
    }

    private suspend fun post(config: AiConfig, path: String, json: String): String =
        execute(request(config, path).post(json.toRequestBody(JSON)).build())

    private suspend fun get(config: AiConfig, path: String): String =
        execute(request(config, path).get().build())

    private fun request(config: AiConfig, path: String) = Request.Builder()
        .url(config.baseUrl.trimEnd('/') + path)
        .header("Authorization", "Bearer ${config.apiKey}")

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        val call = client.newCall(request)
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
                override fun onResponse(call: Call, response: Response) {
                    val result = response.use {
                        val text = it.body.string()
                        if (it.isSuccessful) Result.success(text) else Result.failure(AiException(errorMessage(it.code, text)))
                    }
                    result.fold(cont::resume, cont::resumeWithException)
                }
            })
        }
    }

    private fun errorMessage(code: Int, body: String): String {
        val detail = runCatching {
            Wire.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
        }.getOrNull()
        val hint = when (code) {
            401 -> "The API key was rejected"
            404 -> "Model or URL not found"
            429 -> "Rate limited, try again in a moment"
            else -> "AI request failed ($code)"
        }
        return if (detail.isNullOrBlank()) hint else "$hint: $detail"
    }

    @Serializable
    private data class ChatRequest(
        val model: String,
        val messages: List<WireMessage>,
        @SerialName("response_format") val responseFormat: ResponseFormat? = null
    )

    @Serializable
    private data class WireMessage(val role: String, val content: String? = null, val refusal: String? = null)

    @Serializable
    private data class ResponseFormat(val type: String)

    @Serializable
    private data class ChatResponse(val choices: List<Choice> = emptyList())

    @Serializable
    private data class Choice(val message: WireMessage, @SerialName("finish_reason") val finishReason: String? = null)

    @Serializable
    private data class ModelList(val data: List<ModelInfo> = emptyList())

    @Serializable
    private data class ModelInfo(val id: String)

    private companion object {
        val JSON = "application/json".toMediaType()
        val Wire = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}
