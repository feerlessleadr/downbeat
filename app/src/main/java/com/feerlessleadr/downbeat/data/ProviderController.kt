package com.feerlessleadr.downbeat.data

import com.feerlessleadr.downbeat.ma.MaApi
import com.feerlessleadr.downbeat.ma.MaException
import kotlinx.coroutines.CancellationException

/**
 * Changes to configured providers (administrators only). Every function returns null on success or a short sentence to
 * show the person. Nothing here has been tried on the real server yet: see docs/provider-setup-research.md.
 */
class ProviderController(private val repo: MusicRepository) {
    suspend fun reload(instanceId: String): String? = run { it.reloadProvider(instanceId) }

    suspend fun setEnabled(row: ProviderRow, on: Boolean): String? = run { it.setProviderEnabled(row.domain, row.instanceId, on) }

    suspend fun remove(instanceId: String): String? = run { it.removeProvider(instanceId) }

    suspend fun sync(instanceId: String): String? = run { it.syncProvider(instanceId) }

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
