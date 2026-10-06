/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 *
 * Blazify's loading animation: four rounded equaliser bars rising and falling
 * in a wave, filled with the theme's gradient — the music meter the app is
 * about, rather than the bouncing dots every app has. One component, so every
 * place that waits (lyrics, downloads, menus, the player) shows the same thing.
 * The signature is drop-in compatible with CircularProgressIndicator so
 * existing call sites keep working.
 */

package com.blazify.music.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** How high each bar reaches at the top of its swing, so the four never move as one block. */
private val BarReach = floatArrayOf(0.85f, 1f, 0.7f, 0.92f)

@Composable
fun BlazeLoader(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    // Compatibility with CircularProgressIndicator call sites — ignored.
    strokeWidth: Dp = Dp.Unspecified,
    trackColor: Color = Color.Unspecified,
    strokeCap: StrokeCap = StrokeCap.Round,
) {
    val cs = MaterialTheme.colorScheme
    val top = if (color.isSpecified) color else cs.primary
    val bottom = if (color.isSpecified) color.copy(alpha = 0.7f) else cs.tertiary

    val transition = rememberInfiniteTransition(label = "blazeLoader")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1100, easing = LinearEasing)),
        label = "phase",
    )

    // Fills whatever size the caller gives; sensible default otherwise.
    Canvas(modifier = modifier.size(40.dp)) {
        val w = size.width
        val h = size.height
        val bars = 4
        // Bars a little wider than the gaps between them, centred in the box.
        val barW = w / (bars * 1.75f)
        val gap = barW * 0.75f
        val total = bars * barW + (bars - 1) * gap
        val left = (w - total) / 2f
        val floor = h * 0.86f
        val tallest = h * 0.72f
        val shortest = h * 0.18f
        val brush = Brush.verticalGradient(listOf(top, bottom), startY = floor - tallest, endY = floor)

        for (i in 0 until bars) {
            // A wave running left to right: each bar a quarter-step behind the one before.
            val swing = (sin((t - i * 0.18f) * 2f * PI).toFloat() + 1f) / 2f
            val barH = shortest + (tallest * BarReach[i] - shortest) * swing
            drawRoundRect(
                brush = brush,
                topLeft = Offset(left + i * (barW + gap), floor - barH),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )
        }
    }
}
