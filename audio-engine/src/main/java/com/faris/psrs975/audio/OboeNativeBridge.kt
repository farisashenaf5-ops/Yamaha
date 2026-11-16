package com.faris.psrs975.audio

internal object OboeNativeBridge {
  val isNativeLoaded: Boolean = try {
    System.loadLibrary("psr_oboe_audio")
    true
  } catch (_: Throwable) {
    false
  }

  external fun nativeCreate(): Long
  external fun nativeDestroy(handle: Long)
  external fun nativeStart(handle: Long, sampleRate: Int, burstMultiplier: Int): Boolean
  external fun nativeStop(handle: Long)
  external fun nativeSetSampleRate(handle: Long, sampleRate: Int): Boolean

  external fun nativeRegisterSampleZone(
    handle: Long,
    slotIndex: Int,
    leftSamples: FloatArray,
    rightSamples: FloatArray?,
    sampleRate: Int,
    rootNote: Int,
    loopEnabled: Boolean,
    loopStart: Int,
    loopEnd: Int,
    fineTuneCents: Float,
    attackSec: Float,
    decaySec: Float,
    sustainLevel: Float,
    releaseSec: Float,
    filterCutoffHz: Float,
    baseGain: Float,
    basePan: Float,
    isDrum: Boolean,
  ): Int

  external fun nativeNoteOn(
    handle: Long,
    part: Int,
    zoneIndex: Int,
    midiNote: Int,
    velocity: Int,
    attackScale: Float,
    releaseScale: Float,
    brightnessScale: Float,
  )

  external fun nativeNoteOff(handle: Long, part: Int, midiNote: Int)
  external fun nativeSetSustainPedal(handle: Long, part: Int, down: Boolean)
  external fun nativeSetPartMix(
    handle: Long,
    part: Int,
    volume: Float,
    pan: Float,
    muted: Boolean,
    soloed: Boolean,
  )

  external fun nativeSetPitchBend(handle: Long, part: Int, semitones: Float)
  external fun nativeSetModulation(handle: Long, part: Int, depth: Float)
  external fun nativeSetMasterGainAndBalance(handle: Long, masterVolume: Float, balance: Float)
  external fun nativeAllNotesOff(handle: Long, part: Int)
  external fun nativePanic(handle: Long)

  external fun nativeWriteStereoPcm16(
    handle: Long,
    interleavedPcm16: ShortArray,
    frames: Int,
  ): Int

  external fun nativeAvailablePcmRingFrames(handle: Long): Int

  external fun nativeQueryStats(
    handle: Long,
    outInts: IntArray,
    outFloats: FloatArray,
  )
}
