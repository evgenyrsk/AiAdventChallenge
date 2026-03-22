package com.example.aiadventchallenge.ui.screens.modelversions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aiadventchallenge.data.export.ModelResultsExporter
import com.example.aiadventchallenge.domain.usecase.AskModelUseCase

class ModelVersionsViewModelFactory(
    private val askModelUseCase: AskModelUseCase,
    private val resultsExporter: ModelResultsExporter
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ModelVersionsViewModel::class.java)) {
            return ModelVersionsViewModel(askModelUseCase, resultsExporter) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
