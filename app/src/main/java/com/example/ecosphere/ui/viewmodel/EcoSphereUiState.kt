package com.example.ecosphere.ui.viewmodel

import com.example.ecosphere.data.model.DeviceControl
import com.example.ecosphere.data.model.SensorRecord

data class EcoSphereUiState(
    val isLoading: Boolean = false,
    val isUpdatingControl: Boolean = false,
    val record: SensorRecord? = null,
    val deviceControl: DeviceControl? = null,
    val error: String? = null,
    val controlMessage: String? = null
)
