package com.kapcode.thehapticptsdproject

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ModesViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ModesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ModesViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
