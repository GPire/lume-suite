package com.weenas.castbay.ui.screen

import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weenas.castbay.service.Lyrics
import com.weenas.castbay.service.NowPlaying
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * The lyrics as Apple Music shows them: a tall column beside the cover, every line big and readable, the sung line
 * bright and filling with white as the voice moves through it, the others quieter the further they are. The column
 * glides on a soft spring so the sung line settles about a third of the way down (never snaps), and starts moving a
 * moment before the line begins, as the eye wants. All of it is drawn on the GPU as a layer (alpha, scale, offset),
 * the same on a small stick as on a big box: only the fill of the one sung line is redrawn each frame.
 */
/**
 * The song's clock for the lyrics. The sender reports where the song is only now and then, and its figures wobble by up to
 * a second (whole seconds, network jitter); chasing each report made the lines step back and forth. This clock keeps its own
 * pace and takes a report as truth only on a real jump (a seek, a pause or resume, a new song): within 1.2 s it is left alone.
 */
private class SongClock {
    private var anchorSec = 0.0
    private var anchorMs = 0L
    private var running = false
    private var known = false

    /** Set when the last report moved the clock (so the lines may go backwards, as after a seek). */
    var jumped = false

    fun position(nowMs: Long): Double = if (running) anchorSec + (nowMs - anchorMs) / 1000.0 else anchorSec

    fun report(song: NowPlaying, nowMs: Long) {
        val reported = song.currentPositionSec(nowMs)
        if (!known || song.playing != running || kotlin.math.abs(reported - position(nowMs)) > JUMP_SEC) {
            jumped = known
            anchorSec = reported
            anchorMs = nowMs
            running = song.playing
            known = true
        }
    }
}

/**
 * The lyrics as Apple Music shows them: a tall column beside the cover, every line big and readable, the sung line
 * bright and filling with white as the voice moves through it, the others quieter the further they are. The column
 * glides on a soft spring so the sung line settles about a third of the way down (never snaps), and starts moving a
 * moment before the line begins, as the eye wants. All of it is drawn on the GPU as a layer (alpha, scale, offset),
 * the same on a small stick as on a big box: only the fill of the one sung line is redrawn each frame.
 *
 * [delaySec] moves the words against the voice (positive: later) for the TV or the sender that needs it.
 */
@Composable
fun LyricsStage(lyrics: Lyrics, nowPlaying: NowPlaying, delaySec: Double, modifier: Modifier = Modifier) {
    val lines = lyrics.lines
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val clock = remember(lyrics) { SongClock() }

    // The clock the fill follows. Read only while drawing, so a frame redraws one line and recomposes nothing.
    val frame = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(nowPlaying.playing) {
        if (nowPlaying.playing) {
            while (true) {
                androidx.compose.runtime.withFrameNanos { frame.longValue = SystemClock.elapsedRealtime() }
                delay(FILL_STEP_MS)   // ~25 updates a second: the eye cannot tell, a weak GPU is spared every other frame
            }
        }
    }

    // Which line is current: a few times a second is plenty, it only changes every few seconds. It only moves forward,
    // except right after the clock jumped (a seek), so a wobbling report can never send the lines back and forth.
    var current by remember(lyrics) { mutableIntStateOf(-1) }
    LaunchedEffect(lyrics, nowPlaying) {
        clock.report(nowPlaying, SystemClock.elapsedRealtime())
        var logged = 0L
        while (true) {
            val now = SystemClock.elapsedRealtime()
            val position = clock.position(now) - delaySec
            val index = lyrics.indexAt(position + ANTICIPATE_SEC)
            if (index > current || (index < current && clock.jumped)) current = index
            clock.jumped = false
            if (now - logged >= 1000) {   // for finding a real-world offset: one line a second in the log
                logged = now
                android.util.Log.i("CastBayLyricSync", String.format(java.util.Locale.US,
                    "pos=%.2f reported=%.2f delay=%.1f line=%d at=%.2f next=%.2f playing=%b",
                    position, nowPlaying.currentPositionSec(now), delaySec, current,
                    lines.getOrNull(current)?.timeSec ?: -1.0, lines.getOrNull(current + 1)?.timeSec ?: -1.0, nowPlaying.playing))
            }
            delay(60)
        }
    }

    BoxWithConstraints(
        modifier = modifier
    ) {
        val heightPx = with(density) { maxHeight.toPx() }
        val anchorPx = heightPx * ANCHOR
        val anchorDp = with(density) { anchorPx.toDp() }

        // Where the sung line should rest. The first and last lines can reach it thanks to the spacers.
        LaunchedEffect(current, anchorPx, lines.size) {
            val target = (current + 1).coerceAtLeast(1)      // item 0 is the top spacer
            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
            if (info == null) {
                listState.scrollToItem(target)                 // after a jump (seek) there is no long flight to watch
                return@LaunchedEffect
            }
            val delta = info.offset - anchorPx
            // The spring settles in about half a second: lines can change every two seconds.
            if (abs(delta) > 2f) listState.animateScrollBy(delta, spring(dampingRatio = 0.95f, stiffness = 170f))
        }

        LazyColumn(state = listState, userScrollEnabled = false, modifier = Modifier.fillMaxWidth()) {
            item { Spacer(Modifier.height(anchorDp)) }
            items(lines.size) { index ->
                LyricLine(
                    text = lines[index].text,
                    index = index,
                    current = current,
                    fill = { fillOf(lines, index, clock.position(frame.longValue) - delaySec) }
                )
            }
            item { Spacer(Modifier.height(maxHeight)) }
        }
    }
}

