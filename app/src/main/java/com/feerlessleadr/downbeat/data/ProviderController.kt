package com.feerlessleadr.downbeat.data

import com.feerlessleadr.downbeat.ma.MaApi
import com.feerlessleadr.downbeat.ma.MaException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import com.feerlessleadr.downbeat.ma.ConfigEntry
import com.feerlessleadr.downbeat.ma.MaJson

/**
 * Changes to configured providers (administrators only). Every function returns null on success or a short sentence to
 * show the person. Nothing here has been tried on the real server yet: see docs/provider-setup-research.md.
 */
class ProviderController(private val repo: MusicRepository) {
    suspend fun reload(instanceId: String): String? = run { it.reloadProvider(instanceId) }

    suspend fun setEnabled(row: ProviderRow, on: Boolean): String? = run { it.setProviderEnabled(row.domain, row.instanceId, on) }

    suspend fun remove(instanceId: String): String? = run { it.removeProvider(instanceId) }

    suspend fun sync(instanceId: String): String? = run { it.syncProvider(instanceId) }

    /** The settings form of one provider. */
    suspend fun entries(instanceId: String): EntriesResult {
        val api = repo.api ?: return EntriesResult.Failed("Not connected to the server.")
        return try {
            EntriesResult.Loaded(api.providerEntries(instanceId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: MaException) {
            EntriesResult.Failed(e.message ?: "The server refused that.")
        } catch (e: Exception) {
            EntriesResult.Failed("Could not read the settings. Try again.")
        }
    }

    /** Saves only the changed values. */
    suspend fun save(row: ProviderRow, values: Map<String, JsonElement?>): String? =
        run { it.saveProviderValues(row.domain, row.instanceId, values) }

    /** Runs a button of the settings form. */
    suspend fun action(instanceId: String, action: String): ActionOutcome {
        val api = repo.api ?: return ActionOutcome(error = "Not connected to the server.")
        return try {
            parseActionResult(api.invokeProviderAction(instanceId, action))
        } catch (e: CancellationException) {
            throw e
        } catch (e: MaException) {
            ActionOutcome(error = e.message ?: "The server refused that.")
        } catch (e: Exception) {
            ActionOutcome(error = "Something went wrong. Try again.")
        }
    }

    private suspend fun run(block: suspend (MaApi) -> Unit): String? {
        val api = repo.api ?: return "Not connected to the server."
        return try {
            block(api)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: MaException) {
            e.message ?: "The server refused that."
        } catch (e: Exception) {
            "Something went wrong. Try again."
        }
    }
}

sealed interface EntriesResult {
    data class Loaded(val entries: List<ConfigEntry>) : EntriesResult
    data class Failed(val message: String) : EntriesResult
}

/** What a form button did: a message to show, a link to open, fields to redraw the form with, or an error. */
data class ActionOutcome(
    val message: String? = null,
    val openUrl: String? = null,
    val entries: List<ConfigEntry>? = null,
    val error: String? = null,
)

/** The server answers an action with a message or link (an object), a list of fields, or nothing. */
internal fun parseActionResult(result: JsonElement): ActionOutcome = when (result) {
    is JsonArray -> ActionOutcome(
        entries = try {
            MaJson.decodeFromJsonElement(ListSerializer(ConfigEntry.serializer()), result).takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        },
    )
    is JsonObject -> ActionOutcome(
        message = (result["message"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() },
        openUrl = (result["open_url"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() },
    )
    else -> ActionOutcome()
}
