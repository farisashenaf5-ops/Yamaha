package com.faris.psrs975.effects

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Allocation-free real-time stereo DSP engine covering Reverb, Chorus, Delay/Stereo Delay,
 * Phaser, Flanger, Distortion, Rotary Speaker, 3-Band EQ, and Dynamics Compressor.
 */
class WorkstationDspEngine(sampleRate: Int = 44100) {
  @Volatile var state: EffectsRackState = EffectsRackState()

  private var sr = sampleRate.toFloat()

  // Schroeder Reverb buffers (4 comb + 2 allpass per channel)
  private val combDelaysL = intArrayOf(1557, 1617, 1491, 1422)
  private val combDelaysR = intArrayOf(1580, 1640, 1514, 1445)
  private val allpassDelays = intArrayOf(225, 556)
  private var combBufL = Array(4) { FloatArray(2400) }
  private var combBufR = Array(4) { FloatArray(2400) }
  private var combIdxL = IntArray(4)
  private var combIdxR = IntArray(4)
  private var combFilterL = FloatArray(4)
  private var combFilterR = FloatArray(4)
  private var apBufL = Array(2) { FloatArray(800) }
  private var apBufR = Array(2) { FloatArray(800) }
  private var apIdxL = IntArray(2)
  private var apIdxR = IntArray(2)

  // Chorus / Flanger delay buffers
  private val modBufferSize = 8192
  private val chorusBufL = FloatArray(modBufferSize)
  private val chorusBufR = FloatArray(modBufferSize)
  private var chorusWritePos = 0
  private var chorusPhase = 0f

  private val flangerBufL = FloatArray(2048)
  private val flangerBufR = FloatArray(2048)
  private var flangerWritePos = 0
  private var flangerPhase = 0f

  // Stereo Delay buffer (up to 2 seconds at 48kHz)
  private val delayBufSize = 96000
  private val delayBufL = FloatArray(delayBufSize)
  private val delayBufR = FloatArray(delayBufSize)
  private var delayWritePos = 0

  // Rotary Speaker buffer & phase
  private val rotaryBufL = FloatArray(2048)
  private val rotaryBufR = FloatArray(2048)
  private var rotaryWritePos = 0
  private var rotaryHornPhase = 0f
  private var rotaryDrumPhase = 0f

  // Phaser 4-stage allpass state
  private val phaserZm1L = FloatArray(4)
  private val phaserZm1R = FloatArray(4)
  private var phaserLastL = 0f
  private var phaserLastR = 0f
  private var phaserPhase = 0f

  // Biquad states for Insert EQ & Master EQ (low, mid, high per L/R)
  private val insertEqL = Array(3) { BiquadState() }
  private val insertEqR = Array(3) { BiquadState() }
  private val masterEqL = Array(3) { BiquadState() }
  private val masterEqR = Array(3) { BiquadState() }

  // Compressor envelope states
  private var insertCompEnv = 0f
  private var masterCompEnv = 0f

  fun setSampleRate(newRate: Int) {
    sr = max(8000, newRate).toFloat()
  }

  fun clearBuffers() {
    for (c in 0 until 4) {
      combBufL[c].fill(0f)
      combBufR[c].fill(0f)
      combFilterL[c] = 0f
      combFilterR[c] = 0f
    }
    for (a in 0 until 2) {
      apBufL[a].fill(0f)
      apBufR[a].fill(0f)
    }
    chorusBufL.fill(0f)
    chorusBufR.fill(0f)
    flangerBufL.fill(0f)
    flangerBufR.fill(0f)
    delayBufL.fill(0f)
    delayBufR.fill(0f)
    rotaryBufL.fill(0f)
    rotaryBufR.fill(0f)
    phaserZm1L.fill(0f)
    phaserZm1R.fill(0f)
    phaserLastL = 0f
    phaserLastR = 0f
    for (b in 0 until 3) {
      insertEqL[b].z1 = 0f
      insertEqL[b].z2 = 0f
      insertEqR[b].z1 = 0f
      insertEqR[b].z2 = 0f
      masterEqL[b].z1 = 0f
      masterEqL[b].z2 = 0f
      masterEqR[b].z1 = 0f
      masterEqR[b].z2 = 0f
    }
    insertCompEnv = 0f
    masterCompEnv = 0f
  }

