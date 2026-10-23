package com.devfest.core.validation

import com.devfest.core.catalog.BlockSpec
import com.devfest.core.catalog.Blocks
import com.devfest.core.catalog.Category
import com.devfest.core.catalog.ParamSpec
import com.devfest.core.catalog.ParamType
import com.devfest.core.catalog.Values
import com.devfest.core.engine.Templates
import com.devfest.core.model.Automation
import com.devfest.core.model.forEachStep
import com.devfest.core.model.Step

data class Issue(val stepId: String?, val message: String) {
    override fun toString() = if (stepId == null) message else "$stepId: $message"
}

data class ValidationResult(
    /** The input with ids assigned, defaults filled and values normalized. */
    val flow: Automation,
    val errors: List<Issue>,
    val warnings: List<Issue>
) {
    val isValid: Boolean get() = errors.isEmpty()
}

/**
 * Checks a flow against [Blocks] and normalizes it. Pass [knownPlaces] to check place names;
 * null skips that check.
 */
class FlowValidator(private val knownPlaces: Set<String>? = null) {

    fun validate(input: Automation): ValidationResult {
        val run = Run()
        val title = input.title.trim().take(MAX_TITLE).ifEmpty {
            run.warn(null, "Missing title, using \"New flow\"")
            "New flow"
        }
        val trigger = run.trigger(input.trigger)
        val available = Blocks.builtInVariables.toMutableSet()
        Blocks[trigger.type]?.let { available += it.outputs }
        if (input.steps.isEmpty()) run.error(null, "The flow has no steps")
        val steps = run.list(input.steps, available, depth = 1)
        if (run.count > MAX_STEPS) run.error(null, "Too many steps (${run.count}, max $MAX_STEPS)")
        if (trigger.type != "manual") {
            forEachStep(steps) {
                if (it.type == "take_photo") run.warn(it.id, "Android may block the camera while the phone is locked or the app is closed")
            }
        }
        return ValidationResult(
            Automation(title, input.summary.trim(), trigger, steps),
            run.errors,
            run.warnings
        )
    }

