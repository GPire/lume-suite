package com.weenas.castbay.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/** The launch artwork, decoded once in MainActivity and shared by the screens that show it. */
val LocalBackgroundImage = staticCompositionLocalOf<ImageBitmap?> { null }

/** The artwork's darkest tone, shown until (or instead of) the image. */
private val BACKGROUND_TONE = Color(0xFF0B0910)

/**
 * How much the artwork is darkened, the same on every screen so moving between them doesn't
 * flicker, and dark enough for text on top.
 */
private const val BACKGROUND_DIM = 0.76f

/**
 * The home and Settings backdrop: the launch artwork under a black veil, so text on top stays
 * readable. Playback screens keep a plain black background instead.
 */
@Composable
fun AppBackground(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(BACKGROUND_TONE)) {
        // The same painting wall as the waiting screen, so moving between screens never changes the room.
        PaintingWall(dim = BACKGROUND_DIM)
        // Safe area: this Samsung (like many TVs) crops the edges, so nothing important sits within 3%.
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 18.dp)) { content() }
    }
}
