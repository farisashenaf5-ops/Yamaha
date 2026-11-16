package com.faris.psrs975.ui

import com.faris.psrs975.audio.AudioEngineStats
import com.faris.psrs975.audio.WorkstationPart
import com.faris.psrs975.chord.ChordFingeringMode
import com.faris.psrs975.chord.DetectedChord
import com.faris.psrs975.effects.DspEffectType
import com.faris.psrs975.effects.EffectsRackState
import com.faris.psrs975.expansion.ExpansionImportState
import com.faris.psrs975.samples.CustomDrumKitLibraryState
import com.faris.psrs975.midi.MidiPlayerState
import com.faris.psrs975.midi.MidiSong
import com.faris.psrs975.mixer.MixerConsoleState
import com.faris.psrs975.recording.AudioRecordingItem
import com.faris.psrs975.recording.RecordingEngineState
import com.faris.psrs975.samples.CustomDrumKit
import com.faris.psrs975.samples.GmDrumNoteNames
import com.faris.psrs975.storage.ExpansionPackEntity
import com.faris.psrs975.storage.KeyboardSetEntity
import com.faris.psrs975.storage.RegistrationMemoryEntity
import com.faris.psrs975.style.ArrangerStyle
import com.faris.psrs975.style.StyleCategory
import com.faris.psrs975.style.StyleChannelType
import com.faris.psrs975.style.StyleEngineState
import com.faris.psrs975.style.StyleSection
import com.faris.psrs975.voice.ActiveVoiceSlot
import com.faris.psrs975.voice.VoiceCategory
import com.faris.psrs975.voice.VoiceEngineState
import com.faris.psrs975.voice.VoicePatch
import com.faris.psrs975.voice.VoiceSlotEditParams

enum class LcdPage(val title: String) {
  MAIN("MAIN"),
  VOICE("VOICE"),
  KEYBOARD_SET("KEYBOARD SET"),
  STYLE("ARRANGER / STY"),
  DRUM_KIT("CUSTOM DRUM KIT"),
  SONG("SONG"),
  MIXER("MIXER"),
  EFFECTS("EFFECTS"),
  MULTI_PAD("MULTI PAD"),
  REGISTRATION("REGISTRATION"),
  MIDI("MIDI"),
  RECORDING("RECORDING"),
  EXPANSION_MANAGER("EXPANSION / IMPORT"),
  UTILITY("UTILITY"),
  SETTINGS("SETTINGS"),
}

enum class MultiPadPlayMode(val label: String) {
  ONE_SHOT("One-Shot"),
  LOOP("Loop"),
  SYNC("Sync"),
}

data class MultiPadItem(
  val padIndex: Int, // 1..4
  val label: String,
  val playMode: MultiPadPlayMode = MultiPadPlayMode.ONE_SHOT,
  val volume: Float = 0.85f,
  val notes: List<Int> = listOf(60, 64, 67, 72),
  val isAudioPhrase: Boolean = false,
  val voiceId: String = "gtr_nylon",
  val isPlaying: Boolean = false,
)

data class MultiPadBank(
  val bankIndex: Int, // 0..3
  val bankName: String,
  val pads: List<MultiPadItem>,
)

