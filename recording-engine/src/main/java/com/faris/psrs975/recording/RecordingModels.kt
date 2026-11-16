package com.faris.psrs975.recording

data class AudioRecordingItem(
  val id: String,
  val title: String,
  val filePath: String,
  val durationMs: Long,
  val sizeBytes: Long,
  val sampleRate: Int,
  val includesMicrophone: Boolean,
  val createdAtMs: Long,
)

data class RecordingEngineState(
  val isRecording: Boolean = false,
  val includeMicrophone: Boolean = false,
  val elapsedMs: Long = 0L,
  val recordings: List<AudioRecordingItem> = emptyList(),
  val playingRecordingId: String? = null,
  val playbackProgress: Float = 0f,
  val errorMessage: String? = null,
)
