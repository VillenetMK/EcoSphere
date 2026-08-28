package com.example.ecosphere.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ecosphere.data.model.DeviceControl
import com.example.ecosphere.data.repository.SensorRepository
import com.example.ecosphere.shared.ControlPolicy
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

                val history = selectedMonth?.let {
                    repository.getHistoryByMonth(it, offset = 0)
                } ?: emptyList()
                val totalForMonth = months.firstOrNull { it.monthKey == selectedMonth }?.recordCount ?: 0L

                uiState = uiState.copy(
                    isLoadingHistory = false,
                    isLoadingMoreHistory = false,
                    historyMonths = months,
                    selectedHistoryMonth = selectedMonth,
                    history = history,
                    historyHasMore = history.size.toLong() < totalForMonth
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoadingHistory = false,
                    isLoadingMoreHistory = false,
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
                isLoadingMoreHistory = false,
                selectedHistoryMonth = monthKey,
                history = emptyList(),
                historyHasMore = false,
                error = null
            )

            try {
                val history = repository.getHistoryByMonth(monthKey, offset = 0)
                val totalForMonth = uiState.historyMonths
                    .firstOrNull { it.monthKey == monthKey }
                    ?.recordCount ?: history.size.toLong()

                uiState = uiState.copy(
                    isLoadingHistory = false,
                    history = history,
                    historyHasMore = history.size.toLong() < totalForMonth
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoadingHistory = false,
                    error = e.message ?: "Error cargando el mes seleccionado"
                )
            }
        }
    }

    fun loadMoreHistory() {
        val monthKey = uiState.selectedHistoryMonth ?: return
        if (uiState.isLoadingHistory || uiState.isLoadingMoreHistory || !uiState.historyHasMore) return

        viewModelScope.launch {
            uiState = uiState.copy(isLoadingMoreHistory = true, error = null)
            try {
                val nextPage = repository.getHistoryByMonth(
                    monthKey = monthKey,
                    offset = uiState.history.size
                )
                val combined = uiState.history + nextPage
                val totalForMonth = uiState.historyMonths
                    .firstOrNull { it.monthKey == monthKey }
                    ?.recordCount ?: combined.size.toLong()

                uiState = uiState.copy(
                    isLoadingMoreHistory = false,
                    history = combined,
                    historyHasMore = combined.size.toLong() < totalForMonth && nextPage.isNotEmpty()
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoadingMoreHistory = false,
                    error = e.message ?: "Error cargando más registros"
                )
            }
        }
    }

    fun refreshControlAudit() {
        viewModelScope.launch {
            uiState = uiState.copy(
                isLoadingControlAudit = true,
                controlAuditError = null
            )
            try {
                uiState = uiState.copy(
                    isLoadingControlAudit = false,
                    controlAudit = repository.getControlAudit(),
                    controlAuditError = null
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoadingControlAudit = false,
                    controlAuditError = e.message ?: "No se pudo cargar el registro de actividad"
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

    fun refreshControllerStatus() {
        viewModelScope.launch {
            try {
                uiState = uiState.copy(
                    controllerStatus = repository.getControllerAdminStatus(),
                    controllerMessage = null
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    controllerMessage = e.message ?: "No se pudo consultar el controlador"
                )
            }
        }
    }

    fun replaceActiveController(pairingCode: String) {
        if (pairingCode.replace("-", "").length != 12) {
            uiState = uiState.copy(controllerMessage = "Ingresa el código completo del ESP32.")
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isReplacingController = true, controllerMessage = null)
            try {
                val status = repository.replaceActiveController(pairingCode)
                uiState = uiState.copy(
                    isReplacingController = false,
                    controllerStatus = status ?: repository.getControllerAdminStatus(),
                    deviceControl = repository.getDeviceControl(),
                    controllerMessage = "Controlador reemplazado sin perder historial ni configuración."
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isReplacingController = false,
                    controllerMessage = e.message ?: "No se pudo reemplazar el controlador"
                )
            }
        }
    }

    fun setAutoMode(enabled: Boolean) {
        val message = if (enabled) "Modo automático activado" else "Modo automático desactivado"
        updateControl(message) {
            repository.updateAutoMode(enabled)
        }
    }

    fun setFanPower(power: Int) {
        val safePower = ControlPolicy.clampPower(power)
        updateControl("Ventilador ajustado al $safePower %") {
            repository.updateFanPower(safePower)
        }
    }

    fun setLedPower(power: Int) {
        val safePower = ControlPolicy.clampPower(power)
        updateControl("Iluminación ajustada al $safePower %") {
            repository.updateLedPower(safePower)
        }
    }

    fun requestPump() {
        val currentRecord = ControlPolicy.currentTelemetry(
            record = uiState.record,
            control = uiState.deviceControl
        )
        val decision = ControlPolicy.irrigationDecision(
            soilHumidity = currentRecord?.soilHumidity,
            waterLevel = currentRecord?.waterLevel
        )
        if (!decision.allowed) {
            uiState = uiState.copy(controlMessage = decision.message)
            return
        }

        val currentRequest = uiState.deviceControl?.pumpRequest ?: 0L
        updateControl(null) {
            repository.requestPump(
                currentRequest = currentRequest,
                durationMs = ControlPolicy.PUMP_DURATION_MS
            )
        }
    }

    private fun updateControl(
        successMessage: String?,
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
