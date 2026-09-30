package com.nicholaston.callscribe.transcription

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class ChannelSplitDetectorTest {
    @Test
    fun `mono input is classified as mono`() {
        val samples = FloatArray(100) { it / 100f }

        assertEquals(ChannelLayout.MONO, ChannelSplitDetector.classify(samples, 1))
    }

    @Test
    fun `correlated stereo is classified as duplicate`() {
        val mono = sineWave(8_000, 440.0)
        val stereo = interleave(mono, FloatArray(mono.size) { mono[it] * 0.8f })

        assertEquals(
            ChannelLayout.STEREO_DUPLICATE,
            ChannelSplitDetector.classify(stereo, 2),
        )
    }

    @Test
    fun `independent stereo is classified as uplink downlink`() {
        val uplink = sineWave(8_000, 300.0)
        val downlink = sineWave(8_000, 700.0)

        assertEquals(
            ChannelLayout.STEREO_UPLINK_DOWNLINK,
            ChannelSplitDetector.classify(interleave(uplink, downlink), 2),
        )
    }

    @Test
    fun `split and downmix preserve channel values`() {
        val stereo = floatArrayOf(1f, -1f, 0.5f, 0.25f)

        val split = ChannelSplitDetector.splitStereo(stereo)

        assertArrayEquals(floatArrayOf(1f, 0.5f), split.left, 0f)
        assertArrayEquals(floatArrayOf(-1f, 0.25f), split.right, 0f)
        assertArrayEquals(
            floatArrayOf(0f, 0.375f),
            ChannelSplitDetector.downmix(stereo, 2),
            0f,
        )
    }

    private fun sineWave(sampleRate: Int, frequency: Double): FloatArray =
        FloatArray(sampleRate) { index ->
            sin(2.0 * PI * frequency * index / sampleRate).toFloat()
        }

    private fun interleave(left: FloatArray, right: FloatArray): FloatArray {
        require(left.size == right.size)
        return FloatArray(left.size * 2) { index ->
            if (index % 2 == 0) left[index / 2] else right[index / 2]
        }
    }
}
