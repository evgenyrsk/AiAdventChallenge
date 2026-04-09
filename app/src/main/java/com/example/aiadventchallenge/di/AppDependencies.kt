package com.example.aiadventchallenge.di

import android.annotation.SuppressLint
import com.example.aiadventchallenge.data.agent.ChatAgent
import com.example.aiadventchallenge.data.api.ApiConfig
import com.example.aiadventchallenge.data.api.HttpClient
import com.example.aiadventchallenge.data.parser.ResponseParser
import com.example.aiadventchallenge.data.repository.AiRepositoryImpl
import com.example.aiadventchallenge.data.repository.AiRequestRepository
import com.example.aiadventchallenge.data.repository.InvariantRepositoryImpl
import com.example.aiadventchallenge.data.local.database.AppDatabase
import com.example.aiadventchallenge.data.mcp.McpJsonRpcClient
import com.example.aiadventchallenge.data.mcp.McpRepository
import com.example.aiadventchallenge.domain.repository.AiRepository
import com.example.aiadventchallenge.domain.repository.InvariantRepository
import com.example.aiadventchallenge.domain.usecase.AskAiUseCase
import com.example.aiadventchallenge.domain.usecase.AskWithPromptModeUseCase
import com.example.aiadventchallenge.domain.usecase.AskModelUseCase
import com.example.aiadventchallenge.domain.usecase.CompareResultsUseCase
import com.example.aiadventchallenge.domain.usecase.CompareTemperatureResultsUseCase
import com.example.aiadventchallenge.domain.usecase.TemperatureUseCase
import com.example.aiadventchallenge.domain.usecase.CreateSummaryUseCase
import com.example.aiadventchallenge.domain.usecase.mcp.GetMcpToolsUseCase
import com.example.aiadventchallenge.domain.usecase.mcp.CallMcpToolUseCase
import com.example.aiadventchallenge.domain.validation.InvariantValidator
import com.example.aiadventchallenge.domain.validation.InvariantValidatorImpl
import com.example.aiadventchallenge.domain.parser.UserResponseParser
import com.example.aiadventchallenge.domain.parser.UserResponseParserImpl
import com.example.aiadventchallenge.domain.detector.NutritionRequestDetector
import com.example.aiadventchallenge.domain.detector.NutritionRequestDetectorImpl
import com.example.aiadventchallenge.domain.detector.FitnessRequestDetector
import com.example.aiadventchallenge.domain.detector.FitnessRequestDetectorImpl
import com.example.aiadventchallenge.domain.mcp.McpToolOrchestrator
import com.example.aiadventchallenge.domain.mcp.McpToolOrchestratorImpl
import android.content.Context

@SuppressLint("StaticFieldLeak")
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

    private val mcpJsonRpcClient: McpJsonRpcClient by lazy {
        McpJsonRpcClient(
            serverUrl = "http://10.0.2.2:8080"
        )
    }

    val mcpRepository: McpRepository by lazy {
        McpRepository(
            client = mcpJsonRpcClient
        )
    }

    val getMcpToolsUseCase: GetMcpToolsUseCase by lazy {
        GetMcpToolsUseCase(
            mcpRepository = mcpRepository
        )
    }

    val callMcpToolUseCase: CallMcpToolUseCase by lazy {
        CallMcpToolUseCase(
            mcpRepository = mcpRepository
        )
    }

    val nutritionRequestDetector: NutritionRequestDetector by lazy {
        NutritionRequestDetectorImpl()
    }

    val fitnessRequestDetector: FitnessRequestDetector by lazy {
        FitnessRequestDetectorImpl()
    }

    val mcpToolOrchestrator: McpToolOrchestrator by lazy {
        McpToolOrchestratorImpl(
            callMcpToolUseCase = callMcpToolUseCase,
            nutritionRequestDetector = nutritionRequestDetector,
            fitnessRequestDetector = fitnessRequestDetector
        )
    }

    val userResponseParser: UserResponseParser by lazy {
        UserResponseParserImpl()
    }
}
