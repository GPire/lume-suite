package com.weenas.castbay.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class VocalReducerTest {
    private val rate = 44100

    /** A stereo signal: [centre] Hz in both channels, [left] Hz only left, [right] Hz only right, each at the given amplitude. */
    private fun mix(seconds: Double, centre: Double, left: Double, right: Double, amp: Double = 0.3): ByteArray {
        val n = (seconds * rate).toInt()
        val out = ByteArray(n * 4)
        for (i in 0 until n) {
            val t = i.toDouble() / rate
            val c = if (centre > 0) amp * sin(2 * PI * centre * t) else 0.0
            val l = c + if (left > 0) amp * sin(2 * PI * left * t) else 0.0
            val r = c + if (right > 0) amp * sin(2 * PI * right * t) else 0.0
            put(out, i * 4, l); put(out, i * 4 + 2, r)
        }
        return out
    }

    private fun put(b: ByteArray, at: Int, v: Double) {
        val s = (v * 32768).toInt().coerceIn(-32768, 32767)
        b[at] = s.toByte(); b[at + 1] = (s shr 8).toByte()
    }

    /** Level of [freq] in the left (0) or right (1) channel, by correlation (Goertzel-like), skipping the first [skipSec] of settling. */
    private fun level(pcm: ByteArray, channel: Int, freq: Double, skipSec: Double = 0.5): Double {
        val n = pcm.size / 4
        var re = 0.0; var im = 0.0; var count = 0
        for (i in (skipSec * rate).toInt() until n) {
            val s = (((pcm[i * 4 + channel * 2].toInt() and 0xFF) or (pcm[i * 4 + channel * 2 + 1].toInt() shl 8)).toShort()) / 32768.0
            val t = i.toDouble() / rate
            re += s * cos(2 * PI * freq * t); im += s * sin(2 * PI * freq * t); count++
        }
        return 2 * sqrt(re * re + im * im) / count
    }

    private fun db(after: Double, before: Double) = 20 * log10(after / before)

    private fun run(level: Int, pcm: ByteArray): ByteArray {
        val r = VocalReducer(rate)
        r.level = level
        // in blocks of 352 frames, as they arrive
        var i = 0
        while (i < pcm.size) {
            val end = minOf(i + 352 * 4, pcm.size)
            val block = pcm.copyOfRange(i, end)
            r.process(block)
            System.arraycopy(block, 0, pcm, i, block.size)
            i = end
        }
        return pcm
    }

    @Test fun strongModeTakesTheCentreDownAndLeavesTheRest() {
        val src = mix(3.0, centre = 1000.0, left = 100.0, right = 3000.0)
        val before = src.copyOf()
        val out = run(2, src)
        assertTrue("centre voice down by at least 20 dB", db(level(out, 0, 1000.0), level(before, 0, 1000.0)) < -20)
        assertTrue("bass (left, below the band) kept", db(level(out, 0, 100.0), level(before, 0, 100.0)) > -1.5)
        assertTrue("right-panned instrument loses only a few dB", db(level(out, 1, 3000.0), level(before, 1, 3000.0)) > -7)
    }

    @Test fun reducedModeIsInBetween() {
        val src = mix(3.0, centre = 1000.0, left = 100.0, right = 3000.0)
        val before = src.copyOf()
        val d = db(level(run(1, src), 0, 1000.0), level(before, 0, 1000.0))
        assertTrue("about -8 dB, got $d", d < -5 && d > -12)
    }

    @Test fun offPassesTheSoundOnDelayedButUntouched() {
        val src = mix(2.0, centre = 1000.0, left = 100.0, right = 3000.0)
        val before = src.copyOf()
        val out = run(0, src)
        val d = VocalReducer.DELAY_FRAMES
        for (i in d until out.size / 4) {
            assertEquals(before[(i - d) * 4].toInt(), out[i * 4].toInt())
            assertEquals(before[(i - d) * 4 + 1].toInt(), out[i * 4 + 1].toInt())
        }
    }

    @Test fun monoIsNotSilenced() {
        val src = mix(2.0, centre = 1000.0, left = 0.0, right = 0.0)
        val before = src.copyOf()
        assertEquals(0.0, db(level(run(2, src), 0, 1000.0), level(before, 0, 1000.0)), 0.5)
    }

    @Test fun bassInTheCentreIsKept() {
        // a centred bass line with something panned so the recording is not mono
        val withSide = mix(3.0, centre = 80.0, left = 2500.0, right = 0.0, amp = 0.4)
        val before = withSide.copyOf()
        assertTrue("centred bass kept", db(level(run(2, withSide), 0, 80.0), level(before, 0, 80.0)) > -2)
    }
}