data class WorkstationUiState(
  val powerOn: Boolean = true,
  val currentLcdPage: LcdPage = LcdPage.MAIN,
  val showMenuOverlay: Boolean = false,
  val showHelpOverlay: Boolean = false,
  val audioStats: AudioEngineStats = AudioEngineStats(),
  val voiceState: VoiceEngineState = VoiceEngineState(),
  val styleState: StyleEngineState = StyleEngineState(),
  val mixerState: MixerConsoleState = MixerConsoleState(),
  val effectsState: EffectsRackState = EffectsRackState(),
  val midiState: MidiPlayerState = MidiPlayerState(),
  val availableSongs: List<MidiSong> = emptyList(),
  val recordingState: RecordingEngineState = RecordingEngineState(),
  val expansionState: ExpansionImportState = ExpansionImportState(),
  val drumKitState: CustomDrumKitLibraryState = CustomDrumKitLibraryState(),
  val activeArrangerDrumKitId: String = GmDrumNoteNames.FACTORY_KIT_ID,
  val styleDrumKitBindings: Map<String, String> = emptyMap(),
  val pendingStyDrumSelectionStyleId: String? = null,
  val detectedChord: DetectedChord = DetectedChord.DEFAULT_C_MAJOR,
  val fingeringMode: ChordFingeringMode = ChordFingeringMode.FINGERED,
  val splitPointMidi: Int = 54, // F#3
  val transpose: Int = 0,
  val upperOctave: Int = 0,
  val keySizeScale: Float = 1.0f,
  val keyViewportOffset: Float = 0.2f,
  val activePressedNotes: Set<Int> = emptySet(),
  val pitchBendSemis: Float = 0f,
  val modWheelDepth: Float = 0f,
  val liveKnobCutoff: Float = 0.72f,
  val liveKnobResonance: Float = 0.28f,
  val liveKnobAssignLabelA: String = "CUTOFF",
  val liveKnobAssignLabelB: String = "RESON",
  val selectedVoiceCategory: VoiceCategory = VoiceCategory.PIANO,
  val voiceSearchQuery: String = "",
  val selectedStyleCategory: StyleCategory = StyleCategory.POP_ROCK,
  val multiPadBanks: List<MultiPadBank> = defaultMultiPadBanks(),
  val activeMultiPadBankIndex: Int = 0,
  val isRecordingMultiPad: Int? = null,
  val activeRegBank: Int = 0,
  val activeRegSlot: Int? = null,
  val registrationSlots: List<RegistrationMemoryEntity> = emptyList(),
  val keyboardSets: List<KeyboardSetEntity> = emptyList(),
  val activeKeyboardSetId: String? = null,
  val regMemoryArmed: Boolean = false,
  val regFreezeEnabled: Boolean = false,
  val hardwareMidiDevices: List<String> = emptyList(),
  val midiMonitorLog: List<String> = emptyList(),
  val statusToast: String? = null,
) {
  val activeKeyboardSet: KeyboardSetEntity?
    get() = keyboardSets.find { it.id == activeKeyboardSetId }

  val activeStyleDrumKitId: String
    get() = styleState.currentStyle?.id?.let { styleDrumKitBindings[it] } ?: activeArrangerDrumKitId

  val activeStyleDrumKit: CustomDrumKit
    get() = drumKitState.kits.find { it.id == activeStyleDrumKitId } ?: drumKitState.kits.first()

  companion object {
    fun defaultMultiPadBanks(): List<MultiPadBank> = listOf(
      MultiPadBank(
        0,
        "Acoustic Guitar Strums",
        listOf(
          MultiPadItem(1, "Gtr Strum 1", MultiPadPlayMode.LOOP, 0.85f, listOf(48, 52, 55, 60, 64), voiceId = "gtr_nylon"),
          MultiPadItem(2, "Arp Up", MultiPadPlayMode.SYNC, 0.82f, listOf(60, 64, 67, 72, 67, 64), voiceId = "gtr_acoustic"),
          MultiPadItem(3, "Brass Hit", MultiPadPlayMode.ONE_SHOT, 0.90f, listOf(60, 64, 67, 70), voiceId = "brass_section"),
          MultiPadItem(4, "Shaker Perc", MultiPadPlayMode.LOOP, 0.78f, listOf(42, 42, 46, 42), isAudioPhrase = true, voiceId = "drum_std"),
        ),
      ),
      MultiPadBank(
        1,
        "Ethiopian Grooves",
        listOf(
          MultiPadItem(1, "Krar Ostinato", MultiPadPlayMode.LOOP, 0.86f, listOf(60, 62, 63, 67, 68), voiceId = "eth_krar"),
          MultiPadItem(2, "Masinko Riff", MultiPadPlayMode.ONE_SHOT, 0.88f, listOf(67, 68, 67, 63, 62, 60), voiceId = "eth_masinko"),
          MultiPadItem(3, "Washint Call", MultiPadPlayMode.ONE_SHOT, 0.84f, listOf(72, 74, 75, 79), voiceId = "eth_washint"),
          MultiPadItem(4, "Kebero Pulse", MultiPadPlayMode.LOOP, 0.90f, listOf(36, 60, 36, 60), isAudioPhrase = true, voiceId = "eth_kebero"),
        ),
      ),
      MultiPadBank(
        2,
        "Synth & FX Risers",
        listOf(
          MultiPadItem(1, "Synth Arp", MultiPadPlayMode.LOOP, 0.82f, listOf(60, 67, 72, 74, 79), voiceId = "syn_lead"),
          MultiPadItem(2, "Warm Swell", MultiPadPlayMode.ONE_SHOT, 0.80f, listOf(48, 55, 60, 64), voiceId = "syn_atmos"),
          MultiPadItem(3, "Sub Drop", MultiPadPlayMode.ONE_SHOT, 0.88f, listOf(36, 36, 24), voiceId = "syn_bass"),
          MultiPadItem(4, "Crash Roll", MultiPadPlayMode.ONE_SHOT, 0.85f, listOf(49, 49), isAudioPhrase = true, voiceId = "drum_std"),
        ),
      ),
      MultiPadBank(
        3,
        "User Pad Bank",
        listOf(
          MultiPadItem(1, "User Pad 1", MultiPadPlayMode.ONE_SHOT, 0.85f, listOf(60, 64, 67), voiceId = "piano_grand"),
          MultiPadItem(2, "User Pad 2", MultiPadPlayMode.ONE_SHOT, 0.85f, listOf(62, 65, 69), voiceId = "piano_electric"),
          MultiPadItem(3, "User Pad 3", MultiPadPlayMode.LOOP, 0.85f, listOf(64, 67, 71), voiceId = "str_ensemble"),
          MultiPadItem(4, "User Pad 4", MultiPadPlayMode.SYNC, 0.85f, listOf(67, 71, 74), voiceId = "brass_trumpet"),
        ),
      ),
    )
  }
}