  /**
   * Processes stereo audio in-place with zero heap allocations.
   * [dspWetBuffer], [chorusSendBuffer], [reverbSendBuffer] carry per-channel send levels.
   */
  fun processMasterMix(
    dryL: FloatArray,
    dryR: FloatArray,
    reverbSendL: FloatArray,
    reverbSendR: FloatArray,
    chorusSendL: FloatArray,
    chorusSendR: FloatArray,
    dspSendL: FloatArray,
    dspSendR: FloatArray,
    frameCount: Int,
  ) {
    val rack = state

    // 1. Insert DSP + Insert EQ + Insert Comp on dspSend bus, summed back into dry
    if (rack.insertDsp.enabled && rack.insertDsp.type != DspEffectType.NONE) {
      processInsertDsp(dspSendL, dspSendR, frameCount, rack.insertDsp)
    }
    if (rack.insertEq.enabled) {
      applyThreeBandEq(dspSendL, dspSendR, frameCount, rack.insertEq, insertEqL, insertEqR)
    }
    if (rack.insertCompressor.enabled) {
      insertCompEnv = applyCompressor(dspSendL, dspSendR, frameCount, rack.insertCompressor, insertCompEnv)
    }
    for (i in 0 until frameCount) {
      dryL[i] += dspSendL[i]
      dryR[i] += dspSendR[i]
    }

    // 2. Chorus Bus
    if (rack.chorus.enabled && rack.chorus.level > 0.001f) {
      processChorus(chorusSendL, chorusSendR, frameCount, rack.chorus)
      val cLvl = rack.chorus.level
      for (i in 0 until frameCount) {
        dryL[i] += chorusSendL[i] * cLvl
        dryR[i] += chorusSendR[i] * cLvl
      }
    }

    // 3. Stereo Delay on dry bus
    if (rack.delay.enabled && rack.delay.level > 0.001f) {
      processStereoDelay(dryL, dryR, frameCount, rack.delay)
    }

    // 4. Reverb Bus
    if (rack.reverb.enabled && rack.reverb.level > 0.001f) {
      processReverb(reverbSendL, reverbSendR, frameCount, rack.reverb)
      val rLvl = rack.reverb.level
      for (i in 0 until frameCount) {
        dryL[i] += reverbSendL[i] * rLvl
        dryR[i] += reverbSendR[i] * rLvl
      }
    }

    // 5. Master EQ
    if (rack.masterEq.enabled) {
      applyThreeBandEq(dryL, dryR, frameCount, rack.masterEq, masterEqL, masterEqR)
    }

    // 6. Master Compressor + Soft Limiter
    if (rack.masterCompressor.enabled) {
      masterCompEnv = applyCompressor(dryL, dryR, frameCount, rack.masterCompressor, masterCompEnv)
    }
    for (i in 0 until frameCount) {
      dryL[i] = softLimit(dryL[i])
      dryR[i] = softLimit(dryR[i])
    }
  }

