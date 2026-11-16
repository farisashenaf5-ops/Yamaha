package com.faris.psrs975.recording

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import com.faris.psrs975.audio.AudioTapListener
import com.faris.psrs975.audio.WavCodec
import com.faris.psrs975.audio.WorkstationAudioEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stereo WAV recorder and player capturing the WorkstationAudioEngine master mix bus
 * and optionally mixing live microphone input via Android AudioRecord.
 */
class AudioRecordingEngine(
  private val context: Context,
  private val audioEngine: WorkstationAudioEngine,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val recordingsDir = File(context.filesDir, "recordings").apply { mkdirs() }

  private val _state = MutableStateFlow(RecordingEngineState())
  val state: StateFlow<RecordingEngineState> = _state.asStateFlow()

  private var activeFile: File? = null
  private var pcmOut: BufferedOutputStream? = null
  private var byteBufferScratch = ByteArray(4096)
  private var totalPcmBytes = 0L
  private var recordStartMs = 0L
  private var timerJob: Job? = null

  private var micRecord: AudioRecord? = null
  @Volatile private var micGain = 0.7f
  private var micBuffer = ShortArray(1024)

  private var mediaPlayer: MediaPlayer? = null
  private var playbackJob: Job? = null

  init {
    refreshRecordingsList()
  }

  fun setIncludeMicrophone(enabled: Boolean) {
    _state.update { it.copy(includeMicrophone = enabled, errorMessage = null) }
  }

  fun setMicGain(gain: Float) {
    micGain = gain.coerceIn(0f, 1.5f)
  }

  @SuppressLint("MissingPermission")
  fun startRecording(hasMicPermission: Boolean) {
    if (_state.value.isRecording) return
    stopPlayback()

    val sampleRate = audioEngine.stats.value.sampleRate
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val outFile = File(recordingsDir, "PSR_REC_$stamp.wav")
    activeFile = outFile
    totalPcmBytes = 0L
    recordStartMs = System.currentTimeMillis()

    val stream = BufferedOutputStream(FileOutputStream(outFile), 16384)
    WavCodec.writeStereoPcm16Header(stream, sampleRate, 0L)
    pcmOut = stream

    val useMic = _state.value.includeMicrophone && hasMicPermission
    if (useMic) {
      try {
        val minBuf = AudioRecord.getMinBufferSize(
          sampleRate,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(2048)
        micRecord = AudioRecord(
          MediaRecorder.AudioSource.MIC,
          sampleRate,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
          minBuf,
        ).apply {
          if (state == AudioRecord.STATE_INITIALIZED) startRecording()
        }
      } catch (_: Throwable) {
        micRecord = null
      }
    }

    audioEngine.addTapListener(tapListener)
    _state.update { it.copy(isRecording = true, elapsedMs = 0L, errorMessage = null) }

    timerJob?.cancel()
    timerJob = scope.launch {
      while (isActive && _state.value.isRecording) {
        _state.update { it.copy(elapsedMs = System.currentTimeMillis() - recordStartMs) }
        delay(200L)
      }
    }
  }

  fun stopRecording(): AudioRecordingItem? {
    if (!_state.value.isRecording) return null
    audioEngine.removeTapListener(tapListener)
    timerJob?.cancel()

    try {
      micRecord?.stop()
      micRecord?.release()
    } catch (_: Throwable) {
    }
    micRecord = null

    try {
      pcmOut?.flush()
      pcmOut?.close()
    } catch (_: Throwable) {
    }
    pcmOut = null

    val file = activeFile
    activeFile = null
    if (file != null && file.exists()) {
      patchWavHeader(file, totalPcmBytes)
    }

    _state.update { it.copy(isRecording = false, elapsedMs = 0L) }
    refreshRecordingsList()
    return file?.let { f -> _state.value.recordings.find { it.filePath == f.absolutePath } }
  }

  private val tapListener = AudioTapListener { interleavedPcm16, shortCount, _ ->
    val out = pcmOut ?: return@AudioTapListener
    val neededBytes = shortCount * 2
    if (byteBufferScratch.size < neededBytes) {
      byteBufferScratch = ByteArray(neededBytes)
    }

    val mic = micRecord
    val micReadCount = if (mic != null && mic.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
      val frames = shortCount / 2
      if (micBuffer.size < frames) micBuffer = ShortArray(frames)
      mic.read(micBuffer, 0, frames, AudioRecord.READ_NON_BLOCKING).coerceAtLeast(0)
    } else 0

    for (i in 0 until shortCount) {
      var sample = interleavedPcm16[i].toInt()
      val frameIdx = i / 2
      if (frameIdx < micReadCount) {
        sample += (micBuffer[frameIdx] * micGain).toInt()
      }
      val clamped = sample.coerceIn(-32768, 32767)
      byteBufferScratch[i * 2] = (clamped and 0xFF).toByte()
      byteBufferScratch[i * 2 + 1] = ((clamped ushr 8) and 0xFF).toByte()
    }

    try {
      out.write(byteBufferScratch, 0, neededBytes)
      totalPcmBytes += neededBytes
    } catch (_: Throwable) {
    }
  }

  fun playRecording(item: AudioRecordingItem) {
    if (_state.value.playingRecordingId == item.id) {
      stopPlayback()
      return
    }
    stopPlayback()
    try {
      val player = MediaPlayer().apply {
        setDataSource(item.filePath)
        prepare()
        setOnCompletionListener { stopPlayback() }
        start()
      }
      mediaPlayer = player
      _state.update { it.copy(playingRecordingId = item.id, playbackProgress = 0f, errorMessage = null) }
      playbackJob = scope.launch {
        while (isActive && mediaPlayer != null) {
          val dur = maxOf(1, player.duration)
          val pos = player.currentPosition
          _state.update { it.copy(playbackProgress = (pos.toFloat() / dur).coerceIn(0f, 1f)) }
          delay(150L)
        }
      }
    } catch (e: Throwable) {
      _state.update { it.copy(errorMessage = "Playback failed: ${e.message}") }
    }
  }

  fun stopPlayback() {
    playbackJob?.cancel()
    playbackJob = null
    try {
      mediaPlayer?.stop()
      mediaPlayer?.release()
    } catch (_: Throwable) {
    }
    mediaPlayer = null
    _state.update { it.copy(playingRecordingId = null, playbackProgress = 0f) }
  }

  fun renameRecording(item: AudioRecordingItem, newTitle: String) {
    val sanitized = newTitle.replace(Regex("[^a-zA-Z0-9_\\- ]"), "").trim()
    if (sanitized.isEmpty()) return
    val oldFile = File(item.filePath)
    val newFile = File(recordingsDir, "$sanitized.wav")
    if (oldFile.exists() && !newFile.exists()) {
      oldFile.renameTo(newFile)
      refreshRecordingsList()
    }
  }

  fun deleteRecording(item: AudioRecordingItem) {
    if (_state.value.playingRecordingId == item.id) stopPlayback()
    File(item.filePath).delete()
    refreshRecordingsList()
  }

  fun refreshRecordingsList() {
    val files = recordingsDir.listFiles { f -> f.isFile && f.extension.equals("wav", ignoreCase = true) }
      ?.sortedByDescending { it.lastModified() }
      ?: emptyList()

    val items = files.map { f ->
      val pcmBytes = maxOf(0L, f.length() - 44L)
      val sampleRate = readWavSampleRate(f)
      val durationMs = ((pcmBytes / 4.0) / sampleRate * 1000.0).toLong()
      AudioRecordingItem(
        id = f.nameWithoutExtension,
        title = f.nameWithoutExtension,
        filePath = f.absolutePath,
        durationMs = durationMs,
        sizeBytes = f.length(),
        sampleRate = sampleRate,
        includesMicrophone = false,
        createdAtMs = f.lastModified(),
      )
    }
    _state.update { it.copy(recordings = items) }
  }

  private fun readWavSampleRate(file: File): Int {
    return try {
      RandomAccessFile(file, "r").use { raf ->
        if (raf.length() < 28) return 44100
        raf.seek(24)
        val buf = ByteArray(4)
        raf.readFully(buf)
        ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).int.coerceIn(8000, 96000)
      }
    } catch (_: Throwable) {
      44100
    }
  }

  private fun patchWavHeader(file: File, pcmBytes: Long) {
    try {
      RandomAccessFile(file, "rw").use { raf ->
        val bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        raf.seek(4)
        bb.putInt((36L + pcmBytes).toInt())
        raf.write(bb.array())
        bb.clear()
        raf.seek(40)
        bb.putInt(pcmBytes.toInt())
        raf.write(bb.array())
      }
    } catch (_: Throwable) {
    }
  }
}
