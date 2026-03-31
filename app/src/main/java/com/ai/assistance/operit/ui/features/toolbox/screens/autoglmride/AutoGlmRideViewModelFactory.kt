package com.ai.assistance.operit.ui.features.toolbox.screens.autoglmride

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class AutoGlmRideViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AutoGlmRideViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AutoGlmRideViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
