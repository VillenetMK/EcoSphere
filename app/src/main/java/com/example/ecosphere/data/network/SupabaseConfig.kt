package com.example.ecosphere.data.network

import com.example.ecosphere.shared.EcoSphereConfig

object SupabaseConfig {
    const val BASE_URL = EcoSphereConfig.SUPABASE_URL + "/"
    const val API_KEY = EcoSphereConfig.SUPABASE_PUBLISHABLE_KEY
}
