package com.devfest.core.agent

import com.devfest.core.catalog.Blocks
import com.devfest.core.catalog.Category
import com.devfest.core.catalog.ParamSpec
import com.devfest.core.catalog.ParamType
import com.devfest.core.model.Automation
import com.devfest.core.validation.FlowValidator
import com.devfest.core.validation.Issue
import com.devfest.core.validation.ValidationResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class ChatMessage(val role: Role, val content: String) {
    enum class Role { SYSTEM, USER, ASSISTANT }
}

/** An OpenAI-compatible chat completion call. Returns the assistant's text. */
fun interface ChatModel {
    suspend fun complete(messages: List<ChatMessage>, json: Boolean): String
}

@Serializable
data class AgentReply(val message: String = "", val flow: Automation? = null)

data class AgentResult(
    val message: String,
    /** Null when the model asked a question or could not produce a valid flow. */
    val flow: Automation?,
    /** Problems left after the repair attempt. Empty when [flow] is set. */
    val errors: List<Issue> = emptyList(),
    val warnings: List<Issue> = emptyList()
)

class FlowAgent(
    private val model: ChatModel,
    private val json: Json = DefaultJson
) {

    /**
     * [history] is the conversation so far, ending with the newest user message.
     * [current] is the draft being edited, if any.
     */
    suspend fun respond(
        history: List<ChatMessage>,
        current: Automation?,
        places: List<String>,
        now: String
    ): AgentResult {
        val messages = buildList {
            add(ChatMessage(ChatMessage.Role.SYSTEM, AgentPrompt.system()))
            add(ChatMessage(ChatMessage.Role.SYSTEM, AgentPrompt.context(current, places, now, json)))
            addAll(history)
        }
        val validator = FlowValidator(places.toSet())

        val first = model.complete(messages, json = true)
        val parsed = parse(first)
        val result = parsed?.flow?.let(validator::validate)
        if (parsed != null && (result == null || result.isValid)) return toResult(parsed, result)

        val problem = if (parsed == null) {
            "Your reply was not valid JSON in the required shape. Reply again with only the JSON object."
        } else {
            "The flow has these problems:\n" + result!!.errors.joinToString("\n") { "- $it" } +
                "\nFix them and reply again with the full JSON object."
        }
        val retry = model.complete(
            messages + ChatMessage(ChatMessage.Role.ASSISTANT, first) + ChatMessage(ChatMessage.Role.USER, problem),
            json = true
        )
        val reparsed = parse(retry)
            ?: return AgentResult("The model's reply could not be read. Try rephrasing.", null)
        return toResult(reparsed, reparsed.flow?.let(validator::validate))
    }

    private fun toResult(reply: AgentReply, validation: ValidationResult?): AgentResult = when {
        validation == null -> AgentResult(reply.message, null)
        validation.isValid -> AgentResult(reply.message, validation.flow, warnings = validation.warnings)
        else -> AgentResult(reply.message, null, validation.errors, validation.warnings)
    }

    /** Null when the reply is neither `{"message", "flow"}` nor a bare flow object. */
    private fun parse(raw: String): AgentReply? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val text = raw.substring(start, end + 1)
        val reply = runCatching { json.decodeFromString(AgentReply.serializer(), text) }.getOrNull()
        if (reply != null && (reply.message.isNotBlank() || reply.flow != null)) return reply
        return runCatching { json.decodeFromString(Automation.serializer(), text) }.getOrNull()?.let { AgentReply("", it) }
    }

    companion object {
        val DefaultJson = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
            isLenient = true
        }
    }
}

object AgentPrompt {

    fun system(): String = buildString {
        appendLine("You build phone automations for one Android phone. You talk to its owner.")
        appendLine("Always reply with one JSON object and nothing else:")
        appendLine("""{"message": "short reply to the owner", "flow": <flow or null>}""")
        appendLine()
        appendLine("Flow shape:")
        appendLine("""{"title": "2-4 words", "summary": "one sentence", "trigger": {"type": "...", "params": {...}}, "steps": [step, ...]}""")
        appendLine("""Step: {"type": "...", "params": {...}} . A condition step may also have "then": [steps] and "else": [steps].""")
        appendLine()
        appendLine("Rules:")
        appendLine("- Exactly one trigger. Steps run top to bottom.")
        appendLine("- A condition without then/else is a guard: if it fails, the remaining steps in that list are skipped.")
        appendLine("- Use only the block types and params listed below. All param values are strings.")
        appendLine("- Use {{variable}} in text, url and phone params to insert values set by the trigger or an earlier step.")
        appendLine("- A variable set inside then/else can only be used later in that same branch, not after the condition.")
        appendLine("- Use only the params listed for each block. Leave out optional params you do not need.")
        appendLine("- If the owner is editing, return the whole updated flow, not a diff.")
        appendLine("- If something essential is missing (a phone number, a place, a time), set flow to null and ask in message.")
        appendLine("- If a request is impossible with these blocks, set flow to null and say what is possible instead.")
        appendLine("- Keep message under 40 words.")
        appendLine()
        appendLine("Built-in variables: ${Blocks.builtInVariables.joinToString { "{{$it}}" }}")
        for (category in Category.entries) {
            appendLine()
            appendLine("${category.name}S:")
            Blocks.all.filter { it.category == category }.forEach { spec ->
                append("- ${spec.type}: ${spec.description}")
                if (spec.params.isNotEmpty()) append(" Params: " + spec.params.joinToString("; ") { describe(it) } + ".")
                if (spec.outputs.isNotEmpty()) append(" Sets: " + spec.outputs.joinToString { "{{$it}}" } + ".")
                appendLine()
            }
        }
    }

    fun context(current: Automation?, places: List<String>, now: String, json: Json): String = buildString {
        appendLine("Current time on the phone: $now")
        appendLine("Saved places: ${places.joinToString().ifEmpty { "none (location triggers need a saved place)" }}")
        if (current == null) {
            appendLine("There is no draft yet.")
        } else {
            appendLine("The owner is editing this draft:")
            appendLine(json.encodeToString(Automation.serializer(), current))
        }
    }

    private fun describe(param: ParamSpec): String = buildString {
        append(param.name)
        val kind = when (param.type) {
            ParamType.CHOICE -> param.choices.joinToString("|")
            ParamType.INT -> "int ${param.min}-${param.max}"
            else -> param.type.name.lowercase()
        }
        append(" ($kind")
        if (!param.required) append(", optional")
        if (param.default != null) append(", default ${param.default}")
        append(")")
        if (param.type !in setOf(ParamType.CHOICE, ParamType.INT, ParamType.BOOL)) append(" ${param.description}")
    }
}
