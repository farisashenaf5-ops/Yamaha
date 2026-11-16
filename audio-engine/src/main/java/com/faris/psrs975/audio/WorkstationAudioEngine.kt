package com.faris.psrs975.audio

import android.content.Context
import com.faris.psrs975.effects.EffectsRackState
import com.faris.psrs975.effects.WorkstationDspEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

fun interface AudioTapListener {
  fun onStereoBlock(interleavedPcm16: ShortArray, shortCount: Int, sampleRate: Int)
}

data class AudioEngineStats(
  val sampleRate: Int = 44100,
  val isRunning: Boolean = false,
  val activeVoices: Int = 0,
  val maxPolyphony: Int = 64,
  val peakLeft: Float = 0f,
  val peakRight: Float = 0f,
  val masterVolume: Float = 0.85f,
  val balance: Float = 0f,
)

/**
 * Real-time low-latency 64-voice polyphonic sample playback engine backed by Android AudioTrack.
 * Zero allocations inside the render loop. Supports 44.1kHz / 48kHz, interpolation, looping,
 * ADSR envelopes, pitch bend, modulation vibrato, sustain pedal, and real-time DSP effects.
 */
class WorkstationAudioEngine(
  private val context: Context? = null,
  initialSampleRate: Int = 44100,
) {
  companion object {
    const val MAX_POLYPHONY = 64
    private const val BLOCK_FRAMES = 256
  }

  private enum class EnvStage { IDLE, ATTACK, DECAY, SUSTAIN, RELEASE }

  private class VoiceSlot {
    var active = false
    var part: WorkstationPart = WorkstationPart.RIGHT_1
    var midiNote = 60
    var velocity = 100
    var sustainedByPedal = false
    var ageCounter = 0L
    var zone: SampleZone? = null

    var playPos = 0.0
    var phaseStep = 1.0
    var envLevel = 0f
    var envStage = EnvStage.IDLE
    var attackRate = 0.01f
    var decayRate = 0.001f
    var sustainTarget = 0.7f
    var releaseRate = 0.001f

    // 1-pole lowpass filter state for velocity & cutoff brightness
    var lpZ1L = 0f
    var lpZ1R = 0f
    var lpAlpha = 0.5f
    var noteGain = 1f
  }

  val dspEngine = WorkstationDspEngine(initialSampleRate)

  private val partInstruments = ConcurrentHashMap<WorkstationPart, SampleInstrument>()
  private val partMixes = Array(WorkstationPart.entries.size) { PartChannelMix() }
  private val partPitchBendSemis = FloatArray(WorkstationPart.entries.size)
  private val partModDepth = FloatArray(WorkstationPart.entries.size)
  private val partSustainPedal = BooleanArray(WorkstationPart.entries.size)

  private val voices = Array(MAX_POLYPHONY) { VoiceSlot() }
  private val voiceLock = Any()
  private var globalAge = 0L

  // Pre-allocated mix buses (zero allocations in audio loop)
  private val dryL = FloatArray(BLOCK_FRAMES)
  private val dryR = FloatArray(BLOCK_FRAMES)
  private val revSendL = FloatArray(BLOCK_FRAMES)
  private val revSendR = FloatArray(BLOCK_FRAMES)
  private val choSendL = FloatArray(BLOCK_FRAMES)
  private val choSendR = FloatArray(BLOCK_FRAMES)
  private val dspSendL = FloatArray(BLOCK_FRAMES)
  private val dspSendR = FloatArray(BLOCK_FRAMES)
  private val outputPcm = ShortArray(BLOCK_FRAMES * 2)

  private var lfoPhase = 0f

  @Volatile private var currentSampleRate = initialSampleRate
  @Volatile var masterVolume: Float = 0.85f
  @Volatile var masterBalance: Float = 0.0f // -1 (L) .. +1 (R)

  private val running = AtomicBoolean(false)
  val coreAudioEngine = AudioEngine(context = context, initialSampleRate = initialSampleRate)
  val streamInfo: StateFlow<AudioStreamInfo> = coreAudioEngine.streamInfo

  private val _stats = MutableStateFlow(AudioEngineStats(sampleRate = initialSampleRate))
  val stats: StateFlow<AudioEngineStats> = _stats.asStateFlow()

  @Volatile private var lastMeterUpdateMs = 0L

  init {
    coreAudioEngine.setRenderCallback { outPcm16, frames, _ ->
      val rendered = renderBlock(frames)
      val count = min(outPcm16.size, rendered.size)
      rendered.copyInto(outPcm16, destinationOffset = 0, startIndex = 0, endIndex = count)
      val nowMs = System.currentTimeMillis()
      if (nowMs - lastMeterUpdateMs >= 50L) {
        lastMeterUpdateMs = nowMs
        updateMeterStats()
      }
      true
    }
  }

  fun addTapListener(listener: AudioTapListener) {
    coreAudioEngine.addTapListener(listener)
  }

  fun removeTapListener(listener: AudioTapListener) {
    coreAudioEngine.removeTapListener(listener)
  }

  fun setEffectsState(state: EffectsRackState) {
    dspEngine.state = state
  }

  fun setPartInstrument(part: WorkstationPart, instrument: SampleInstrument) {
    partInstruments[part] = instrument
    coreAudioEngine.setPartInstrument(part, instrument)
  }

  fun setPartMix(part: WorkstationPart, mix: PartChannelMix) {
    partMixes[part.channelIndex] = mix
    coreAudioEngine.setPartMix(part, mix)
  }

  fun setSampleRate(sampleRate: Int) {
    if (sampleRate != 44100 && sampleRate != 48000) return
    if (sampleRate == currentSampleRate) return
    val wasRunning = running.get()
    if (wasRunning) stop()
    currentSampleRate = sampleRate
    dspEngine.setSampleRate(sampleRate)
    coreAudioEngine.setSampleRate(sampleRate)
    _stats.value = _stats.value.copy(sampleRate = sampleRate)
    if (wasRunning) start()
  }

  fun start() {
    if (running.getAndSet(true)) return
    dspEngine.setSampleRate(currentSampleRate)
    coreAudioEngine.masterVolume = masterVolume
    coreAudioEngine.masterBalance = masterBalance
    coreAudioEngine.start()
    _stats.value = _stats.value.copy(isRunning = true, sampleRate = currentSampleRate)
  }

  fun stop() {
    if (!running.getAndSet(false)) return
    coreAudioEngine.stop()
    _stats.value = _stats.value.copy(isRunning = false, activeVoices = 0, peakLeft = 0f, peakRight = 0f)
  }

  fun noteOn(part: WorkstationPart, midiNote: Int, velocity: Int) {
    val instrument = partInstruments[part] ?: return
    val clampedNote = midiNote.coerceIn(0, 127)
    val clampedVel = velocity.coerceIn(1, 127)
    val zone = instrument.findZone(clampedNote, clampedVel) ?: return

    val mix = partMixes[part.channelIndex]
    val sr = currentSampleRate.toFloat()

    val semitoneShift = if (instrument.isDrumKit) 0f else (clampedNote - zone.rootNote).toFloat()
    val pitchFactor = 2.0.pow((semitoneShift + zone.fineTuneCents / 100.0) / 12.0)
    val ratio = (zone.sampleRate.toDouble() / currentSampleRate.toDouble()) * pitchFactor

    val attackSamples = max(1f, zone.attackSec * mix.adsrAttackScale * sr)
    val decaySamples = max(16f, zone.decaySec * sr)
    val releaseSamples = max(24f, zone.releaseSec * mix.adsrReleaseScale * sr)

    val velNorm = clampedVel / 127f
    val cutoff = (zone.filterCutoffHz * mix.brightnessScale * (0.35f + 0.65f * velNorm)).coerceIn(220f, sr * 0.45f)
    val lpAlpha = (1f - exp((-2.0 * PI * cutoff / sr).toFloat())).coerceIn(0.03f, 0.99f)

    synchronized(voiceLock) {
      if (instrument.isDrumKit && (clampedNote == 42 || clampedNote == 44)) {
        for (existing in voices) {
          if (existing.active && existing.part == part && existing.midiNote == 46 && existing.envStage != EnvStage.RELEASE) {
            existing.releaseRate = 1f / max(24f, sr * 0.035f)
            existing.envStage = EnvStage.RELEASE
          }
        }
      }
      val slot = allocateVoiceSlot(part, clampedNote)
      slot.active = true
      slot.part = part
      slot.midiNote = clampedNote
      slot.velocity = clampedVel
      slot.sustainedByPedal = false
      slot.ageCounter = ++globalAge
      slot.zone = zone
      slot.playPos = 0.0
      slot.phaseStep = ratio
      slot.envLevel = 0f
      slot.envStage = EnvStage.ATTACK
      slot.attackRate = 1f / attackSamples
      slot.decayRate = (1f - zone.sustainLevel.coerceIn(0f, 1f)) / decaySamples
      slot.sustainTarget = zone.sustainLevel.coerceIn(0f, 1f)
      slot.releaseRate = 1f / releaseSamples
      slot.lpZ1L = 0f
      slot.lpZ1R = 0f
      slot.lpAlpha = lpAlpha
      slot.noteGain = (velNorm * velNorm * 0.85f + 0.15f) * zone.baseGain
    }
  }

  fun noteOff(part: WorkstationPart, midiNote: Int) {
    val isDrum = partInstruments[part]?.isDrumKit == true
    val pedalDown = partSustainPedal[part.channelIndex]
    synchronized(voiceLock) {
      for (slot in voices) {
        if (slot.active && slot.part == part && slot.midiNote == midiNote && slot.envStage != EnvStage.RELEASE) {
          if (isDrum && slot.zone?.loopEnabled == false) {
            continue
          }
          if (pedalDown) {
            slot.sustainedByPedal = true
          } else {
            slot.envStage = EnvStage.RELEASE
          }
        }
      }
    }
  }

  fun setSustainPedal(part: WorkstationPart, down: Boolean) {
    partSustainPedal[part.channelIndex] = down
    coreAudioEngine.setSustainPedal(part, down)
    if (!down) {
      synchronized(voiceLock) {
        for (slot in voices) {
          if (slot.active && slot.part == part && slot.sustainedByPedal) {
            slot.sustainedByPedal = false
            slot.envStage = EnvStage.RELEASE
          }
        }
      }
    }
  }

  fun setPitchBend(part: WorkstationPart, semitones: Float) {
    val clamped = semitones.coerceIn(-12f, 12f)
    partPitchBendSemis[part.channelIndex] = clamped
    coreAudioEngine.setPitchBend(part, clamped)
  }

  fun setModulation(part: WorkstationPart, depth: Float) {
    val clamped = depth.coerceIn(0f, 1f)
    partModDepth[part.channelIndex] = clamped
    coreAudioEngine.setModulation(part, clamped)
  }

  fun allNotesOff(part: WorkstationPart? = null) {
    synchronized(voiceLock) {
      for (slot in voices) {
        if (slot.active && (part == null || slot.part == part)) {
          slot.sustainedByPedal = false
          slot.envStage = EnvStage.RELEASE
        }
      }
    }
  }

  fun panic() {
    synchronized(voiceLock) {
      for (slot in voices) {
        slot.active = false
        slot.envStage = EnvStage.IDLE
      }
    }
    dspEngine.clearBuffers()
  }

  private fun allocateVoiceSlot(part: WorkstationPart, midiNote: Int): VoiceSlot {
    // Reuse same note on same part if already releasing to avoid buildup
    for (slot in voices) {
      if (!slot.active) return slot
    }
    var bestReleasing: VoiceSlot? = null
    var oldestActive = voices[0]
    for (slot in voices) {
      if (slot.part == part && slot.midiNote == midiNote && slot.envStage == EnvStage.RELEASE) {
        return slot
      }
      if (slot.envStage == EnvStage.RELEASE) {
        if (bestReleasing == null || slot.envLevel < bestReleasing.envLevel) {
          bestReleasing = slot
        }
      }
      if (slot.ageCounter < oldestActive.ageCounter) {
        oldestActive = slot
      }
    }
    return bestReleasing ?: oldestActive
  }

  /**
   * Renders [frames] stereo samples into [outputPcm]. Public for deterministic unit testing as well.
   */
  fun renderBlock(frames: Int = BLOCK_FRAMES): ShortArray {
    val n = frames.coerceAtMost(BLOCK_FRAMES)
    dryL.fill(0f, 0, n)
    dryR.fill(0f, 0, n)
    revSendL.fill(0f, 0, n)
    revSendR.fill(0f, 0, n)
    choSendL.fill(0f, 0, n)
    choSendR.fill(0f, 0, n)
    dspSendL.fill(0f, 0, n)
    dspSendR.fill(0f, 0, n)

    var anySolo = false
    for (m in partMixes) {
      if (m.soloed) {
        anySolo = true
        break
      }
    }

    val lfoInc = (2.0 * PI * 5.2 / currentSampleRate).toFloat()

    synchronized(voiceLock) {
      for (slot in voices) {
        if (!slot.active) continue
        val zone = slot.zone ?: run {
          slot.active = false
          continue
        }
        val mix = partMixes[slot.part.channelIndex]
        val partAudible = !mix.muted && (!anySolo || mix.soloed)
        val partVol = if (partAudible) mix.volume else 0f
        val pan = (mix.pan + zone.basePan).coerceIn(-1f, 1f)
        val gainL = partVol * slot.noteGain * (1f - max(0f, pan))
        val gainR = partVol * slot.noteGain * (1f + min(0f, pan))

        val bendSemis = partPitchBendSemis[slot.part.channelIndex]
        val modDepth = partModDepth[slot.part.channelIndex]
        val bendFactor = if (bendSemis != 0f) 2.0.pow(bendSemis / 12.0) else 1.0

        val samplesL = zone.samples
        val samplesR = zone.rightSamples ?: samplesL
        val len = samplesL.size
        if (len < 2) {
          slot.active = false
          continue
        }

        val loopOk = zone.loopEnabled && zone.loopEnd > zone.loopStart + 1 && zone.loopEnd < len
        val loopLen = if (loopOk) (zone.loopEnd - zone.loopStart).toDouble() else 0.0
        val alpha = slot.lpAlpha

        for (i in 0 until n) {
          // Envelope
          when (slot.envStage) {
            EnvStage.IDLE -> {
              slot.active = false
              break
            }
            EnvStage.ATTACK -> {
              slot.envLevel += slot.attackRate
              if (slot.envLevel >= 1f) {
                slot.envLevel = 1f
                slot.envStage = EnvStage.DECAY
              }
            }
            EnvStage.DECAY -> {
              slot.envLevel -= slot.decayRate
              if (slot.envLevel <= slot.sustainTarget) {
                slot.envLevel = slot.sustainTarget
                slot.envStage = EnvStage.SUSTAIN
              }
            }
            EnvStage.SUSTAIN -> {
              slot.envLevel = slot.sustainTarget
            }
            EnvStage.RELEASE -> {
              slot.envLevel -= slot.releaseRate
              if (slot.envLevel <= 0.0005f) {
                slot.envLevel = 0f
                slot.active = false
                break
              }
            }
          }

          val idx0 = slot.playPos.toInt()
          if (idx0 >= len - 1) {
            slot.active = false
            break
          }
          val t = (slot.playPos - idx0).toFloat()
          val idxM1 = if (idx0 > 0) idx0 - 1 else if (loopOk) zone.loopEnd - 1 else 0
          val idx1 = idx0 + 1
          val idx2 = when {
            idx0 + 2 < len -> idx0 + 2
            loopOk -> zone.loopStart
            else -> len - 1
          }
          val l0 = samplesL[idxM1]; val l1 = samplesL[idx0]; val l2 = samplesL[idx1]; val l3 = samplesL[idx2]
          val rawL = l1 + 0.5f * t * (
            (l2 - l0) + t * ((2f * l0 - 5f * l1 + 4f * l2 - l3) + t * (3f * (l1 - l2) + l3 - l0))
          )
          val rawR = if (samplesR === samplesL) {
            rawL
          } else {
            val r0 = samplesR[idxM1]; val r1 = samplesR[idx0]; val r2 = samplesR[idx1]; val r3 = samplesR[idx2]
            r1 + 0.5f * t * (
              (r2 - r0) + t * ((2f * r0 - 5f * r1 + 4f * r2 - r3) + t * (3f * (r1 - r2) + r3 - r0))
            )
          }

          // 1-pole velocity brightness lowpass
          slot.lpZ1L += alpha * (rawL - slot.lpZ1L)
          slot.lpZ1R += alpha * (rawR - slot.lpZ1R)

          val env = slot.envLevel
          val outL = slot.lpZ1L * env * gainL
          val outR = slot.lpZ1R * env * gainR

          dryL[i] += outL
          dryR[i] += outR

          if (mix.reverbSend > 0.001f) {
            revSendL[i] += outL * mix.reverbSend
            revSendR[i] += outR * mix.reverbSend
          }
          if (mix.chorusSend > 0.001f) {
            choSendL[i] += outL * mix.chorusSend
            choSendR[i] += outR * mix.chorusSend
          }
          if (mix.dspSend > 0.001f) {
            dspSendL[i] += outL * mix.dspSend
            dspSendR[i] += outR * mix.dspSend
          }

          val modVib = if (modDepth > 0.001f) 1.0 + sin(lfoPhase + i * lfoInc) * 0.015 * modDepth else 1.0
          slot.playPos += slot.phaseStep * bendFactor * modVib
          if (loopOk && slot.playPos >= zone.loopEnd) {
            slot.playPos -= loopLen
          } else if (!loopOk && slot.playPos >= len - 1) {
            slot.active = false
            break
          }
        }
      }
    }

    lfoPhase += lfoInc * n
    if (lfoPhase >= (2.0 * PI).toFloat()) lfoPhase -= (2.0 * PI).toFloat()

    // Apply master & insert DSP
    dspEngine.processMasterMix(dryL, dryR, revSendL, revSendR, choSendL, choSendR, dspSendL, dspSendR, n)

    val masterGain = masterVolume.coerceIn(0f, 1.5f)
    val bal = masterBalance.coerceIn(-1f, 1f)
    val balL = masterGain * (1f - max(0f, bal))
    val balR = masterGain * (1f + min(0f, bal))

    for (i in 0 until n) {
      val sL = (dryL[i] * balL * 32767f).toInt().coerceIn(-32768, 32767).toShort()
      val sR = (dryR[i] * balR * 32767f).toInt().coerceIn(-32768, 32767).toShort()
      outputPcm[i * 2] = sL
      outputPcm[i * 2 + 1] = sR
    }
    return outputPcm
  }

  private fun updateMeterStats() {
    var activeCount = 0
    for (slot in voices) if (slot.active) activeCount++
    var peakL = 0f
    var peakR = 0f
    for (i in 0 until BLOCK_FRAMES) {
      val l = abs(outputPcm[i * 2].toFloat()) / 32768f
      val r = abs(outputPcm[i * 2 + 1].toFloat()) / 32768f
      if (l > peakL) peakL = l
      if (r > peakR) peakR = r
    }
    _stats.value = AudioEngineStats(
      sampleRate = currentSampleRate,
      isRunning = running.get(),
      activeVoices = activeCount,
      maxPolyphony = MAX_POLYPHONY,
      peakLeft = peakL,
      peakRight = peakR,
      masterVolume = masterVolume,
      balance = masterBalance,
    )
  }
}
