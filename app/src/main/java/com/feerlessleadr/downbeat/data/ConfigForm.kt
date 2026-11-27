package com.feerlessleadr.downbeat.data

import com.feerlessleadr.downbeat.ma.ConfigEntry
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * The rules of a provider settings form, with no Android in them so they can be tested. The server describes the fields
 * (see [ConfigEntry]); this holds the values being edited and answers: which fields show, which are missing, which are
 * invalid, and what changed. Only changed values are ever sent: a secret that was not edited is never sent back, so a
 * masked placeholder can never overwrite the real one.
 *
 * [store] holds the current values; the screen passes a snapshot map so edits redraw it.
 */
class ConfigFormState(
    val entries: List<ConfigEntry>,
    private val store: MutableMap<String, JsonElement?> = mutableMapOf(),
) {
    private val initial: Map<String, JsonElement?> = entries.associate { it.key to clean(it.value ?: it.defaultValue) }

    init {
        initial.forEach { (k, v) -> store[k] = v }
    }

    fun value(key: String): JsonElement? = store[key]

    fun set(key: String, value: JsonElement?) {
        store[key] = clean(value)
    }

    /** Sets a text-typed field from what was typed: numbers are stored as numbers when they parse, else kept as text. */
    fun setText(entry: ConfigEntry, text: String) {
        set(
            entry.key,
            when {
                entry.type == "integer" -> if (text.isBlank()) null else text.trim().toLongOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(text)
                entry.type == "float" -> if (text.isBlank()) null else text.trim().toDoubleOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(text)
                entry.type == "splitted_string" || entry.multiValue ->
                    JsonArray(text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map(::JsonPrimitive))
                else -> JsonPrimitive(text)
            },
        )
    }

    /** The text to show in a text box for [entry]. */
    fun text(entry: ConfigEntry): String {
        val v = value(entry.key) ?: return ""
        return when (v) {
            is JsonArray -> v.joinToString("\n") { (it as? JsonPrimitive)?.content.orEmpty() }
            is JsonPrimitive -> v.content
            else -> ""
        }
    }

    fun isEditable(entry: ConfigEntry): Boolean = entry.type in EDITABLE_TYPES && !entry.readOnly

    /** False for hidden fields and for fields whose dependency is not met. */
    fun isVisible(entry: ConfigEntry): Boolean {
        if (entry.hidden) return false
        val dependency = entry.dependsOn ?: return true
        val current = value(dependency)
        return when {
            entry.dependsOnValue != null -> same(current, clean(entry.dependsOnValue))
            entry.dependsOnValueNot != null -> !same(current, clean(entry.dependsOnValueNot))
            else -> truthy(current)
        }
    }

    /** The fields to draw, in the server's order. Advanced ones only when [showAdvanced]. */
    fun visibleEntries(showAdvanced: Boolean): List<ConfigEntry> =
        entries.filter { isVisible(it) && (showAdvanced || !it.advanced) }

    fun hasAdvanced(): Boolean = entries.any { it.advanced && isVisible(it) }

    /** Visible, editable, required fields with no value. */
    fun missingRequired(): List<ConfigEntry> =
        entries.filter { isVisible(it) && isEditable(it) && it.required && it.type != "boolean" && isEmpty(value(it.key)) }

    /** A short problem with the current value of [entry], or null. */
    fun problem(entry: ConfigEntry): String? {
        val v = value(entry.key) ?: return null
        if (entry.type != "integer" && entry.type != "float") return null
        val text = (v as? JsonPrimitive)?.content ?: return null
        val number = if (entry.type == "integer") text.toLongOrNull()?.toDouble() else text.toDoubleOrNull()
        if (number == null) return if (entry.type == "integer") "Enter a whole number." else "Enter a number."
        val range = entry.range
        if (range != null && range.size == 2 && (number < range[0] || number > range[1])) return "Enter a value from ${range[0]} to ${range[1]}."
        return null
    }

    /** Every problem at once, by field key. */
    fun problems(): Map<String, String> {
        val result = linkedMapOf<String, String>()
        entries.filter { isVisible(it) && isEditable(it) }.forEach { e -> problem(e)?.let { result[e.key] = it } }
        missingRequired().forEach { e -> result.putIfAbsent(e.key, "Required.") }
        return result
    }

    fun isChanged(entry: ConfigEntry): Boolean = isEditable(entry) && !same(value(entry.key), initial[entry.key])

    fun hasChanges(): Boolean = entries.any { isChanged(it) }

    /** Every editable, visible field that has a value: what a setup form step sends (a new provider has no earlier values). */
    fun submitValues(): Map<String, JsonElement?> =
        entries.filter { isVisible(it) && isEditable(it) }.mapNotNull { e -> value(e.key)?.let { e.key to it } }.toMap()

    /** Only what the person changed, ready for `config/providers/save`. */
    fun changedValues(): Map<String, JsonElement?> =
        entries.filter { isChanged(it) }.associate { it.key to value(it.key) }

    companion object {
        val EDITABLE_TYPES = setOf("boolean", "string", "secure_string", "integer", "float", "splitted_string")

        private fun clean(v: JsonElement?): JsonElement? = if (v == null || v is JsonNull) null else v

        private fun isEmpty(v: JsonElement?): Boolean = when (v) {
            null -> true
            is JsonArray -> v.isEmpty()
            is JsonPrimitive -> v.contentOrNull.isNullOrBlank()
            else -> false
        }

        private fun truthy(v: JsonElement?): Boolean = when (v) {
            null -> false
            is JsonArray -> v.isNotEmpty()
            is JsonPrimitive -> v.booleanOrNull ?: (v.doubleOrNull?.let { it != 0.0 } ?: !v.content.isNullOrBlank())
            else -> true
        }

        /** Same value, comparing numbers and text by their text so 1 and "1" match. */
        private fun same(a: JsonElement?, b: JsonElement?): Boolean = when {
            a == null && b == null -> true
            a == null || b == null -> false
            a is JsonPrimitive && b is JsonPrimitive -> a.content == b.content
            else -> a.toString() == b.toString()
        }
    }
}
