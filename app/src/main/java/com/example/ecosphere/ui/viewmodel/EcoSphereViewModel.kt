package com.example.ecosphere.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ecosphere.data.model.DeviceControl
import com.example.ecosphere.data.repository.SensorRepository
import kotlinx.coroutines.launch

class EcoSphereViewModel(
    private val repository: SensorRepository
) : ViewModel() {

    var uiState by mutableStateOf(EcoSphereUiState())
        private set

    init {
        refreshDashboard()
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null, controlMessage = null)

            try {
                val record = repository.getLatestRecord()
                val deviceControl = repository.getDeviceControl()

                uiState = uiState.copy(
                    isLoading = false,
                    record = record,
                    deviceControl = deviceControl,
                    error = if (record == null) "No hay registros todavía en Supabase." else null
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    error = e.message ?: "Error desconocido"
                )
            }
        }
    }

    fun loadLatestRecord() {
        refreshDashboard()
    }

    fun setAutoMode(enabled: Boolean) {
        val message = if (enabled) "Modo automático activado" else "Modo automático desactivado"
        updateControl(message) {
            repository.updateAutoMode(enabled)
        }
    }

    fun setFanTarget(enabled: Boolean) {
        val message = if (enabled) "Ventilador encendido" else "Ventilador apagado"
        updateControl(message) {
            repository.updateFanTarget(enabled)
        }
    }

    fun setLedTarget(enabled: Boolean) {
        val message = if (enabled) "LED grow encendido" else "LED grow apagado"
        updateControl(message) {
            repository.updateLedTarget(enabled)
        }
    }

    fun requestPump() {
        val record = uiState.record
        val soilHumidity = record?.soilHumidity
        val waterLevel = record?.waterLevel?.lowercase()

        when {
            soilHumidity == null -> {
                uiState = uiState.copy(
                    controlMessage = "Riego bloqueado: no hay lectura válida de humedad del suelo."
                )
                return
            }

            soilHumidity >= 60.0 -> {
                uiState = uiState.copy(
                    controlMessage = "Riego bloqueado: el suelo ya está húmedo (${soilHumidity.toInt()}%)."
                )
                return
            }

            waterLevel == "low" -> {
                uiState = uiState.copy(
                    controlMessage = "Riego bloqueado: nivel de agua bajo."
                )
                return
            }
        }

        val currentRequest = uiState.deviceControl?.pumpRequest ?: 0L
        updateControl("Solicitud de riego enviada") {
            repository.requestPump(currentRequest = currentRequest, durationMs = 3000)
        }
    }

    private fun updateControl(
        successMessage: String,
        action: suspend () -> DeviceControl?
    ) {
        viewModelScope.launch {
            uiState = uiState.copy(
                isUpdatingControl = true,
                error = null,
                controlMessage = null
            )

            try {
                val updatedControl = action()
                val latestRecord = repository.getLatestRecord()

                uiState = uiState.copy(
                    isUpdatingControl = false,
                    record = latestRecord,
                    deviceControl = updatedControl ?: repository.getDeviceControl(),
                    controlMessage = successMessage
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isUpdatingControl = false,
                    error = e.message ?: "Error actualizando control"
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
