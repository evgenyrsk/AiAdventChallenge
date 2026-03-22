package com.example.aiadventchallenge.ui.screens.modelversions

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiadventchallenge.domain.model.ModelComparisonBatch
import com.example.aiadventchallenge.domain.model.ModelStrength
import com.example.aiadventchallenge.domain.usecase.AskModelUseCase
import com.example.aiadventchallenge.data.export.ModelResultsExporter
import com.example.aiadventchallenge.data.export.ConclusionsGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ModelVersionsViewModel(
    private val askModelUseCase: AskModelUseCase,
    private val resultsExporter: ModelResultsExporter
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _loadingModels = MutableStateFlow<Set<ModelStrength>>(emptySet())
    val loadingModels: StateFlow<Set<ModelStrength>> = _loadingModels.asStateFlow()

    private val _showConclusions = MutableStateFlow(false)
    val showConclusions: StateFlow<Boolean> = _showConclusions.asStateFlow()

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState
        data class Success(val batch: ModelComparisonBatch) : UiState
        data class Error(val message: String) : UiState
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
    }

    companion object {
        private const val TAG = "ModelVersionsVM"
    }
}
