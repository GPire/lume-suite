package com.weenas.castbay.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The one way a focused row or card looks on the secondary screens (Help, Licenses, Diagnostics): a light glass plate
 * with a white rim that lifts a little, the same language as Lume's pages. Replaces the thin outlines each screen drew
 * for itself.
 */
fun Modifier.glassFocus(focused: Boolean, shape: Shape): Modifier = focusLift(focused).focusPlate(focused, shape)

/** The lift alone, for a card that draws its own background: put it before the background, [focusPlate] after. */
fun Modifier.focusLift(focused: Boolean): Modifier = composed {
    val s by animateFloatAsState(if (focused) 1.02f else 1f, tween(150), label = "glassFocusScale")
    this.scale(s)
}

/** The light plate and white rim alone. */
fun Modifier.focusPlate(focused: Boolean, shape: Shape): Modifier =
    if (focused) this.background(Color.White.copy(alpha = 0.2f), shape).border(2.dp, Color.White.copy(alpha = 0.9f), shape) else this
