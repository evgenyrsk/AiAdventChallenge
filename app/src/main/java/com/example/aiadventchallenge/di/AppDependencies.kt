package com.example.aiadventchallenge.di

import android.annotation.SuppressLint
import com.example.aiadventchallenge.data.agent.ChatAgent
import com.example.aiadventchallenge.data.api.ApiConfig
import com.example.aiadventchallenge.data.api.HttpClient
import com.example.aiadventchallenge.data.parser.ResponseParser
import com.example.aiadventchallenge.data.rag.McpRagRetriever
import com.example.aiadventchallenge.data.repository.AiRepositoryImpl
import com.example.aiadventchallenge.data.repository.AiRequestRepository
import com.example.aiadventchallenge.data.repository.InvariantRepositoryImpl
import com.example.aiadventchallenge.data.repository.LocalOllamaRepository
import com.example.aiadventchallenge.data.repository.PrivateAiServiceRepository
import com.example.aiadventchallenge.data.repository.RoutingAiRepository
import com.example.aiadventchallenge.data.local.database.AppDatabase
import com.example.aiadventchallenge.data.mcp.MultiServerRepository
import com.example.aiadventchallenge.data.mcp.McpServerConfig
import com.example.aiadventchallenge.data.repository.ChatSettingsRepository as DataChatSettingsRepository
import com.example.aiadventchallenge.domain.repository.AiRepository
import com.example.aiadventchallenge.domain.repository.ChatSettingsRepository
import com.example.aiadventchallenge.domain.repository.InvariantRepository
import com.example.aiadventchallenge.domain.llm.LocalLlmProfileResolver
import com.example.aiadventchallenge.domain.usecase.AskAiUseCase
import com.example.aiadventchallenge.domain.usecase.AskWithPromptModeUseCase
import com.example.aiadventchallenge.domain.usecase.AskModelUseCase
import com.example.aiadventchallenge.domain.usecase.CompareResultsUseCase
import com.example.aiadventchallenge.domain.usecase.CompareLocalOptimizationUseCase
import com.example.aiadventchallenge.domain.usecase.CompareTemperatureResultsUseCase
import com.example.aiadventchallenge.domain.usecase.PrepareRagRequestUseCase
import com.example.aiadventchallenge.domain.usecase.RunRagEvaluationUseCase
import com.example.aiadventchallenge.domain.usecase.TemperatureUseCase
import com.example.aiadventchallenge.domain.usecase.CreateSummaryUseCase

import com.example.aiadventchallenge.domain.validation.InvariantValidator
import com.example.aiadventchallenge.domain.validation.InvariantValidatorImpl
import com.example.aiadventchallenge.domain.mcp.McpToolOrchestrator
import com.example.aiadventchallenge.domain.mcp.MultiServerOrchestrator
import com.example.aiadventchallenge.domain.rag.DefaultQueryRewriter
import com.example.aiadventchallenge.domain.rag.RagPromptBuilder
import com.example.aiadventchallenge.domain.rag.QueryRewriter
import android.content.Context
import com.example.aiadventchallenge.domain.usecase.RewriteQueryUseCase

@SuppressLint("StaticFieldLeak")
object AppDependencies {
    private lateinit var context: Context

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    private val apiConfig: ApiConfig = ApiConfig()

    private val httpClient: HttpClient by lazy {
        HttpClient.getInstance()
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

    val chatSettingsRepository: ChatSettingsRepository by lazy {
        DataChatSettingsRepository(database.chatSettingsDao())
    }

    val remoteRepository: AiRepository by lazy {
        AiRepositoryImpl(
            httpClient = httpClient,
            config = apiConfig,
            responseParser = responseParser,
            aiRequestRepository = aiRequestRepository
        )
    }

    val localOllamaRepository: LocalOllamaRepository by lazy {
        LocalOllamaRepository(
            httpClient = httpClient,
            aiRequestRepository = aiRequestRepository
        )
    }

    val privateAiServiceRepository: PrivateAiServiceRepository by lazy {
        PrivateAiServiceRepository(
            httpClient = httpClient,
            aiRequestRepository = aiRequestRepository
        )
    }

    val localLlmProfileResolver: LocalLlmProfileResolver by lazy {
        LocalLlmProfileResolver()
    }

    val repository: AiRepository by lazy {
        RoutingAiRepository(
            chatSettingsRepository = chatSettingsRepository,
            remoteRepository = remoteRepository,
            localOllamaRepository = localOllamaRepository,
            privateAiServiceRepository = privateAiServiceRepository
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
            repository = repository,
            invariantValidator = invariantValidator
        )
    }

    private val invariantRepository: InvariantRepository by lazy {
        InvariantRepositoryImpl()
    }

    val invariantValidator: InvariantValidator by lazy {
        InvariantValidatorImpl(
            config = invariantRepository.getInvariantConfig()
        )
    }

    val createSummaryUseCase: CreateSummaryUseCase by lazy {
        CreateSummaryUseCase(repository = repository)
    }

    val multiServerRepository: MultiServerRepository by lazy {
        MultiServerRepository(McpServerConfig.getAllServers())
    }

    val ragPromptBuilder: RagPromptBuilder by lazy {
        RagPromptBuilder()
    }

    val queryRewriter: QueryRewriter by lazy {
        DefaultQueryRewriter()
    }

    val rewriteQueryUseCase: RewriteQueryUseCase by lazy {
        RewriteQueryUseCase(queryRewriter)
    }

    val ragRetriever: McpRagRetriever by lazy {
        McpRagRetriever(multiServerRepository)
    }

    val prepareRagRequestUseCase: PrepareRagRequestUseCase by lazy {
        PrepareRagRequestUseCase(
            ragRetriever = ragRetriever,
            ragPromptBuilder = ragPromptBuilder,
            rewriteQueryUseCase = rewriteQueryUseCase
        )
    }

    val compareLocalOptimizationUseCase: CompareLocalOptimizationUseCase by lazy {
        CompareLocalOptimizationUseCase(
            prepareRagRequestUseCase = prepareRagRequestUseCase,
            chatAgent = chatAgent,
            localOllamaRepository = localOllamaRepository,
            chatSettingsRepository = chatSettingsRepository,
            localLlmProfileResolver = localLlmProfileResolver
        )
    }

    val runRagEvaluationUseCase: RunRagEvaluationUseCase by lazy {
        RunRagEvaluationUseCase(compareLocalOptimizationUseCase)
    }

    val multiServerOrchestrator: MultiServerOrchestrator by lazy {
        MultiServerOrchestrator(multiServerRepository)
    }
}
