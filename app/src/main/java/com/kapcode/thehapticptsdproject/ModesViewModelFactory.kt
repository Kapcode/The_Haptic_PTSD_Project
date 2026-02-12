package com.kapcode.thehapticptsdproject

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ModesViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ModesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ModesViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
