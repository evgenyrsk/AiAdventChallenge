package com.example.aiadventchallenge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aiadventchallenge.domain.usecase.AskAiUseCase

class MainViewModelFactory(
    private val askAiUseCase: AskAiUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(askAiUseCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
