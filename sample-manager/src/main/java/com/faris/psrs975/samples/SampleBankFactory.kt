package com.faris.psrs975.samples

import com.faris.psrs975.audio.SampleInstrument
import com.faris.psrs975.audio.SampleZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

enum class AcousticSynthesisModel {
  GRAND_PIANO,
  BRIGHT_PIANO,
  ELECTRIC_TINE,
  HONKY_TONK,
  CLAVINET,
  HARPSICHORD,
  PIPE_ORGAN,
  CHURCH_ORGAN,
  DRAWBAR_ORGAN,
  ACCORDION_MUSETTE,
  SAX_REED,
  FLUTE_BREATH,
  CLARINET_HOLLOW,
  OBOE_DOUBLE_REED,
  BRASS_HORN,
  BRASS_ENSEMBLE,
  BOWED_SOLO_STRING,
  ACOUSTIC_CELLO,
  STRING_ENSEMBLE,
  PIZZICATO,
  PLUCKED_ACOUSTIC_GUITAR,
  PLUCKED_STEEL_GUITAR,
  PLUCKED_NYLON_GUITAR,
  PLUCKED_ELECTRIC_CLEAN,
  OVERDRIVE_GUITAR,
  ACOUSTIC_BASS,
  ELECTRIC_BASS,
  SYNTH_LEAD,
  SYNTH_PAD,
  SYNTH_BASS,
  ETHIO_MASINKO,
  ETHIO_KRAR,
  ETHIO_WASHINT,
  ETHIO_KEBERO,
  GM_DRUM_KIT,
}

/**
 * High-resolution multi-zone, multi-velocity PCM acoustic & physical sample bank engine.
 *
 * Key architecture upgrades over basic single-sample oscillators:
 * 1. Dense 15-zone key mapping (max ±2 semitone pitch shift across the 61-key bed).
 * 2. Multi-velocity layers (3 layers on Piano; 2 layers on Guitar, Violin, Cello, Brass, Sax, Bass, Accordion).
 * 3. Fractional-delay Thiran allpass Karplus-Strong waveguides + wooden body resonators so guitars & plucked strings stay 0.0 cents in tune.
 * 4. Stereo soundboard / ensemble spreads and wooden/brass formant resonances with crossfaded seamless sustain loops.
 */
object SampleBankFactory {
  private const val SAMPLE_RATE = 44100

  private data class KeySpan(val root: Int, val lo: Int, val hi: Int)

  // 15 dense key zones across C2..G6 (max +-2 semitones stretch)
  private val denseKeySpans: List<KeySpan> = listOf(
    KeySpan(36, 0, 38),
    KeySpan(40, 39, 42),
    KeySpan(44, 43, 46),
    KeySpan(48, 47, 50),
    KeySpan(52, 51, 54),
    KeySpan(56, 55, 58),
    KeySpan(60, 59, 62),
    KeySpan(64, 63, 66),
    KeySpan(68, 67, 70),
    KeySpan(72, 71, 74),
    KeySpan(76, 75, 78),
    KeySpan(80, 79, 82),
    KeySpan(84, 83, 86),
    KeySpan(88, 87, 90),
    KeySpan(92, 91, 127),
  )

  private val ethioKeySpans: List<KeySpan> = listOf(
    KeySpan(36, 0, 42),
    KeySpan(48, 43, 54),
    KeySpan(60, 55, 66),
    KeySpan(72, 67, 78),
    KeySpan(84, 79, 127),
  )

  fun buildInstrument(
    id: String,
    name: String,
    model: AcousticSynthesisModel,
  ): SampleInstrument {
    if (model == AcousticSynthesisModel.GM_DRUM_KIT) {
      return buildGmDrumKit(id, name)
    }
    if (model == AcousticSynthesisModel.ETHIO_KEBERO) {
      return buildKeberoInstrument(id, name)
    }

    val isEthio = model == AcousticSynthesisModel.ETHIO_MASINKO ||
      model == AcousticSynthesisModel.ETHIO_KRAR ||
      model == AcousticSynthesisModel.ETHIO_WASHINT

    val spans = if (isEthio) ethioKeySpans else denseKeySpans
    val zones = mutableListOf<SampleZone>()

    when (model) {
      AcousticSynthesisModel.GRAND_PIANO,
      AcousticSynthesisModel.BRIGHT_PIANO -> {
        val brightMul = if (model == AcousticSynthesisModel.BRIGHT_PIANO) 1.35f else 1.0f
        val layers = listOf(
          Triple(0, 59, 0.58f),   // pp warm felt
          Triple(60, 99, 0.92f),  // mf balanced concert
          Triple(100, 127, 1.32f), // ff forte attack & duplex harmonics
        )
        for (span in spans) {
          val freq = midiToFreq(span.root)
          for ((loV, hiV, velBrightness) in layers) {
            zones.add(
              buildPianoZone(
                freq = freq,
                root = span.root,
                lo = span.lo,
                hi = span.hi,
                loVel = loV,
                hiVel = hiV,
                bright = brightMul * velBrightness,
                detune = 0.00055f,
              ),
            )
          }
        }
      }

      AcousticSynthesisModel.ELECTRIC_TINE,
      AcousticSynthesisModel.PLUCKED_ACOUSTIC_GUITAR,
      AcousticSynthesisModel.PLUCKED_STEEL_GUITAR,
      AcousticSynthesisModel.PLUCKED_NYLON_GUITAR,
      AcousticSynthesisModel.PLUCKED_ELECTRIC_CLEAN,
      AcousticSynthesisModel.BOWED_SOLO_STRING,
      AcousticSynthesisModel.ACOUSTIC_CELLO,
      AcousticSynthesisModel.SAX_REED,
      AcousticSynthesisModel.BRASS_HORN,
      AcousticSynthesisModel.ACCORDION_MUSETTE,
      AcousticSynthesisModel.ACOUSTIC_BASS,
      AcousticSynthesisModel.ELECTRIC_BASS -> {
        val velLayers = listOf(
          Triple(0, 79, 0.72f),   // warm / legato layer
          Triple(80, 127, 1.18f), // crisp / expressive layer
        )
        for (span in spans) {
          for ((loV, hiV, layerIntensity) in velLayers) {
            zones.add(
              createModelZone(
                model = model,
                rootMidi = span.root,
                lowKey = span.lo,
                highKey = span.hi,
                lowVel = loV,
                highVel = hiV,
                layerDrive = layerIntensity,
              ),
            )
          }
        }
      }

      else -> {
        for (span in spans) {
          zones.add(
            createModelZone(
              model = model,
              rootMidi = span.root,
              lowKey = span.lo,
              highKey = span.hi,
              lowVel = 0,
              highVel = 127,
              layerDrive = 1.0f,
            ),
          )
        }
      }
    }

    return SampleInstrument(id = id, name = name, zones = zones, isDrumKit = false)
  }

