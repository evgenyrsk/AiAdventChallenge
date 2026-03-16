package com.example.aiadventchallenge.data

import android.util.Log
import com.example.aiadventchallenge.BuildConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONStringer
import java.io.IOException

class OpenAIService {

    private val client = OkHttpClient()

    fun ask(prompt: String, callback: (String) -> Unit) {

        val json = JSONObject()
        json.put("model", "gpt-5")
        json.put("input", prompt)

        val body = json.toString()
            .toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("https://api.openai.com/v1/responses")
            .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                callback("Error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {

                val responseBody = response.body?.string()

                val jsonResponse = JSONObject(responseBody!!)
                Log.d("OPENAI", responseBody)

                val output = jsonResponse.getJSONArray("output")
                val firstItem = output.getJSONObject(0)
                val content = firstItem.getJSONArray("content")
                val firstContent = content.getJSONObject(0)
                //val text = firstContent.optString("text", "No response")
                val text = jsonResponse.optString("output_text")

                callback(text)
            }
        })
    }
}