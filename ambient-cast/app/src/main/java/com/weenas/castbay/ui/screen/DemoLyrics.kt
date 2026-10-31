package com.weenas.castbay.ui.screen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.weenas.castbay.service.Lyrics
import com.weenas.castbay.service.NowPlaying
import java.io.ByteArrayOutputStream

/**
 * Developer demo (`am start ... --es demo lyrics`): the music screen with an invented song and invented words, so
 * the lyrics layout and its motion can be seen and measured without a phone. The words are the author's own.
 */
@Composable
fun DemoLyrics(english: Boolean = false) {
    val cover = remember {
        val bitmap = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { shader = LinearGradient(0f, 0f, 600f, 600f, 0xFFE2536B.toInt(), 0xFF2E3A8C.toInt(), Shader.TileMode.CLAMP) }
        Canvas(bitmap).drawRect(0f, 0f, 600f, 600f, paint)
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }
    val words = if (english) listOf(
        "A small light on the still water", "The boat is breathing slow", "Nobody is in a hurry tonight",
        "We count the stars one by one and lose the count", "The wind carries the names away", "but it leaves the road home",
        "", "If you stay a minute more", "I will tell you where the rivers go", "Where night turns into morning without a sound",
        "Sing with me, sing softly", "The lake is listening", "Sing with me, sing softly", "Until the last wave"
    ) else listOf(
        "Una luce piccola sull'acqua ferma", "La barca respira piano", "Nessuno ha fretta stasera",
        "Contiamo le stelle una a una e poi sbagliamo il conto", "Il vento porta via i nomi", "ma lascia la strada di casa",
        "", "Se resti ancora un minuto", "ti racconto dove vanno i fiumi", "Dove la notte diventa mattina senza far rumore",
        "Canta con me, canta piano", "Che il lago ci ascolta", "Canta con me, canta piano", "Fino all'ultima onda"
    )
    val lrc = words.mapIndexed { i, text ->
        val t = 2.0 + i * 4.0
        String.format(java.util.Locale.US, "[%02d:%05.2f]%s", (t / 60).toInt(), t % 60, text)
    }.joinToString("\n")
    val lyrics = remember { Lyrics.parseLrc(lrc) }
    val start = remember { SystemClock.elapsedRealtime() }
    AudioPlayback(
        nowPlaying = NowPlaying(
            title = if (english) "Sing With Me" else "Canta con me", artist = if (english) "Sample Song" else "Prova Demo", album = "Demo", coverArt = cover,
            positionSec = 0.0, durationSec = 200.0, positionAtMs = start, playing = true
        ),
        onCommand = {}, onSkip = {}, lyrics = lyrics, lyricsEnabled = true, noLyrics = false
    )
}
