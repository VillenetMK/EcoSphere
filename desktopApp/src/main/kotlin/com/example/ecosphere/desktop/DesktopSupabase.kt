package com.example.ecosphere.desktop

import com.example.ecosphere.shared.EcoSphereConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlin.time.Duration.Companion.minutes

const val DESKTOP_OAUTH_PORT = 54321

object DesktopSupabase {
    val client = createSupabaseClient(
        supabaseUrl = EcoSphereConfig.SUPABASE_URL,
        supabaseKey = EcoSphereConfig.SUPABASE_PUBLISHABLE_KEY
    ) {
        install(Auth) {
            flowType = FlowType.PKCE
            httpCallbackConfig {
                httpPort = DESKTOP_OAUTH_PORT
                timeout = 3.minutes
                htmlTitle = "EcoSphere — acceso completado"
                redirectHtml = """
                    <!doctype html><html lang="es"><head><meta charset="utf-8">
                    <title>EcoSphere</title><style>
                    body{background:#0b0f0d;color:#f4fff6;font-family:system-ui;display:grid;place-items:center;height:100vh;margin:0}
                    main{background:#121915;border:1px solid #294735;border-radius:22px;padding:36px;max-width:480px;text-align:center}
                    b{color:#5cff72}</style></head><body><main><h1>EcoSphere</h1>
                    <p><b>Acceso completado.</b></p><p>Ya puedes cerrar esta pestaña y continuar en la aplicación.</p>
                    </main></body></html>
                """.trimIndent()
            }
        }
        install(Postgrest) {
            requireValidSession = true
        }
    }
}
