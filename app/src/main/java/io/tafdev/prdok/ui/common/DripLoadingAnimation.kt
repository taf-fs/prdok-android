package io.tafdev.prdok.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.tafdev.prdok.ui.theme.PrdokForAndroidTheme
import kotlin.math.min
import kotlin.math.round

/**
 * An animation where a portafilter mark drips one block per work item, the blocks stack up
 * in the cup. Port of the iOS DripLoadingAnimation.swift.
 *
 * Drawn in the pixel coordinates of the 518 x 550 logo asset and scaled to the given size; edges
 * snap to whole pixels so adjacent blocks stay flush. Defaults to 95 x 101 dp.
 *
 * Determinate:   DripLoadingAnimation(progress = done.toFloat() / total)
 * Indeterminate: DripLoadingAnimation()
 *
 * @param ink the mark color; unspecified follows the logo — black on a light theme, white on a dark one.
 * @param progress 0..1 fills the cup deterministically; null loops forever.
 */
@Composable
fun DripLoadingAnimation(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    ink: Color = Color.Unspecified,
    accent: Color = PixelQueueAccent,
) {
    // Follows the active MaterialTheme rather than the system, since MainScreen forces light on eBony.
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val resolvedInk = ink.takeOrElse { if (dark) Color.White else Color.Black }

    val step by produceState(initialValue = 0) {
        while (true) withFrameMillis { value = (it / StepMillis).toInt() }
    }

    val semantics = if (progress != null) {
        Modifier.progressSemantics(progress.coerceIn(0f, 1f))
    } else {
        Modifier.progressSemantics()
    }

    Canvas(modifier.then(semantics).size(95.dp, 101.dp)) {
        val s = min(size.width / GridW, size.height / GridH)
        val ox = (size.width - GridW * s) / 2
        val oy = (size.height - GridH * s) / 2

        fun block(r: Rect, color: Color) {
            val left = round(ox + r.left * s)
            val top = round(oy + r.top * s)
            val right = round(ox + r.right * s)
            val bottom = round(oy + r.bottom * s)
            drawRect(color, topLeft = Offset(left, top), size = Size(right - left, bottom - top))
        }

        Mark.forEach { block(it, resolvedInk) }

        // falling drops — each step every lit slot moves down one
        for (slot in 0 until DropSlots) {
            if ((step - slot).mod(DropSpacing) != 0) continue
            val y = DropTop + DropPitch * slot
            block(Rect(Spout.left, y, Spout.right, y + DropH), accent)
        }

        // blocks that have landed; a drop leaves the last slot on steps where (step - DropSlots) % DropSpacing == 0
        val filled = if (progress != null) {
            (progress.coerceIn(0f, 1f) * BarSlots).toInt()
        } else {
            (step - DropSlots).mod(CycleSteps) / DropSpacing
        }
        repeat(filled) { i ->
            val top = CupInterior.bottom - BarH * (i + 1)
            block(Rect(CupInterior.left, top, CupInterior.right, top + BarH), accent)
        }
    }
}

private val PixelQueueAccent = Color(0xFFC2571F)

private const val GridW = 518f
private const val GridH = 550f

private fun rect(x: Float, y: Float, w: Float, h: Float) = Rect(Offset(x, y), Size(w, h))

private val Spout = rect(150f, 113f, 51f, 40f)
private val CupInterior = rect(113f, 397f, 116f, 99f)

/** The static parts of the mark, measured from the logo asset. */
private val Mark = listOf(
    rect(76f, 0f, 51f, 113f),    // left tine
    rect(221f, 0f, 50f, 113f),   // right tine
    rect(76f, 62f, 195f, 51f),   // basket
    rect(271f, 37f, 247f, 51f),  // handle
    Spout,
    rect(59f, 346f, 221f, 51f),  // cup lip
    rect(59f, 346f, 54f, 150f),  // cup left wall
    rect(229f, 346f, 51f, 150f), // cup right wall
    rect(280f, 411f, 54f, 54f),  // cup ear
    rect(0f, 496f, 351f, 54f),   // slab
)

// Drops fall through fixed slots spanning the logo's drip stream (y 176..312), keeping its gaps
// to the spout (23) and the cup lip (34) so they never touch either.
private const val DropH = 24f
private const val DropTop = 176f
private const val DropPitch = 28f
private const val DropSlots = 5
private const val DropSpacing = 2 // a lit slot every other slot
private const val StepMillis = 250L // stepped fall — brutalist, not smooth

private const val BarH = 24f
private const val BarSlots = 3
/** One landing per [DropSpacing] steps; the cup fills, holds, then empties on the next landing. */
private const val CycleSteps = DropSpacing * (BarSlots + 1)

@Preview(name = "Light")
@Composable
private fun DripLoadingAnimationLightPreview() {
    PrdokForAndroidTheme(darkTheme = false) {
        Surface { DripLoadingAnimation(Modifier.padding(40.dp)) }
    }
}

@Preview(name = "Dark")
@Composable
private fun DripLoadingAnimationDarkPreview() {
    PrdokForAndroidTheme(darkTheme = true) {
        Surface { DripLoadingAnimation(Modifier.padding(40.dp), progress = 0.67f) }
    }
}