  private fun processReverb(bufL: FloatArray, bufR: FloatArray, frames: Int, cfg: ReverbSettings) {
    val feedback = 0.70f + cfg.roomSize.coerceIn(0f, 1f) * 0.25f
    val damp1 = cfg.damping.coerceIn(0f, 0.9f) * 0.45f
    val damp2 = 1f - damp1

    for (i in 0 until frames) {
      val inputL = bufL[i] * 0.22f
      val inputR = bufR[i] * 0.22f
      var outL = 0f
      var outR = 0f

      for (c in 0 until 4) {
        val lenL = combDelaysL[c]
        val idxL = combIdxL[c]
        val delayedL = combBufL[c][idxL]
        combFilterL[c] = delayedL * damp2 + combFilterL[c] * damp1
        combBufL[c][idxL] = inputL + combFilterL[c] * feedback
        combIdxL[c] = if (idxL + 1 >= lenL) 0 else idxL + 1
        outL += delayedL

        val lenR = combDelaysR[c]
        val idxR = combIdxR[c]
        val delayedR = combBufR[c][idxR]
        combFilterR[c] = delayedR * damp2 + combFilterR[c] * damp1
        combBufR[c][idxR] = inputR + combFilterR[c] * feedback
        combIdxR[c] = if (idxR + 1 >= lenR) 0 else idxR + 1
        outR += delayedR
      }

      for (a in 0 until 2) {
        val len = allpassDelays[a]
        val iL = apIdxL[a]
        val bufValL = apBufL[a][iL]
        val nextOutL = -outL + bufValL
        apBufL[a][iL] = outL + bufValL * 0.5f
        apIdxL[a] = if (iL + 1 >= len) 0 else iL + 1
        outL = nextOutL

        val iR = apIdxR[a]
        val bufValR = apBufR[a][iR]
        val nextOutR = -outR + bufValR
        apBufR[a][iR] = outR + bufValR * 0.5f
        apIdxR[a] = if (iR + 1 >= len) 0 else iR + 1
        outR = nextOutR
      }

      bufL[i] = outL * 0.55f
      bufR[i] = outR * 0.55f
    }
  }

  private fun processChorus(bufL: FloatArray, bufR: FloatArray, frames: Int, cfg: ChorusSettings) {
    val phaseInc = (2.0 * PI * cfg.rateHz.coerceIn(0.1f, 8f) / sr).toFloat()
    val baseDelay = sr * 0.015f
    val modDepth = sr * 0.006f * cfg.depth.coerceIn(0f, 1f)
    val mask = modBufferSize - 1

    for (i in 0 until frames) {
      val inL = bufL[i]
      val inR = bufR[i]
      chorusBufL[chorusWritePos] = inL
      chorusBufR[chorusWritePos] = inR

      val lfoL = sin(chorusPhase)
      val lfoR = cos(chorusPhase)
      val delayL = baseDelay + lfoL * modDepth
      val delayR = baseDelay + lfoR * modDepth

      val wetL = readInterpolated(chorusBufL, chorusWritePos, delayL, mask)
      val wetR = readInterpolated(chorusBufR, chorusWritePos, delayR, mask)

      bufL[i] = wetL
      bufR[i] = wetR

      chorusWritePos = (chorusWritePos + 1) and mask
      chorusPhase += phaseInc
      if (chorusPhase >= (2.0 * PI).toFloat()) chorusPhase -= (2.0 * PI).toFloat()
    }
  }

  private fun processStereoDelay(bufL: FloatArray, bufR: FloatArray, frames: Int, cfg: DelaySettings) {
    val dSamplesL = ((cfg.timeMs.coerceIn(20f, 1800f) / 1000f) * sr).toInt().coerceIn(1, delayBufSize - 2)
    val dSamplesR = if (cfg.stereoPingPong) {
      ((cfg.rightTimeMs.coerceIn(20f, 1800f) / 1000f) * sr).toInt().coerceIn(1, delayBufSize - 2)
    } else {
      dSamplesL
    }
    val fb = cfg.feedback.coerceIn(0f, 0.85f)
    val mix = cfg.level.coerceIn(0f, 1f)

    for (i in 0 until frames) {
      val readL = (delayWritePos - dSamplesL + delayBufSize) % delayBufSize
      val readR = (delayWritePos - dSamplesR + delayBufSize) % delayBufSize
      val tapL = delayBufL[readL]
      val tapR = delayBufR[readR]

      if (cfg.stereoPingPong) {
        delayBufL[delayWritePos] = bufL[i] + tapR * fb
        delayBufR[delayWritePos] = bufR[i] + tapL * fb
      } else {
        delayBufL[delayWritePos] = bufL[i] + tapL * fb
        delayBufR[delayWritePos] = bufR[i] + tapR * fb
      }

      bufL[i] += tapL * mix
      bufR[i] += tapR * mix
      delayWritePos = (delayWritePos + 1) % delayBufSize
    }
  }

