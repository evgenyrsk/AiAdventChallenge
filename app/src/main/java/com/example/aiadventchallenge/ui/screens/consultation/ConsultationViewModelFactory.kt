package com.example.aiadventchallenge.ui.screens.consultation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aiadventchallenge.domain.usecase.AskAiUseCase

class ConsultationViewModelFactory(
    private val askAiUseCase: AskAiUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ConsultationViewModel::class.java)) {
            return ConsultationViewModel(askAiUseCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
