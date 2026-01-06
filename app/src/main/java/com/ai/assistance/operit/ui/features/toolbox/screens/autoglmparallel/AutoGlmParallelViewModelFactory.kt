package com.ai.assistance.operit.ui.features.toolbox.screens.autoglmparallel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class AutoGlmParallelViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AutoGlmParallelViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AutoGlmParallelViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}