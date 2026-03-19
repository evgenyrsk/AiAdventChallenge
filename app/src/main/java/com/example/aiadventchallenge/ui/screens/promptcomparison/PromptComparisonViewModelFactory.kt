package com.example.aiadventchallenge.ui.screens.promptcomparison

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aiadventchallenge.domain.usecase.AskWithPromptModeUseCase
import com.example.aiadventchallenge.domain.usecase.CompareResultsUseCase

class PromptComparisonViewModelFactory(
    private val askWithPromptModeUseCase: AskWithPromptModeUseCase,
    private val compareResultsUseCase: CompareResultsUseCase,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PromptComparisonViewModel::class.java)) {
            return PromptComparisonViewModel(
                askWithPromptModeUseCase,
                compareResultsUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
