package com.example.aiadventchallenge.ui.screens.temperature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aiadventchallenge.domain.usecase.CompareTemperatureResultsUseCase
import com.example.aiadventchallenge.domain.usecase.TemperatureUseCase

class TemperatureViewModelFactory(
    private val temperatureUseCase: TemperatureUseCase,
    private val compareTemperatureResultsUseCase: CompareTemperatureResultsUseCase
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TemperatureViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TemperatureViewModel(temperatureUseCase, compareTemperatureResultsUseCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}