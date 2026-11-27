package com.feerlessleadr.downbeat.ma

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface LoginResult {
    data class Success(val token: String) : LoginResult
    data object InvalidCredentials : LoginResult
    /** The server could not be reached or answered with something unexpected. [message] is safe to show. */
    data class Failed(val message: String) : LoginResult
}

/**
 * Username and password sign-in with the builtin provider: POST <server>/auth/login.
 * Request and response shape come from the server's own /api-docs/openapi.json (provider_id and credentials in;
 * success, token and user out; 400 for bad credentials). NOT yet verified against a live login.
 * The password is only ever in the request body and is never logged or stored.
 */
object MaAuth {
    private val JSON = "application/json".toMediaType()

    suspend fun login(http: OkHttpClient, serverUrl: String, username: String, password: String): LoginResult {
        val base = serverUrl.trim().toHttpUrlOrNull() ?: return LoginResult.Failed("That address isn't valid.")
        val body = buildJsonObject {
            put("provider_id", "builtin")
            put("credentials", buildJsonObject { put("username", username); put("password", password) })
        }.toString().toRequestBody(JSON)
        val request = Request.Builder()
            .url(base.newBuilder().encodedPath("/auth/login").query(null).build())
            .post(body)
            .build()
        return try {
            http.newCall(request).await().use { response ->
                val text = response.body?.string().orEmpty()
                when {
                    response.code in listOf(400, 401, 403) -> LoginResult.InvalidCredentials
                    !response.isSuccessful -> LoginResult.Failed("The server answered with error ${response.code}.")
                    else -> parseToken(text)
                }
            }
        } catch (e: IOException) {
            LoginResult.Failed("Couldn't reach the server. Check the address and your connection.")
        }
    }

    private fun parseToken(text: String): LoginResult = try {
        val obj: JsonObject = MaJson.parseToJsonElement(text).jsonObject
        val token = obj["token"]?.jsonPrimitive?.contentOrNull
        if (token.isNullOrBlank()) LoginResult.InvalidCredentials else LoginResult.Success(token)
    } catch (e: Exception) {
        LoginResult.Failed("The server's answer wasn't understood.")
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
            override fun onResponse(call: Call, response: Response) = cont.resume(response)
        })
    }
}
