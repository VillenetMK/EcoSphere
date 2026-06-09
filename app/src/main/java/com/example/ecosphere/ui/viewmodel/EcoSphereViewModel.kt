package com.example.ecosphere.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ecosphere.data.repository.SensorRepository
import kotlinx.coroutines.launch

class EcoSphereViewModel(
    private val repository: SensorRepository
) : ViewModel() {

    var uiState by mutableStateOf(EcoSphereUiState())
        private set

    init {
        loadLatestRecord()
    }

    fun loadLatestRecord() {
        viewModelScope.launch {
            uiState = EcoSphereUiState(isLoading = true)

            try {
                val record = repository.getLatestRecord()
                uiState = EcoSphereUiState(
                    isLoading = false,
                    record = record,
                    error = if (record == null) "No hay registros todavía en Supabase." else null
                )
            } catch (e: Exception) {
                uiState = EcoSphereUiState(
                    isLoading = false,
                    record = null,
                    error = e.message ?: "Error desconocido"
                )
            }
        }
    }

    companion object {
        fun factory(repository: SensorRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return EcoSphereViewModel(repository) as T
                }
            }
        }
    }
}