interface WorkstationActions {
  fun togglePower()
  fun navigateTo(page: LcdPage)
  fun toggleMenuOverlay()
  fun toggleHelpOverlay()
  fun dismissToast()

  // Keyboard & Performance
  fun onKeyDown(midiNote: Int, velocity: Int)
  fun onKeyUp(midiNote: Int)
  fun setMasterVolume(volume: Float)
  fun setMasterBalance(balance: Float)
  fun setMicGain(gain: Float)
  fun setTranspose(semitones: Int)
  fun setUpperOctave(octaves: Int)
  fun setSplitPoint(midiNote: Int)
  fun setFingeringMode(mode: ChordFingeringMode)
  fun setKeySizeScale(scale: Float)
  fun setKeyViewportOffset(offset: Float)
  fun setPitchBend(semitones: Float)
  fun setModWheel(depth: Float)
  fun setLiveKnobCutoff(value: Float)
  fun setLiveKnobResonance(value: Float)
  fun panicAllNotesOff()

  // Voices
  fun selectVoiceCategory(category: VoiceCategory)
  fun setVoiceSearch(query: String)
  fun selectVoicePatch(patch: VoicePatch)
  fun previewVoicePatch(patch: VoicePatch)
  fun cycleVoice(delta: Int)
  fun setActiveVoiceSlot(slot: ActiveVoiceSlot)
  fun toggleVoiceSlotEnabled(slot: ActiveVoiceSlot)
  fun toggleFavoriteVoice(voiceId: String)
  fun updateVoiceSlotEdit(slot: ActiveVoiceSlot, params: VoiceSlotEditParams)
  fun saveUserVoice(name: String)
  fun toggleHarmony()
  fun toggleSustain()
  fun toggleDsp()

  // Styles & Chords
  fun selectStyleCategory(category: StyleCategory)
  fun selectStyle(style: ArrangerStyle)
  fun toggleStyleStartStop()
  fun selectStyleSection(section: StyleSection)
  fun triggerFillIn()
  fun selectChordPreset(chord: DetectedChord)
  fun toggleAcmp()
  fun toggleSyncStart()
  fun toggleSyncStop()
  fun toggleAutoFill()
  fun triggerFadeInOut()
  fun setTempo(bpm: Int)
  fun tapTempo()
  fun resetTempo()
  fun toggleStyleChannel(channel: StyleChannelType)
  fun selectOts(slot: Int)
  fun toggleStylePatternStep(section: StyleSection, stepIndex: Int, channel: StyleChannelType, midiNote: Int)
  fun importCurrentMidiAsStyle(styleName: String)

