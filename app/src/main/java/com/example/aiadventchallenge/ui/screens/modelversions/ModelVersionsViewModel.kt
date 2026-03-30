package com.example.aiadventchallenge.ui.screens.modelversions

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiadventchallenge.domain.model.ModelComparisonBatch
import com.example.aiadventchallenge.domain.model.ModelStrength
import com.example.aiadventchallenge.domain.usecase.AskModelUseCase
import com.example.aiadventchallenge.data.export.ModelResultsExporter
import com.example.aiadventchallenge.data.export.ConclusionsGenerator
import com.example.aiadventchallenge.data.config.ModelVersionsConfig
import com.example.aiadventchallenge.domain.repository.AiRepository
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.RequestConfig
import com.example.aiadventchallenge.domain.model.RequestType
import com.example.aiadventchallenge.data.config.Prompts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ModelVersionsViewModel(
    private val askModelUseCase: AskModelUseCase,
    private val resultsExporter: ModelResultsExporter,
    private val aiRepository: AiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _loadingModels = MutableStateFlow<Set<ModelStrength>>(emptySet())
    val loadingModels: StateFlow<Set<ModelStrength>> = _loadingModels.asStateFlow()

    private val _showConclusions = MutableStateFlow(false)
    val showConclusions: StateFlow<Boolean> = _showConclusions.asStateFlow()

    private val _llmAnalysisState = MutableStateFlow<LlmAnalysisState>(LlmAnalysisState.Idle)
    val llmAnalysisState: StateFlow<LlmAnalysisState> = _llmAnalysisState.asStateFlow()

    private val _showLlmAnalysis = MutableStateFlow(false)
    val showLlmAnalysis: StateFlow<Boolean> = _showLlmAnalysis.asStateFlow()

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState
        data class Success(val batch: ModelComparisonBatch) : UiState
        data class Error(val message: String) : UiState
    }

    sealed interface LlmAnalysisState {
        data object Idle : LlmAnalysisState
        data object Loading : LlmAnalysisState
        data class Success(val analysis: String) : LlmAnalysisState
        data class Error(val message: String) : LlmAnalysisState
    }

    fun sendPrompt(userInput: String) {
        if (userInput.isBlank()) return
        if (_uiState.value is UiState.Loading) return

        viewModelScope.launch {
            _uiState.value = UiState.Loading
            _loadingModels.value = setOf(
                ModelStrength.WEAK,
                ModelStrength.MEDIUM,
                ModelStrength.STRONG
            )

            try {
                val batch = askModelUseCase(userInput)
                _loadingModels.value = emptySet()
                
                if (batch.results.values.all { it.error != null }) {
                    _uiState.value = UiState.Error("Все модели вернули ошибки")
                } else {
                    _uiState.value = UiState.Success(batch)
                    exportResults(batch)
                }
            } catch (e: Exception) {
                _loadingModels.value = emptySet()
                _uiState.value = UiState.Error(e.message ?: "Неизвестная ошибка")
            }
        }
    }

    fun toggleConclusions() {
        _showConclusions.value = !_showConclusions.value
    }

    fun compareWithLLM() {
        val currentState = _uiState.value
        if (currentState !is UiState.Success) {
            return
        }

        val batch = currentState.batch
        val validResults = batch.results.values.filter { it.error == null }

        if (validResults.size < 2) {
            _llmAnalysisState.value = LlmAnalysisState.Error("Недостаточно успешных ответов для анализа (нужно минимум 2)")
            _showLlmAnalysis.value = true
            return
        }

        viewModelScope.launch {
            _llmAnalysisState.value = LlmAnalysisState.Loading

            try {
                val strongModel = ModelVersionsConfig.getModel(ModelStrength.STRONG)
                if (strongModel == null) {
                    _llmAnalysisState.value = LlmAnalysisState.Error("Сильная модель не найдена в конфигурации")
                    _showLlmAnalysis.value = true
                    return@launch
                }

                val analysisPrompt = buildAnalysisPrompt(batch, validResults)

                val config = RequestConfig(
                    systemPrompt = "Ты - эксперт в анализе качества ответов языковых моделей. Проанализируй ответы и дай объективную оценку.",
                    modelId = strongModel.modelId,
                    maxTokens = 3000
                )

                val result = aiRepository.askWithUsage(analysisPrompt, null, config, RequestType.COMPARISON)

                when (result) {
                    is ChatResult.Success -> {
                        _llmAnalysisState.value = LlmAnalysisState.Success(result.data.content)
                        _showLlmAnalysis.value = true
                    }
                    is ChatResult.Error -> {
                        _llmAnalysisState.value = LlmAnalysisState.Error(result.message)
                        _showLlmAnalysis.value = true
                    }
                }
            } catch (e: Exception) {
                _llmAnalysisState.value = LlmAnalysisState.Error(e.message ?: "Неизвестная ошибка при анализе")
                _showLlmAnalysis.value = true
            }
        }
    }

    private fun buildAnalysisPrompt(batch: ModelComparisonBatch, validResults: List<com.example.aiadventchallenge.domain.model.ModelComparisonResult>): String {
        return buildString {
            appendLine("Проанализируй ответы трёх моделей на один и тот же запрос и сравни их качество.")
            appendLine()
            appendLine("## Исходный запрос:")
            appendLine(batch.prompt)
            appendLine()
            appendLine("## Ответы моделей:")
            appendLine()
            
            validResults.sortedBy { it.modelVersion.strength }.forEach { result ->
                appendLine("### ${result.modelVersion.label} (${result.modelVersion.modelName})")
                appendLine("- Время ответа: ${result.latencyMs} мс")
                appendLine("- Токены: ${result.totalTokens ?: "N/A"}")
                appendLine("- Стоимость: $${String.format("%.6f", result.cost)}")
                appendLine("- Ответ:")
                appendLine(result.response)
                appendLine()
            }
            
            appendLine("## Задача:")
            appendLine("Сравни ответы по следующим критериям:")
            appendLine("👉 Качество ответов (полнота, точность, полезность)")
            appendLine("👉 Скорость (время ответа)")
            appendLine("👉 Ресурсоёмкость (количество токенов и стоимость)")
            appendLine()
            appendLine("Дай краткий, но информативный анализ с выводами о том, какая модель лучше справилась и почему.")
        }
    }

    fun toggleLlmAnalysis() {
        _showLlmAnalysis.value = !_showLlmAnalysis.value
    }

    fun getConclusions(): String {
        val currentState = _uiState.value
        if (currentState !is UiState.Success) {
            return "Нет данных для анализа"
        }

        return ConclusionsGenerator.buildSummaryMarkdown(currentState.batch)
    }

    private fun exportResults(batch: ModelComparisonBatch) {
        viewModelScope.launch {
            try {
                resultsExporter.export(batch)
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting results: ${e.message}", e)
            }
        }
    }

    fun reset() {
        _uiState.value = UiState.Idle
        _loadingModels.value = emptySet()
        _llmAnalysisState.value = LlmAnalysisState.Idle
        _showLlmAnalysis.value = false
    }

    companion object {
        private const val TAG = "ModelVersionsVM"
    }
}
