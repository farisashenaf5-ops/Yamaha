package com.faris.psrs975.voice

import com.faris.psrs975.samples.AcousticSynthesisModel

enum class VoiceCategory(val displayName: String) {
  PIANO("Piano"),
  KEYBOARD("Keyboard"),
  WOODWIND("Woodwind"),
  BRASS("Brass"),
  STRINGS("Strings"),
  GUITAR("Guitar & Bass"),
  SYNTH("Synth"),
  ETHIOPIAN("Ethiopian"),
  DRUMS("Perc & Drum"),
  USER_EXPANSION("User / Expansion"),
}

data class VoicePatch(
  val id: String,
  val name: String,
  val category: VoiceCategory,
  val synthesisModel: AcousticSynthesisModel,
  val badge: String = "S.Art!",
  val defaultAttackScale: Float = 1.0f,
  val defaultReleaseScale: Float = 1.0f,
  val defaultBrightness: Float = 1.0f,
  val defaultOctaveOffset: Int = 0,
  val isUserVoice: Boolean = false,
  val sampleInstrumentId: String = id,
  val sourceFormat: String = "Built-in PCM",
)

data class VoiceSlotEditParams(
  val attackScale: Float = 1.0f,
  val releaseScale: Float = 1.0f,
  val brightnessScale: Float = 1.0f,
  val octaveOffset: Int = 0,
  val dspEnabled: Boolean = false,
)

data class VoiceEngineState(
  val allVoices: List<VoicePatch> = emptyList(),
  val favoriteIds: Set<String> = emptySet(),
  val right1Voice: VoicePatch? = null,
  val right2Voice: VoicePatch? = null,
  val leftVoice: VoicePatch? = null,
  val right1Enabled: Boolean = true,
  val right2Enabled: Boolean = false,
  val leftEnabled: Boolean = true,
  val activePartSelection: ActiveVoiceSlot = ActiveVoiceSlot.RIGHT_1,
  val right1Edit: VoiceSlotEditParams = VoiceSlotEditParams(),
  val right2Edit: VoiceSlotEditParams = VoiceSlotEditParams(),
  val leftEdit: VoiceSlotEditParams = VoiceSlotEditParams(),
  val harmonyEnabled: Boolean = false,
  val sustainEnabled: Boolean = false,
  val dspEnabled: Boolean = false,
)

enum class ActiveVoiceSlot(val label: String) {
  RIGHT_1("RIGHT 1"),
  RIGHT_2("RIGHT 2"),
  LEFT("LEFT"),
}