  // Multi Pads
  fun selectMultiPadBank(bankIndex: Int)
  fun triggerMultiPad(padIndex: Int)
  fun stopAllMultiPads()
  fun setMultiPadMode(padIndex: Int, mode: MultiPadPlayMode)
  fun setMultiPadVolume(padIndex: Int, volume: Float)
  fun toggleRecordMultiPad(padIndex: Int)

  // Registration Memory
  fun selectRegBank(deltaOrIndex: Int, isRelative: Boolean)
  fun pressRegistrationSlot(slotIndex: Int)
  fun toggleRegMemoryArm()
  fun toggleRegFreeze()
  fun exportRegistrationsJson(): String
  fun importRegistrationsJson(json: String)

  // Keyboard Sets (including Empty Keyboard Set workflow)
  fun createEmptyKeyboardSet(name: String? = null, activateImmediately: Boolean = true)
  fun saveCurrentAsKeyboardSet(name: String? = null)
  fun updateKeyboardSetFromLive(setId: String)
  fun selectAndApplyKeyboardSet(setId: String)
  fun clearLiveKeyboardToEmpty()
  fun clearKeyboardSetSlot(setId: String?, slot: ActiveVoiceSlot)
  fun renameKeyboardSet(setId: String, newName: String)
  fun duplicateKeyboardSet(setId: String)
  fun deleteKeyboardSet(setId: String)

  // Mixer & Effects
  fun setMixerVolume(part: WorkstationPart, volume: Float)
  fun setMixerPan(part: WorkstationPart, pan: Float)
  fun setMixerReverbSend(part: WorkstationPart, level: Float)
  fun setMixerChorusSend(part: WorkstationPart, level: Float)
  fun setMixerDspSend(part: WorkstationPart, level: Float)
  fun toggleMixerMute(part: WorkstationPart)
  fun toggleMixerSolo(part: WorkstationPart)
  fun resetMixer()
  fun updateEffectsState(transform: (EffectsRackState) -> EffectsRackState)

  // MIDI & Song Player
  fun selectMidiSong(song: MidiSong)
  fun toggleSongPlayPause()
  fun stopSong()
  fun seekSong(fraction: Float)
  fun toggleSongLoop()
  fun toggleSongTrackMute(trackIndex: Int)
  fun toggleSongTrackSolo(trackIndex: Int)
  fun toggleMidiRecording()
  fun exportActiveSongMidi()
  fun launchQuickDemo()

  // Audio Recording
  fun toggleAudioRecording()
  fun toggleRecordMicrophone(enabled: Boolean)
  fun playOrStopRecording(item: AudioRecordingItem)
  fun renameRecording(item: AudioRecordingItem, newName: String)
  fun deleteRecording(item: AudioRecordingItem)
  fun shareRecording(item: AudioRecordingItem)

  // Expansion, SF2/STY Importer, Custom Drum Kits & Utility
  fun requestImportSf2File()
  fun requestImportStyFile()
  fun requestImportExpansionFile()
  fun requestImportDrumKitFiles()
  fun requestImportDrumSampleForSlot(kitId: String, mappingId: String)
  fun selectDrumKitInEditor(kitId: String)
  fun assignDrumKitToStyle(kitId: String, styleId: String? = null)
  fun createCustomDrumKit(name: String)
  fun renameCustomDrumKit(kitId: String, newName: String)
  fun saveCustomDrumKit(kitId: String)
  fun deleteCustomDrumKit(kitId: String)
  fun updateDrumMappingMidiNote(kitId: String, mappingId: String, midiNote: Int)
  fun updateDrumMappingGain(kitId: String, mappingId: String, gain: Float)
  fun addCustomDrumMappingSlot(kitId: String, midiNote: Int)
  fun removeCustomDrumMappingSlot(kitId: String, mappingId: String)
  fun previewDrumPad(kitId: String, midiNote: Int, velocity: Int = 114)
  fun dismissPendingStyDrumPrompt()
  fun previewExpansionPack(pack: ExpansionPackEntity)
  fun updateExpansionGroup(id: String, group: String)
  fun deleteExpansionPack(pack: ExpansionPackEntity)
  fun setSampleRate(sampleRate: Int)
  fun resetSessionToDefaults()
}
