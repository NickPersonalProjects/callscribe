package com.nicholaston.callscribe.transcription

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DecodedAudioFormat(
    val sampleRate: Int,
    val channelCount: Int,
)

data class FloatPcmChunk(
    val samples: FloatArray,
    val presentationTimeUs: Long,
)

fun interface PcmChunkConsumer {
    fun onChunk(chunk: FloatPcmChunk)

    fun onFormat(format: DecodedAudioFormat) = Unit
}

sealed class AudioDecodingException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    class SourceUnavailable(uri: Uri, cause: Throwable) :
        AudioDecodingException("Unable to open audio source: $uri", cause)

    class AudioTrackNotFound(uri: Uri) :
        AudioDecodingException("No decodable audio track found in: $uri")

    class UnsupportedPcmEncoding(encoding: Int) :
        AudioDecodingException("Unsupported decoder PCM encoding: $encoding")

    class CodecFailure(mimeType: String, cause: Throwable) :
        AudioDecodingException("Failed while decoding audio track $mimeType", cause)
}

/**
 * Synchronously streams decoded PCM from [uri]. Call this from a worker thread.
 *
 * Decoder output buffers are converted and delivered one at a time, so the complete recording is
 * never retained in memory.
 */
class AudioDecoder(
    private val context: Context,
    private val uri: Uri,
) {
    fun decode(consumer: PcmChunkConsumer): DecodedAudioFormat {
        val extractor = MediaExtractor()
        try {
            try {
                extractor.setDataSource(context, uri, null)
            } catch (error: Exception) {
                throw AudioDecodingException.SourceUnavailable(uri, error)
            }

            val track = findAudioTrack(extractor)
                ?: throw AudioDecodingException.AudioTrackNotFound(uri)
            extractor.selectTrack(track.index)

            val codec = try {
                MediaCodec.createDecoderByType(track.mimeType)
            } catch (error: Exception) {
                throw AudioDecodingException.CodecFailure(track.mimeType, error)
            }

            try {
                codec.configure(track.format, null, null, 0)
                codec.start()
                return drainDecoder(extractor, codec, track, consumer)
            } catch (error: AudioDecodingException) {
                throw error
            } catch (error: Exception) {
                throw AudioDecodingException.CodecFailure(track.mimeType, error)
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun drainDecoder(
        extractor: MediaExtractor,
        codec: MediaCodec,
        track: AudioTrack,
        consumer: PcmChunkConsumer,
    ): DecodedAudioFormat {
        var inputEnded = false
        var outputEnded = false
        var outputFormat = track.format.toDecodedAudioFormat()
        var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
        val bufferInfo = MediaCodec.BufferInfo()
        consumer.onFormat(outputFormat)

        while (!outputEnded) {
            if (!inputEnded) {
                val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                        ?: throw IllegalStateException("Decoder returned a null input buffer")
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputEnded = true
                    } else {
                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            sampleSize,
                            extractor.sampleTime.coerceAtLeast(0),
                            extractor.sampleFlags,
                        )
                        extractor.advance()
                    }
                }
            }

            when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val format = codec.outputFormat
                    val changedFormat = format.toDecodedAudioFormat()
                    if (changedFormat != outputFormat) {
                        outputFormat = changedFormat
                        consumer.onFormat(outputFormat)
                    }
                    pcmEncoding = format.getIntegerOrDefault(
                        MediaFormat.KEY_PCM_ENCODING,
                        AudioFormat.ENCODING_PCM_16BIT,
                    )
                }

                MediaCodec.INFO_TRY_AGAIN_LATER,
                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED,
                -> Unit

                else -> if (outputIndex >= 0) {
                    if (bufferInfo.size > 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                            ?: throw IllegalStateException("Decoder returned a null output buffer")
                        val samples = outputBuffer.readPcm(bufferInfo, pcmEncoding)
                        if (samples.isNotEmpty()) {
                            consumer.onChunk(
                                FloatPcmChunk(samples, bufferInfo.presentationTimeUs),
                            )
                        }
                    }
                    outputEnded =
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(outputIndex, false)
                }
            }
        }

        return outputFormat
    }

    private fun findAudioTrack(extractor: MediaExtractor): AudioTrack? {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mimeType = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mimeType.startsWith("audio/")) {
                return AudioTrack(index, mimeType, format)
            }
        }
        return null
    }

    private data class AudioTrack(
        val index: Int,
        val mimeType: String,
        val format: MediaFormat,
    )

    private companion object {
        const val DEQUEUE_TIMEOUT_US = 10_000L
    }
}

private fun MediaFormat.toDecodedAudioFormat(): DecodedAudioFormat {
    val sampleRate = getIntegerOrDefault(MediaFormat.KEY_SAMPLE_RATE, 0)
    val channelCount = getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 0)
    require(sampleRate > 0) { "Audio format has no valid sample rate" }
    require(channelCount > 0) { "Audio format has no valid channel count" }
    return DecodedAudioFormat(sampleRate, channelCount)
}

private fun MediaFormat.getIntegerOrDefault(key: String, defaultValue: Int): Int =
    if (containsKey(key)) getInteger(key) else defaultValue

private fun ByteBuffer.readPcm(
    info: MediaCodec.BufferInfo,
    encoding: Int,
): FloatArray {
    val data = duplicate().order(ByteOrder.LITTLE_ENDIAN).apply {
        position(info.offset)
        limit(info.offset + info.size)
    }.slice().order(ByteOrder.LITTLE_ENDIAN)

    return when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> FloatArray(data.remaining()) {
            ((data.get().toInt() and 0xff) - 128) / 128f
        }

        AudioFormat.ENCODING_PCM_16BIT -> FloatArray(data.remaining() / Short.SIZE_BYTES) {
            data.short / 32768f
        }

        AudioFormat.ENCODING_PCM_24BIT_PACKED -> FloatArray(data.remaining() / 3) {
            val value = (data.get().toInt() and 0xff) or
                ((data.get().toInt() and 0xff) shl 8) or
                (data.get().toInt() shl 16)
            value / 8_388_608f
        }

        AudioFormat.ENCODING_PCM_32BIT -> FloatArray(data.remaining() / Int.SIZE_BYTES) {
            data.int / 2_147_483_648f
        }

        AudioFormat.ENCODING_PCM_FLOAT -> FloatArray(data.remaining() / Float.SIZE_BYTES) {
            data.float.coerceIn(-1f, 1f)
        }

        else -> throw AudioDecodingException.UnsupportedPcmEncoding(encoding)
    }
}
