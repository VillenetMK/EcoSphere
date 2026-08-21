package com.example.ecosphere.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ecosphere.data.model.DeviceControl
import com.example.ecosphere.data.repository.SensorRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class EcoSphereViewModel(
    private val repository: SensorRepository
) : ViewModel() {

    var uiState by mutableStateOf(EcoSphereUiState())
        private set

    init {
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            loadDashboard(showLoading = true, clearControlMessage = false)

            while (isActive) {
                delay(POLL_INTERVAL_MS)
                loadDashboard(showLoading = false, clearControlMessage = false)
            }
        }
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            loadDashboard(showLoading = true, clearControlMessage = true)
        }
    }

    fun refreshHistory() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoadingHistory = true, error = null)
            try {
                val months = repository.getHistoryMonths()
                val currentSelection = uiState.selectedHistoryMonth
                val selectedMonth = when {
                    currentSelection != null && months.any { it.monthKey == currentSelection } -> currentSelection
                    months.isNotEmpty() -> months.first().monthKey
                    else -> null
                }

                val history = selectedMonth?.let { repository.getHistoryByMonth(it) } ?: emptyList()

                uiState = uiState.copy(
                    isLoadingHistory = false,
                    historyMonths = months,
                    selectedHistoryMonth = selectedMonth,
                    history = history
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoadingHistory = false,
                    error = e.message ?: "Error cargando el historial"
                )
            }
        }
    }

    fun selectHistoryMonth(monthKey: String) {
        if (monthKey == uiState.selectedHistoryMonth && uiState.history.isNotEmpty()) return

        viewModelScope.launch {
            uiState = uiState.copy(
                isLoadingHistory = true,
                selectedHistoryMonth = monthKey,
                history = emptyList(),
                error = null
            )

            try {
                val history = repository.getHistoryByMonth(monthKey)
                uiState = uiState.copy(
                    isLoadingHistory = false,
                    history = history
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoadingHistory = false,
                    error = e.message ?: "Error cargando el mes seleccionado"
                )
            }
        }
    }

    private suspend fun loadDashboard(
        showLoading: Boolean,
        clearControlMessage: Boolean
    ) {
        if (showLoading) {
            uiState = uiState.copy(
                isLoading = true,
                error = null,
                controlMessage = if (clearControlMessage) null else uiState.controlMessage
            )
        }

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
                error = e.message ?: "Error sincronizando con Supabase"
            )
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

    fun setFanPower(power: Int) {
        val safePower = power.coerceIn(0, 100)
        updateControl("Ventilador ajustado al $safePower %") {
            repository.updateFanPower(safePower)
        }
    }

    fun setLedPower(power: Int) {
        val safePower = power.coerceIn(0, 100)
        updateControl("Iluminación ajustada al $safePower %") {
            repository.updateLedPower(safePower)
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
        private const val POLL_INTERVAL_MS = 2_000L

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
