package com.example.aiadventchallenge.di

import com.example.aiadventchallenge.data.agent.ChatAgent
import com.example.aiadventchallenge.data.api.ApiConfig
import com.example.aiadventchallenge.data.api.HttpClient
import com.example.aiadventchallenge.data.parser.ResponseParser
import com.example.aiadventchallenge.data.repository.AiRepositoryImpl
import com.example.aiadventchallenge.data.repository.AiRequestRepository
import com.example.aiadventchallenge.data.local.database.AppDatabase
import com.example.aiadventchallenge.domain.repository.AiRepository
import com.example.aiadventchallenge.domain.usecase.AskAiUseCase
import com.example.aiadventchallenge.domain.usecase.AskWithPromptModeUseCase
import com.example.aiadventchallenge.domain.usecase.AskModelUseCase
import com.example.aiadventchallenge.domain.usecase.CompareResultsUseCase
import com.example.aiadventchallenge.domain.usecase.CompareTemperatureResultsUseCase
import com.example.aiadventchallenge.domain.usecase.TemperatureUseCase
import com.example.aiadventchallenge.domain.usecase.CreateSummaryUseCase
import android.content.Context

object AppDependencies {
    private lateinit var context: Context

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    private val apiConfig: ApiConfig = ApiConfig()

    private val httpClient: HttpClient by lazy {
        HttpClient.getInstance(config = apiConfig)
    }

    private val responseParser: ResponseParser by lazy {
        ResponseParser()
    }

    private val database: AppDatabase by lazy {
        AppDatabase.getDatabase(context)
    }

    private val aiRequestRepository: AiRequestRepository by lazy {
        AiRequestRepository(
            aiRequestDao = database.aiRequestDao()
        )
    }

    val repository: AiRepository by lazy {
        AiRepositoryImpl(
            httpClient = httpClient,
            config = apiConfig,
            responseParser = responseParser,
            aiRequestRepository = aiRequestRepository
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

    val temperatureUseCase: TemperatureUseCase by lazy {
        TemperatureUseCase(repository = repository)
    }

    val compareTemperatureResultsUseCase: CompareTemperatureResultsUseCase by lazy {
        CompareTemperatureResultsUseCase(repository = repository)
    }

    val askModelUseCase: AskModelUseCase by lazy {
        AskModelUseCase(repository = repository)
    }

    val chatAgent: ChatAgent by lazy {
        ChatAgent(
            askAiUseCase = askAiUseCase,
            repository = repository
        )
    }

    val createSummaryUseCase: CreateSummaryUseCase by lazy {
        CreateSummaryUseCase(repository = repository)
    }
}
