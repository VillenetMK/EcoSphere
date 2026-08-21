package com.example.ecosphere.ui.icons

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Exact 24x24 control icon set supplied for the EcoSphere dashboard. */
object DashboardControlIcons {
    val Green = Color(0xFF66FF7A)

    private val refreshAnimationTrigger = mutableIntStateOf(0)

    fun triggerRefreshAnimation() {
        refreshAnimationTrigger.intValue++
    }

    private data class IconPath(
        val data: String,
        val fill: Boolean = false,
        val stroke: Boolean = true,
        val strokeWidth: Float = 2.1f
    )

    private fun icon(
        name: String,
        paths: List<IconPath>,
        rotation: Float = 0f
    ): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            if (rotation != 0f) {
                addGroup(
                    name = "${name}AnimatedGroup",
                    rotate = rotation,
                    pivotX = 12f,
                    pivotY = 12f
                )
            }

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

            if (rotation != 0f) {
                clearGroup()
            }
        }.build()

    val AirHumidity: ImageVector by lazy {
        icon("AirHumidity", listOf(
            IconPath("M10 3.2S5.5 8.2 5.5 12a4.5 4.5 0 0 0 9 0C14.5 8.2 10 3.2 10 3.2Z"),
            IconPath("M16.5 9h3.5M16.8 12h4.2M16.1 15h3.9M15 18h3.4")
        ))
    }

    val AutoMode: ImageVector by lazy {
        icon("AutoMode", listOf(
            IconPath("M9.2 3.4 10 2h4l.8 1.4 1.7.7 1.6-.4 2.2 2.2-.4 1.6.7 1.7L22 10v4l-1.4.8-.7 1.7.4 1.6-2.2 2.2-1.6-.4-1.7.7L14 22h-4l-.8-1.4-1.7-.7-1.6.4-2.2-2.2.4-1.6-.7-1.7L2 14v-4l1.4-.8.7-1.7-.4-1.6 2.2-2.2 1.6.4 1.7-.7Z"),
            IconPath("m13.1 6.8-4 5.6h3.1l-1.1 4.8 4.1-6h-3l.9-4.4Z", fill = true, stroke = false)
        ))
    }

    val Fan: ImageVector by lazy {
        icon("Fan", listOf(
            IconPath("M8.4 12a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0-3.2 0Z"),
            IconPath("M9.6 10.4C7.5 9 6.4 6.9 7.3 5.1c.7-1.4 2.6-1.9 3.5-1.3 1.3.8 1 3.8-.2 6.6"),
            IconPath("M11.6 11.6c1.4-2.1 3.5-3.2 5.3-2.3 1.4.7 1.9 2.6 1.3 3.5-.8 1.3-3.8 1-6.6-.2"),
            IconPath("M10.4 13.6c2.1 1.4 3.2 3.5 2.3 5.3-.7 1.4-2.6 1.9-3.5 1.3-1.3-.8-1-3.8.2-6.6"),
            IconPath("M8.4 12.4c-1.4 2.1-3.5 3.2-5.3 2.3-1.4-.7-1.9-2.6-1.3-3.5.8-1.3 3.8-1 6.6.2"),
            IconPath("M18 6v12M16.5 9h3M16.5 15h3", strokeWidth = 1.6f)
        ))
    }

    val GrowLed: ImageVector by lazy {
        icon("GrowLed", listOf(
            IconPath("M5 4h14l-2 5H7L5 4Z"),
            IconPath("M8 11.5v1.5M12 11.5V13M16 11.5V13"),
            IconPath("M12 21v-4.3"),
            IconPath("M12 18.2c-2.7-.1-4.2-1.4-4.6-3.7 2.6-.2 4.3 1 4.6 3.7Z"),
            IconPath("M12.1 17.4c.4-2.4 2-3.5 4.5-3.3-.4 2.3-1.9 3.4-4.5 3.3Z")
        ))
    }

    val Light: ImageVector by lazy {
        icon("Light", listOf(
            IconPath("M7.8 12a4.2 4.2 0 1 0 8.4 0a4.2 4.2 0 1 0-8.4 0Z"),
            IconPath("M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M18.7 5.3l-1.4 1.4M6.7 17.3l-1.4 1.4")
        ))
    }

    val ManualMode: ImageVector by lazy {
        icon("ManualMode", listOf(
            IconPath("M5 11.5V8.8a1.5 1.5 0 0 1 3 0v2.1-5.1a1.5 1.5 0 0 1 3 0v4.6-6a1.5 1.5 0 0 1 3 0v6-4.8a1.5 1.5 0 0 1 3 0v7.7c0 5-2.8 7.7-7 7.7S3 18.2 3 14.8v-2.3a2 2 0 0 1 2-2Z"),
            IconPath("M19.5 5v14M18 8h3M18 16h3", strokeWidth = 1.55f)
        ))
    }

    val Offline: ImageVector by lazy {
        icon("Offline", listOf(
            IconPath("M5.2 6.9A13.5 13.5 0 0 1 21 8.7M3 8.7c.6-.5 1.2-.9 1.8-1.3M7.4 11.5a8.5 8.5 0 0 1 10.1.7M9.8 15.5a3.8 3.8 0 0 1 4.5 0"),
            IconPath("M10.75 19a1.25 1.25 0 1 0 2.5 0a1.25 1.25 0 1 0-2.5 0Z", fill = true, stroke = false),
            IconPath("M3 3l18 18")
        ))
    }

    val Online: ImageVector by lazy {
        icon("Online", listOf(
            IconPath("M3 8.7a13.5 13.5 0 0 1 18 0M6.5 12.2a8.5 8.5 0 0 1 11 0M9.7 15.5a3.8 3.8 0 0 1 4.6 0"),
            IconPath("M10.75 19a1.25 1.25 0 1 0 2.5 0a1.25 1.25 0 1 0-2.5 0Z", fill = true, stroke = false)
        ))
    }

    val Pump: ImageVector by lazy {
        icon("Pump", listOf(
            IconPath("M5 7H12a2 2 0 0 1 2 2V15a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2Z"),
            IconPath("M6 7V4h5v3M14 10h3.5l2 2v2h-2.5"),
            IconPath("M18.5 15.2s-2.5 2.7-2.5 4.2a2.5 2.5 0 0 0 5 0c0-1.5-2.5-4.2-2.5-4.2Z"),
            IconPath("M7 10v4M10 10v4", strokeWidth = 1.65f)
        ))
    }

    val Refresh: ImageVector
        @Composable get() {
            val trigger = refreshAnimationTrigger.intValue
            val rotation = remember { Animatable(0f) }

            LaunchedEffect(trigger) {
                if (trigger > 0) {
                    rotation.snapTo(0f)
                    rotation.animateTo(
                        targetValue = 360f,
                        animationSpec = tween(
                            durationMillis = 650,
                            easing = LinearEasing
                        )
                    )
                    rotation.snapTo(0f)
                }
            }

            val angle = rotation.value
            return remember(angle) {
                icon(
                    name = "Refresh",
                    rotation = angle,
                    paths = listOf(
                        IconPath("M19 8V4l-2 2a8 8 0 0 0-13.1 4"),
                        IconPath("M5 16v4l2-2a8 8 0 0 0 13.1-4"),
                        IconPath("M15.5 4H19v3.5M8.5 20H5v-3.5")
                    )
                )
            }
        }

    val SoilHumidity: ImageVector by lazy {
        icon("SoilHumidity", listOf(
            IconPath("M3 18h12M5 21h10"),
            IconPath("M9.2 18v-7"),
            IconPath("M9.1 12.3C6 12.2 4.7 10.7 4.2 8c3-.2 4.8 1.2 4.9 4.3Z"),
            IconPath("M9.3 10.8c.3-3 2.1-4.4 5.1-4.3-.3 2.8-2 4.2-5.1 4.3Z"),
            IconPath("M18.6 12.2s-2.4 2.8-2.4 4.7a2.4 2.4 0 0 0 4.8 0c0-1.9-2.4-4.7-2.4-4.7Z")
        ))
    }

    val Temperature: ImageVector by lazy {
        icon("Temperature", listOf(
            IconPath("M9 14.2V5.8a3 3 0 0 1 6 0v8.4a5 5 0 1 1-6 0Z"),
            IconPath("M12 7v9"),
            IconPath("M10.1 17a1.9 1.9 0 1 0 3.8 0a1.9 1.9 0 1 0-3.8 0Z", fill = true, stroke = false),
            IconPath("M5 7h2M4 10h3M5 13h2")
        ))
    }

    val WaterLevel: ImageVector by lazy {
        icon("WaterLevel", listOf(
            IconPath("M7.5 3H16.5a3 3 0 0 1 3 3V18a3 3 0 0 1-3 3H7.5a3 3 0 0 1-3-3V6a3 3 0 0 1 3-3Z"),
            IconPath("M7 13.8c1.3-1.1 2.6-1.1 3.9 0s2.6 1.1 3.9 0 2.6-1.1 3.9 0M7 17.2c1.3-1.1 2.6-1.1 3.9 0s2.6 1.1 3.9 0 2.6-1.1 3.9 0", strokeWidth = 1.7f),
            IconPath("M7 8a1 1 0 1 0 2 0a1 1 0 1 0-2 0Z", fill = true, stroke = false),
            IconPath("M15 8a1 1 0 1 0 2 0a1 1 0 1 0-2 0Z", fill = true, stroke = false)
        ))
    }
}