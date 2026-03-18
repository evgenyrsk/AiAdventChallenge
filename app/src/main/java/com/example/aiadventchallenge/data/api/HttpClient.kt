package com.example.aiadventchallenge.data.api

import android.util.Log

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume

class HttpClient(
    private val client: OkHttpClient = OkHttpClient(),
    private val config: ApiConfig
) {
    suspend fun post(requestJson: String): Result<String> = suspendCancellableCoroutine { continuation ->
        val body = requestJson.toRequestBody("application/json".toMediaType())

        val httpRequest = Request.Builder()
            .url(config.baseUrl)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://example.com")
            .addHeader("X-Title", "AiAdventChallenge")
            .post(body)
            .build()

        val call = client.newCall(httpRequest)

        continuation.invokeOnCancellation {
            call.cancel()
        }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) {
                    continuation.resume(Result.failure(e))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { httpResponse ->
                    val responseBody = httpResponse.body.string()
                    Log.d(TAG, "Response: $responseBody")

                    if (!continuation.isCancelled) {
                        if (httpResponse.isSuccessful) {
                            continuation.resume(Result.success(responseBody))
                        } else {
                            continuation.resume(Result.failure(HttpException(httpResponse.code, responseBody)))
                        }
                    }
                }
            }
        })
    }

    class HttpException(val code: Int, val body: String) : Exception("HTTP $code")

    companion object {
        private const val TAG = "HttpClient"
    }
}
