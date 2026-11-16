package com.faris.psrs975.style

import com.faris.psrs975.audio.WorkstationPart

enum class StyleCategory(val displayName: String) {
  POP_ROCK("Pop&Rock"),
  DANCE("Dance"),
  BALLAD("Ballad"),
  SWING_JAZZ("Swing&Jazz"),
  RNB("R&B"),
  COUNTRY("Country"),
  LATIN("Latin"),
  ENTERTAINER("Entertainer"),
  WORLD("World"),
}

enum class StyleSection(val label: String, val isIntro: Boolean = false, val isEnding: Boolean = false, val isFill: Boolean = false) {
  INTRO_1("INTRO I", isIntro = true),
  INTRO_2("INTRO II", isIntro = true),
  INTRO_3("INTRO III", isIntro = true),
  MAIN_A("MAIN A"),
  MAIN_B("MAIN B"),
  MAIN_C("MAIN C"),
  MAIN_D("MAIN D"),
  FILL_A("FILL A", isFill = true),
  FILL_B("FILL B", isFill = true),
  FILL_C("FILL C", isFill = true),
  FILL_D("FILL D", isFill = true),
  BREAK("BREAK", isFill = true),
  ENDING_1("ENDING I", isEnding = true),
  ENDING_2("ENDING II", isEnding = true),
  ENDING_3("ENDING III", isEnding = true),
}

enum class StyleChannelType(val part: WorkstationPart, val label: String) {
  RHYTHM_1(WorkstationPart.RHYTHM_1, "RHY1"),
  RHYTHM_2(WorkstationPart.RHYTHM_2, "RHY2"),
  BASS(WorkstationPart.BASS, "BASS"),
  CHORD_1(WorkstationPart.CHORD_1, "CHD1"),
  CHORD_2(WorkstationPart.CHORD_2, "CHD2"),
  PAD(WorkstationPart.PAD, "PAD"),
  PHRASE_1(WorkstationPart.PHRASE_1, "PHR1"),
  PHRASE_2(WorkstationPart.PHRASE_2, "PHR2"),
}

/**
 * A pattern step event inside a 16-step-per-bar (16th-note quantized) section pattern.
 * [chordDegree] >= 0 picks a note from the active chord voicing (0=root, 1=3rd, 2=5th, 3=7th/octave).
 * For drums (RHY1/RHY2), [fixedMidiNote] is played directly.
 */
data class StylePatternEvent(
  val stepIndex: Int, // 0 until totalSteps (16 * bars)
  val channel: StyleChannelType,
  val fixedMidiNote: Int = 36,
  val chordDegree: Int = -1, // -1 = use fixedMidiNote, >=0 = chord-transposed degree
  val octaveOffset: Int = 0,
  val velocity: Int = 96,
  val durationSteps: Int = 1,
)

data class StyleSectionPattern(
  val section: StyleSection,
  val lengthBars: Int = 2,
  val events: List<StylePatternEvent>,
) {
  val totalSteps: Int get() = lengthBars * 16
}

data class OneTouchSetting(
  val slot: Int, // 1..4
  val voiceR1Id: String,
  val voiceR2Id: String?,
  val voiceLeftId: String,
  val label: String,
)

data class ArrangerStyle(
  val id: String,
  val name: String,
  val category: StyleCategory,
  val defaultBpm: Int = 120,
  val timeSignatureNumerator: Int = 4,
  val timeSignatureDenominator: Int = 4,
  val isUserStyle: Boolean = false,
  val sections: Map<StyleSection, StyleSectionPattern>,
  val otsPresets: List<OneTouchSetting> = emptyList(),
  val channelVoices: Map<StyleChannelType, String> = defaultChannelVoices(),
) {
  companion object {
    fun defaultChannelVoices(): Map<StyleChannelType, String> = mapOf(
      StyleChannelType.RHYTHM_1 to "drum_std",
      StyleChannelType.RHYTHM_2 to "drum_ethio",
      StyleChannelType.BASS to "syn_bass",
      StyleChannelType.CHORD_1 to "gtr_acoustic",
      StyleChannelType.CHORD_2 to "piano_electric",
      StyleChannelType.PAD to "syn_pad",
      StyleChannelType.PHRASE_1 to "brass_section",
      StyleChannelType.PHRASE_2 to "str_ensemble",
    )
  }
}

data class StyleEngineState(
  val allStyles: List<ArrangerStyle> = emptyList(),
  val currentStyle: ArrangerStyle? = null,
  val acmpEnabled: Boolean = true,
  val syncStartEnabled: Boolean = false,
  val syncStopEnabled: Boolean = false,
  val autoFillEnabled: Boolean = true,
  val isPlaying: Boolean = false,
  val currentSection: StyleSection = StyleSection.MAIN_A,
  val pendingSection: StyleSection? = null,
  val tempoBpm: Int = 120,
  val currentBar: Int = 1,
  val currentBeat: Int = 1,
  val activeStepInBar: Int = 0,
  val activeOtsSlot: Int = 1,
  val fadeGain: Float = 1.0f,
  val isFading: Boolean = false,
  val channelEnabled: Map<StyleChannelType, Boolean> = StyleChannelType.entries.associateWith { true },
)
