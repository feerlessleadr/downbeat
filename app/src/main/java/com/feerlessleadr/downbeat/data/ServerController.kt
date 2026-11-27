package com.feerlessleadr.downbeat.data

import com.feerlessleadr.downbeat.ma.MaApi
import com.feerlessleadr.downbeat.ma.MaException
import kotlinx.coroutines.CancellationException

/** What reading a text from the server gave: the text, or a sentence saying why not. */
sealed interface TextResult {
    data class Text(val text: String) : TextResult
    data class Failed(val message: String) : TextResult
}

/**
 * Server admin actions (administrators only): background tasks, library sync, access tokens. Every action returns null on
 * success or a short sentence to show the person (the server's own message when it gave one). Nothing here has been tried
 * on the real server yet: see docs/server-admin-research.md.
 */
class ServerController(private val repo: MusicRepository) {
    suspend fun runTask(id: String): String? = run { it.runTask(id) }
    suspend fun cancelTask(id: String): String? = run { it.cancelTask(id) }
    suspend fun retryTask(id: String): String? = run { it.retryTask(id) }
    suspend fun clearFinishedTasks(): String? = run { it.clearFinishedTasks() }
    suspend fun setTaskEnabled(id: String, enabled: Boolean): String? = run { it.setTaskEnabled(id, enabled) }
    suspend fun syncAllLibraries(): String? = run { it.syncAllLibraries() }
    suspend fun revokeToken(tokenId: String): String? = run { it.revokeToken(tokenId) }

    suspend fun taskLog(id: String): TextResult {
        val api = repo.api ?: return TextResult.Failed("Not connected to the server.")
        return try {
            TextResult.Text(api.taskLog(id))
        } catch (e: CancellationException) {
            throw e
        } catch (e: MaException) {
            TextResult.Failed(e.message ?: "The server refused that.")
        } catch (e: Exception) {
            TextResult.Failed("Something went wrong. Try again.")
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
