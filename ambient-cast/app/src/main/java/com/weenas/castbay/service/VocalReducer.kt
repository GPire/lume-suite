package com.weenas.castbay.service

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * "Sing": turns the singer down and keeps the band, for karaoke. Lead vocals are mixed in the centre of a stereo recording, so
 * the centre ((L+R)/2), limited to the band where a voice lives (about 400 Hz to 4.5 kHz), is taken off both channels. The
 * bass, the kick, the cymbals and everything panned left or right stay; a hard-panned instrument loses a few dB, and
 * instruments that sit in the centre inside that band (a snare, a guitar) go down with the voice. It is the classic method,
 * light enough to run live on a stick; it is not Apple Music's trained separation, and a recording with the voice spread wide or
 * drowned in stereo effects keeps a trace of the singer. A mono recording has no centre to take out (all of it is centre), so
 * it is left alone rather than silenced.
 *
 * The band is cut with a linear-phase filter and the untouched sound is delayed by the same amount ([DELAY_FRAMES], about 4 ms):
 * with an ordinary filter the two would differ in phase and the voice would only half cancel below 1 kHz.
 *
 * Works in place on 16-bit little-endian stereo PCM at 44.1 kHz, in the order the music arrives. With [level] 0 the sound is
 * passed on delayed but otherwise untouched, so switching on and off never jumps.
 */
class VocalReducer(sampleRate: Int = 44100) {
    /** 0 off, 1 reduced (the voice about 8 dB down), 2 almost gone (about 26 dB down). */
    @Volatile var level = 0

    private val taps = design(sampleRate, LOW_HZ, HIGH_HZ)
    private var midHistory = DoubleArray(TAPS - 1)
    private var leftHistory = DoubleArray(DELAY_FRAMES)
    private var rightHistory = DoubleArray(DELAY_FRAMES)
    private var gain = 0.0   // what is actually subtracted: it glides to its target, so switching never clicks

    /** Forget what was heard (after a seek, a pause or a new song): no old sound leaks into the new. */
    fun reset() {
        midHistory = DoubleArray(TAPS - 1)
        leftHistory = DoubleArray(DELAY_FRAMES)
        rightHistory = DoubleArray(DELAY_FRAMES)
        gain = 0.0
    }

    fun process(pcm: ByteArray) {
        val frames = pcm.size / 4
        if (frames == 0) return
        val left = DoubleArray(DELAY_FRAMES + frames)
        val right = DoubleArray(DELAY_FRAMES + frames)
        val mid = DoubleArray(TAPS - 1 + frames)
        System.arraycopy(leftHistory, 0, left, 0, DELAY_FRAMES)
        System.arraycopy(rightHistory, 0, right, 0, DELAY_FRAMES)
        System.arraycopy(midHistory, 0, mid, 0, TAPS - 1)
        var sideEnergy = 0.0
        var midEnergy = 0.0
        for (i in 0 until frames) {
            val l = sample(pcm, i * 4)
            val r = sample(pcm, i * 4 + 2)
            left[DELAY_FRAMES + i] = l
            right[DELAY_FRAMES + i] = r
            val m = (l + r) * 0.5
            mid[TAPS - 1 + i] = m
            val s = (l - r) * 0.5
            midEnergy += m * m
            sideEnergy += s * s
        }
        // Mono guard: if the sides are almost silent next to the centre, there is nothing to separate.
        val mono = midEnergy > 0 && sideEnergy < midEnergy * MONO_SIDE_RATIO
        val target = if (mono) 0.0 else when (level) {
            1 -> 0.6
            2 -> 0.95
            else -> 0.0
        }
        val step = (target - gain) / frames
        val working = target > 0.0 || gain > 0.0
        for (i in 0 until frames) {
            var voice = 0.0
            if (working) {
                var k = 0
                var at = i + TAPS - 1
                while (k < TAPS) {
                    voice += taps[k] * mid[at]
                    k++
                    at--
                }
            }
            gain += step
            write(pcm, i * 4, left[i] - gain * voice)       // left[i] is the sample from DELAY_FRAMES ago
            write(pcm, i * 4 + 2, right[i] - gain * voice)
        }
        gain = target
        System.arraycopy(mid, frames, midHistory, 0, TAPS - 1)
        System.arraycopy(left, frames, leftHistory, 0, DELAY_FRAMES)
        System.arraycopy(right, frames, rightHistory, 0, DELAY_FRAMES)
    }

    private fun sample(b: ByteArray, at: Int): Double = ((b[at].toInt() and 0xFF) or (b[at + 1].toInt() shl 8)).toShort() / 32768.0

    private fun write(b: ByteArray, at: Int, v: Double) {
        val s = (v * 32768.0).toInt().coerceIn(-32768, 32767)
        b[at] = s.toByte()
        b[at + 1] = (s shr 8).toByte()
    }

    companion object {
        const val TAPS = 383
        /** The untouched sound is held back by this many frames to line up with the band-limited one. */
        const val DELAY_FRAMES = TAPS / 2
        private const val LOW_HZ = 250.0
        private const val HIGH_HZ = 4800.0
        /** Side energy under this share of the centre's: a mono recording. */
        private const val MONO_SIDE_RATIO = 0.0025

        /** A band-pass by windowed sinc (Blackman): symmetric taps, so its delay is exactly [DELAY_FRAMES]. */
        private fun design(rate: Int, low: Double, high: Double): DoubleArray {
            val w1 = 2 * PI * low / rate
            val w2 = 2 * PI * high / rate
            return DoubleArray(TAPS) { k ->
                val m = k - DELAY_FRAMES
                val ideal = if (m == 0) (w2 - w1) / PI else (sin(w2 * m) - sin(w1 * m)) / (PI * m)
                val window = 0.42 - 0.5 * cos(2 * PI * k / (TAPS - 1)) + 0.08 * cos(4 * PI * k / (TAPS - 1))
                ideal * window
            }
        }
    }
}

/** The "Sing" level the music screen asks for, read by the music player as each piece of sound arrives (0, 1 or 2). */
object SingMode {
    @Volatile var level = 0
}
