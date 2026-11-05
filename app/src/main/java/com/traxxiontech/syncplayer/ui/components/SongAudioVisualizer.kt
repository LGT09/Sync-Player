package com.traxxiontech.syncplayer.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.traxxiontech.syncplayer.ui.theme.LocalGlassmorphicColors
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun SongAudioVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 28,
    maxHeightDp: Dp = 60.dp,
    primaryColor: Color? = null,
    secondaryColor: Color? = null
) {
    val glassColors = LocalGlassmorphicColors.current
    val color1 = primaryColor ?: glassColors.glowColor
    val color2 = secondaryColor ?: glassColors.waveColor

    val transition = rememberInfiniteTransition(label = "audio_visualizer")

    val phase1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )

    val phase2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (4 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phase2"
    )

    val beatPulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beatPulse"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(maxHeightDp)
    ) {
        val width = size.width
        val height = size.height
        if (width <= 0 || height <= 0) return@Canvas

        val spacing = 3.dp.toPx()
        val totalSpacing = spacing * (barCount - 1)
        val barWidth = ((width - totalSpacing) / barCount).coerceAtLeast(2.dp.toPx())

        for (i in 0 until barCount) {
            val normalizedX = i.toFloat() / barCount

            val barHeightFraction = if (isPlaying) {
                // Multi-frequency synthesized audio wave harmonics
                val wave1 = abs(sin(phase1 + i * 0.45f))
                val wave2 = abs(sin(phase2 + i * 0.25f))
                val wave3 = abs(sin(i * 0.7f + phase1 * 0.5f))
                val combined = (wave1 * 0.45f + wave2 * 0.35f + wave3 * 0.20f) * beatPulse
                combined.coerceIn(0.12f, 0.98f)
            } else {
                // Tranquil stationary sine wave when paused
                val wave = abs(sin(i * 0.22f)) * 0.25f + 0.08f
                wave.coerceIn(0.08f, 0.35f)
            }

            val barHeight = height * barHeightFraction
            val x = i * (barWidth + spacing)
            val y = (height - barHeight) / 2f

            // Neon gradient brush from bottom to top
            val barBrush = Brush.verticalGradient(
                colors = listOf(
                    color2,
                    color1,
                    Color.White.copy(alpha = 0.9f)
                ),
                startY = y + barHeight,
                endY = y
            )

            drawRoundRect(
                brush = barBrush,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )

            // Luminous glowing tip
            if (isPlaying && barHeightFraction > 0.45f) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = (barWidth * 0.6f),
                    center = Offset(x + barWidth / 2f, y)
                )
            }
        }
    }
}