  private fun processInsertDsp(bufL: FloatArray, bufR: FloatArray, frames: Int, cfg: DspInsertSettings) {
    val wetMix = cfg.mix.coerceIn(0f, 1f)
    val dryMix = 1f - wetMix * 0.5f

    when (cfg.type) {
      DspEffectType.NONE -> return
      DspEffectType.DISTORTION -> {
        val gain = 1f + cfg.drive.coerceIn(0f, 1f) * 14f
        for (i in 0 until frames) {
          val dL = tanh(bufL[i] * gain)
          val dR = tanh(bufR[i] * gain)
          bufL[i] = bufL[i] * dryMix + dL * wetMix * 0.65f
          bufR[i] = bufR[i] * dryMix + dR * wetMix * 0.65f
        }
      }
      DspEffectType.PHASER -> {
        val inc = (2.0 * PI * cfg.rateHz.coerceIn(0.1f, 6f) / sr).toFloat()
        val fb = cfg.feedback.coerceIn(0f, 0.8f)
        for (i in 0 until frames) {
          val lfo = 0.5f + 0.5f * sin(phaserPhase)
          val freq = 350f + lfo * cfg.depth * 2400f
          val coeff = (1f - tanApprox(PI.toFloat() * freq / sr)) / (1f + tanApprox(PI.toFloat() * freq / sr))
          var xL = bufL[i] + phaserLastL * fb
          var xR = bufR[i] + phaserLastR * fb
          for (s in 0 until 4) {
            val yL = coeff * xL + phaserZm1L[s]
            phaserZm1L[s] = xL - coeff * yL
            xL = yL
            val yR = coeff * xR + phaserZm1R[s]
            phaserZm1R[s] = xR - coeff * yR
            xR = yR
          }
          phaserLastL = xL
          phaserLastR = xR
          bufL[i] = bufL[i] * dryMix + xL * wetMix
          bufR[i] = bufR[i] * dryMix + xR * wetMix
          phaserPhase += inc
          if (phaserPhase >= (2.0 * PI).toFloat()) phaserPhase -= (2.0 * PI).toFloat()
        }
      }
      DspEffectType.FLANGER -> {
        val inc = (2.0 * PI * cfg.rateHz.coerceIn(0.05f, 5f) / sr).toFloat()
        val mask = 2047
        val fb = cfg.feedback.coerceIn(0f, 0.78f)
        val maxDelay = sr * 0.005f * cfg.depth.coerceIn(0.1f, 1f)
        for (i in 0 until frames) {
          val lfo = 0.5f + 0.5f * sin(flangerPhase)
          val delay = 20f + lfo * maxDelay
          val tapL = readInterpolated(flangerBufL, flangerWritePos, delay, mask)
          val tapR = readInterpolated(flangerBufR, flangerWritePos, delay, mask)
          flangerBufL[flangerWritePos] = bufL[i] + tapL * fb
          flangerBufR[flangerWritePos] = bufR[i] + tapR * fb
          bufL[i] = bufL[i] * dryMix + tapL * wetMix
          bufR[i] = bufR[i] * dryMix + tapR * wetMix
          flangerWritePos = (flangerWritePos + 1) and mask
          flangerPhase += inc
          if (flangerPhase >= (2.0 * PI).toFloat()) flangerPhase -= (2.0 * PI).toFloat()
        }
      }
      DspEffectType.ROTARY_SPEAKER -> {
        val hornInc = (2.0 * PI * cfg.rateHz.coerceIn(0.5f, 8.5f) / sr).toFloat()
        val drumInc = hornInc * 0.72f
        val mask = 2047
        for (i in 0 until frames) {
          rotaryBufL[rotaryWritePos] = bufL[i]
          rotaryBufR[rotaryWritePos] = bufR[i]
          val dL = 12f + (0.5f + 0.5f * sin(rotaryHornPhase)) * 28f * cfg.depth
          val dR = 12f + (0.5f + 0.5f * cos(rotaryHornPhase)) * 28f * cfg.depth
          val ampL = 0.65f + 0.35f * sin(rotaryDrumPhase)
          val ampR = 0.65f + 0.35f * cos(rotaryDrumPhase)
          val tapL = readInterpolated(rotaryBufL, rotaryWritePos, dL, mask) * ampL
          val tapR = readInterpolated(rotaryBufR, rotaryWritePos, dR, mask) * ampR
          bufL[i] = bufL[i] * (1f - wetMix) + tapL * wetMix
          bufR[i] = bufR[i] * (1f - wetMix) + tapR * wetMix
          rotaryWritePos = (rotaryWritePos + 1) and mask
          rotaryHornPhase += hornInc
          rotaryDrumPhase += drumInc
          if (rotaryHornPhase >= (2.0 * PI).toFloat()) rotaryHornPhase -= (2.0 * PI).toFloat()
          if (rotaryDrumPhase >= (2.0 * PI).toFloat()) rotaryDrumPhase -= (2.0 * PI).toFloat()
        }
      }
      DspEffectType.STEREO_DELAY -> {
        processStereoDelay(
          bufL,
          bufR,
          frames,
          DelaySettings(
            enabled = true,
            timeMs = 240f,
            rightTimeMs = 360f,
            feedback = cfg.feedback,
            stereoPingPong = true,
            level = wetMix,
          ),
        )
      }
      DspEffectType.COMPRESSOR -> {
        insertCompEnv = applyCompressor(
          bufL,
          bufR,
          frames,
          CompressorSettings(enabled = true, thresholdDb = -18f, ratio = 4f, makeupGainDb = 3f),
          insertCompEnv,
        )
      }
      DspEffectType.EQ_PRESENCE -> {
        applyThreeBandEq(
          bufL,
          bufR,
          frames,
          EqSettings(enabled = true, lowGainDb = 2f, midGainDb = 3.5f, midFreqHz = 2200f, highGainDb = 4f),
          insertEqL,
          insertEqR,
        )
      }
    }
  }

