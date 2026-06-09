package com.example.ecosphere.ui.viewmodel

import com.example.ecosphere.data.model.SensorRecord

data class EcoSphereUiState(
    val isLoading: Boolean = false,
    val record: SensorRecord? = null,
    val error: String? = null
)