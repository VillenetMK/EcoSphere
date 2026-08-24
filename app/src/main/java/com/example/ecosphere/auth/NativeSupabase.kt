package com.example.ecosphere.auth

import com.example.ecosphere.shared.EcoSphereConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.ExternalAuthAction
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object NativeSupabase {
    val client = createSupabaseClient(
        supabaseUrl = EcoSphereConfig.SUPABASE_URL,
        supabaseKey = EcoSphereConfig.SUPABASE_PUBLISHABLE_KEY
    ) {
        install(Auth) {
            flowType = FlowType.PKCE
            scheme = "ecosphere"
            host = "auth-callback"
            defaultExternalAuthAction = ExternalAuthAction.CustomTabs()
        }
        install(Postgrest) {
            requireValidSession = true
        }
    }
}