    private inner class Run {
        val errors = mutableListOf<Issue>()
        val warnings = mutableListOf<Issue>()
        val usedIds = mutableSetOf(TRIGGER_ID)
        var count = 0

        fun error(stepId: String?, message: String) { errors += Issue(stepId, message) }
        fun warn(stepId: String?, message: String) { warnings += Issue(stepId, message) }

        fun trigger(step: Step): Step {
            val spec = Blocks[step.type]
            if (spec == null) {
                error(TRIGGER_ID, "Unknown trigger \"${step.type}\". Use one of: ${typesOf(Category.TRIGGER)}")
                return step.copy(id = TRIGGER_ID)
            }
            if (spec.category != Category.TRIGGER) {
                error(TRIGGER_ID, "\"${step.type}\" is not a trigger. Use one of: ${typesOf(Category.TRIGGER)}")
            }
            if (step.hasBranches) error(TRIGGER_ID, "A trigger cannot have then/else branches")
            return Step(TRIGGER_ID, step.type, params(TRIGGER_ID, spec, step.params, Blocks.builtInVariables.toSet()))
        }

        fun list(steps: List<Step>, available: MutableSet<String>, depth: Int): List<Step> =
            steps.map { step(it, available, depth) }

        private fun step(step: Step, available: MutableSet<String>, depth: Int): Step {
            count++
            val id = step.id.trim().takeIf { it.isNotEmpty() && usedIds.add(it) } ?: nextId()
            val spec = Blocks[step.type]
            if (spec == null) {
                error(id, "Unknown block \"${step.type}\". Use one of: ${typesOf(Category.CONDITION)}, ${typesOf(Category.ACTION)}")
                return step.copy(id = id)
            }
            if (spec.category == Category.TRIGGER) {
                error(id, "\"${step.type}\" is a trigger and can only be the flow's trigger")
            }
            val params = params(id, spec, step.params, available)
            var then = emptyList<Step>()
            var otherwise = emptyList<Step>()
            if (step.hasBranches) {
                when {
                    spec.category != Category.CONDITION -> error(id, "Only conditions can have then/else branches")
                    depth >= MAX_DEPTH -> error(id, "Branches are nested too deep (max $MAX_DEPTH levels)")
                    else -> {
                        // Outputs set inside a branch may not exist after it, so each branch gets a copy.
                        then = list(step.then, available.toMutableSet(), depth + 1)
                        otherwise = list(step.otherwise, available.toMutableSet(), depth + 1)
                    }
                }
            }
            available += spec.outputs
            return Step(id, step.type, params, then, otherwise)
        }

        private fun params(id: String, spec: BlockSpec, raw: Map<String, String>, available: Set<String>): Map<String, String> {
            raw.keys.filter { spec.param(it) == null }.forEach {
                val known = spec.params.joinToString { p -> p.name }.ifEmpty { "none" }
                error(id, "${spec.type} has no parameter \"$it\". Its parameters: $known")
            }
            val result = linkedMapOf<String, String>()
            for (param in spec.params) {
                val value = raw[param.name]?.trim()?.takeIf { it.isNotEmpty() } ?: param.default
                if (value == null) {
                    if (param.required) error(id, "${spec.type} needs \"${param.name}\" (${param.description})")
                    continue
                }
                normalize(id, spec, param, value, available)?.let { result[param.name] = it }
            }
            if (spec.type == "battery_between") {
                val min = result["min"]?.toIntOrNull()
                val max = result["max"]?.toIntOrNull()
                if (min != null && max != null && min > max) error(id, "battery_between.min ($min) is above max ($max)")
            }
            return result
        }

        private fun normalize(id: String, spec: BlockSpec, param: ParamSpec, value: String, available: Set<String>): String? {
            val label = "${spec.type}.${param.name}"
            if (param.type in TEMPLATE_TYPES && Templates.hasVariables(value)) {
                Templates.variables(value).filter { it !in available }.forEach {
                    error(id, "$label uses {{$it}}, which is not set before this step. Available: ${available.sorted().joinToString()}")
                }
                val startsWithVariable = value.trimStart().startsWith("{{")
                if (param.type == ParamType.URL && !startsWithVariable && Values.url(Templates.fillPlaceholders(value)) == null) {
                    error(id, "$label is not a valid http(s) URL: $value")
                }
                return value
            }
            val normalized: String? = when (param.type) {
                ParamType.TEXT, ParamType.APP -> value.takeIf { it.length <= MAX_TEXT }
                ParamType.INT -> Values.int(value, param.min, param.max)?.toString()
                ParamType.NUMBER -> value.toDoubleOrNull()?.takeIf { it.isFinite() }?.toString()
                ParamType.BOOL -> Values.bool(value)?.toString()
                ParamType.TIME -> Values.time(value)?.let(Values::formatTime)
                ParamType.DAYS -> Values.days(value)?.let(Values::formatDays)
                ParamType.CHOICE -> param.choices.firstOrNull { it.equals(value, ignoreCase = true) }
                ParamType.PHONE -> Values.phone(value)
                ParamType.URL -> Values.url(value)
                ParamType.PLACE -> value.also {
                    if (knownPlaces != null && knownPlaces.none { place -> place.equals(it, ignoreCase = true) }) {
                        error(id, "Place \"$it\" is not saved yet. Saved places: ${knownPlaces.joinToString().ifEmpty { "none" }}")
                    }
                }
            }
            if (normalized == null) error(id, "$label has an invalid value \"$value\" (${describe(param)})")
            if (param.type == ParamType.URL && normalized?.startsWith("http://", ignoreCase = true) == true) {
                warn(id, "$label uses http://, so the request is not encrypted. Fine on your home network, risky elsewhere")
            }
            return normalized
        }

        private fun nextId(): String {
            var n = usedIds.size
            while (!usedIds.add("s$n")) n++
            return "s$n"
        }
    }

    private fun describe(param: ParamSpec): String = when (param.type) {
        ParamType.INT -> "whole number ${param.min ?: ""}..${param.max ?: ""}"
        ParamType.CHOICE -> "one of ${param.choices.joinToString()}"
        else -> param.description
    }

    private fun typesOf(category: Category) =
        Blocks.all.filter { it.category == category }.joinToString { it.type }

    companion object {
        const val TRIGGER_ID = "trigger"
        const val MAX_STEPS = 40
        const val MAX_DEPTH = 4
        const val MAX_TITLE = 40
        const val MAX_TEXT = 2000
        private val TEMPLATE_TYPES = setOf(ParamType.TEXT, ParamType.URL, ParamType.PHONE)
    }
}
