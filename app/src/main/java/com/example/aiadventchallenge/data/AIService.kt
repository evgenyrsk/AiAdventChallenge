package com.example.aiadventchallenge.data

import android.util.Log
import com.example.aiadventchallenge.BuildConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class AIService {

    private val client = OkHttpClient()

    fun ask(prompt: String, callback: (String) -> Unit) {

        val json = JSONObject().apply {
            put("model", "google/gemma-3-4b-it:free")
            put(
                "messages",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    }
                )
            )
        }

        val body = json.toString()
            .toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${BuildConfig.AI_API_KEY}")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://example.com")
            .addHeader("X-Title", "AiAdventChallenge")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                callback("Error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {

                val responseBody = response.body?.string().orEmpty()
                Log.d("OPENROUTER", responseBody)

                if (!response.isSuccessful) {
                    val errorMessage = try {
                        JSONObject(responseBody)
                            .optJSONObject("error")
                            ?.optString("message", "Unknown API error")
                            ?: "Unknown API error"
                    } catch (_: Exception) {
                        "HTTP ${response.code}: $responseBody"
                    }

                    callback("Error: $errorMessage")
                    return
                }

                val text = try {
                    val jsonResponse = JSONObject(responseBody)
                    jsonResponse
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .optString("content", "Empty response")
                } catch (e: Exception) {
                    "Error parsing response: ${e.message}"
                }

                callback(text)
            }
        })
    }
}