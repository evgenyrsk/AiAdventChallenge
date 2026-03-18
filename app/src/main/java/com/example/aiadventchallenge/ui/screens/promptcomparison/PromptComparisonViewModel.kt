package com.example.aiadventchallenge.ui.screens.promptcomparison

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.PromptMode
import com.example.aiadventchallenge.domain.usecase.AskWithPromptModeUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PromptComparisonViewModel(
    private val askWithPromptModeUseCase: AskWithPromptModeUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _currentMode = MutableStateFlow(PromptMode.DIRECT)
    val currentMode: StateFlow<PromptMode> = _currentMode.asStateFlow()

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState
        data class Success(val answer: String, val mode: PromptMode) : UiState
        data class Error(val message: String) : UiState
    }

    fun setMode(mode: PromptMode) {
        _currentMode.value = mode
    }

    fun sendMessage(userInput: String) {
        if (userInput.isBlank()) return
        if (_uiState.value is UiState.Loading) return

        val mode = _currentMode.value

        viewModelScope.launch {
            _uiState.value = UiState.Loading

            when (val result = askWithPromptModeUseCase(userInput, null, mode)) {
                is ChatResult.Success -> {
                    _uiState.value = UiState.Success(result.data.content, mode)
                }
                is ChatResult.Error -> {
                    _uiState.value = UiState.Error(result.message)
                }
            }
        }
    }

    fun reset() {
        _uiState.value = UiState.Idle
    }
}
