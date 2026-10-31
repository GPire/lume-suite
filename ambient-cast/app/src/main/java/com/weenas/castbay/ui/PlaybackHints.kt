package com.weenas.castbay.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weenas.castbay.R
import kotlinx.coroutines.delay

private const val CONNECTED_MS = 5000L
private const val HINT_AT_START_MS = 9000L
private const val HINT_AFTER_KEY_MS = 4000L

/**
 * What a person needs to know and can't guess, as soon as a device connects: who is casting (video and
 * mirroring have no badge of their own), and that Down or Menu opens the options (lyrics, voice, picture,
 * timing). Without it the quick menu is invisible: nobody presses a remote button to find out what it does.
 *
 * [streamKey] changes with every new cast, which shows both again. [keyPulse] goes up with each key press
 * while nothing is open, which brings the hint back for a moment. On the music screen a small chevron stays
 * after the text has gone, so the way to the options is always in sight.
 */
@Composable
fun PlaybackHints(
    streamKey: Any,
    senderLabel: String,
    isAudio: Boolean,
    menuOpen: Boolean,
    keyPulse: Int,
    modifier: Modifier = Modifier
) {
    var connectedShown by remember(streamKey) { mutableStateOf(true) }
    var hintShown by remember(streamKey) { mutableStateOf(true) }
    LaunchedEffect(streamKey) {
        delay(CONNECTED_MS)
        connectedShown = false
    }
    LaunchedEffect(streamKey) {
        delay(HINT_AT_START_MS)
        hintShown = false
    }
    LaunchedEffect(keyPulse) {
        if (keyPulse > 0) {
            hintShown = true
            delay(HINT_AFTER_KEY_MS)
            hintShown = false
        }
    }
    val connectedAlpha by animateFloatAsState(if (connectedShown && !isAudio && senderLabel.isNotEmpty()) 1f else 0f, tween(400), label = "connected")
    val hintAlpha by animateFloatAsState(if (hintShown && !menuOpen) 1f else 0f, tween(400), label = "hint")
    val chevronAlpha by animateFloatAsState(if (isAudio && !hintShown && !menuOpen) 0.5f else 0f, tween(600), label = "chevron")

    androidx.compose.foundation.layout.Box(modifier = modifier) {
        if (connectedAlpha > 0.01f) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 36.dp)
                    .alpha(connectedAlpha)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color(0xCC101014))
                    .padding(horizontal = 28.dp, vertical = 14.dp)
            ) {
                androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFF34C759)))
                Text(stringResource(R.string.connected_to, senderLabel), color = Color.White, fontSize = 22.sp, maxLines = 1)
            }
        }
        if (hintAlpha > 0.01f) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
                    .alpha(hintAlpha)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color(0xCC101014))
                    .padding(horizontal = 28.dp, vertical = 14.dp)
            ) {
                Chevron(Color.White, Modifier.size(22.dp))
                Text(
                    stringResource(if (isAudio) R.string.hint_options_music else R.string.hint_options_picture),
                    color = Color.White, fontSize = 20.sp, maxLines = 1
                )
            }
        }
        if (chevronAlpha > 0.01f) {
            Chevron(Color.White, Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp).size(28.dp).alpha(chevronAlpha))
        }
    }
}

/** A downward chevron, drawn: no emoji or font glyph to go missing on a TV. */
@Composable
private fun Chevron(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val s = Stroke(width = w * 0.12f, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.12f, h * 0.34f), Offset(w * 0.5f, h * 0.68f), strokeWidth = s.width, cap = s.cap)
        drawLine(color, Offset(w * 0.5f, h * 0.68f), Offset(w * 0.88f, h * 0.34f), strokeWidth = s.width, cap = s.cap)
    }
}
