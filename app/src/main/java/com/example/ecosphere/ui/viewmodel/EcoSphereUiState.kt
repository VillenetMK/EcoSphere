package com.example.ecosphere.ui.viewmodel

import com.example.ecosphere.data.model.DeviceControl
import com.example.ecosphere.data.model.ControllerAdminStatus
import com.example.ecosphere.data.model.HistoryMonthSummary
import com.example.ecosphere.data.model.SensorRecord

data class EcoSphereUiState(
    val isLoading: Boolean = false,
    val isLoadingHistory: Boolean = false,
    val isLoadingMoreHistory: Boolean = false,
    val isUpdatingControl: Boolean = false,
    val record: SensorRecord? = null,
    val history: List<SensorRecord> = emptyList(),
    val historyMonths: List<HistoryMonthSummary> = emptyList(),
    val selectedHistoryMonth: String? = null,
    val historyHasMore: Boolean = false,
    val deviceControl: DeviceControl? = null,
    val controllerStatus: ControllerAdminStatus? = null,
    val isReplacingController: Boolean = false,
    val controllerMessage: String? = null,
    val error: String? = null,
    val controlMessage: String? = null
)
