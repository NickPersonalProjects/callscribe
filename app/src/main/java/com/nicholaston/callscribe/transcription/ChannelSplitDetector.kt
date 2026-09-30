package com.nicholaston.callscribe.transcription

import kotlin.math.abs
import kotlin.math.sqrt

enum class ChannelLayout {
    MONO,
    STEREO_DUPLICATE,
    STEREO_UPLINK_DOWNLINK,
}

data class SplitChannels(
    val left: FloatArray,
    val right: FloatArray,
)

object ChannelSplitDetector {
    private const val SILENCE_ENERGY = 1e-8
    private const val DUPLICATE_CORRELATION = 0.96
    private const val MIN_DUPLICATE_ENERGY_RATIO = 0.25

    fun classify(interleavedSamples: FloatArray, channelCount: Int): ChannelLayout {
        require(channelCount == 1 || channelCount == 2) {
            "Only mono and stereo PCM are supported, got $channelCount channels"
        }
        require(interleavedSamples.size % channelCount == 0) {
            "Interleaved sample count must be divisible by channel count"
        }
        if (channelCount == 1) return ChannelLayout.MONO

        val channels = splitStereo(interleavedSamples)
        val leftStats = signalStats(channels.left)
        val rightStats = signalStats(channels.right)
        if (leftStats.energy < SILENCE_ENERGY && rightStats.energy < SILENCE_ENERGY) {
            return ChannelLayout.STEREO_DUPLICATE
        }
        if (leftStats.energy < SILENCE_ENERGY || rightStats.energy < SILENCE_ENERGY) {
            return ChannelLayout.STEREO_UPLINK_DOWNLINK
        }

        val correlation = correlation(channels.left, channels.right, leftStats.mean, rightStats.mean)
        val energyRatio = minOf(leftStats.energy, rightStats.energy) /
            maxOf(leftStats.energy, rightStats.energy)
        return if (
            correlation >= DUPLICATE_CORRELATION &&
            energyRatio >= MIN_DUPLICATE_ENERGY_RATIO
        ) {
            ChannelLayout.STEREO_DUPLICATE
        } else {
            ChannelLayout.STEREO_UPLINK_DOWNLINK
        }
    }

    fun downmix(interleavedSamples: FloatArray, channelCount: Int): FloatArray {
        require(channelCount == 1 || channelCount == 2) {
            "Only mono and stereo PCM are supported, got $channelCount channels"
        }
        require(interleavedSamples.size % channelCount == 0) {
            "Interleaved sample count must be divisible by channel count"
        }
        if (channelCount == 1) return interleavedSamples.copyOf()

        return FloatArray(interleavedSamples.size / 2) { frame ->
            val offset = frame * 2
            (interleavedSamples[offset] + interleavedSamples[offset + 1]) * 0.5f
        }
    }

    fun splitStereo(interleavedSamples: FloatArray): SplitChannels {
        require(interleavedSamples.size % 2 == 0) {
            "Stereo sample count must contain complete frames"
        }
        val frameCount = interleavedSamples.size / 2
        val left = FloatArray(frameCount)
        val right = FloatArray(frameCount)
        for (frame in 0 until frameCount) {
            left[frame] = interleavedSamples[frame * 2]
            right[frame] = interleavedSamples[frame * 2 + 1]
        }
        return SplitChannels(left, right)
    }

    private fun signalStats(samples: FloatArray): SignalStats {
        if (samples.isEmpty()) return SignalStats(0.0, 0.0)
        val mean = samples.sumOf { it.toDouble() } / samples.size
        val energy = samples.sumOf {
            val centered = it - mean
            centered * centered
        } / samples.size
        return SignalStats(mean, energy)
    }

    private fun correlation(
        left: FloatArray,
        right: FloatArray,
        leftMean: Double,
        rightMean: Double,
    ): Double {
        var dotProduct = 0.0
        var leftMagnitude = 0.0
        var rightMagnitude = 0.0
        for (index in left.indices) {
            val leftValue = left[index] - leftMean
            val rightValue = right[index] - rightMean
            dotProduct += leftValue * rightValue
            leftMagnitude += leftValue * leftValue
            rightMagnitude += rightValue * rightValue
        }
        val denominator = sqrt(leftMagnitude * rightMagnitude)
        return if (denominator <= SILENCE_ENERGY) 0.0 else {
            (dotProduct / denominator).coerceIn(-1.0, 1.0).let(::abs)
        }
    }

    private data class SignalStats(
        val mean: Double,
        val energy: Double,
    )
}
