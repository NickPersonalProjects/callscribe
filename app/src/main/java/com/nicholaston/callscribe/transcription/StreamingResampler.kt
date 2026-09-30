package com.nicholaston.callscribe.transcription

/**
 * Stateful linear-interpolation resampler for interleaved float PCM.
 *
 * It retains the boundary frame between calls, which makes chunked and one-shot processing
 * equivalent. [flush] must be called once after the final input chunk.
 */
class StreamingResampler(
    val inputSampleRate: Int,
    val outputSampleRate: Int = 16_000,
    val channelCount: Int = 1,
) {
    private var pending = FloatArray(0)
    private var nextOutputPosition = 0L
    private var finished = false

    init {
        require(inputSampleRate > 0) { "Input sample rate must be positive" }
        require(outputSampleRate > 0) { "Output sample rate must be positive" }
        require(channelCount > 0) { "Channel count must be positive" }
    }

    fun process(samples: FloatArray): FloatArray {
        check(!finished) { "Cannot process samples after flush()" }
        require(samples.size % channelCount == 0) {
            "Interleaved sample count must be divisible by channel count"
        }
        if (samples.isEmpty()) return FloatArray(0)

        pending = pending + samples
        return produce(includeFinalFrame = false)
    }

    fun flush(): FloatArray {
        check(!finished) { "flush() may only be called once" }
        finished = true
        return produce(includeFinalFrame = true).also {
            pending = FloatArray(0)
        }
    }

    private fun produce(includeFinalFrame: Boolean): FloatArray {
        val frameCount = pending.size / channelCount
        if (frameCount == 0) return FloatArray(0)

        val output = FloatArrayBuilder()
        val limit = if (includeFinalFrame) frameCount.toLong() else frameCount - 1L
        while (nextOutputPosition < limit * outputSampleRate) {
            val lowerFrame = (nextOutputPosition / outputSampleRate).toInt()
            val upperFrame = (lowerFrame + 1).coerceAtMost(frameCount - 1)
            val fraction =
                (nextOutputPosition % outputSampleRate).toFloat() / outputSampleRate
            val lowerOffset = lowerFrame * channelCount
            val upperOffset = upperFrame * channelCount
            for (channel in 0 until channelCount) {
                val lower = pending[lowerOffset + channel]
                val upper = pending[upperOffset + channel]
                output.add(lower + (upper - lower) * fraction)
            }
            nextOutputPosition += inputSampleRate
        }

        if (!includeFinalFrame && frameCount > 1) {
            val discardFrames =
                (nextOutputPosition / outputSampleRate).toInt().coerceAtMost(frameCount - 1)
            if (discardFrames > 0) {
                pending = pending.copyOfRange(discardFrames * channelCount, pending.size)
                nextOutputPosition -= discardFrames.toLong() * outputSampleRate
            }
        }
        return output.toArray()
    }
}

private class FloatArrayBuilder(initialCapacity: Int = 1_024) {
    private var values = FloatArray(initialCapacity)
    private var size = 0

    fun add(value: Float) {
        if (size == values.size) {
            values = values.copyOf(values.size * 2)
        }
        values[size++] = value
    }

    fun toArray(): FloatArray = values.copyOf(size)
}
