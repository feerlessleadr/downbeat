package com.feerlessleadr.downbeat.data

import com.feerlessleadr.downbeat.ma.AuthToken
import com.feerlessleadr.downbeat.ma.BackgroundTask
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Shows a date from the server. It may be a number (seconds or milliseconds since 1970) or text (an ISO date and time);
 * anything else, or nothing, gives "never". Pure, so it can be tested.
 */
fun formatWhen(value: JsonElement?, zone: ZoneId = ZoneId.systemDefault()): String {
    val primitive = value as? JsonPrimitive ?: return "never"
    if (value is JsonNull) return "never"
    val text = primitive.contentOrNull ?: return "never"
    val instant: Instant? = primitive.doubleOrNull?.takeIf { !primitive.isString }?.let { n ->
        // Seconds since 1970 are about 1.7 billion; milliseconds are a thousand times more.
        if (n > 100_000_000_000.0) Instant.ofEpochMilli(n.toLong()) else Instant.ofEpochMilli((n * 1000).toLong())
    } ?: runCatching { Instant.parse(text) }.getOrNull()
        ?: runCatching { java.time.LocalDateTime.parse(text).atZone(zone).toInstant() }.getOrNull()
    return instant?.let { DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a").format(it.atZone(zone)) } ?: text.ifBlank { "never" }
}

/** A token's kind in words. */
fun tokenKind(token: AuthToken): String = if (token.isLongLived) "Long-lived" else "Sign-in session"

/** True when this token is the one the app itself made when signing in (named "Downbeat xxxxxx"). */
fun isThisPhone(token: AuthToken, thisTokenName: String?): Boolean = thisTokenName != null && token.name == thisTokenName

/** The last [lines] lines of a log, so a large log stays readable. */
fun logTail(log: String, lines: Int = 200): String {
    val all = log.trimEnd().lines()
    return if (all.size <= lines) all.joinToString("\n") else all.takeLast(lines).joinToString("\n")
}

/** One line about a task for the list: its status in the server's word, progress, and when it last and next runs. */
fun taskSummary(task: BackgroundTask, zone: ZoneId = ZoneId.systemDefault()): String {
    val parts = mutableListOf<String>()
    task.status.takeIf { it.isNotBlank() }?.let { parts += it.replace('_', ' ').replaceFirstChar { c -> c.uppercase() } }
    task.progress?.takeIf { it > 0.0 && it < 1.0 }?.let { parts += "${(it * 100).toInt().coerceIn(0, 100)} percent" }
    task.progressText?.takeIf { it.isNotBlank() }?.let { parts += it }
    if (task.lastRun != null && task.lastRun !is JsonNull) parts += "last run ${formatWhen(task.lastRun, zone)}"
    if (task.nextRun != null && task.nextRun !is JsonNull) parts += "next ${formatWhen(task.nextRun, zone)}"
    return parts.joinToString(" · ")
}

/** The text of a task's last error: a string, or the message inside an object; null when there is none. */
fun taskError(task: BackgroundTask): String? = when (val e = task.lastError) {
    null, is JsonNull -> null
    is JsonPrimitive -> e.contentOrNull?.takeIf { it.isNotBlank() }
    is JsonObject -> (e["message"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: e.toString()
    else -> e.toString()
}
