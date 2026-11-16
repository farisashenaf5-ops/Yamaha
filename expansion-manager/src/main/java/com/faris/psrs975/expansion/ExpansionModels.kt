package com.faris.psrs975.expansion

import com.faris.psrs975.audio.SampleInstrument
import com.faris.psrs975.midi.MidiSong
import com.faris.psrs975.storage.ExpansionPackEntity
import com.faris.psrs975.style.ArrangerStyle

data class ExpansionImportState(
  val isImporting: Boolean = false,
  val progress: Float = 0f,
  val statusMessage: String = "Ready to import SF2, STY, SFZ, WAV, MIDI, or ZIP packs",
  val errorMessage: String? = null,
  val packs: List<ExpansionPackEntity> = emptyList(),
  val totalStorageBytes: Long = 0L,
  val lastImportSummary: String? = null,
)

data class ExpansionImportResult(
  val entity: ExpansionPackEntity,
  val instruments: List<SampleInstrument> = emptyList(),
  val midiSongs: List<MidiSong> = emptyList(),
  val styles: List<ArrangerStyle> = emptyList(),
  val summaryDetail: String = "",
)

data class PersistedExpansionAssets(
  val instruments: List<SampleInstrument> = emptyList(),
  val midiSongs: List<MidiSong> = emptyList(),
  val styles: List<ArrangerStyle> = emptyList(),
)
