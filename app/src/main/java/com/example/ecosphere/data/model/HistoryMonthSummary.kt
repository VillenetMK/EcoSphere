package com.example.ecosphere.data.model

import com.google.gson.annotations.SerializedName

data class HistoryMonthSummary(
    @SerializedName("month_key")
    val monthKey: String,

    @SerializedName("first_record")
    val firstRecord: String?,

    @SerializedName("last_record")
    val lastRecord: String?,

    @SerializedName("record_count")
    val recordCount: Long
)
