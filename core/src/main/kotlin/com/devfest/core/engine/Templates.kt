package com.devfest.core.engine

/** `{{name}}` placeholders inside text params, filled from step outputs at run time. */
object Templates {
    // Escape every brace: Android's ICU regex rejects a bare `}` that the desktop JVM accepts.
    private val pattern = Regex("""\{\{\s*([a-zA-Z0-9_.]+)\s*\}\}""")

    fun hasVariables(text: String): Boolean = pattern.containsMatchIn(text)

    fun variables(text: String): List<String> = pattern.findAll(text).map { it.groupValues[1] }.toList()

    /** Unknown names become empty strings. */
    fun render(text: String, values: Map<String, String>): String =
        pattern.replace(text) { values[it.groupValues[1]].orEmpty() }

    /** For syntax checks only: replaces every placeholder with a harmless token. */
    fun fillPlaceholders(text: String): String = pattern.replace(text, "x")
}
