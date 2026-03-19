package com.example.aiadventchallenge.di

import com.example.aiadventchallenge.data.api.ApiConfig
import com.example.aiadventchallenge.data.api.HttpClient
import com.example.aiadventchallenge.data.parser.ResponseParser
import com.example.aiadventchallenge.data.repository.AiRepositoryImpl
import com.example.aiadventchallenge.domain.repository.AiRepository
import com.example.aiadventchallenge.domain.usecase.AskAiUseCase
import com.example.aiadventchallenge.domain.usecase.AskWithPromptModeUseCase
import com.example.aiadventchallenge.domain.usecase.CompareResultsUseCase

object AppDependencies {
    private val apiConfig: ApiConfig = ApiConfig()

    private val httpClient: HttpClient by lazy {
        HttpClient.getInstance(config = apiConfig)
    }

    private val responseParser: ResponseParser by lazy {
        ResponseParser()
    }

    private val repository: AiRepository by lazy {
        AiRepositoryImpl(
            httpClient = httpClient,
            config = apiConfig,
            responseParser = responseParser
        )
    }

    val askAiUseCase: AskAiUseCase by lazy {
        AskAiUseCase(repository = repository)
    }

    val askWithPromptModeUseCase: AskWithPromptModeUseCase by lazy {
        AskWithPromptModeUseCase(repository = repository)
    }

    val compareResultsUseCase: CompareResultsUseCase by lazy {
        CompareResultsUseCase(repository = repository)
    }
}
