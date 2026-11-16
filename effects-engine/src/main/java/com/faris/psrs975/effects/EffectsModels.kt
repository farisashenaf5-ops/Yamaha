package com.faris.psrs975.effects

enum class DspEffectType(val displayName: String) {
  NONE("Bypass"),
  DISTORTION("Overdrive / Distortion"),
  PHASER("Studio Phaser"),
  FLANGER("Jet Flanger"),
  ROTARY_SPEAKER("Rotary Speaker"),
  STEREO_DELAY("Stereo Ping-Pong"),
  COMPRESSOR("Channel Comp"),
  EQ_PRESENCE("Presence EQ"),
}

data class ReverbSettings(
  val enabled: Boolean = true,
  val roomSize: Float = 0.58f,
  val damping: Float = 0.42f,
  val level: Float = 0.32f,
)

data class ChorusSettings(
  val enabled: Boolean = true,
  val rateHz: Float = 0.85f,
  val depth: Float = 0.45f,
  val level: Float = 0.25f,
)

data class DelaySettings(
  val enabled: Boolean = false,
  val timeMs: Float = 320f,
  val rightTimeMs: Float = 440f,
  val feedback: Float = 0.35f,
  val stereoPingPong: Boolean = true,
  val level: Float = 0.28f,
)

data class CompressorSettings(
  val enabled: Boolean = true,
  val thresholdDb: Float = -14f,
  val ratio: Float = 3.2f,
  val attackMs: Float = 8f,
  val releaseMs: Float = 110f,
  val makeupGainDb: Float = 2.0f,
)

data class EqSettings(
  val enabled: Boolean = true,
  val lowGainDb: Float = 1.5f,
  val midGainDb: Float = 0.5f,
  val midFreqHz: Float = 1200f,
  val highGainDb: Float = 2.0f,
)

data class DspInsertSettings(
  val type: DspEffectType = DspEffectType.ROTARY_SPEAKER,
  val enabled: Boolean = false,
  val drive: Float = 0.45f,
  val rateHz: Float = 1.6f,
  val depth: Float = 0.65f,
  val feedback: Float = 0.4f,
  val mix: Float = 0.5f,
)

data class EffectsRackState(
  val reverb: ReverbSettings = ReverbSettings(),
  val chorus: ChorusSettings = ChorusSettings(),
  val delay: DelaySettings = DelaySettings(),
  val insertDsp: DspInsertSettings = DspInsertSettings(),
  val insertEq: EqSettings = EqSettings(enabled = false, lowGainDb = 0f, midGainDb = 0f, highGainDb = 0f),
  val insertCompressor: CompressorSettings = CompressorSettings(enabled = false),
  val masterEq: EqSettings = EqSettings(),
  val masterCompressor: CompressorSettings = CompressorSettings(),
)