/**
 * How much of line [index] has been sung at [positionSec], 0..1. Lyrics files time only where each line starts, so the
 * fill takes about the time until the next line (a little less, so it finishes before the next one lights up), capped
 * for lines that sit before a long instrumental. It marks "this line, now"; it does not pretend to know each word.
 */
private fun fillOf(lines: List<Lyrics.Line>, index: Int, positionSec: Double): Float {
    val start = lines[index].timeSec
    val gap = (lines.getOrNull(index + 1)?.timeSec ?: (start + 5.0)) - start
    val span = (gap * 0.9).coerceIn(0.8, MAX_FILL_SEC)
    return ((positionSec - start) / span).toFloat().coerceIn(0f, 1f)
}

@Composable
private fun LyricLine(text: String, index: Int, current: Int, fill: () -> Float) {
    val distance = index - current
    val active = distance == 0
    val target = when {
        distance == 0 -> 1f
        distance == 1 -> 0.62f
        distance == 2 -> 0.46f
        distance > 2 -> 0.34f
        distance == -1 -> 0.40f
        else -> 0.26f
    }
    val quiet by animateFloatAsState(target, tween(560, easing = FastOutSlowInEasing), label = "lineAlpha")
    val size by animateFloatAsState(if (active) 1f else 0.9f, tween(560, easing = FastOutSlowInEasing), label = "lineScale")
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val shown = text.ifEmpty { "♪ ♪ ♪" }
    val style = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = quiet
                scaleX = size
                scaleY = size
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        // The words as they will sound, then, over them and only on the sung line, the part already sung in full white.
        Text(shown, style = style, color = if (active) Color.White.copy(alpha = 0.45f) else Color.White, onTextLayout = { layout = it })
        if (active) {
            Text(
                shown, style = style, color = Color.White,
                modifier = Modifier.drawWithContent {
                    val measured = layout
                    if (measured == null) {
                        drawContent()
                        return@drawWithContent
                    }
                    val sung = fill()
                    val count = measured.lineCount
                    for (row in 0 until count) {
                        val part = (sung * count - row).coerceIn(0f, 1f)
                        if (part <= 0f) continue
                        val left = measured.getLineLeft(row)
                        clipRect(
                            left = left, top = measured.getLineTop(row),
                            right = left + (measured.getLineRight(row) - left) * part, bottom = measured.getLineBottom(row)
                        ) { this@drawWithContent.drawContent() }
                    }
                }
            )
        }
    }
}

/** The column starts moving this long before a line begins. */
private const val FILL_STEP_MS = 24L

private const val ANTICIPATE_SEC = 0.25

/** A report this far from the clock is a real jump (seek, pause, new song), not jitter. */
private const val JUMP_SEC = 1.2

/** Where the sung line rests, as a fraction of the height from the top. */
private const val ANCHOR = 0.30f

/** The longest a line's fill takes. */
private const val MAX_FILL_SEC = 7.0