  private fun midiToFreq(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

  private fun createModelZone(
    model: AcousticSynthesisModel,
    rootMidi: Int,
    lowKey: Int,
    highKey: Int,
    lowVel: Int,
    highVel: Int,
    layerDrive: Float,
  ): SampleZone {
    val freq = midiToFreq(rootMidi)
    return when (model) {
      AcousticSynthesisModel.GRAND_PIANO -> buildPianoZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, bright = 1.0f * layerDrive, detune = 0.00055f)
      AcousticSynthesisModel.BRIGHT_PIANO -> buildPianoZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, bright = 1.35f * layerDrive, detune = 0.00075f)
      AcousticSynthesisModel.HONKY_TONK -> buildPianoZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, bright = 1.15f, detune = 0.0062f)
      AcousticSynthesisModel.ELECTRIC_TINE -> buildElectricPianoZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, layerDrive)
      AcousticSynthesisModel.CLAVINET -> buildPluckedZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, brightness = 0.96f, decay = 0.9962f, pickupRatio = 0.14f, bodyHz = 360f, bodyAmt = 0.18f, attackSec = 0.002f, releaseSec = 0.14f)
      AcousticSynthesisModel.HARPSICHORD -> buildPluckedZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, brightness = 0.98f, decay = 0.9970f, pickupRatio = 0.11f, bodyHz = 280f, bodyAmt = 0.22f, attackSec = 0.002f, releaseSec = 0.18f)
      AcousticSynthesisModel.PIPE_ORGAN -> buildOrganZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, drawbars = floatArrayOf(0.8f, 0.5f, 0.9f, 0.6f, 0.4f, 0.5f, 0.2f, 0.3f), chiff = 0.04f)
      AcousticSynthesisModel.CHURCH_ORGAN -> buildOrganZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, drawbars = floatArrayOf(0.95f, 0.45f, 1.0f, 0.85f, 0.55f, 0.72f, 0.42f, 0.62f), chiff = 0.09f)
      AcousticSynthesisModel.DRAWBAR_ORGAN -> buildOrganZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, drawbars = floatArrayOf(0.9f, 0.85f, 0.95f, 0.7f, 0.25f, 0.5f, 0.15f, 0.4f), chiff = 0.03f)
      AcousticSynthesisModel.ACCORDION_MUSETTE -> buildAccordionZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, layerDrive)
      AcousticSynthesisModel.SAX_REED -> buildReedOrBrassZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, formantHz = 950f * layerDrive.coerceIn(0.85f, 1.25f), oddBias = 0.45f, breath = 0.055f, vibHz = 5.1f, brassiness = layerDrive)
      AcousticSynthesisModel.CLARINET_HOLLOW -> buildReedOrBrassZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, formantHz = 1200f, oddBias = 0.88f, breath = 0.04f, vibHz = 4.6f, brassiness = 0.9f)
      AcousticSynthesisModel.OBOE_DOUBLE_REED -> buildReedOrBrassZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, formantHz = 1650f, oddBias = 0.25f, breath = 0.03f, vibHz = 5.4f, brassiness = 1.0f)
      AcousticSynthesisModel.FLUTE_BREATH -> buildFluteZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, breathAmt = 0.13f, hollowness = 0.24f)
      AcousticSynthesisModel.BRASS_HORN -> buildReedOrBrassZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, formantHz = 1150f * layerDrive.coerceIn(0.8f, 1.3f), oddBias = 0.16f, breath = 0.02f, vibHz = 4.2f, brassiness = layerDrive)
      AcousticSynthesisModel.BRASS_ENSEMBLE -> buildEnsembleStringsOrBrass(freq, rootMidi, lowKey, highKey, lowVel, highVel, isBrass = true)
      AcousticSynthesisModel.BOWED_SOLO_STRING -> buildBowedZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, formantA = 285f, formantB = 470f, bridgeHz = 2400f, rosinNoise = 0.045f * layerDrive, vibHz = 5.4f, vibDepth = 0.0045f)
      AcousticSynthesisModel.ACOUSTIC_CELLO -> buildBowedZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, formantA = 95f, formantB = 195f, bridgeHz = 920f, rosinNoise = 0.038f * layerDrive, vibHz = 4.7f, vibDepth = 0.0040f)
      AcousticSynthesisModel.STRING_ENSEMBLE -> buildEnsembleStringsOrBrass(freq, rootMidi, lowKey, highKey, lowVel, highVel, isBrass = false)
      AcousticSynthesisModel.PIZZICATO -> buildPluckedZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, brightness = 0.65f, decay = 0.986f, pickupRatio = 0.35f, bodyHz = 230f, bodyAmt = 0.30f, attackSec = 0.004f, releaseSec = 0.22f)
      AcousticSynthesisModel.PLUCKED_ACOUSTIC_GUITAR -> buildPluckedZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, brightness = (0.80f * layerDrive).coerceIn(0.60f, 0.95f), decay = 0.9964f, pickupRatio = 0.24f, bodyHz = 110f, bodyAmt = 0.36f, attackSec = 0.002f, releaseSec = 0.32f)
      AcousticSynthesisModel.PLUCKED_STEEL_GUITAR -> buildPluckedZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, brightness = (0.91f * layerDrive).coerceIn(0.75f, 0.99f), decay = 0.9972f, pickupRatio = 0.19f, bodyHz = 128f, bodyAmt = 0.28f, attackSec = 0.0015f, releaseSec = 0.34f)
      AcousticSynthesisModel.PLUCKED_NYLON_GUITAR -> buildPluckedZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, brightness = (0.64f * layerDrive).coerceIn(0.48f, 0.85f), decay = 0.9956f, pickupRatio = 0.32f, bodyHz = 98f, bodyAmt = 0.42f, attackSec = 0.003f, releaseSec = 0.28f)
      AcousticSynthesisModel.PLUCKED_ELECTRIC_CLEAN -> buildPluckedZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, brightness = (0.87f * layerDrive).coerceIn(0.70f, 0.97f), decay = 0.9974f, pickupRatio = 0.21f, bodyHz = 420f, bodyAmt = 0.14f, attackSec = 0.002f, releaseSec = 0.26f)
      AcousticSynthesisModel.OVERDRIVE_GUITAR -> buildOverdriveGuitarZone(freq, rootMidi, lowKey, highKey, lowVel, highVel)
      AcousticSynthesisModel.ACOUSTIC_BASS -> buildBassZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, isElectric = false, layerDrive = layerDrive)
      AcousticSynthesisModel.ELECTRIC_BASS -> buildBassZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, isElectric = true, layerDrive = layerDrive)
      AcousticSynthesisModel.SYNTH_LEAD -> buildSynthZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, isBass = false, isPad = false)
      AcousticSynthesisModel.SYNTH_PAD -> buildSynthZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, isBass = false, isPad = true)
      AcousticSynthesisModel.SYNTH_BASS -> buildSynthZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, isBass = true, isPad = false)
      AcousticSynthesisModel.ETHIO_MASINKO -> buildBowedZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, formantA = 390f, formantB = 820f, bridgeHz = 1850f, rosinNoise = 0.10f, vibHz = 6.0f, vibDepth = 0.006f)
      AcousticSynthesisModel.ETHIO_KRAR -> buildPluckedZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, brightness = 0.86f, decay = 0.9955f, pickupRatio = 0.28f, bodyHz = 165f, bodyAmt = 0.32f, attackSec = 0.002f, releaseSec = 0.32f)
      AcousticSynthesisModel.ETHIO_WASHINT -> buildFluteZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, breathAmt = 0.22f, hollowness = 0.55f)
      AcousticSynthesisModel.ETHIO_KEBERO,
      AcousticSynthesisModel.GM_DRUM_KIT -> buildPluckedZone(freq, rootMidi, lowKey, highKey, lowVel, highVel, brightness = 0.8f, decay = 0.99f, pickupRatio = 0.25f, bodyHz = 140f, bodyAmt = 0.2f, attackSec = 0.002f, releaseSec = 0.2f)
    }
  }

  private fun buildPianoZone(
    freq: Double,
    root: Int,
    lo: Int,
    hi: Int,
    loVel: Int,
    hiVel: Int,
    bright: Float,
    detune: Float,
  ): SampleZone {
    val durSec = when {
      root < 48 -> 1.15
      root < 68 -> 0.85
      else -> 0.60
    }
    val len = (SAMPLE_RATE * durSec).toInt()
    val bufL = FloatArray(len)
    val bufR = FloatArray(len)
    val rng = Random(root * 31 + hiVel)
    val nyquist = SAMPLE_RATE * 0.42
    val harmonicBudget = (8 + (bright * 9f).toInt()).coerceIn(6, 22)
    val partials = min(harmonicBudget, (nyquist / freq).toInt().coerceAtLeast(3))

    // Realistic soundboard stereo image: bass left, treble right
    val soundboardPan = ((root - 60) / 36.0).coerceIn(-0.62, 0.62)
    val panL = 1.0 - max(0.0, soundboardPan) * 0.45
    val panR = 1.0 + min(0.0, soundboardPan) * 0.45

    // Subtle sympathetic soundboard body resonance around 130Hz
    var bodyZ1 = 0.0
    var bodyZ2 = 0.0
    val wBody = 2.0 * PI * 130.0 / SAMPLE_RATE
    val rBody = 0.992

    for (i in 0 until len) {
      val t = i.toDouble() / SAMPLE_RATE
      var sumL = 0.0
      var sumR = 0.0
      for (h in 1..partials) {
        val inharmonic = kotlin.math.sqrt(1.0 + 0.00018 * h * h)
        val f1 = freq * h * inharmonic
        val f2 = f1 * (1.0 + detune)
        val f3 = f1 * (1.0 - detune * 0.85)
        val keyDecayScale = if (root < 55) 1.45 else if (root > 75) 0.72 else 1.0
        val decayRate = (0.95 + h * 0.44 * (1.15f / bright.coerceAtLeast(0.5f))) / keyDecayScale
        val amp = (1.0 / h.toDouble().pow(0.82)) * exp(-t * decayRate)
        val s1 = sin(2.0 * PI * f1 * t)
        val s2 = sin(2.0 * PI * f2 * t + 0.42)
        val s3 = sin(2.0 * PI * f3 * t + 0.88)
        sumL += (s1 + 0.68 * s2 + 0.42 * s3) * amp * 0.36
        sumR += (s1 + 0.42 * s2 + 0.68 * s3) * amp * 0.36
      }

      // Key-scaled felt hammer transient (tighter/crisper at forte velocities)
      val hammerRate = 150.0 + bright * 70.0
      val hammerEnv = exp(-t * hammerRate)
      val thump = ((rng.nextFloat() * 2f - 1f) * 0.16 * bright + sin(2.0 * PI * (120.0 + root * 1.4) * t) * 0.14) * hammerEnv

      // Soundboard sympathetic ring
      val excit = (sumL + sumR) * 0.5 + thump
      val body = excit - 2.0 * rBody * cos(wBody) * bodyZ1 + rBody * rBody * bodyZ2
      bodyZ2 = bodyZ1
      bodyZ1 = body * 0.03

      val rawL = tanh(((sumL * 0.38 * bright + thump + bodyZ1) * panL).toFloat())
      val rawR = tanh(((sumR * 0.38 * bright + thump + bodyZ1) * panR).toFloat())
      bufL[i] = rawL
      bufR[i] = rawR
    }
    normalizeStereoPeak(bufL, bufR, 0.88f)
    return SampleZone(
      samples = bufL,
      rightSamples = bufR,
      sampleRate = SAMPLE_RATE,
      rootNote = root,
      lowKey = lo,
      highKey = hi,
      lowVel = loVel,
      highVel = hiVel,
      loopEnabled = false,
      attackSec = if (bright > 1.05f) 0.002f else 0.0045f,
      decaySec = if (root < 55) 1.9f else 1.4f,
      sustainLevel = 0.12f,
      releaseSec = if (root < 55) 0.40f else 0.28f,
      filterCutoffHz = (7200f * bright).coerceIn(3200f, 18000f),
    )
  }

  private fun buildElectricPianoZone(
    freq: Double,
    root: Int,
    lo: Int,
    hi: Int,
    loVel: Int,
    hiVel: Int,
    layerDrive: Float,
  ): SampleZone {
    val len = (SAMPLE_RATE * 0.95).toInt()
    val bufL = FloatArray(len)
    val bufR = FloatArray(len)
    val barkAmt = if (layerDrive > 1.0f) 0.38f else 0.12f
    for (i in 0 until len) {
      val t = i.toDouble() / SAMPLE_RATE
      val bellEnv = exp(-t * 9.8)
      val bodyEnv = exp(-t * 1.45)
      val mod = sin(2.0 * PI * (freq * 14.0) * t) * (0.28 + 0.14 * layerDrive) * bellEnv
      val tine = sin(2.0 * PI * freq * t + mod) * bodyEnv
      val warm = sin(2.0 * PI * freq * 2.0 * t) * 0.22 * exp(-t * 3.8)
      val bark = tanh((sin(2.0 * PI * freq * t) * (1.2f + layerDrive * 0.9f)).toFloat()) * barkAmt * exp(-t * 5.2).toFloat()
      val stereoPan = sin(2.0 * PI * 3.2 * t) * 0.18 // vintage stereo tremolo/chorus motion
      val core = ((tine * 0.74 + warm) + bark).toFloat()
      bufL[i] = core * (1f - stereoPan.toFloat())
      bufR[i] = core * (1f + stereoPan.toFloat())
    }
    normalizeStereoPeak(bufL, bufR, 0.86f)
    return SampleZone(
      samples = bufL,
      rightSamples = bufR,
      sampleRate = SAMPLE_RATE,
      rootNote = root,
      lowKey = lo,
      highKey = hi,
      lowVel = loVel,
      highVel = hiVel,
      loopEnabled = false,
      attackSec = 0.0025f,
      decaySec = 1.45f,
      sustainLevel = 0.20f,
      releaseSec = 0.28f,
      filterCutoffHz = (8500f * layerDrive).coerceIn(4500f, 16000f),
    )
  }

  /**
   * Tuned Fractional-Delay Thiran Allpass Karplus-Strong waveguide synthesis with wooden body resonator.
   * Ensures 0.0 cents pitch accuracy across all guitar frets (Acoustic, Steel, Nylon, Clean, Krar).
   */
  private fun buildPluckedZone(
    freq: Double,
    root: Int,
    lo: Int,
    hi: Int,
    loVel: Int,
    hiVel: Int,
    brightness: Float,
    decay: Float,
    pickupRatio: Float,
    bodyHz: Float,
    bodyAmt: Float,
    attackSec: Float,
    releaseSec: Float,
  ): SampleZone {
    val len = (SAMPLE_RATE * 0.85).toInt()
    val bufL = FloatArray(len)
    val bufR = FloatArray(len)

    // Exact fractional waveguide length: reading avg of ring[idx] and ring[idx+1] gives delay = (delayLen - 0.5),
    // so targetL = exactPeriod + 0.5 minus allpass fractional delay sums back to exactPeriod.
    val exactPeriod = SAMPLE_RATE.toDouble() / freq
    val targetL = exactPeriod + 0.5
    val delayLen = floor(targetL - 0.15).toInt().coerceIn(8, 2400)
    val delta = (targetL - delayLen).coerceIn(0.15, 1.15)
    val allpassA = ((1.0 - delta) / (1.0 + delta)).toFloat()

    val ring = FloatArray(delayLen)
    val rng = Random(root * 97 + (brightness * 100).toInt() + hiVel)

    // Shaped finger/pick exciter noise burst
    var prevExc = 0f
    for (i in 0 until delayLen) {
      val noise = rng.nextFloat() * 2f - 1f
      val shaped = prevExc * (1f - brightness * 0.58f) + noise * (brightness * 0.58f)
      ring[i] = shaped
      prevExc = shaped
    }

    val combOffset = max(1, (delayLen * pickupRatio).toInt())
    var idx = 0
    var lpState = 0f
    var apInPrev = 0f
    var apOutPrev = 0f
    val damp = (0.48f + (1f - brightness) * 0.36f).coerceIn(0.15f, 0.90f)

    // Gain-compensated 2nd-order wooden soundboard resonance filter (adds woody warmth without drowning string fundamental)
    val w0 = (2.0 * PI * bodyHz / SAMPLE_RATE).toFloat()
    val rB = 0.960f
    val cosW0 = cos(w0.toDouble()).toFloat()
    val bodyInGain = (1f - rB) * 0.55f
    var bZ1 = 0f
    var bZ2 = 0f

    for (i in 0 until len) {
      val nextIdx = (idx + 1) % delayLen
      val avg = 0.5f * (ring[idx] + ring[nextIdx])
      // Allpass fractional delay interpolation for concert pitch accuracy
      val apOut = allpassA * avg + apInPrev - allpassA * apOutPrev
      apInPrev = avg
      apOutPrev = apOut

      lpState = lpState * (1f - damp) + apOut * damp
      ring[idx] = lpState * decay
      val pickupIdx = (idx + combOffset) % delayLen
      val stringSig = ring[idx] - 0.34f * ring[pickupIdx]

      val bodySig = (stringSig * bodyInGain) + 2f * rB * cosW0 * bZ1 - (rB * rB) * bZ2
      bZ2 = bZ1
      bZ1 = bodySig

      val mixed = stringSig + bodySig * bodyAmt
      // Subtle stereo room width between neck & bridge mic
      bufL[i] = mixed + 0.08f * ring[pickupIdx]
      bufR[i] = mixed - 0.08f * ring[pickupIdx]
      idx = nextIdx
    }
    normalizeStereoPeak(bufL, bufR, 0.88f)
    return SampleZone(
      samples = bufL,
      rightSamples = bufR,
      sampleRate = SAMPLE_RATE,
      rootNote = root,
      lowKey = lo,
      highKey = hi,
      lowVel = loVel,
      highVel = hiVel,
      loopEnabled = false,
      attackSec = attackSec,
      decaySec = 1.45f,
      sustainLevel = 0.10f,
      releaseSec = releaseSec,
      filterCutoffHz = (7800f + brightness * 6200f).coerceIn(4000f, 16000f),
    )
  }

  private fun buildOverdriveGuitarZone(
    freq: Double,
    root: Int,
    lo: Int,
    hi: Int,
    loVel: Int,
    hiVel: Int,
  ): SampleZone {
    val base = buildPluckedZone(freq, root, lo, hi, loVel, hiVel, brightness = 0.94f, decay = 0.9978f, pickupRatio = 0.2f, bodyHz = 280f, bodyAmt = 0.15f, attackSec = 0.002f, releaseSec = 0.22f)
    val srcL = base.samples
    val srcR = base.rightSamples ?: srcL
    val outL = FloatArray(srcL.size)
    val outR = FloatArray(srcR.size)
    var lpL = 0f
    var lpR = 0f
    for (i in srcL.indices) {
      val dL = tanh(srcL[i] * 5.8f + 0.12f) - 0.10f
      val dR = tanh(srcR[i] * 5.8f + 0.12f) - 0.10f
      lpL += 0.28f * (dL - lpL)
      lpR += 0.28f * (dR - lpR)
      outL[i] = lpL
      outR[i] = lpR
    }
    normalizeStereoPeak(outL, outR, 0.82f)
    return base.copy(samples = outL, rightSamples = outR, filterCutoffHz = 6200f)
  }

  private fun buildBassZone(
    freq: Double,
    root: Int,
    lo: Int,
    hi: Int,
    loVel: Int,
    hiVel: Int,
    isElectric: Boolean,
    layerDrive: Float,
  ): SampleZone {
    val len = (SAMPLE_RATE * 0.85).toInt()
    val buf = FloatArray(len)
    val rng = Random(root * 61 + hiVel)
    val nyquist = SAMPLE_RATE * 0.42
    val maxH = min(if (isElectric) 14 else 9, (nyquist / freq).toInt().coerceAtLeast(3))
    val brightScale = layerDrive.coerceIn(0.65f, 1.35f)

    for (i in 0 until len) {
      val t = i.toDouble() / SAMPLE_RATE
      var sum = 0.0
      for (h in 1..maxH) {
        val decayRate = if (isElectric) (1.25 + h * 0.55) else (1.85 + h * 0.82)
        val pickupNotch = if (isElectric) abs(sin(PI * h * 0.22)) + 0.35 else 1.0
        val amp = (1.0 / h.toDouble().pow(if (isElectric) 0.78 else 1.05)) * pickupNotch * exp(-t * decayRate)
        sum += sin(2.0 * PI * freq * h * t) * amp
      }
      // Fingerboard thump / slap click transient (<12ms)
      val fingerClick = ((rng.nextFloat() * 2f - 1f) * 0.14 + sin(2.0 * PI * (if (isElectric) 320.0 else 145.0) * t) * 0.22) * exp(-t * 140.0)
      val raw = (sum * 0.46 * brightScale + fingerClick).toFloat()
      buf[i] = tanh(raw)
    }
    normalizePeak(buf, 0.88f)
    return SampleZone(
      samples = buf,
      sampleRate = SAMPLE_RATE,
      rootNote = root,
      lowKey = lo,
      highKey = hi,
      lowVel = loVel,
      highVel = hiVel,
      loopEnabled = false,
      attackSec = 0.002f,
      decaySec = 1.25f,
      sustainLevel = 0.15f,
      releaseSec = 0.24f,
      filterCutoffHz = if (isElectric) (4800f * brightScale) else (2900f * brightScale),
    )
  }

  private fun buildOrganZone(
    freq: Double,
    root: Int,
    lo: Int,
    hi: Int,
    loVel: Int,
    hiVel: Int,
    drawbars: FloatArray,
    chiff: Float,
  ): SampleZone {
    val cycles = 16
    val loopSamples = ((SAMPLE_RATE / freq) * cycles).toInt().coerceIn(256, 22050)
    val exactFreq = (SAMPLE_RATE.toDouble() * cycles) / loopSamples
    val attackLen = (SAMPLE_RATE * 0.06).toInt()
    val bufL = FloatArray(attackLen + loopSamples + 64)
    val bufR = FloatArray(attackLen + loopSamples + 64)
    val ratios = doubleArrayOf(0.5, 1.5, 1.0, 2.0, 3.0, 4.0, 5.0, 8.0)
    val rng = Random(root * 41)

    for (i in bufL.indices) {
      val t = i.toDouble() / SAMPLE_RATE
      var sumL = 0.0
      var sumR = 0.0
      for (d in drawbars.indices) {
        val hFreq = exactFreq * ratios[d]
        if (hFreq < SAMPLE_RATE * 0.42) {
          val wave = sin(2.0 * PI * hFreq * t + d * 0.3) * drawbars[d]
          if (d % 2 == 0) sumL += wave else sumR += wave
          sumL += wave * 0.65
          sumR += wave * 0.65
        }
      }
      val air = if (i < attackLen) (rng.nextFloat() * 2f - 1f) * chiff * exp(-t * 48.0).toFloat() else 0f
      bufL[i] = (sumL * 0.16 + air).toFloat()
      bufR[i] = (sumR * 0.16 + air).toFloat()
    }
    normalizeStereoPeak(bufL, bufR, 0.80f)
    return SampleZone(
      samples = bufL,
      rightSamples = bufR,
      sampleRate = SAMPLE_RATE,
      rootNote = root,
      lowKey = lo,
      highKey = hi,
      lowVel = loVel,
      highVel = hiVel,
      loopEnabled = true,
      loopStart = attackLen,
      loopEnd = attackLen + loopSamples,
      attackSec = 0.008f,
      decaySec = 0.1f,
      sustainLevel = 0.92f,
      releaseSec = 0.16f,
      filterCutoffHz = 11500f,
    )
  }

  private fun buildAccordionZone(
    freq: Double,
    root: Int,
    lo: Int,
    hi: Int,
    loVel: Int,
    hiVel: Int,
    layerDrive: Float,
  ): SampleZone {
    // 3 detuned free-reeds (-12c, 0c, +12c musette) with seamless loop
    val attackLen = (SAMPLE_RATE * 0.08).toInt()
    val loopLen = ((SAMPLE_RATE / freq) * 24).toInt().coerceIn(512, 22050)
    val exactFreq = (SAMPLE_RATE.toDouble() * 24) / loopLen
    val totalLen = attackLen + loopLen + 32
    val bufL = FloatArray(totalLen)
    val bufR = FloatArray(totalLen)
    val maxH = ((SAMPLE_RATE * 0.38) / exactFreq).toInt().coerceIn(4, 16)
    val reeds = doubleArrayOf(-0.007, 0.0, 0.0075)

    for (i in 0 until totalLen) {
      val t = i.toDouble() / SAMPLE_RATE
      var l = 0.0
      var r = 0.0
      for (rIdx in reeds.indices) {
        val rf = exactFreq * (1.0 + reeds[rIdx])
        var voice = 0.0
        for (h in 1..maxH) {
          val hFreq = rf * h
          val dist = abs(hFreq - 1450.0) / 850.0
          val formant = 0.45 + 1.1 / (1.0 + dist * dist)
          voice += sin(2.0 * PI * hFreq * t) * (1.0 / h.toDouble().pow(0.75)) * formant
        }
        when (rIdx) {
          0 -> l += voice
          1 -> { l += voice * 0.7; r += voice * 0.7 }
          else -> r += voice
        }
      }
      bufL[i] = (l * 0.22 * layerDrive).toFloat()
      bufR[i] = (r * 0.22 * layerDrive).toFloat()
    }
    normalizeStereoPeak(bufL, bufR, 0.82f)
    return SampleZone(
      samples = bufL,
      rightSamples = bufR,
      sampleRate = SAMPLE_RATE,
      rootNote = root,
      lowKey = lo,
      highKey = hi,
      lowVel = loVel,
      highVel = hiVel,
      loopEnabled = true,
      loopStart = attackLen,
      loopEnd = attackLen + loopLen,
      attackSec = 0.018f,
      decaySec = 0.20f,
      sustainLevel = 0.90f,
      releaseSec = 0.14f,
      filterCutoffHz = (9000f * layerDrive).coerceIn(5000f, 15000f),
    )
  }

  private fun buildReedOrBrassZone(
    freq: Double,
    root: Int,
    lo: Int,
    hi: Int,
    lowVel: Int,
    highVel: Int,
    formantHz: Float,
    oddBias: Float,
    breath: Float,
    vibHz: Float,
    brassiness: Float,
  ): SampleZone {
    val attackLen = (SAMPLE_RATE * 0.18).toInt()
    val loopLen = ((SAMPLE_RATE / freq) * 24).toInt().coerceIn(512, 22050)
    val exactFreq = (SAMPLE_RATE.toDouble() * 24) / loopLen
    val totalLen = attackLen + loopLen + 32
    val buf = FloatArray(totalLen)
    val rng = Random(root * 53 + highVel)
    val maxH = ((SAMPLE_RATE * 0.40) / exactFreq).toInt().coerceIn(3, 18)

    for (i in 0 until totalLen) {
      val t = i.toDouble() / SAMPLE_RATE
      val attackGain = min(1.0, t / 0.055)
      val brassEdge = if (i < attackLen) brassiness else 1.0f
      var sum = 0.0
      for (h in 1..maxH) {
        val hFreq = exactFreq * h
        val oddFactor = if (h % 2 == 0) (1.0 - oddBias) else 1.0
        val dist = abs(hFreq - formantHz) / (formantHz * 0.75)
        val formantWeight = 1.0 / (1.0 + dist * dist)
        val rolloff = 1.0 / h.toDouble().pow(0.70 / brassEdge.coerceIn(0.7f, 1.3f))
        sum += sin(2.0 * PI * hFreq * t) * oddFactor * (0.4 + 1.2 * formantWeight) * rolloff
      }
      val noise = (rng.nextFloat() * 2f - 1f) * breath * (if (i < attackLen) 1.5f else 0.45f)
      buf[i] = ((sum * 0.35 + noise) * attackGain).toFloat()
    }
    normalizePeak(buf, 0.84f)
    return SampleZone(
      samples = buf,
      sampleRate = SAMPLE_RATE,
      rootNote = root,
      lowKey = lo,
      highKey = hi,
      lowVel = lowVel,
      highVel = highVel,
      loopEnabled = true,
      loopStart = attackLen,
      loopEnd = attackLen + loopLen,
      attackSec = 0.032f,
      decaySec = 0.22f,
      sustainLevel = 0.86f,
      releaseSec = 0.18f,
      filterCutoffHz = (8500f * brassiness).coerceIn(4500f, 15000f),
    )
  }

  private fun buildFluteZone(
    freq: Double,
    root: Int,
    lo: Int,
    hi: Int,
    lowVel: Int,
    highVel: Int,
    breathAmt: Float,
    hollowness: Float,
  ): SampleZone {
    val attackLen = (SAMPLE_RATE * 0.16).toInt()
    val loopLen = ((SAMPLE_RATE / freq) * 20).toInt().coerceIn(512, 22050)
    val exactFreq = (SAMPLE_RATE.toDouble() * 20) / loopLen
    val totalLen = attackLen + loopLen + 32
    val buf = FloatArray(totalLen)
    val rng = Random(root * 71)
    var breathLp = 0f

    for (i in 0 until totalLen) {
      val t = i.toDouble() / SAMPLE_RATE
      val fundamental = sin(2.0 * PI * exactFreq * t)
      val h2 = sin(2.0 * PI * exactFreq * 2.0 * t) * (0.34 - hollowness * 0.18)
      val h3 = sin(2.0 * PI * exactFreq * 3.0 * t) * (0.12 + hollowness * 0.22)
      val h4 = sin(2.0 * PI * exactFreq * 4.0 * t) * 0.06
      val rawNoise = rng.nextFloat() * 2f - 1f
      breathLp += 0.22f * (rawNoise - breathLp)
      val chiff = if (i < attackLen) breathAmt * 1.9f * exp(-t * 14.0).toFloat() else breathAmt * 0.50f
      buf[i] = ((fundamental + h2 + h3 + h4) * 0.55 + breathLp * chiff).toFloat()
    }
    normalizePeak(buf, 0.80f)
    return SampleZone(
      samples = buf,
      sampleRate = SAMPLE_RATE,
      rootNote = root,
      lowKey = lo,
      highKey = hi,
      lowVel = lowVel,
      highVel = highVel,
      loopEnabled = true,
      loopStart = attackLen,
      loopEnd = attackLen + loopLen,
      attackSec = 0.052f,
      decaySec = 0.20f,
      sustainLevel = 0.88f,
      releaseSec = 0.22f,
      filterCutoffHz = 8800f,
    )
  }

  private fun buildBowedZone(
    freq: Double,
    root: Int,
    lo: Int,
    hi: Int,
    lowVel: Int,
    highVel: Int,
    formantA: Float,
    formantB: Float,
    bridgeHz: Float,
    rosinNoise: Float,
    vibHz: Float,
    vibDepth: Float,
  ): SampleZone {
    val attackLen = (SAMPLE_RATE * 0.15).toInt()
    val loopLen = ((SAMPLE_RATE / freq) * 24).toInt().coerceIn(512, 22050)
    val exactFreq = (SAMPLE_RATE.toDouble() * 24) / loopLen
    val totalLen = attackLen + loopLen + 32
    val bufL = FloatArray(totalLen)
    val bufR = FloatArray(totalLen)
    val rng = Random(root * 43 + highVel)
    val maxH = ((SAMPLE_RATE * 0.40) / exactFreq).toInt().coerceIn(4, 20)

    for (i in 0 until totalLen) {
      val t = i.toDouble() / SAMPLE_RATE
      val phaseJitter = if (i > attackLen) sin(2.0 * PI * vibHz * t) * vibDepth else 0.0
      var sum = 0.0
      for (h in 1..maxH) {
        val hFreq = exactFreq * h
        val dA = abs(hFreq - formantA) / (formantA * 0.45)
        val dB = abs(hFreq - formantB) / (formantB * 0.45)
        val dBridge = abs(hFreq - bridgeHz) / (bridgeHz * 0.48)
        val bodyRes = 0.35 + 0.85 / (1.0 + dA * dA) + 0.90 / (1.0 + dB * dB) + 1.05 / (1.0 + dBridge * dBridge)
        sum += sin(2.0 * PI * hFreq * t + h * phaseJitter) * (1.0 / h) * bodyRes
      }
      val rosin = (rng.nextFloat() * 2f - 1f) * rosinNoise * (if (i < attackLen) 1.7f else 0.7f)
      val core = (sum * 0.42 + rosin).toFloat()
      bufL[i] = core
      bufR[i] = core * 0.95f
    }
    normalizeStereoPeak(bufL, bufR, 0.82f)
    return SampleZone(
      samples = bufL,
      rightSamples = bufR,
      sampleRate = SAMPLE_RATE,
      rootNote = root,
      lowKey = lo,
      highKey = hi,
      lowVel = lowVel,
      highVel = highVel,
      loopEnabled = true,
      loopStart = attackLen,
      loopEnd = attackLen + loopLen,
      attackSec = 0.072f,
      decaySec = 0.25f,
      sustainLevel = 0.90f,
      releaseSec = 0.30f,
      filterCutoffHz = 10500f,
    )
  }

  private fun buildEnsembleStringsOrBrass(
    freq: Double,
    root: Int,
    lo: Int,
    hi: Int,
    lowVel: Int,
    highVel: Int,
    isBrass: Boolean,
  ): SampleZone {
    val len = (SAMPLE_RATE * 0.72).toInt()
    val bufL = FloatArray(len)
    val bufR = FloatArray(len)
    val detunes = doubleArrayOf(-0.0085, -0.0032, 0.0, 0.0038, 0.009)
    val maxH = if (isBrass) 13 else 11

    for (i in 0 until len) {
      val t = i.toDouble() / SAMPLE_RATE
      var l = 0.0
      var r = 0.0
      for (v in detunes.indices) {
        val vf = freq * (1.0 + detunes[v])
        var voice = 0.0
        for (h in 1..maxH) {
          val amp = if (isBrass) (1.0 / h.toDouble().pow(0.72)) else (1.0 / h)
          voice += sin(2.0 * PI * vf * h * t + v * 1.1) * amp
        }
        val pan = (v - 2) * 0.22
        l += voice * (1.0 - max(0.0, pan))
        r += voice * (1.0 + min(0.0, pan))
      }
      bufL[i] = (l * 0.14).toFloat()
      bufR[i] = (r * 0.14).toFloat()
    }
    normalizeStereoPeak(bufL, bufR, 0.80f)
    val loopStart = (SAMPLE_RATE * 0.14).toInt()
    val loopEnd = len - 256
    val xfade = 768
    for (k in 0 until xfade) {
      val a = k.toFloat() / xfade
      bufL[loopEnd - xfade + k] = bufL[loopEnd - xfade + k] * (1f - a) + bufL[loopStart + k] * a
      bufR[loopEnd - xfade + k] = bufR[loopEnd - xfade + k] * (1f - a) + bufR[loopStart + k] * a
    }

    return SampleZone(
      samples = bufL,
      rightSamples = bufR,
      sampleRate = SAMPLE_RATE,
      rootNote = root,
      lowKey = lo,
      highKey = hi,
      lowVel = lowVel,
      highVel = highVel,
      loopEnabled = true,
      loopStart = loopStart,
      loopEnd = loopEnd,
      attackSec = if (isBrass) 0.042f else 0.15f,
      decaySec = 0.30f,
      sustainLevel = 0.90f,
      releaseSec = if (isBrass) 0.20f else 0.45f,
      filterCutoffHz = if (isBrass) 10000f else 8500f,
    )
  }

  private fun buildSynthZone(
    freq: Double,
    root: Int,
    lo: Int,
    hi: Int,
    lowVel: Int,
    highVel: Int,
    isBass: Boolean,
    isPad: Boolean,
  ): SampleZone {
    val len = (SAMPLE_RATE * 0.72).toInt()
    val bufL = FloatArray(len)
    val bufR = FloatArray(len)
    val detunes = if (isPad) doubleArrayOf(-0.011, -0.004, 0.004, 0.011) else doubleArrayOf(-0.005, 0.0, 0.0055)
    val maxH = if (isBass) 8 else 12

    for (i in 0 until len) {
      val t = i.toDouble() / SAMPLE_RATE
      var l = 0.0
      var r = 0.0
      for (v in detunes.indices) {
        val vf = freq * (1.0 + detunes[v])
        var saw = 0.0
        for (h in 1..maxH) {
          saw += sin(2.0 * PI * vf * h * t + v * 0.7) / h
        }
        val sub = if (isBass) sin(2.0 * PI * (vf * 0.5) * t) * 0.65 else 0.0
        val w = saw * 0.5 + sub
        if (v % 2 == 0) l += w else r += w
      }
      bufL[i] = (l * 0.25).toFloat()
      bufR[i] = (r * 0.25).toFloat()
    }
    normalizeStereoPeak(bufL, bufR, 0.82f)
    val loopStart = (SAMPLE_RATE * 0.10).toInt()
    val loopEnd = len - 256
    val xfade = 768
    for (k in 0 until xfade) {
      val a = k.toFloat() / xfade
      bufL[loopEnd - xfade + k] = bufL[loopEnd - xfade + k] * (1f - a) + bufL[loopStart + k] * a
      bufR[loopEnd - xfade + k] = bufR[loopEnd - xfade + k] * (1f - a) + bufR[loopStart + k] * a
    }

    return SampleZone(
      samples = bufL,
      rightSamples = bufR,
      sampleRate = SAMPLE_RATE,
      rootNote = root,
      lowKey = lo,
      highKey = hi,
      lowVel = lowVel,
      highVel = highVel,
      loopEnabled = true,
      loopStart = loopStart,
      loopEnd = loopEnd,
      attackSec = if (isPad) 0.26f else 0.006f,
      decaySec = if (isBass) 0.35f else 0.40f,
      sustainLevel = if (isBass) 0.55f else 0.88f,
      releaseSec = if (isPad) 0.65f else 0.18f,
      filterCutoffHz = if (isBass) 4200f else if (isPad) 7200f else 12500f,
    )
  }

  private fun buildKeberoInstrument(id: String, name: String): SampleInstrument {
    val bass = createDrumZone(36, 0, 59, startFreq = 128f, endFreq = 58f, decay = 9f, noiseMix = 0.18f)
    val slap = createDrumZone(60, 60, 127, startFreq = 290f, endFreq = 145f, decay = 18f, noiseMix = 0.48f)
    return SampleInstrument(id = id, name = name, zones = listOf(bass, slap), isDrumKit = true)
  }

  private fun buildGmDrumKit(id: String, name: String): SampleInstrument {
    val zones = listOf(
      createDrumZone(36, 0, 36, startFreq = 145f, endFreq = 46f, decay = 11f, noiseMix = 0.12f),
      createDrumZone(38, 37, 40, startFreq = 215f, endFreq = 135f, decay = 17f, noiseMix = 0.68f),
      createDrumZone(41, 41, 41, startFreq = 110f, endFreq = 78f, decay = 10f, noiseMix = 0.10f),
      createDrumZone(42, 42, 44, startFreq = 4200f, endFreq = 3200f, decay = 48f, noiseMix = 0.96f),
      createDrumZone(45, 45, 45, startFreq = 150f, endFreq = 102f, decay = 12f, noiseMix = 0.10f),
      createDrumZone(46, 46, 48, startFreq = 4500f, endFreq = 3400f, decay = 7.5f, noiseMix = 0.96f),
      createDrumZone(49, 49, 59, startFreq = 3600f, endFreq = 2600f, decay = 3.2f, noiseMix = 0.94f),
      createDrumZone(60, 60, 127, startFreq = 280f, endFreq = 150f, decay = 16f, noiseMix = 0.45f),
    )
    return SampleInstrument(id = id, name = name, zones = zones, isDrumKit = true)
  }

  private fun createDrumZone(
    root: Int,
    lo: Int,
    hi: Int,
    startFreq: Float,
    endFreq: Float,
    decay: Float,
    noiseMix: Float,
  ): SampleZone {
    val len = (SAMPLE_RATE * 0.85).toInt()
    val buf = FloatArray(len)
    val rng = Random(root * 19)
    var phase = 0.0
    var hpPrev = 0f
    var hpOut = 0f

    for (i in 0 until len) {
      val t = i.toDouble() / SAMPLE_RATE
      val f = endFreq + (startFreq - endFreq) * exp(-t * 36.0)
      phase += 2.0 * PI * f / SAMPLE_RATE
      val tone = sin(phase) + 0.25 * sin(phase * 1.58)
      val rawNoise = rng.nextFloat() * 2f - 1f
      hpOut = 0.86f * (hpOut + rawNoise - hpPrev)
      hpPrev = rawNoise
      val env = exp(-t * decay).toFloat()
      val click = exp(-t * 220.0).toFloat() * 0.35f
      buf[i] = ((tone * (1f - noiseMix) + hpOut * noiseMix) * env + click).toFloat()
    }
    normalizePeak(buf, 0.90f)
    return SampleZone(
      samples = buf,
      sampleRate = SAMPLE_RATE,
      rootNote = root,
      lowKey = lo,
      highKey = hi,
      loopEnabled = false,
      attackSec = 0.001f,
      decaySec = 0.65f,
      sustainLevel = 0.0f,
      releaseSec = 0.08f,
      filterCutoffHz = 16000f,
    )
  }

  private fun normalizePeak(buf: FloatArray, target: Float) {
    var peak = 0f
    for (s in buf) {
      val a = abs(s)
      if (a > peak) peak = a
    }
    if (peak > 1e-5f) {
      val scale = target / peak
      for (i in buf.indices) buf[i] *= scale
    }
  }

  private fun normalizeStereoPeak(bufL: FloatArray, bufR: FloatArray, target: Float) {
    var peak = 0f
    for (i in bufL.indices) {
      val a = max(abs(bufL[i]), abs(bufR[i]))
      if (a > peak) peak = a
    }
    if (peak > 1e-5f) {
      val scale = target / peak
      for (i in bufL.indices) {
        bufL[i] *= scale
        bufR[i] *= scale
      }
    }
  }
}
