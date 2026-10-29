package com.example.ecosphere.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val EcoAccent = Color(0xFF80F59A)
internal val EcoText = Color(0xFFF1F7F2)
internal val EcoMuted = Color(0xFFB5C7BB)
internal val EcoBackground = Color(0xFF0B100D)
internal val EcoSurface = Color(0xFF131E17)

@Composable
internal fun DesktopTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = EcoAccent, onPrimary = Color(0xFF092410),
        secondary = EcoAccent, onSecondary = Color(0xFF092410),
        background = EcoBackground, onBackground = EcoText,
        surface = EcoSurface, onSurface = EcoText,
        surfaceVariant = Color(0xFF1E2E23), onSurfaceVariant = EcoMuted,
        outline = Color(0xFF758D7C), error = Color(0xFFFFB4AB)
    )) {
        Surface(Modifier.fillMaxSize(), color = EcoBackground, contentColor = EcoText, content = content)
    }
}

@Composable
internal fun DesktopBrand(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Image(painterResource("brand/ecosphere.png"), "EcoSphere", Modifier.size(44.dp))
        Column {
            Text("EcoSphere", color = EcoText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("MICROCLIMA INTELIGENTE", color = EcoMuted, fontSize = 9.sp, letterSpacing = 1.sp)
        }
    }
}
