package com.faris.psrs975.mixer

import com.faris.psrs975.audio.PartChannelMix
import com.faris.psrs975.audio.WorkstationAudioEngine
import com.faris.psrs975.audio.WorkstationPart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MixerChannelState(
  val part: WorkstationPart,
  val volume: Float = 0.85f,
  val pan: Float = 0f,
  val reverbSend: Float = 0.28f,
  val chorusSend: Float = 0.14f,
  val dspSend: Float = 0f,
  val muted: Boolean = false,
  val soloed: Boolean = false,
) {
  fun toPartChannelMix(
    attackScale: Float = 1f,
    releaseScale: Float = 1f,
    brightnessScale: Float = 1f,
  ): PartChannelMix = PartChannelMix(
    volume = volume,
    pan = pan,
    reverbSend = reverbSend,
    chorusSend = chorusSend,
    dspSend = dspSend,
    muted = muted,
    soloed = soloed,
    adsrAttackScale = attackScale,
    adsrReleaseScale = releaseScale,
    brightnessScale = brightnessScale,
  )
}

data class MixerConsoleState(
  val channels: Map<WorkstationPart, MixerChannelState> = defaultChannels(),
) {
  companion object {
    val mixerParts = listOf(
      WorkstationPart.RIGHT_1,
      WorkstationPart.RIGHT_2,
      WorkstationPart.LEFT,
      WorkstationPart.RHYTHM_1,
      WorkstationPart.RHYTHM_2,
      WorkstationPart.BASS,
      WorkstationPart.CHORD_1,
      WorkstationPart.CHORD_2,
      WorkstationPart.PAD,
      WorkstationPart.PHRASE_1,
      WorkstationPart.PHRASE_2,
      WorkstationPart.MULTI_PAD,
    )

    fun defaultChannels(): Map<WorkstationPart, MixerChannelState> =
      WorkstationPart.entries.associateWith { part ->
        when (part) {
          WorkstationPart.RIGHT_1 -> MixerChannelState(part, volume = 0.90f, reverbSend = 0.30f, chorusSend = 0.12f)
          WorkstationPart.RIGHT_2 -> MixerChannelState(part, volume = 0.72f, pan = 0.12f, reverbSend = 0.34f, chorusSend = 0.22f)
          WorkstationPart.LEFT -> MixerChannelState(part, volume = 0.78f, pan = -0.15f, reverbSend = 0.22f)
          WorkstationPart.RHYTHM_1 -> MixerChannelState(part, volume = 0.86f, reverbSend = 0.16f, chorusSend = 0.04f)
          WorkstationPart.RHYTHM_2 -> MixerChannelState(part, volume = 0.76f, reverbSend = 0.20f, chorusSend = 0.08f)
          WorkstationPart.BASS -> MixerChannelState(part, volume = 0.88f, reverbSend = 0.06f, chorusSend = 0.02f)
          WorkstationPart.CHORD_1 -> MixerChannelState(part, volume = 0.70f, pan = -0.22f, reverbSend = 0.26f)
          WorkstationPart.CHORD_2 -> MixerChannelState(part, volume = 0.68f, pan = 0.24f, reverbSend = 0.28f, chorusSend = 0.18f)
          WorkstationPart.PAD -> MixerChannelState(part, volume = 0.64f, reverbSend = 0.42f, chorusSend = 0.30f)
          WorkstationPart.PHRASE_1 -> MixerChannelState(part, volume = 0.74f, pan = -0.18f, reverbSend = 0.30f)
          WorkstationPart.PHRASE_2 -> MixerChannelState(part, volume = 0.72f, pan = 0.20f, reverbSend = 0.32f)
          WorkstationPart.MULTI_PAD -> MixerChannelState(part, volume = 0.80f, reverbSend = 0.26f)
          WorkstationPart.SONG_MIDI -> MixerChannelState(part, volume = 0.82f, reverbSend = 0.24f)
        }
      }
  }
}

class MixerEngine(private val audioEngine: WorkstationAudioEngine) {
  private val _state = MutableStateFlow(MixerConsoleState())
  val state: StateFlow<MixerConsoleState> = _state.asStateFlow()

  private val partEditModifiers = mutableMapOf<WorkstationPart, Triple<Float, Float, Float>>()

  init {
    syncAllToAudio()
  }

  fun setVolume(part: WorkstationPart, volume: Float) {
    updateChannel(part) { it.copy(volume = volume.coerceIn(0f, 1f)) }
  }

  fun setPan(part: WorkstationPart, pan: Float) {
    updateChannel(part) { it.copy(pan = pan.coerceIn(-1f, 1f)) }
  }

  fun setReverbSend(part: WorkstationPart, level: Float) {
    updateChannel(part) { it.copy(reverbSend = level.coerceIn(0f, 1f)) }
  }

  fun setChorusSend(part: WorkstationPart, level: Float) {
    updateChannel(part) { it.copy(chorusSend = level.coerceIn(0f, 1f)) }
  }

  fun setDspSend(part: WorkstationPart, level: Float) {
    updateChannel(part) { it.copy(dspSend = level.coerceIn(0f, 1f)) }
  }

  fun toggleMute(part: WorkstationPart) {
    updateChannel(part) { it.copy(muted = !it.muted) }
  }

  fun setMuted(part: WorkstationPart, muted: Boolean) {
    updateChannel(part) { it.copy(muted = muted) }
  }

  fun toggleSolo(part: WorkstationPart) {
    updateChannel(part) { it.copy(soloed = !it.soloed) }
  }

  fun setPartVoiceModifiers(part: WorkstationPart, attackScale: Float, releaseScale: Float, brightnessScale: Float) {
    partEditModifiers[part] = Triple(attackScale, releaseScale, brightnessScale)
    val ch = _state.value.channels[part] ?: return
    audioEngine.setPartMix(part, ch.toPartChannelMix(attackScale, releaseScale, brightnessScale))
  }

  fun applySnapshot(channels: Map<WorkstationPart, MixerChannelState>) {
    _state.update { current ->
      val merged = current.channels.toMutableMap()
      channels.forEach { (k, v) -> merged[k] = v }
      current.copy(channels = merged)
    }
    syncAllToAudio()
  }

  fun resetAll() {
    _state.value = MixerConsoleState()
    syncAllToAudio()
  }

  private fun updateChannel(part: WorkstationPart, transform: (MixerChannelState) -> MixerChannelState) {
    _state.update { current ->
      val existing = current.channels[part] ?: MixerChannelState(part)
      val updated = transform(existing)
      val mods = partEditModifiers[part] ?: Triple(1f, 1f, 1f)
      audioEngine.setPartMix(part, updated.toPartChannelMix(mods.first, mods.second, mods.third))
      current.copy(channels = current.channels + (part to updated))
    }
  }

  private fun syncAllToAudio() {
    _state.value.channels.forEach { (part, ch) ->
      val mods = partEditModifiers[part] ?: Triple(1f, 1f, 1f)
      audioEngine.setPartMix(part, ch.toPartChannelMix(mods.first, mods.second, mods.third))
    }
  }
}
