package com.example.aiadventchallenge.ui.screens.temperature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiadventchallenge.domain.model.Answer
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.usecase.CompareTemperatureResultsUseCase
import com.example.aiadventchallenge.domain.usecase.TemperatureUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class TemperatureMode(val value: Double, val label: String) {
    ZERO(0.0, "0"),
    LOW(0.35, "0.35"),
    MEDIUM(0.6, "0.6")
}

class TemperatureViewModel(
    private val temperatureUseCase: TemperatureUseCase,
    private val compareTemperatureResultsUseCase: CompareTemperatureResultsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _currentMode = MutableStateFlow(TemperatureMode.ZERO)
    val currentMode: StateFlow<TemperatureMode> = _currentMode.asStateFlow()

    private val _loadingModes = MutableStateFlow<Map<TemperatureMode, Boolean>>(emptyMap())
    val loadingModes: StateFlow<Map<TemperatureMode, Boolean>> = _loadingModes.asStateFlow()

    private val _isComparing = MutableStateFlow(false)
    val isComparing: StateFlow<Boolean> = _isComparing.asStateFlow()

    private val _hasAllAnswers = MutableStateFlow(false)
    val hasAllAnswers: StateFlow<Boolean> = _hasAllAnswers.asStateFlow()

    private val _showingComparison = MutableStateFlow(false)
    val showingComparison: StateFlow<Boolean> = _showingComparison.asStateFlow()

    private val answerCache = mutableMapOf<TemperatureMode, String>()

    private var lastComparedUserInput: String? = null
    private var cachedComparisonResult: String? = null

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState
        data class Success(val answer: String) : UiState
        data object ComparisonLoading : UiState
        data class ComparisonResult(val answer: String) : UiState
        data class Error(val message: String) : UiState
    }

    fun setMode(mode: TemperatureMode) {
        if (_isComparing.value) return

        _currentMode.value = mode
        val savedAnswer = answerCache[mode]
        if (savedAnswer != null) {
            _uiState.value = UiState.Success(savedAnswer)
        } else {
            _uiState.value = UiState.Idle
        }
    }

    fun sendMessage(userInput: String) {
        if (userInput.isBlank()) return
        if (_uiState.value is UiState.Loading || _uiState.value is UiState.ComparisonLoading) return

        lastComparedUserInput = null
        cachedComparisonResult = null
        _showingComparison.value = false

        viewModelScope.launch {
            _uiState.value = UiState.Loading

            TemperatureMode.entries.forEach { mode ->
                _loadingModes.value = _loadingModes.value.toMutableMap().apply { this[mode] = true }
                val result = temperatureUseCase(userInput, mode.value)

                when (result) {
                    is ChatResult.Success -> {
                        answerCache[mode] = result.data.content
                    }
                    is ChatResult.Error -> {
                    }
                }

                _loadingModes.value = _loadingModes.value.toMutableMap().apply { this.remove(mode) }
            }

            _loadingModes.value = emptyMap()

            _hasAllAnswers.value = answerCache.size == TemperatureMode.entries.size

            val currentModeValue = _currentMode.value
            val savedAnswer = answerCache[currentModeValue]
            if (savedAnswer != null) {
                _uiState.value = UiState.Success(savedAnswer)
            } else {
                _uiState.value = UiState.Idle
            }
        }
    }

    fun compareAnswers(userInput: String) {
        if (_uiState.value is UiState.Loading || _uiState.value is UiState.ComparisonLoading) return

        if (cachedComparisonResult != null && lastComparedUserInput == userInput) {
            _uiState.value = UiState.ComparisonResult(cachedComparisonResult!!)
            _showingComparison.value = true
            return
        }

        viewModelScope.launch {
            _isComparing.value = true
            _uiState.value = UiState.ComparisonLoading

            val answers = mutableMapOf<Double, Answer>()
            TemperatureMode.entries.forEach { mode ->
                val answer = answerCache[mode]
                if (answer != null) {
                    answers[mode.value] = Answer(content = answer)
                }
            }

            if (answers.size < TemperatureMode.entries.size) {
                _uiState.value = UiState.Error("Недостаточно данных для сравнения")
                _isComparing.value = false
                return@launch
            }

            when (val result = compareTemperatureResultsUseCase(answers)) {
                is ChatResult.Success -> {
                    _uiState.value = UiState.ComparisonResult(result.data.content)
                    _showingComparison.value = true
                    lastComparedUserInput = userInput
                    cachedComparisonResult = result.data.content
                }
                is ChatResult.Error -> {
                    _uiState.value = UiState.Error(result.message)
                }
            }

            _isComparing.value = false
        }
    }

    fun reset() {
        _uiState.value = UiState.Idle
        _loadingModes.value = emptyMap()
        _isComparing.value = false
        _hasAllAnswers.value = false
        _showingComparison.value = false
        lastComparedUserInput = null
        cachedComparisonResult = null
    }
}