  private fun applyThreeBandEq(
    bufL: FloatArray,
    bufR: FloatArray,
    frames: Int,
    cfg: EqSettings,
    stateL: Array<BiquadState>,
    stateR: Array<BiquadState>,
  ) {
    val low = computeLowShelf(110f, cfg.lowGainDb)
    val mid = computePeaking(cfg.midFreqHz.coerceIn(250f, 6000f), cfg.midGainDb, 1.0f)
    val high = computeHighShelf(5200f, cfg.highGainDb)

    for (i in 0 until frames) {
      var l = processBiquad(bufL[i], low, stateL[0])
      l = processBiquad(l, mid, stateL[1])
      l = processBiquad(l, high, stateL[2])
      bufL[i] = l

      var r = processBiquad(bufR[i], low, stateR[0])
      r = processBiquad(r, mid, stateR[1])
      r = processBiquad(r, high, stateR[2])
      bufR[i] = r
    }
  }

  private fun applyCompressor(
    bufL: FloatArray,
    bufR: FloatArray,
    frames: Int,
    cfg: CompressorSettings,
    initialEnv: Float,
  ): Float {
    val threshLin = 10f.pow(cfg.thresholdDb / 20f)
    val makeupLin = 10f.pow(cfg.makeupGainDb / 20f)
    val ratio = max(1f, cfg.ratio)
    val attCoeff = exp(-1f / (max(0.5f, cfg.attackMs) * 0.001f * sr))
    val relCoeff = exp(-1f / (max(5f, cfg.releaseMs) * 0.001f * sr))
    var env = initialEnv

    for (i in 0 until frames) {
      val peak = max(abs(bufL[i]), abs(bufR[i]))
      env = if (peak > env) {
        attCoeff * env + (1f - attCoeff) * peak
      } else {
        relCoeff * env + (1f - relCoeff) * peak
      }
      val gain = if (env > threshLin && env > 1e-6f) {
        val envDb = 20f * (ln(env) / ln(10f))
        val overDb = envDb - cfg.thresholdDb
        val reducedDb = cfg.thresholdDb + overDb / ratio
        val targetLin = 10f.pow(reducedDb / 20f)
        (targetLin / env) * makeupLin
      } else {
        makeupLin
      }
      bufL[i] *= gain
      bufR[i] *= gain
    }
    return env
  }

