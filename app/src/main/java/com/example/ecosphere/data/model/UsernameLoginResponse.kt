package com.example.ecosphere.data.model

import com.google.gson.annotations.SerializedName

data class UsernameLoginResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String
)
