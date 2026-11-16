package com.faris.psrs975.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

enum class AudioBackendType(val label: String) {
  OBOE_AAUDIO("Oboe (AAudio)"),
  OBOE_OPENSL_ES("Oboe (OpenSL ES)"),
  AUDIOTRACK_FALLBACK("AudioTrack Low-Latency"),
  UNINITIALIZED("Uninitialized"),
}

data class AudioStreamInfo(
  val isRunning: Boolean = false,
  val backend: AudioBackendType = AudioBackendType.UNINITIALIZED,
  val sampleRate: Int = 44100,
  val framesPerBurst: Int = 256,
  val bufferSizeFrames: Int = 512,
  val bufferCapacityFrames: Int = 1024,
  val xRunCount: Int = 0,
  val activeVoices: Int = 0,
  val exclusiveMode: Boolean = false,
  val lowLatencyMode: Boolean = false,
  val estimatedLatencyMs: Float = 0f,
  val peakLeft: Float = 0f,
  val peakRight: Float = 0f,
)

/**
 * Real-time callback interface invoked on the audio thread/producer thread to render stereo
 * interleaved 16-bit PCM frames for polyphonic synthesis and DSP processing.
 */
fun interface AudioRenderCallback {
  fun onRenderStereoBlock(outputPcm16: ShortArray, frames: Int, sampleRate: Int): Boolean
}

/**
 * Core low-latency AudioEngine powered by Google Oboe (AAudio / OpenSL ES) with automatic
 * AudioTrack fallback when native shared libraries are unavailable (e.g. JVM/Robolectric tests).
 *
 * Responsibilities:
 * 1. Low-latency audio stream initialization (sample rate negotiation, exclusive/shared mode,
 *    2x burst double-buffering, AudioFocus lifecycle, stream error recovery).
 * 2. Callback processing for 64-voice polyphonic playback (lock-free C++ Oboe callback rendering
 *    plus external DSP render callback ring-buffer bridge).
 * 3. Real-time thread management (Process.THREAD_PRIORITY_URGENT_AUDIO + Thread.MAX_PRIORITY
 *    worker loop with lock-free ring-buffer pacing and clean join/release shutdown).
 */
