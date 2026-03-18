package com.example.aiadventchallenge.ui.promptcomparison

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aiadventchallenge.domain.usecase.AskWithPromptModeUseCase

class PromptComparisonViewModelFactory(
    private val askWithPromptModeUseCase: AskWithPromptModeUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PromptComparisonViewModel::class.java)) {
            return PromptComparisonViewModel(askWithPromptModeUseCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
