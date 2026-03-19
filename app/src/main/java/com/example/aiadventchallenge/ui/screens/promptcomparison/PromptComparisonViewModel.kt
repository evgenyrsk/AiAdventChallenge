package com.example.aiadventchallenge.ui.screens.promptcomparison

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.PromptMode
import com.example.aiadventchallenge.domain.usecase.AskWithPromptModeUseCase
import com.example.aiadventchallenge.domain.usecase.CompareResultsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PromptComparisonViewModel(
    private val askWithPromptModeUseCase: AskWithPromptModeUseCase,
    private val compareResultsUseCase: CompareResultsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _currentMode = MutableStateFlow(PromptMode.DIRECT)
    val currentMode: StateFlow<PromptMode> = _currentMode.asStateFlow()

    private val _loadingModes = MutableStateFlow<Map<PromptMode, Boolean>>(emptyMap())
    val loadingModes: StateFlow<Map<PromptMode, Boolean>> = _loadingModes.asStateFlow()

    private val _hasAnswers = MutableStateFlow(false)
    val hasAnswers: StateFlow<Boolean> = _hasAnswers.asStateFlow()

    private val _isComparing = MutableStateFlow(false)
    val isComparing: StateFlow<Boolean> = _isComparing.asStateFlow()

    private val _showingComparison = MutableStateFlow(false)
    val showingComparison: StateFlow<Boolean> = _showingComparison.asStateFlow()

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState
        data class Success(val answer: String, val mode: PromptMode) : UiState
        data object ComparisonLoading : UiState
        data class ComparisonResult(val answer: String) : UiState
        data class Error(val message: String) : UiState
    }

    fun setMode(mode: PromptMode) {
        if (_isComparing.value) return
        
        _currentMode.value = mode
        val savedAnswer = askWithPromptModeUseCase.getLatestAnswer(mode)
        if (savedAnswer != null) {
            _uiState.value = UiState.Success(savedAnswer.content, mode)
        } else {
            _uiState.value = UiState.Idle
        }
    }

    fun sendMessage(userInput: String) {
        if (userInput.isBlank()) return
        if (_uiState.value is UiState.Loading || _uiState.value is UiState.ComparisonLoading) return

        viewModelScope.launch {
            _uiState.value = UiState.Loading
            
            val results = askWithPromptModeUseCase.askAllModes(
                userInput = userInput,
                profile = null,
                onProgress = { mode, isLoading ->
                    val updated = _loadingModes.value.toMutableMap()
                    if (isLoading) {
                        updated[mode] = true
                    } else {
                        updated.remove(mode)
                    }
                    _loadingModes.value = updated
                }
            )

            _loadingModes.value = emptyMap()

            val currentModeValue = _currentMode.value
            val savedAnswer = askWithPromptModeUseCase.getLatestAnswer(currentModeValue)
            if (savedAnswer != null) {
                _uiState.value = UiState.Success(savedAnswer.content, currentModeValue)
                _hasAnswers.value = true
                _showingComparison.value = false
            } else {
                _uiState.value = UiState.Idle
            }
        }
    }

    fun compareAnswers() {
        if (_uiState.value is UiState.Loading || _uiState.value is UiState.ComparisonLoading) return

        viewModelScope.launch {
            _isComparing.value = true
            _uiState.value = UiState.ComparisonLoading

            val answers = mutableMapOf<PromptMode, com.example.aiadventchallenge.domain.model.Answer>()
            PromptMode.entries.forEach { mode ->
                val answer = askWithPromptModeUseCase.getLatestAnswer(mode)
                if (answer != null) {
                    answers[mode] = answer
                }
            }

            if (answers.size < PromptMode.entries.size) {
                _uiState.value = UiState.Error("Недостаточно данных для сравнения")
                _isComparing.value = false
                return@launch
            }

            when (val result = compareResultsUseCase(answers)) {
                is ChatResult.Success -> {
                    _uiState.value = UiState.ComparisonResult(result.data.content)
                    _showingComparison.value = true
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
        _hasAnswers.value = false
        _isComparing.value = false
        _showingComparison.value = false
    }
}