class AudioEngine(
  private val context: Context? = null,
  initialSampleRate: Int = 44100,
  private val burstMultiplier: Int = 2,
) {
  companion object {
    const val MAX_POLYPHONY = 64
    const val DEFAULT_BLOCK_FRAMES = 256
    private const val MAX_NATIVE_ZONES = 512
  }

  private enum class EnvStage { IDLE, ATTACK, DECAY, SUSTAIN, RELEASE }

  private class FallbackVoice {
    var active = false
    var part: WorkstationPart = WorkstationPart.RIGHT_1
    var midiNote = 60
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
    var lpZ1L = 0f
    var lpZ1R = 0f
    var lpAlpha = 0.5f
    var noteGain = 1f
  }

  private val nativeHandle: Long = if (OboeNativeBridge.isNativeLoaded) {
    OboeNativeBridge.nativeCreate()
  } else {
    0L
  }

  @Volatile private var currentSampleRate: Int = initialSampleRate.takeIf { it == 44100 || it == 48000 } ?: 44100
  @Volatile var masterVolume: Float = 0.85f
    set(value) {
      field = value.coerceIn(0f, 1.5f)
      if (nativeHandle != 0L) {
        OboeNativeBridge.nativeSetMasterGainAndBalance(nativeHandle, field, masterBalance)
      }
    }

  @Volatile var masterBalance: Float = 0.0f
    set(value) {
      field = value.coerceIn(-1f, 1f)
      if (nativeHandle != 0L) {
        OboeNativeBridge.nativeSetMasterGainAndBalance(nativeHandle, masterVolume, field)
      }
    }

  private val running = AtomicBoolean(false)
  private val released = AtomicBoolean(false)
  private val useOboeStream = AtomicBoolean(false)

  private var audioThread: Thread? = null
  private var fallbackAudioTrack: AudioTrack? = null

  @Volatile private var renderCallback: AudioRenderCallback? = null
  private val tapListeners = CopyOnWriteArrayList<AudioTapListener>()

  private val _streamInfo = MutableStateFlow(AudioStreamInfo(sampleRate = currentSampleRate))
  val streamInfo: StateFlow<AudioStreamInfo> = _streamInfo.asStateFlow()

  // Native zone registry mapping SampleZone identity -> native slot index [0..511]
  private val zoneToNativeSlot = ConcurrentHashMap<SampleZone, Int>()
  private val nextZoneSlot = AtomicInteger(0)

  // Software fallback polyphonic state (active when Oboe native lib is unavailable or callback-free)
  private val partInstruments = ConcurrentHashMap<WorkstationPart, SampleInstrument>()
  private val partMixes = Array(WorkstationPart.entries.size) { PartChannelMix() }
  private val partPitchBendSemis = FloatArray(WorkstationPart.entries.size)
  private val partModDepth = FloatArray(WorkstationPart.entries.size)
  private val partSustainPedal = BooleanArray(WorkstationPart.entries.size)
  private val voices = Array(MAX_POLYPHONY) { FallbackVoice() }
  private val voiceLock = Any()
  private var globalAge = 0L
  private var lfoPhase = 0f

  private val mixBusL = FloatArray(DEFAULT_BLOCK_FRAMES)
  private val mixBusR = FloatArray(DEFAULT_BLOCK_FRAMES)
  private val callbackPcmBuffer = ShortArray(DEFAULT_BLOCK_FRAMES * 2)
  private val statsIntsScratch = IntArray(10)
  private val statsFloatsScratch = FloatArray(2)

  private var audioManager: AudioManager? = null
  private var focusRequest: AudioFocusRequest? = null

  private val focusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
    when (focusChange) {
      AudioManager.AUDIOFOCUS_LOSS -> panic()
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> allNotesOff()
    }
  }

  val isUsingOboe: Boolean
    get() = useOboeStream.get()

  fun setRenderCallback(callback: AudioRenderCallback?) {
    renderCallback = callback
  }

  fun addTapListener(listener: AudioTapListener) {
    tapListeners.addIfAbsent(listener)
  }

  fun removeTapListener(listener: AudioTapListener) {
    tapListeners.remove(listener)
  }

  /**
   * Queries the device's optimal native output sample rate via AudioManager when available.
   */
  fun queryOptimalSampleRate(): Int {
    val ctx = context ?: return currentSampleRate
    return try {
      val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
      val rateStr = am?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
      val rate = rateStr?.toIntOrNull()
      if (rate == 44100 || rate == 48000) rate else currentSampleRate
    } catch (_: Throwable) {
      currentSampleRate
    }
  }

  /**
   * Registers an instrument and uploads its sample zones to the native Oboe audio thread table
   * so polyphonic callbacks can render directly without JVM heap allocations.
   */
  fun setPartInstrument(part: WorkstationPart, instrument: SampleInstrument) {
    partInstruments[part] = instrument
    if (nativeHandle != 0L) {
      for (zone in instrument.zones) {
        ensureNativeZoneRegistered(zone, instrument.isDrumKit)
      }
    }
  }

  private fun ensureNativeZoneRegistered(zone: SampleZone, isDrum: Boolean): Int {
    zoneToNativeSlot[zone]?.let { return it }
    val slot = (nextZoneSlot.getAndIncrement() % MAX_NATIVE_ZONES)
    val registered = OboeNativeBridge.nativeRegisterSampleZone(
      handle = nativeHandle,
      slotIndex = slot,
      leftSamples = zone.samples,
      rightSamples = zone.rightSamples,
      sampleRate = zone.sampleRate,
      rootNote = zone.rootNote,
      loopEnabled = zone.loopEnabled,
      loopStart = zone.loopStart,
      loopEnd = zone.loopEnd,
      fineTuneCents = zone.fineTuneCents,
      attackSec = zone.attackSec,
      decaySec = zone.decaySec,
      sustainLevel = zone.sustainLevel,
      releaseSec = zone.releaseSec,
      filterCutoffHz = zone.filterCutoffHz,
      baseGain = zone.baseGain,
      basePan = zone.basePan,
      isDrum = isDrum,
    )
    if (registered >= 0) {
      zoneToNativeSlot[zone] = registered
    }
    return registered
  }

  fun setPartMix(part: WorkstationPart, mix: PartChannelMix) {
    partMixes[part.channelIndex] = mix
    if (nativeHandle != 0L) {
      OboeNativeBridge.nativeSetPartMix(
        handle = nativeHandle,
        part = part.channelIndex,
        volume = mix.volume,
        pan = mix.pan,
        muted = mix.muted,
        soloed = mix.soloed,
      )
    }
  }

  fun setSampleRate(sampleRate: Int) {
    if (sampleRate != 44100 && sampleRate != 48000) return
    if (sampleRate == currentSampleRate) return
    val wasRunning = running.get()
    if (wasRunning) stop()
    currentSampleRate = sampleRate
    if (nativeHandle != 0L) {
      OboeNativeBridge.nativeSetSampleRate(nativeHandle, sampleRate)
    }
    _streamInfo.value = _streamInfo.value.copy(sampleRate = sampleRate)
    if (wasRunning) start()
  }

  /**
   * Initializes and starts the low-latency Oboe output stream (or AudioTrack fallback on JVM)
   * and launches the real-time audio worker thread.
   */
  fun start(): Boolean {
    if (released.get()) return false
    if (running.getAndSet(true)) return true

    requestAudioFocus()

    var oboeStarted = false
    if (nativeHandle != 0L) {
      OboeNativeBridge.nativeSetMasterGainAndBalance(nativeHandle, masterVolume, masterBalance)
      oboeStarted = OboeNativeBridge.nativeStart(nativeHandle, currentSampleRate, burstMultiplier)
    }
    useOboeStream.set(oboeStarted)

    if (!oboeStarted) {
      fallbackAudioTrack = buildFallbackAudioTrack(currentSampleRate)
      fallbackAudioTrack?.play()
    } else {
      refreshNativeStreamInfo()
    }

    startRealtimeThread(oboeStarted)
    return true
  }

  fun stop() {
    if (!running.getAndSet(false)) return
    val thread = audioThread
    audioThread = null
    if (thread != null) {
      LockSupport.unpark(thread)
      try {
        thread.join(500)
      } catch (_: InterruptedException) {
      }
    }

    if (useOboeStream.getAndSet(false) && nativeHandle != 0L) {
      OboeNativeBridge.nativeStop(nativeHandle)
    }

    try {
      fallbackAudioTrack?.pause()
      fallbackAudioTrack?.flush()
      fallbackAudioTrack?.release()
    } catch (_: Throwable) {
    }
    fallbackAudioTrack = null
    abandonAudioFocus()

    _streamInfo.value = _streamInfo.value.copy(
      isRunning = false,
      activeVoices = 0,
      peakLeft = 0f,
      peakRight = 0f,
    )
  }

  fun restart(): Boolean {
    stop()
    return start()
  }

  fun release() {
    if (released.getAndSet(true)) return
    stop()
    if (nativeHandle != 0L) {
      OboeNativeBridge.nativeDestroy(nativeHandle)
    }
  }

  fun noteOn(part: WorkstationPart, midiNote: Int, velocity: Int) {
    val instrument = partInstruments[part] ?: return
    val clampedNote = midiNote.coerceIn(0, 127)
    val clampedVel = velocity.coerceIn(1, 127)
    val zone = instrument.findZone(clampedNote, clampedVel) ?: return
    val mix = partMixes[part.channelIndex]

    if (useOboeStream.get() && nativeHandle != 0L && renderCallback == null) {
      val slotIndex = ensureNativeZoneRegistered(zone, instrument.isDrumKit)
      if (slotIndex >= 0) {
        OboeNativeBridge.nativeNoteOn(
          handle = nativeHandle,
          part = part.channelIndex,
          zoneIndex = slotIndex,
          midiNote = clampedNote,
          velocity = clampedVel,
          attackScale = mix.adsrAttackScale,
          releaseScale = mix.adsrReleaseScale,
          brightnessScale = mix.brightnessScale,
        )
        return
      }
    }

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
      val slot = allocateFallbackVoice(part, clampedNote)
      slot.active = true
      slot.part = part
      slot.midiNote = clampedNote
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
    if (useOboeStream.get() && nativeHandle != 0L && renderCallback == null) {
      OboeNativeBridge.nativeNoteOff(nativeHandle, part.channelIndex, midiNote.coerceIn(0, 127))
      return
    }
    val pedalDown = partSustainPedal[part.channelIndex]
    synchronized(voiceLock) {
      for (slot in voices) {
        if (slot.active && slot.part == part && slot.midiNote == midiNote && slot.envStage != EnvStage.RELEASE) {
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
    if (nativeHandle != 0L) {
      OboeNativeBridge.nativeSetSustainPedal(nativeHandle, part.channelIndex, down)
    }
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
    if (nativeHandle != 0L) {
      OboeNativeBridge.nativeSetPitchBend(nativeHandle, part.channelIndex, clamped)
    }
  }

  fun setModulation(part: WorkstationPart, depth: Float) {
    val clamped = depth.coerceIn(0f, 1f)
    partModDepth[part.channelIndex] = clamped
    if (nativeHandle != 0L) {
      OboeNativeBridge.nativeSetModulation(nativeHandle, part.channelIndex, clamped)
    }
  }

  fun allNotesOff(part: WorkstationPart? = null) {
    if (nativeHandle != 0L) {
      OboeNativeBridge.nativeAllNotesOff(nativeHandle, part?.channelIndex ?: -1)
    }
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
    if (nativeHandle != 0L) {
      OboeNativeBridge.nativePanic(nativeHandle)
    }
    synchronized(voiceLock) {
      for (slot in voices) {
        slot.active = false
        slot.envStage = EnvStage.IDLE
      }
    }
  }

  /**
   * Renders a stereo PCM-16 block via the internal fallback polyphonic voice pool.
   */
  fun renderPolyphonicBlock(frames: Int = DEFAULT_BLOCK_FRAMES): ShortArray {
    val n = frames.coerceAtMost(DEFAULT_BLOCK_FRAMES)
    mixBusL.fill(0f, 0, n)
    mixBusR.fill(0f, 0, n)

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
            EnvStage.SUSTAIN -> slot.envLevel = slot.sustainTarget
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
          val frac = (slot.playPos - idx0).toFloat()
          val rawL = samplesL[idx0] + (samplesL[idx0 + 1] - samplesL[idx0]) * frac
          val rawR = samplesR[idx0] + (samplesR[idx0 + 1] - samplesR[idx0]) * frac

          slot.lpZ1L += alpha * (rawL - slot.lpZ1L)
          slot.lpZ1R += alpha * (rawR - slot.lpZ1R)

          val env = slot.envLevel
          mixBusL[i] += slot.lpZ1L * env * gainL
          mixBusR[i] += slot.lpZ1R * env * gainR

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

    val masterGain = masterVolume.coerceIn(0f, 1.5f)
    val bal = masterBalance.coerceIn(-1f, 1f)
    val balL = masterGain * (1f - max(0f, bal))
    val balR = masterGain * (1f + min(0f, bal))
    for (i in 0 until n) {
      callbackPcmBuffer[i * 2] = (mixBusL[i] * balL * 32767f).toInt().coerceIn(-32768, 32767).toShort()
      callbackPcmBuffer[i * 2 + 1] = (mixBusR[i] * balR * 32767f).toInt().coerceIn(-32768, 32767).toShort()
    }
    return callbackPcmBuffer
  }

  private fun startRealtimeThread(oboeActive: Boolean) {
    val blockDurationNs = (DEFAULT_BLOCK_FRAMES * 1_000_000_000L) / currentSampleRate.coerceAtLeast(8000)

    audioThread = Thread({
      try {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
      } catch (_: Throwable) {
        // Ignored in JVM/Robolectric tests
      }

      var lastMeterMs = 0L
      while (running.get()) {
        val cb = renderCallback
        if (oboeActive && nativeHandle != 0L) {
          if (cb != null) {
            val buffered = OboeNativeBridge.nativeAvailablePcmRingFrames(nativeHandle)
            val targetWatermark = (_streamInfo.value.framesPerBurst.coerceAtLeast(DEFAULT_BLOCK_FRAMES) * 3)
              .coerceAtLeast(DEFAULT_BLOCK_FRAMES * 2)
            if (buffered < targetWatermark) {
              val rendered = cb.onRenderStereoBlock(callbackPcmBuffer, DEFAULT_BLOCK_FRAMES, currentSampleRate)
              if (rendered) {
                OboeNativeBridge.nativeWriteStereoPcm16(nativeHandle, callbackPcmBuffer, DEFAULT_BLOCK_FRAMES)
                notifyTapListeners(callbackPcmBuffer, DEFAULT_BLOCK_FRAMES * 2, currentSampleRate)
              } else {
                LockSupport.parkNanos(blockDurationNs / 2)
              }
            } else {
              LockSupport.parkNanos(blockDurationNs / 2)
            }
          } else {
            // Native Oboe onAudioReady callback directly renders all polyphonic voices
            LockSupport.parkNanos(16_000_000L)
          }
          val nowMs = System.currentTimeMillis()
          if (nowMs - lastMeterMs >= 50L) {
            lastMeterMs = nowMs
            refreshNativeStreamInfo()
          }
        } else {
          val produced = if (cb != null) {
            cb.onRenderStereoBlock(callbackPcmBuffer, DEFAULT_BLOCK_FRAMES, currentSampleRate)
          } else {
            renderPolyphonicBlock(DEFAULT_BLOCK_FRAMES)
            true
          }
          if (produced) {
            val written = try {
              fallbackAudioTrack?.write(
                callbackPcmBuffer,
                0,
                DEFAULT_BLOCK_FRAMES * 2,
                AudioTrack.WRITE_BLOCKING,
              ) ?: -1
            } catch (_: Throwable) {
              -1
            }
            if (written <= 0) {
              LockSupport.parkNanos(6_000_000L)
            }
            notifyTapListeners(callbackPcmBuffer, DEFAULT_BLOCK_FRAMES * 2, currentSampleRate)
          } else {
            LockSupport.parkNanos(6_000_000L)
          }
          val nowMs = System.currentTimeMillis()
          if (nowMs - lastMeterMs >= 50L) {
            lastMeterMs = nowMs
            refreshFallbackStreamInfo()
          }
        }
      }
    }, "AudioEngine-RealtimeAudio").apply {
      priority = Thread.MAX_PRIORITY
      isDaemon = true
      start()
    }
  }

  private fun notifyTapListeners(pcm16: ShortArray, shortCount: Int, sampleRate: Int) {
    for (listener in tapListeners) {
      listener.onStereoBlock(pcm16, shortCount, sampleRate)
    }
  }

  private fun refreshNativeStreamInfo() {
    if (nativeHandle == 0L) return
    OboeNativeBridge.nativeQueryStats(nativeHandle, statsIntsScratch, statsFloatsScratch)
    val sr = statsIntsScratch[0].takeIf { it > 0 } ?: currentSampleRate
    val burst = statsIntsScratch[1].takeIf { it > 0 } ?: DEFAULT_BLOCK_FRAMES
    val bufFrames = statsIntsScratch[2]
    val capFrames = statsIntsScratch[3]
    val xruns = statsIntsScratch[4]
    val active = statsIntsScratch[5]
    val backend = when (statsIntsScratch[6]) {
      1 -> AudioBackendType.OBOE_AAUDIO
      2 -> AudioBackendType.OBOE_OPENSL_ES
      else -> AudioBackendType.AUDIOTRACK_FALLBACK
    }
    val exclusive = statsIntsScratch[7] == 1
    val lowLatency = statsIntsScratch[8] == 2
    val isRun = statsIntsScratch[9] == 1
    val latencyMs = (bufFrames.toFloat() / sr.toFloat()) * 1000f

    _streamInfo.value = AudioStreamInfo(
      isRunning = isRun,
      backend = backend,
      sampleRate = sr,
      framesPerBurst = burst,
      bufferSizeFrames = bufFrames,
      bufferCapacityFrames = capFrames,
      xRunCount = xruns,
      activeVoices = active,
      exclusiveMode = exclusive,
      lowLatencyMode = lowLatency,
      estimatedLatencyMs = latencyMs,
      peakLeft = statsFloatsScratch[0],
      peakRight = statsFloatsScratch[1],
    )
  }

  private fun refreshFallbackStreamInfo() {
    var activeCount = 0
    for (slot in voices) if (slot.active) activeCount++
    var peakL = 0f
    var peakR = 0f
    for (i in 0 until DEFAULT_BLOCK_FRAMES) {
      val l = abs(callbackPcmBuffer[i * 2].toFloat()) / 32768f
      val r = abs(callbackPcmBuffer[i * 2 + 1].toFloat()) / 32768f
      if (l > peakL) peakL = l
      if (r > peakR) peakR = r
    }
    val latencyMs = (DEFAULT_BLOCK_FRAMES * 2f / currentSampleRate.toFloat()) * 1000f
    _streamInfo.value = AudioStreamInfo(
      isRunning = running.get(),
      backend = AudioBackendType.AUDIOTRACK_FALLBACK,
      sampleRate = currentSampleRate,
      framesPerBurst = DEFAULT_BLOCK_FRAMES,
      bufferSizeFrames = DEFAULT_BLOCK_FRAMES * 2,
      bufferCapacityFrames = DEFAULT_BLOCK_FRAMES * 4,
      xRunCount = 0,
      activeVoices = activeCount,
      exclusiveMode = false,
      lowLatencyMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O,
      estimatedLatencyMs = latencyMs,
      peakLeft = peakL,
      peakRight = peakR,
    )
  }

  private fun allocateFallbackVoice(part: WorkstationPart, midiNote: Int): FallbackVoice {
    for (slot in voices) {
      if (!slot.active) return slot
    }
    var bestReleasing: FallbackVoice? = null
    var oldest = voices[0]
    for (slot in voices) {
      if (slot.part == part && slot.midiNote == midiNote && slot.envStage == EnvStage.RELEASE) {
        return slot
      }
      if (slot.envStage == EnvStage.RELEASE) {
        if (bestReleasing == null || slot.envLevel < bestReleasing.envLevel) {
          bestReleasing = slot
        }
      }
      if (slot.ageCounter < oldest.ageCounter) {
        oldest = slot
      }
    }
    return bestReleasing ?: oldest
  }

  private fun buildFallbackAudioTrack(sampleRate: Int): AudioTrack? {
    return try {
      val minBuf = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_STEREO,
        AudioFormat.ENCODING_PCM_16BIT,
      ).coerceAtLeast(DEFAULT_BLOCK_FRAMES * 4)
      val builder = AudioTrack.Builder()
        .setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build(),
        )
        .setAudioFormat(
          AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build(),
        )
        .setBufferSizeInBytes(minBuf)
        .setTransferMode(AudioTrack.MODE_STREAM)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
      }
      builder.build()
    } catch (_: Throwable) {
      null
    }
  }

  private fun requestAudioFocus() {
    val ctx = context ?: return
    try {
      val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
      audioManager = am
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
              .build(),
          )
          .setOnAudioFocusChangeListener(focusListener)
          .build()
        focusRequest = req
        am.requestAudioFocus(req)
      }
    } catch (_: Throwable) {
    }
  }

  private fun abandonAudioFocus() {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
      }
    } catch (_: Throwable) {
    }
  }
}
