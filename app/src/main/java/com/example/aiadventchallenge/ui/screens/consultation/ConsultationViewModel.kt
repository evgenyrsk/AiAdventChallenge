package com.example.aiadventchallenge.ui.screens.consultation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiadventchallenge.domain.model.Answer
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.UserProfile
import com.example.aiadventchallenge.domain.usecase.AskAiUseCase
import com.example.aiadventchallenge.domain.usecase.AskMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConsultationViewModel(
    private val askAiUseCase: AskAiUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _currentMode = MutableStateFlow(AskMode.WITHOUT_LIMITS)
    val currentMode: StateFlow<AskMode> = _currentMode.asStateFlow()

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState
        data class Success(val answer: Answer, val mode: AskMode) : UiState
        data class Error(val message: String) : UiState
    }

    fun setMode(mode: AskMode) {
        _currentMode.value = mode
        val savedAnswer = askAiUseCase.getLatestAnswer(mode)
        if (savedAnswer != null) {
            _uiState.value = UiState.Success(savedAnswer, mode)
        } else {
            _uiState.value = UiState.Idle
        }
    }

    fun updateProfile(profile: UserProfile) {
        _userProfile.value = profile
    }

    fun sendMessage(userInput: String) {
        if (userInput.isBlank()) return
        if (_uiState.value == UiState.Loading) return

        val mode = _currentMode.value
        val profile = _userProfile.value

        viewModelScope.launch {
            _uiState.value = UiState.Loading

            when (val result = askAiUseCase(userInput, mode, profile)) {
                is ChatResult.Success -> {
                    askAiUseCase.saveAnswer(mode, result.data)
                    _uiState.value = UiState.Success(result.data, mode)
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
