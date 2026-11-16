package com.faris.psrs975.soundfont

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.faris.psrs975.audio.WavCodec
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

data class DecodedAudioSample(
  val left: FloatArray,
  val right: FloatArray?,
  val sampleRate: Int,
)

/**
 * Decodes WAV (pure Kotlin) as well as MP3, OGG, M4A, and FLAC audio files (via Android MediaExtractor + MediaCodec)
 * into normalized 32-bit float PCM buffers ready for real-time SampleZone playback.
 */
object AudioSampleDecoder {

  private const val MAX_ONE_SHOT_FRAMES = 48000 * 12 // Cap single drum hit at 12s to stay memory-safe

  fun decodeFile(file: File, extensionHint: String = ""): DecodedAudioSample {
    val ext = (extensionHint.ifBlank { file.extension }).lowercase()
    if (ext == "wav" || ext == "wave") {
      val wav = WavCodec.decodeBytes(file.readBytes())
      return DecodedAudioSample(left = wav.left, right = wav.right, sampleRate = wav.sampleRate)
    }
    return decodeWithMediaCodec(file.absolutePath)
  }

  fun decodeBytes(bytes: ByteArray, extension: String, scratchFile: File? = null): DecodedAudioSample {
    val ext = extension.lowercase()
    if (ext == "wav" || ext == "wave" || (bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE")) {
      val wav = WavCodec.decodeBytes(bytes)
      return DecodedAudioSample(left = wav.left, right = wav.right, sampleRate = wav.sampleRate)
    }
    val targetFile = scratchFile ?: File.createTempFile("drum_dec_", ".$ext")
    val createdTemp = scratchFile == null
    return try {
      targetFile.writeBytes(bytes)
      decodeWithMediaCodec(targetFile.absolutePath)
    } finally {
      if (createdTemp) {
        runCatching { targetFile.delete() }
      }
    }
  }

  private fun decodeWithMediaCodec(filePath: String): DecodedAudioSample {
    val extractor = MediaExtractor()
    var codec: MediaCodec? = null
    try {
      extractor.setDataSource(filePath)
      var trackIdx = -1
      var format: MediaFormat? = null
      var mime = ""
      for (i in 0 until extractor.trackCount) {
        val f = extractor.getTrackFormat(i)
        val m = f.getString(MediaFormat.KEY_MIME) ?: ""
        if (m.startsWith("audio/")) {
          trackIdx = i
          format = f
          mime = m
          break
        }
      }
      require(trackIdx >= 0 && format != null) { "No decodable audio track found in file" }
      extractor.selectTrack(trackIdx)

      var sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
        format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
      } else 44100
      var channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
        format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
      } else 1

      codec = MediaCodec.createDecoderByType(mime)
      codec.configure(format, null, null, 0)
      codec.start()

      val pcmOut = ByteArrayOutputStream()
      val bufferInfo = MediaCodec.BufferInfo()
      var sawInputEos = false
      var sawOutputEos = false
      val maxBytes = MAX_ONE_SHOT_FRAMES * channelCount.coerceAtLeast(1) * 2

      while (!sawOutputEos && pcmOut.size() < maxBytes) {
        if (!sawInputEos) {
          val inIdx = codec.dequeueInputBuffer(10_000L)
          if (inIdx >= 0) {
            val inBuf = codec.getInputBuffer(inIdx)
            if (inBuf != null) {
              val readSize = extractor.readSampleData(inBuf, 0)
              if (readSize < 0) {
                codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                sawInputEos = true
              } else {
                codec.queueInputBuffer(inIdx, 0, readSize, extractor.sampleTime, 0)
                extractor.advance()
              }
            }
          }
        }

        val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
        when {
          outIdx >= 0 -> {
            val outBuf = codec.getOutputBuffer(outIdx)
            if (outBuf != null && bufferInfo.size > 0) {
              outBuf.position(bufferInfo.offset)
              outBuf.limit(bufferInfo.offset + bufferInfo.size)
              val chunk = ByteArray(bufferInfo.size)
              outBuf.get(chunk)
              val remaining = maxBytes - pcmOut.size()
              if (remaining > 0) {
                pcmOut.write(chunk, 0, min(chunk.size, remaining))
              }
            }
            codec.releaseOutputBuffer(outIdx, false)
            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
              sawOutputEos = true
            }
          }
          outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
            val outFmt = codec.outputFormat
            if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
              sampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            }
            if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
              channelCount = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            }
          }
        }
      }

      val rawBytes = pcmOut.toByteArray()
      require(rawBytes.size >= 4) { "Decoded audio contained no PCM frames" }
      val ch = channelCount.coerceIn(1, 2)
      val frameCount = rawBytes.size / (2 * ch)
      val bb = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN)
      val left = FloatArray(frameCount)
      val right = if (ch >= 2) FloatArray(frameCount) else null
      for (i in 0 until frameCount) {
        left[i] = bb.getShort().toFloat() / 32768f
        if (right != null) {
          right[i] = bb.getShort().toFloat() / 32768f
        }
        if (channelCount > 2) {
          repeat(channelCount - 2) { if (bb.remaining() >= 2) bb.getShort() }
        }
      }
      return DecodedAudioSample(left = left, right = right, sampleRate = sampleRate.coerceIn(8000, 96000))
    } finally {
      runCatching { codec?.stop() }
      runCatching { codec?.release() }
      runCatching { extractor.release() }
    }
  }
}
