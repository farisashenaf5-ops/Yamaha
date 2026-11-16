package com.faris.psrs975.audio

enum class WorkstationPart(val channelIndex: Int, val label: String) {
  RIGHT_1(0, "Right 1"),
  RIGHT_2(1, "Right 2"),
  LEFT(2, "Left"),
  RHYTHM_1(3, "Rhythm 1"),
  RHYTHM_2(4, "Rhythm 2"),
  BASS(5, "Bass"),
  CHORD_1(6, "Chord 1"),
  CHORD_2(7, "Chord 2"),
  PAD(8, "Pad"),
  PHRASE_1(9, "Phrase 1"),
  PHRASE_2(10, "Phrase 2"),
  MULTI_PAD(11, "Multi Pad"),
  SONG_MIDI(12, "Song MIDI"),
}

data class SampleZone(
  val samples: FloatArray,
  val rightSamples: FloatArray? = null,
  val sampleRate: Int = 44100,
  val rootNote: Int = 60,
  val lowKey: Int = 0,
  val highKey: Int = 127,
  val lowVel: Int = 0,
  val highVel: Int = 127,
  val loopEnabled: Boolean = false,
  val loopStart: Int = 0,
  val loopEnd: Int = 0,
  val fineTuneCents: Float = 0f,
  val attackSec: Float = 0.005f,
  val decaySec: Float = 0.35f,
  val sustainLevel: Float = 0.7f,
  val releaseSec: Float = 0.25f,
  val filterCutoffHz: Float = 16000f,
  val baseGain: Float = 1.0f,
  val basePan: Float = 0.0f,
)

data class SampleInstrument(
  val id: String,
  val name: String,
  val zones: List<SampleZone>,
  val isDrumKit: Boolean = false,
) {
  fun findZone(midiNote: Int, velocity: Int): SampleZone? {
    var fallback: SampleZone? = null
    for (z in zones) {
      if (midiNote in z.lowKey..z.highKey) {
        if (velocity in z.lowVel..z.highVel) return z
        if (fallback == null) fallback = z
      }
    }
    return fallback ?: if (isDrumKit) null else zones.firstOrNull()
  }
}

data class PartChannelMix(
  val volume: Float = 0.85f,
  val pan: Float = 0.0f,
  val reverbSend: Float = 0.28f,
  val chorusSend: Float = 0.15f,
  val dspSend: Float = 0.0f,
  val muted: Boolean = false,
  val soloed: Boolean = false,
  val adsrAttackScale: Float = 1.0f,
  val adsrReleaseScale: Float = 1.0f,
  val brightnessScale: Float = 1.0f,
)
