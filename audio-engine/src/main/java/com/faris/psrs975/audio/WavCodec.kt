package com.faris.psrs975.audio

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

data class DecodedWav(
  val sampleRate: Int,
  val channels: Int,
  val bitsPerSample: Int,
  val left: FloatArray,
  val right: FloatArray?,
  val loopStart: Int = 0,
  val loopEnd: Int = 0,
  val hasLoop: Boolean = false,
)

object WavCodec {
  fun decode(input: InputStream): DecodedWav {
    val bytes = input.readBytes()
    return decodeBytes(bytes)
  }

  fun decodeBytes(bytes: ByteArray): DecodedWav {
    require(bytes.size >= 44) { "Invalid WAV: file too small (${bytes.size} bytes)" }
    val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val riff = readFourCC(bb, 0)
    val wave = readFourCC(bb, 8)
    require(riff == "RIFF" && wave == "WAVE") { "Invalid RIFF/WAVE header" }

    var audioFormat = 1
    var channels = 1
    var sampleRate = 44100
    var bitsPerSample = 16
    var dataOffset = -1
    var dataSize = 0
    var loopStart = 0
    var loopEnd = 0
    var hasLoop = false

    var pos = 12
    while (pos + 8 <= bytes.size) {
      val chunkId = readFourCC(bb, pos)
      val chunkSize = bb.getInt(pos + 4)
      val payloadPos = pos + 8
      if (chunkSize < 0 || payloadPos + chunkSize > bytes.size) break

      when (chunkId) {
        "fmt " -> {
          if (chunkSize >= 16) {
            audioFormat = bb.getShort(payloadPos).toInt() and 0xFFFF
            channels = bb.getShort(payloadPos + 2).toInt() and 0xFFFF
            sampleRate = bb.getInt(payloadPos + 4)
            bitsPerSample = bb.getShort(payloadPos + 14).toInt() and 0xFFFF
          }
        }
        "data" -> {
          dataOffset = payloadPos
          dataSize = chunkSize
        }
        "smpl" -> {
          if (chunkSize >= 60) {
            val numLoops = bb.getInt(payloadPos + 28)
            if (numLoops > 0) {
              loopStart = bb.getInt(payloadPos + 36 + 8)
              loopEnd = bb.getInt(payloadPos + 36 + 12)
              if (loopEnd > loopStart) hasLoop = true
            }
          }
        }
      }
      pos = payloadPos + chunkSize + (chunkSize and 1)
    }

    require(dataOffset >= 0 && dataSize > 0) { "WAV missing audio data chunk" }
    require(channels in 1..2) { "Unsupported WAV channel count: $channels" }

    val bytesPerSample = max(1, bitsPerSample / 8)
    val frameStride = bytesPerSample * channels
    val frameCount = dataSize / frameStride
    val left = FloatArray(frameCount)
    val right = if (channels == 2) FloatArray(frameCount) else null

    var p = dataOffset
    for (i in 0 until frameCount) {
      left[i] = readSampleFloat(bytes, bb, p, audioFormat, bitsPerSample)
      if (channels == 2 && right != null) {
        right[i] = readSampleFloat(bytes, bb, p + bytesPerSample, audioFormat, bitsPerSample)
      }
      p += frameStride
    }

    return DecodedWav(
      sampleRate = sampleRate,
      channels = channels,
      bitsPerSample = bitsPerSample,
      left = left,
      right = right,
      loopStart = loopStart.coerceIn(0, max(0, frameCount - 1)),
      loopEnd = loopEnd.coerceIn(0, max(0, frameCount - 1)),
      hasLoop = hasLoop,
    )
  }

  private fun readSampleFloat(
    bytes: ByteArray,
    bb: ByteBuffer,
    offset: Int,
    audioFormat: Int,
    bitsPerSample: Int,
  ): Float {
    return when {
      audioFormat == 3 && bitsPerSample == 32 -> bb.getFloat(offset).coerceIn(-1f, 1f)
      bitsPerSample == 8 -> ((bytes[offset].toInt() and 0xFF) - 128) / 128f
      bitsPerSample == 16 -> bb.getShort(offset).toFloat() / 32768f
      bitsPerSample == 24 -> {
        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = bytes[offset + 1].toInt() and 0xFF
        val b2 = bytes[offset + 2].toInt() // signed high byte
        val value = (b2 shl 16) or (b1 shl 8) or b0
        value.toFloat() / 8388608f
      }
      bitsPerSample == 32 -> bb.getInt(offset).toFloat() / 2147483648f
      else -> 0f
    }
  }

  private fun readFourCC(bb: ByteBuffer, offset: Int): String {
    val chars = CharArray(4) { idx -> (bb.get(offset + idx).toInt() and 0xFF).toChar() }
    return String(chars)
  }

  fun writeStereoPcm16Header(out: OutputStream, sampleRate: Int, totalPcmBytes: Long) {
    val byteRate = sampleRate.toLong() * 2L * 2L
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    header.put("RIFF".toByteArray(Charsets.US_ASCII))
    header.putInt((36L + totalPcmBytes).toInt())
    header.put("WAVE".toByteArray(Charsets.US_ASCII))
    header.put("fmt ".toByteArray(Charsets.US_ASCII))
    header.putInt(16)
    header.putShort(1) // PCM
    header.putShort(2) // Stereo
    header.putInt(sampleRate)
    header.putInt(byteRate.toInt())
    header.putShort(4) // Block align
    header.putShort(16) // Bits per sample
    header.put("data".toByteArray(Charsets.US_ASCII))
    header.putInt(totalPcmBytes.toInt())
    out.write(header.array())
  }

  fun encodeWavBytes(
    left: FloatArray,
    right: FloatArray?,
    sampleRate: Int,
  ): ByteArray {
    val frameCount = left.size
    val channels = if (right != null) 2 else 1
    val totalPcmBytes = frameCount.toLong() * channels.toLong() * 2L
    val byteRate = sampleRate.toLong() * channels.toLong() * 2L
    val out = ByteArrayOutputStream(44 + totalPcmBytes.toInt())
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    header.put("RIFF".toByteArray(Charsets.US_ASCII))
    header.putInt((36L + totalPcmBytes).toInt())
    header.put("WAVE".toByteArray(Charsets.US_ASCII))
    header.put("fmt ".toByteArray(Charsets.US_ASCII))
    header.putInt(16)
    header.putShort(1) // PCM
    header.putShort(channels.toShort())
    header.putInt(sampleRate)
    header.putInt(byteRate.toInt())
    header.putShort((channels * 2).toShort())
    header.putShort(16)
    header.put("data".toByteArray(Charsets.US_ASCII))
    header.putInt(totalPcmBytes.toInt())
    out.write(header.array())

    val buf = ByteBuffer.allocate(frameCount * channels * 2).order(ByteOrder.LITTLE_ENDIAN)
    for (i in 0 until frameCount) {
      val sL = (left[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
      buf.putShort(sL)
      if (right != null) {
        val sR = (right.getOrElse(i) { 0f }.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        buf.putShort(sR)
      }
    }
    out.write(buf.array())
    return out.toByteArray()
  }
}
