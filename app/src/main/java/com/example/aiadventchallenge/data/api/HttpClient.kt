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
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class HttpClient private constructor() {
    private val baseClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun post(
        url: String,
        requestJson: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Long? = null
    ): Result<String> = suspendCancellableCoroutine { continuation ->
        val body = requestJson.toRequestBody("application/json".toMediaType())

        val requestBuilder = Request.Builder()
            .url(url)
            .post(body)

        headers.forEach { (name, value) ->
            requestBuilder.addHeader(name, value)
        }

        val httpRequest = requestBuilder.build()

        val call = client(timeoutMs).newCall(httpRequest)

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

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Long? = null
    ): Result<String> = suspendCancellableCoroutine { continuation ->
        val requestBuilder = Request.Builder()
            .url(url)
            .get()

        headers.forEach { (name, value) ->
            requestBuilder.addHeader(name, value)
        }

        val call = client(timeoutMs).newCall(requestBuilder.build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) {
                    continuation.resume(Result.failure(e))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { httpResponse ->
                    val responseBody = httpResponse.body.string()
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

    private fun client(timeoutMs: Long?): OkHttpClient {
        if (timeoutMs == null) return baseClient
        return baseClient.newBuilder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()
    }

    class HttpException(val code: Int, val body: String) : Exception("HTTP $code: $body")

    companion object {
        @Volatile
        private var instance: HttpClient? = null

        fun getInstance(): HttpClient {
            return instance ?: synchronized(this) {
                instance ?: HttpClient().also { instance = it }
            }
        }

        private const val TAG = "HttpClient"
    }
}
