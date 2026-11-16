package com.faris.psrs975.voice

import com.faris.psrs975.audio.SampleInstrument
import com.faris.psrs975.audio.WorkstationAudioEngine
import com.faris.psrs975.audio.WorkstationPart
import com.faris.psrs975.mixer.MixerEngine
import com.faris.psrs975.samples.AcousticSynthesisModel
import com.faris.psrs975.samples.SampleManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Manages Right 1, Right 2, Left, and Accompaniment voice assignments,
 * real-time voice editing, favorites, and user/expansion voices.
 */
class VoiceEngine(
  private val audioEngine: WorkstationAudioEngine,
  private val sampleManager: SampleManager,
  private val mixerEngine: MixerEngine,
) {
  private val _state = MutableStateFlow(
    VoiceEngineState(
      allVoices = VoiceCatalog.builtInVoices,
    ),
  )
  val state: StateFlow<VoiceEngineState> = _state.asStateFlow()

  init {
    val grand = VoiceCatalog.builtInVoices.first { it.id == "piano_grand" }
    val warmPad = VoiceCatalog.builtInVoices.first { it.id == "syn_pad" }
    val strings = VoiceCatalog.builtInVoices.first { it.id == "str_ensemble" }

    assignVoiceToSlot(ActiveVoiceSlot.RIGHT_1, grand)
    assignVoiceToSlot(ActiveVoiceSlot.RIGHT_2, warmPad)
    assignVoiceToSlot(ActiveVoiceSlot.LEFT, strings)

    // Preload default accompaniment instruments for style & MIDI engine channels
    loadPartInstrument(WorkstationPart.RHYTHM_1, "drum_std")
    loadPartInstrument(WorkstationPart.RHYTHM_2, "eth_kebero")
    loadPartInstrument(WorkstationPart.BASS, "syn_bass")
    loadPartInstrument(WorkstationPart.CHORD_1, "gtr_acoustic")
    loadPartInstrument(WorkstationPart.CHORD_2, "piano_electric")
    loadPartInstrument(WorkstationPart.PAD, "syn_pad")
    loadPartInstrument(WorkstationPart.PHRASE_1, "brass_section")
    loadPartInstrument(WorkstationPart.PHRASE_2, "str_ensemble")
    loadPartInstrument(WorkstationPart.MULTI_PAD, "gtr_nylon")
    loadPartInstrument(WorkstationPart.SONG_MIDI, "piano_grand")
  }

  fun loadPartInstrument(part: WorkstationPart, voiceId: String) {
    val patch = _state.value.allVoices.find { it.id == voiceId }
      ?: _state.value.allVoices.first()
    val instrument = resolveInstrument(patch)
    audioEngine.setPartInstrument(part, instrument)
  }

  fun assignVoiceToActiveSlot(patch: VoicePatch) {
    assignVoiceToSlot(_state.value.activePartSelection, patch)
  }

  fun assignVoiceToSlot(slot: ActiveVoiceSlot, patch: VoicePatch) {
    val instrument = resolveInstrument(patch)
    val part = slotToPart(slot)
    audioEngine.setPartInstrument(part, instrument)

    val editParams = VoiceSlotEditParams(
      attackScale = patch.defaultAttackScale,
      releaseScale = patch.defaultReleaseScale,
      brightnessScale = patch.defaultBrightness,
      octaveOffset = patch.defaultOctaveOffset,
    )
    mixerEngine.setPartVoiceModifiers(part, editParams.attackScale, editParams.releaseScale, editParams.brightnessScale)

    _state.update { st ->
      when (slot) {
        ActiveVoiceSlot.RIGHT_1 -> st.copy(right1Voice = patch, right1Edit = editParams)
        ActiveVoiceSlot.RIGHT_2 -> st.copy(right2Voice = patch, right2Edit = editParams)
        ActiveVoiceSlot.LEFT -> st.copy(leftVoice = patch, leftEdit = editParams)
      }
    }
  }

  fun setActiveSlot(slot: ActiveVoiceSlot) {
    _state.update { it.copy(activePartSelection = slot) }
  }

  fun togglePartEnabled(slot: ActiveVoiceSlot) {
    _state.update { st ->
      when (slot) {
        ActiveVoiceSlot.RIGHT_1 -> st.copy(right1Enabled = !st.right1Enabled)
        ActiveVoiceSlot.RIGHT_2 -> st.copy(right2Enabled = !st.right2Enabled)
        ActiveVoiceSlot.LEFT -> st.copy(leftEnabled = !st.leftEnabled)
      }
    }
  }

  fun setPartEnabled(slot: ActiveVoiceSlot, enabled: Boolean) {
    _state.update { st ->
      when (slot) {
        ActiveVoiceSlot.RIGHT_1 -> st.copy(right1Enabled = enabled)
        ActiveVoiceSlot.RIGHT_2 -> st.copy(right2Enabled = enabled)
        ActiveVoiceSlot.LEFT -> st.copy(leftEnabled = enabled)
      }
    }
  }

  fun updateSlotEditParams(slot: ActiveVoiceSlot, params: VoiceSlotEditParams) {
    val part = slotToPart(slot)
    mixerEngine.setPartVoiceModifiers(part, params.attackScale, params.releaseScale, params.brightnessScale)
    _state.update { st ->
      when (slot) {
        ActiveVoiceSlot.RIGHT_1 -> st.copy(right1Edit = params)
        ActiveVoiceSlot.RIGHT_2 -> st.copy(right2Edit = params)
        ActiveVoiceSlot.LEFT -> st.copy(leftEdit = params)
      }
    }
  }

  fun toggleFavorite(voiceId: String) {
    _state.update { st ->
      val next = if (voiceId in st.favoriteIds) st.favoriteIds - voiceId else st.favoriteIds + voiceId
      st.copy(favoriteIds = next)
    }
  }

  fun setFavorites(ids: Set<String>) {
    _state.update { it.copy(favoriteIds = ids) }
  }

  fun toggleHarmony() {
    setHarmonyEnabled(!_state.value.harmonyEnabled)
  }

  fun setHarmonyEnabled(enabled: Boolean) {
    _state.update { it.copy(harmonyEnabled = enabled) }
  }

  fun toggleSustain() {
    setSustainEnabled(!_state.value.sustainEnabled)
  }

  fun setSustainEnabled(enabled: Boolean) {
    _state.update { it.copy(sustainEnabled = enabled) }
    audioEngine.setSustainPedal(WorkstationPart.RIGHT_1, enabled)
    audioEngine.setSustainPedal(WorkstationPart.RIGHT_2, enabled)
    audioEngine.setSustainPedal(WorkstationPart.LEFT, enabled)
  }

  fun toggleDspForRight1() {
    setDspEnabled(!_state.value.dspEnabled)
  }

  fun setDspEnabled(enabled: Boolean) {
    _state.update { it.copy(dspEnabled = enabled) }
    mixerEngine.setDspSend(WorkstationPart.RIGHT_1, if (enabled) 0.75f else 0f)
  }

  fun clearSlot(slot: ActiveVoiceSlot) {
    val part = slotToPart(slot)
    audioEngine.allNotesOff(part)
    val defaultParams = VoiceSlotEditParams()
    mixerEngine.setPartVoiceModifiers(part, defaultParams.attackScale, defaultParams.releaseScale, defaultParams.brightnessScale)
    _state.update { st ->
      when (slot) {
        ActiveVoiceSlot.RIGHT_1 -> st.copy(right1Voice = null, right1Enabled = false, right1Edit = defaultParams)
        ActiveVoiceSlot.RIGHT_2 -> st.copy(right2Voice = null, right2Enabled = false, right2Edit = defaultParams)
        ActiveVoiceSlot.LEFT -> st.copy(leftVoice = null, leftEnabled = false, leftEdit = defaultParams)
      }
    }
  }

  fun clearAllKeyboardSlots() {
    clearSlot(ActiveVoiceSlot.RIGHT_1)
    clearSlot(ActiveVoiceSlot.RIGHT_2)
    clearSlot(ActiveVoiceSlot.LEFT)
    setHarmonyEnabled(false)
    setSustainEnabled(false)
    setDspEnabled(false)
  }

  fun registerImportedVoice(
    instrument: SampleInstrument,
    category: VoiceCategory = VoiceCategory.USER_EXPANSION,
    sourceFormat: String,
  ): VoicePatch {
    sampleManager.registerImportedInstrument(instrument)
    val patch = VoicePatch(
      id = instrument.id,
      name = instrument.name,
      category = category,
      synthesisModel = AcousticSynthesisModel.GRAND_PIANO,
      badge = sourceFormat,
      isUserVoice = true,
      sampleInstrumentId = instrument.id,
      sourceFormat = sourceFormat,
    )
    _state.update { st ->
      val updated = listOf(patch) + st.allVoices.filterNot { it.id == patch.id }
      st.copy(allVoices = updated)
    }
    return patch
  }

  fun saveAsUserVoice(basePatch: VoicePatch, customName: String, params: VoiceSlotEditParams): VoicePatch {
    val newId = "user_${System.currentTimeMillis()}"
    val userPatch = basePatch.copy(
      id = newId,
      name = customName.ifBlank { "${basePatch.name} User" },
      badge = "USER",
      defaultAttackScale = params.attackScale,
      defaultReleaseScale = params.releaseScale,
      defaultBrightness = params.brightnessScale,
      defaultOctaveOffset = params.octaveOffset,
      isUserVoice = true,
      sampleInstrumentId = basePatch.sampleInstrumentId,
      sourceFormat = "User Voice",
    )
    _state.update { st ->
      st.copy(allVoices = listOf(userPatch) + st.allVoices)
    }
    return userPatch
  }

  fun removeUserVoice(voiceId: String) {
    _state.update { st ->
      st.copy(allVoices = st.allVoices.filterNot { it.id == voiceId && it.isUserVoice })
    }
  }

  fun removeVoicesWithPrefix(prefix: String) {
    _state.update { st ->
      st.copy(allVoices = st.allVoices.filterNot { it.id.startsWith(prefix) && it.isUserVoice })
    }
  }

  fun findVoiceById(id: String): VoicePatch? = _state.value.allVoices.find { it.id == id }

  private fun resolveInstrument(patch: VoicePatch): SampleInstrument {
    sampleManager.getRegisteredInstrument(patch.sampleInstrumentId)?.let { return it }
    return sampleManager.getOrBuildInstrument(patch.sampleInstrumentId, patch.name, patch.synthesisModel)
  }

  private fun slotToPart(slot: ActiveVoiceSlot): WorkstationPart = when (slot) {
    ActiveVoiceSlot.RIGHT_1 -> WorkstationPart.RIGHT_1
    ActiveVoiceSlot.RIGHT_2 -> WorkstationPart.RIGHT_2
    ActiveVoiceSlot.LEFT -> WorkstationPart.LEFT
  }
}