  private fun readInterpolated(buf: FloatArray, writePos: Int, delaySamples: Float, mask: Int): Float {
    val pos = writePos - delaySamples
    val base = kotlin.math.floor(pos.toDouble()).toInt()
    val frac = pos - base
    val idx0 = base and mask
    val idx1 = (base + 1) and mask
    return buf[idx0] * (1f - frac) + buf[idx1] * frac
  }

  private fun softLimit(x: Float): Float = if (abs(x) <= 0.85f) x else 0.85f * tanh(x / 0.85f)

  private fun tanApprox(x: Float): Float {
    val clamped = x.coerceIn(-1.4f, 1.4f)
    return sin(clamped) / max(0.01f, cos(clamped))
  }

  private data class BiquadCoeffs(val b0: Float, val b1: Float, val b2: Float, val a1: Float, val a2: Float)
  private class BiquadState {
    var z1 = 0f
    var z2 = 0f
  }

  private fun processBiquad(x: Float, c: BiquadCoeffs, s: BiquadState): Float {
    val y = c.b0 * x + s.z1
    s.z1 = c.b1 * x - c.a1 * y + s.z2
    s.z2 = c.b2 * x - c.a2 * y
    return y
  }

  private fun computeLowShelf(freqHz: Float, gainDb: Float): BiquadCoeffs {
    val a = 10f.pow(gainDb / 40f)
    val w0 = (2.0 * PI * freqHz / sr).toFloat()
    val cosW = cos(w0)
    val sinW = sin(w0)
    val alpha = sinW / 1.4142135f
    val twoSqrtAAlpha = 2f * kotlin.math.sqrt(a) * alpha
    val a0 = (a + 1f) + (a - 1f) * cosW + twoSqrtAAlpha
    val b0 = a * ((a + 1f) - (a - 1f) * cosW + twoSqrtAAlpha) / a0
    val b1 = 2f * a * ((a - 1f) - (a + 1f) * cosW) / a0
    val b2 = a * ((a + 1f) - (a - 1f) * cosW - twoSqrtAAlpha) / a0
    val a1 = -2f * ((a - 1f) + (a + 1f) * cosW) / a0
    val a2 = ((a + 1f) + (a - 1f) * cosW - twoSqrtAAlpha) / a0
    return BiquadCoeffs(b0, b1, b2, a1, a2)
  }

  private fun computeHighShelf(freqHz: Float, gainDb: Float): BiquadCoeffs {
    val a = 10f.pow(gainDb / 40f)
    val w0 = (2.0 * PI * freqHz / sr).toFloat()
    val cosW = cos(w0)
    val sinW = sin(w0)
    val alpha = sinW / 1.4142135f
    val twoSqrtAAlpha = 2f * kotlin.math.sqrt(a) * alpha
    val a0 = (a + 1f) - (a - 1f) * cosW + twoSqrtAAlpha
    val b0 = a * ((a + 1f) + (a - 1f) * cosW + twoSqrtAAlpha) / a0
    val b1 = -2f * a * ((a - 1f) + (a + 1f) * cosW) / a0
    val b2 = a * ((a + 1f) + (a - 1f) * cosW - twoSqrtAAlpha) / a0
    val a1 = 2f * ((a - 1f) - (a + 1f) * cosW) / a0
    val a2 = ((a + 1f) - (a - 1f) * cosW - twoSqrtAAlpha) / a0
    return BiquadCoeffs(b0, b1, b2, a1, a2)
  }

  private fun computePeaking(freqHz: Float, gainDb: Float, q: Float): BiquadCoeffs {
    val a = 10f.pow(gainDb / 40f)
    val w0 = (2.0 * PI * freqHz / sr).toFloat()
    val cosW = cos(w0)
    val alpha = sin(w0) / (2f * q)
    val a0 = 1f + alpha / a
    return BiquadCoeffs(
      b0 = (1f + alpha * a) / a0,
      b1 = (-2f * cosW) / a0,
      b2 = (1f - alpha * a) / a0,
      a1 = (-2f * cosW) / a0,
      a2 = (1f - alpha / a) / a0,
    )
  }
}
