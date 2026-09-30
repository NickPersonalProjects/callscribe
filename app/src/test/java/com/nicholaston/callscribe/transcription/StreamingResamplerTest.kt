package com.nicholaston.callscribe.transcription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class StreamingResamplerTest {
    @Test
    fun `common input rates preserve one second duration`() {
        for (inputRate in listOf(8_000, 16_000, 44_100, 48_000)) {
            val input = sineWave(inputRate, 440.0)
            val output = resampleInUnevenChunks(input, inputRate)

            assertEquals("rate=$inputRate", 16_000, output.size)
        }
    }

    @Test
    fun `chunked processing matches one-shot processing`() {
        val input = sineWave(44_100, 997.0)
        val oneShot = StreamingResampler(44_100).run {
            process(input) + flush()
        }
        val chunked = resampleInUnevenChunks(input, 44_100)

        assertEquals(oneShot.size, chunked.size)
        assertTrue(
            oneShot.indices.all { abs(oneShot[it] - chunked[it]) < 1e-6f },
        )
    }

    @Test
    fun `linear interpolation preserves low frequency signal shape`() {
        val output = StreamingResampler(8_000).run {
            process(sineWave(8_000, 200.0)) + flush()
        }
        val expected = sineWave(16_000, 200.0)
        val meanAbsoluteError = output.indices.sumOf {
            abs(output[it] - expected[it]).toDouble()
        } / output.size

        assertTrue("mean absolute error=$meanAbsoluteError", meanAbsoluteError < 0.003)
        assertTrue(output.all { it in -1.001f..1.001f })
    }

    @Test
    fun `stereo channels remain independent`() {
        val input = FloatArray(8_000 * 2) { index ->
            if (index % 2 == 0) 0.75f else -0.25f
        }
        val output = StreamingResampler(8_000, channelCount = 2).run {
            process(input) + flush()
        }

        assertEquals(16_000 * 2, output.size)
        assertTrue(output.indices.filter { it % 2 == 0 }.all { output[it] == 0.75f })
        assertTrue(output.indices.filter { it % 2 == 1 }.all { output[it] == -0.25f })
    }

    private fun resampleInUnevenChunks(input: FloatArray, inputRate: Int): FloatArray {
        val resampler = StreamingResampler(inputRate)
        val chunks = ArrayList<FloatArray>()
        var offset = 0
        var chunkSize = 137
        while (offset < input.size) {
            val end = (offset + chunkSize).coerceAtMost(input.size)
            chunks += resampler.process(input.copyOfRange(offset, end))
            offset = end
            chunkSize = if (chunkSize == 137) 509 else 137
        }
        chunks += resampler.flush()
        return chunks.fold(FloatArray(0)) { result, chunk -> result + chunk }
    }

    private fun sineWave(sampleRate: Int, frequency: Double): FloatArray =
        FloatArray(sampleRate) { index ->
            sin(2.0 * PI * frequency * index / sampleRate).toFloat()
        }
}
