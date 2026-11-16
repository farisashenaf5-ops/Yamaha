package com.faris.psrs975.midi

enum class MidiEventType {
  NOTE_ON,
  NOTE_OFF,
  CONTROL_CHANGE,
  PROGRAM_CHANGE,
  PITCH_BEND,
  TEMPO,
}

data class MidiEvent(
  val tick: Long,
  val trackIndex: Int,
  val channel: Int,
  val type: MidiEventType,
  val data1: Int = 0,
  val data2: Int = 0,
  val tempoBpm: Int = 120,
)

data class MidiTrackInfo(
  val trackIndex: Int,
  val name: String,
  val channel: Int,
  val programNumber: Int = 0,
  val noteCount: Int = 0,
  val muted: Boolean = false,
  val soloed: Boolean = false,
)

data class MidiSong(
  val id: String,
  val title: String,
  val ticksPerQuarterNote: Int = 480,
  val initialBpm: Int = 120,
  val timeSignatureNumerator: Int = 4,
  val timeSignatureDenominator: Int = 4,
  val totalTicks: Long,
  val tracks: List<MidiTrackInfo>,
  val events: List<MidiEvent>,
  val filePath: String? = null,
)
