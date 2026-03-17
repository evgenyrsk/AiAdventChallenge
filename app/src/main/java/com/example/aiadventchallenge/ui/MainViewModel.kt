package com.example.aiadventchallenge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiadventchallenge.data.AIService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val aiService = AIService()

    private val _answer = MutableStateFlow("Loading...")
    val answer: StateFlow<String> = _answer.asStateFlow()

    fun loadAnswer() {
        viewModelScope.launch {
            _answer.value = try {
                aiService.ask("Explain what Kotlin is in one sentence")
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }
}