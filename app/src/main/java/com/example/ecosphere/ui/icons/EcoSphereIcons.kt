package com.example.ecosphere.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

object EcoSphereIcons {
    val Green = Color(0xFF66FF7A)

    private data class IconPath(
        val data: String,
        val fill: Boolean = false,
        val stroke: Boolean = true,
        val strokeWidth: Float = 2.1f
    )

    private fun icon(name: String, paths: List<IconPath>): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            paths.forEach { path ->
                addPath(
                    pathData = PathParser().parsePathString(path.data).toNodes(),
                    fill = if (path.fill) SolidColor(Green) else null,
                    stroke = if (path.stroke) SolidColor(Green) else null,
                    strokeLineWidth = path.strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round
                )
            }
        }.build()

    val AirHumidity: ImageVector by lazy {
        icon("AirHumidity", listOf(
            IconPath("M10 3.2S5.5 8.2 5.5 12a4.5 4.5 0 0 0 9 0C14.5 8.2 10 3.2 10 3.2Z"),
            IconPath("M16.5 9h3.5M16.8 12h4.2M16.1 15h3.9M15 18h3.4"),
        ))
    }

    val AutoMode: ImageVector by lazy {
        icon("AutoMode", listOf(
            IconPath("M9.2 3.4 10 2h4l.8 1.4 1.7.7 1.6-.4 2.2 2.2-.4 1.6.7 1.7L22 10v4l-1.4.8-.7 1.7.4 1.6-2.2 2.2-1.6-.4-1.7.7L14 22h-4l-.8-1.4-1.7-.7-1.6.4-2.2-2.2.4-1.6-.7-1.7L2 14v-4l1.4-.8.7-1.7-.4-1.6 2.2-2.2 1.6.4 1.7-.7Z"),
            IconPath("m13.1 6.8-4 5.6h3.1l-1.1 4.8 4.1-6h-3l.9-4.4Z", fill = true, stroke = false),
        ))
    }

    val Cloud: ImageVector by lazy {
        icon("Cloud", listOf(
            IconPath("M7 18.5h10.5a4 4 0 0 0 .8-7.9A6.2 6.2 0 0 0 6.6 9.2 4.7 4.7 0 0 0 7 18.5Z"),
            IconPath("M10 16V10.8M10 10.8 8.2 12.6M10 10.8l1.8 1.8M14.5 11.2v5.2M14.5 16.4l-1.8-1.8M14.5 16.4l1.8-1.8", strokeWidth = 1.65f),
        ))
    }

    val Dashboard: ImageVector by lazy {
        icon("Dashboard", listOf(
            IconPath("M4.5 3.5H8.5A1.5 1.5 0 0 1 10 5V9A1.5 1.5 0 0 1 8.5 10.5H4.5A1.5 1.5 0 0 1 3 9V5A1.5 1.5 0 0 1 4.5 3.5Z"),
            IconPath("M15.5 3.5H19.5A1.5 1.5 0 0 1 21 5V9A1.5 1.5 0 0 1 19.5 10.5H15.5A1.5 1.5 0 0 1 14 9V5A1.5 1.5 0 0 1 15.5 3.5Z"),
            IconPath("M4.5 14H8.5A1.5 1.5 0 0 1 10 15.5V19.5A1.5 1.5 0 0 1 8.5 21H4.5A1.5 1.5 0 0 1 3 19.5V15.5A1.5 1.5 0 0 1 4.5 14Z"),
            IconPath("M15.5 14H19.5A1.5 1.5 0 0 1 21 15.5V19.5A1.5 1.5 0 0 1 19.5 21H15.5A1.5 1.5 0 0 1 14 19.5V15.5A1.5 1.5 0 0 1 15.5 14Z"),
            IconPath("M16 18.3c1.9-2.2 3.3-1.7 3.3-1.7s.2 2.4-2.1 2.9c-.8.2-1.4-.3-1.2-1.2Z", fill = true, stroke = false),
            IconPath("M16.1 19.4c.6-.8 1.3-1.4 2.3-1.8", strokeWidth = 1.35f),
        ))
    }

    val Diagnostics: ImageVector by lazy {
        icon("Diagnostics", listOf(
            IconPath("M7 5H14A2 2 0 0 1 16 7V14A2 2 0 0 1 14 16H7A2 2 0 0 1 5 14V7A2 2 0 0 1 7 5Z"),
            IconPath("M8 2.5V5M12 2.5V5M8 16v2.5M3 8h2M3 12h2M16 8h2"),
            IconPath("M7.3 10.7h1.8l1-2.2 1.5 4 1-1.8h2", strokeWidth = 1.65f),
            IconPath("M20.4 17.2A3.2 3.2 0 1 0 14 17.2A3.2 3.2 0 1 0 20.4 17.2Z"),
            IconPath("m19.6 19.6 2 2"),
        ))
    }

    val Error: ImageVector by lazy {
        icon("Error", listOf(
            IconPath("M21 12A9 9 0 1 0 3 12A9 9 0 1 0 21 12Z"),
            IconPath("m9 9 6 6M15 9l-6 6"),
        ))
    }

    val Esp32: ImageVector by lazy {
        icon("Esp32", listOf(
            IconPath("M6 5.5H18A2 2 0 0 1 20 7.5V16.5A2 2 0 0 1 18 18.5H6A2 2 0 0 1 4 16.5V7.5A2 2 0 0 1 6 5.5Z"),
            IconPath("M8 8.5h8v7H8Z", strokeWidth = 1.7f),
            IconPath("M8 2.5v3M12 2.5v3M16 2.5v3M8 18.5v3M12 18.5v3M16 18.5v3M1.5 9h2.5M1.5 12h2.5M1.5 15h2.5M20 9h2.5M20 12h2.5M20 15h2.5"),
        ))
    }

    val Fan: ImageVector by lazy {
        icon("Fan", listOf(
            IconPath("M14 12A2 2 0 1 0 10 12A2 2 0 1 0 14 12Z"),
            IconPath("M12 10c-1.2-3.8-.1-6.5 2.2-6.7 2.4-.2 3.2 2.5 2 4.5C15.1 9.7 13.8 10.1 12 10Z"),
            IconPath("M14 12c3.8-1.2 6.5-.1 6.7 2.2.2 2.4-2.5 3.2-4.5 2C14.3 15.1 13.9 13.8 14 12Z"),
            IconPath("M12 14c1.2 3.8.1 6.5-2.2 6.7-2.4.2-3.2-2.5-2-4.5C8.9 14.3 10.2 13.9 12 14Z"),
            IconPath("M10 12c-3.8 1.2-6.5.1-6.7-2.2-.2-2.4 2.5-3.2 4.5-2C9.7 8.9 10.1 10.2 10 12Z"),
        ))
    }

    val GrowLed: ImageVector by lazy {
        icon("GrowLed", listOf(
            IconPath("M5 4.5h14l-1.8 5H6.8l-1.8-5Z"),
            IconPath("M8 9.5v2M12 9.5v2M16 9.5v2", strokeWidth = 1.65f),
            IconPath("M12 20v-5.5"),
            IconPath("M12 16c-2.8-2.7-5.7-2.3-6.8-1.7 1.7 3.2 4.2 4 6.8 1.7Z"),
            IconPath("M12 16c2.8-2.7 5.7-2.3 6.8-1.7-1.7 3.2-4.2 4-6.8 1.7Z"),
        ))
    }

    val History: ImageVector by lazy {
        icon("History", listOf(
            IconPath("M4.5 6.5V3.8M4.5 6.5H7.2"),
            IconPath("M4.8 6.2A8.5 8.5 0 1 1 3.7 15"),
            IconPath("M12 7.2v5l3.4 2"),
            IconPath("m6.2 17.5 2.1-2 2.2 1.2 2.2-2.4 2 1.1 2.5-2.8", strokeWidth = 1.7f),
        ))
    }

    val Info: ImageVector by lazy {
        icon("Info", listOf(
            IconPath("M21 12A9 9 0 1 0 3 12A9 9 0 1 0 21 12Z"),
            IconPath("M12 10v6M12 7h.01"),
        ))
    }

    val Light: ImageVector by lazy {
        icon("Light", listOf(
            IconPath("M15.8 12A3.8 3.8 0 1 0 8.2 12A3.8 3.8 0 1 0 15.8 12Z"),
            IconPath("M12 2.5V5M12 19v2.5M2.5 12H5M19 12h2.5M5.3 5.3l1.8 1.8M16.9 16.9l1.8 1.8M18.7 5.3l-1.8 1.8M7.1 16.9l-1.8 1.8"),
        ))
    }

    val ManualMode: ImageVector by lazy {
        icon("ManualMode", listOf(
            IconPath("M7.5 11V6.8a1.7 1.7 0 1 1 3.4 0V11"),
            IconPath("M10.9 10V5.6a1.7 1.7 0 1 1 3.4 0V10"),
            IconPath("M14.3 11V7.2a1.7 1.7 0 1 1 3.4 0v6.2"),
            IconPath("M7.5 10.5a1.7 1.7 0 0 0-3.4 0v3.1c0 5 3.1 7.4 7.6 7.4h.7c4.5 0 7.5-2.7 7.5-7.4v-3a1.7 1.7 0 1 0-3.4 0"),
        ))
    }

    val Offline: ImageVector by lazy {
        icon("Offline", listOf(
            IconPath("M4 9.2a12 12 0 0 1 4.3-2.4M11.5 6.1A12.6 12.6 0 0 1 20 9.2M7.2 12.4a7.6 7.6 0 0 1 3.1-1.5M13.6 11a7.6 7.6 0 0 1 3.2 1.4M10.2 15.6a2.8 2.8 0 0 1 3.6 0"),
            IconPath("M21 3 3 21"),
            IconPath("M13 19A1 1 0 1 0 11 19A1 1 0 1 0 13 19Z", fill = true, stroke = false),
        ))
    }

    val Ok: ImageVector by lazy {
        icon("Ok", listOf(
            IconPath("M21 12A9 9 0 1 0 3 12A9 9 0 1 0 21 12Z"),
            IconPath("m7.8 12.3 2.7 2.8 5.8-6"),
        ))
    }

    val Online: ImageVector by lazy {
        icon("Online", listOf(
            IconPath("M4 9.2a12.3 12.3 0 0 1 16 0M7.2 12.4a7.7 7.7 0 0 1 9.6 0M10.2 15.6a2.8 2.8 0 0 1 3.6 0"),
            IconPath("M13 19A1 1 0 1 0 11 19A1 1 0 1 0 13 19Z", fill = true, stroke = false),
        ))
    }

    val Power: ImageVector by lazy {
        icon("Power", listOf(
            IconPath("M12 3v8"),
            IconPath("M7 5.7a8 8 0 1 0 10 0"),
            IconPath("m13.8 8.5-3.6 5h2.6l-1 4 3.7-5.3h-2.6l.9-3.7Z", fill = true, stroke = false),
        ))
    }

    val Pump: ImageVector by lazy {
        icon("Pump", listOf(
            IconPath("M4.5 8.5H13A2 2 0 0 1 15 10.5V17.5A2 2 0 0 1 13 19.5H4.5Z"),
            IconPath("M7 8.5V5h5v3.5M15 12h4.2v2.2"),
            IconPath("M19.2 14.2h2v2.3h-2"),
            IconPath("M18.5 18s-2 2.2-2 3.4a2 2 0 0 0 4 0c0-1.2-2-3.4-2-3.4Z"),
        ))
    }

    val Refresh: ImageVector by lazy {
        icon("Refresh", listOf(
            IconPath("M20 7v5h-5"),
            IconPath("M4 17v-5h5"),
            IconPath("M6.1 8A7 7 0 0 1 18 6.5L20 12"),
            IconPath("M17.9 16A7 7 0 0 1 6 17.5L4 12"),
        ))
    }

    val Settings: ImageVector by lazy {
        icon("Settings", listOf(
            IconPath("M14.8 12A2.8 2.8 0 1 0 9.2 12A2.8 2.8 0 1 0 14.8 12Z"),
            IconPath("M19.4 13.6v-3.2l-2-.7a6.4 6.4 0 0 0-.7-1.6l.9-1.9-2.3-2.3-1.9.9a6.4 6.4 0 0 0-1.6-.7L11.1 2H8.9l-.7 2.1a6.4 6.4 0 0 0-1.6.7l-1.9-.9-2.3 2.3.9 1.9a6.4 6.4 0 0 0-.7 1.6l-2 .7v3.2l2 .7c.2.6.4 1.1.7 1.6l-.9 1.9 2.3 2.3 1.9-.9c.5.3 1 .5 1.6.7l.7 2.1h2.2l.7-2.1c.6-.2 1.1-.4 1.6-.7l1.9.9 2.3-2.3-.9-1.9c.3-.5.5-1 .7-1.6l2-.7Z"),
        ))
    }

    val SoilHumidity: ImageVector by lazy {
        icon("SoilHumidity", listOf(
            IconPath("M3.5 17.5h17M5 20.5h14"),
            IconPath("M12 17.5V9"),
            IconPath("M12 11.5c-3.4-.2-5.4-2-5.9-4.6 3.3-.2 5.4 1.4 5.9 4.6Z"),
            IconPath("M12 10.5c3-.1 4.8-1.6 5.4-3.8-3-.2-4.7 1.2-5.4 3.8Z"),
            IconPath("M18.2 12.5s-2.1 2.4-2.1 4a2.1 2.1 0 1 0 4.2 0c0-1.6-2.1-4-2.1-4Z"),
        ))
    }

    val Temperature: ImageVector by lazy {
        icon("Temperature", listOf(
            IconPath("M9 14.2V5.8a3 3 0 0 1 6 0v8.4a5 5 0 1 1-6 0Z"),
            IconPath("M12 7v9"),
            IconPath("M13.9 17A1.9 1.9 0 1 0 10.1 17A1.9 1.9 0 1 0 13.9 17Z", fill = true, stroke = false),
            IconPath("M5 7h2M4 10h3M5 13h2"),
        ))
    }

    val Warning: ImageVector by lazy {
        icon("Warning", listOf(
            IconPath("M10.3 4.1 2.7 18a2 2 0 0 0 1.8 3h15a2 2 0 0 0 1.8-3L13.7 4.1a2 2 0 0 0-3.4 0Z"),
            IconPath("M12 9v4M12 17h.01"),
        ))
    }

    val WaterLevel: ImageVector by lazy {
        icon("WaterLevel", listOf(
            IconPath("M7 3H17A2 2 0 0 1 19 5V19A2 2 0 0 1 17 21H7A2 2 0 0 1 5 19V5A2 2 0 0 1 7 3Z"),
            IconPath("M7.2 14.2c1.6-1.1 2.8 1 4.4 0s2.7-1 4.3 0 2.5.2 3.1-.2", strokeWidth = 1.65f),
            IconPath("M8 17.5h8M8 7h3M8 10h5"),
        ))
    }
}
