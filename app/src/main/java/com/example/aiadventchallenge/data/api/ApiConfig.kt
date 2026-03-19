package com.example.aiadventchallenge.data.api

import com.example.aiadventchallenge.BuildConfig

data class ApiConfig(
    val baseUrl: String = "https://routerai.ru/api/v1/chat/completions",
    //val model: String = "deepseek/deepseek-v3.2",
    val model: String = "minimax/minimax-m2.7",
    val apiKey: String = BuildConfig.AI_API_KEY
)
