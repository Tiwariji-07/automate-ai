package com.devfest.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * One automation: a trigger, then steps run top to bottom.
 *
 * A condition step with empty [Step.then] and [Step.otherwise] is a guard: when it fails,
 * the rest of its list is skipped. With branches, it runs one branch and the list continues.
 */
@Serializable
data class Automation(
    val title: String = "",
    val summary: String = "",
    val trigger: Step,
    val steps: List<Step> = emptyList()
)

@Serializable
data class Step(
    val id: String = "",
    val type: String,
    @Serializable(with = LenientParamsSerializer::class)
    val params: Map<String, String> = emptyMap(),
    val then: List<Step> = emptyList(),
    @SerialName("else") val otherwise: List<Step> = emptyList()
) {
    val hasBranches: Boolean get() = then.isNotEmpty() || otherwise.isNotEmpty()
}

/** Visits every step below [steps], depth first, in run order. */
fun forEachStep(steps: List<Step>, action: (Step) -> Unit) {
    for (step in steps) {
        action(step)
        forEachStep(step.then, action)
        forEachStep(step.otherwise, action)
    }
}

/**
 * LLMs write `"percent": 50`, `"enabled": true` or `"days": ["mon", "tue"]`.
 * Accept any of those and store the value as a string; arrays become comma lists.
 */
object LenientParamsSerializer : KSerializer<Map<String, String>> {
    private val delegate = MapSerializer(String.serializer(), String.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: Map<String, String>) =
        delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): Map<String, String> {
        val json = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        val element = json.decodeJsonElement()
        if (element !is JsonObject) return emptyMap()
        return element.jsonObject.mapNotNull { (key, value) ->
            val text = when (value) {
                is JsonNull -> null
                is JsonPrimitive -> value.contentOrNull
                is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString(",")
                is JsonObject -> value.toString()
            }
            text?.let { key to it }
        }.toMap()
    }
}
