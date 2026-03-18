package com.example.aiadventchallenge.data.api

import com.example.aiadventchallenge.BuildConfig

data class ApiConfig(
    val baseUrl: String = "https://openrouter.ai/api/v1/chat/completions",
    val model: String = "nvidia/nemotron-3-super-120b-a12b:free",
    val apiKey: String = BuildConfig.AI_API_KEY
)
