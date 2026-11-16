package com.faris.psrs975.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class AudioEngineTest {

  private fun createTestInstrument(freqHz: Float = 440f): SampleInstrument {
    val sr = 44100
    val len = 4096
    val samples = FloatArray(len) { i ->
      sin(2.0 * PI * freqHz * i / sr).toFloat() * 0.8f
    }
    return SampleInstrument(
      id = "test_tone",
      name = "Test Tone",
      zones = listOf(
        SampleZone(
          samples = samples,
          sampleRate = sr,
          rootNote = 69,
          loopEnabled = true,
          loopStart = 200,
          loopEnd = 3800,
          attackSec = 0.002f,
          decaySec = 0.1f,
          sustainLevel = 0.8f,
          releaseSec = 0.05f,
        ),
      ),
    )
  }

  @Test
  fun `polyphonic playback renders non-silent stereo PCM and respects sustain and panic`() {
    val engine = AudioEngine(context = null, initialSampleRate = 44100)
    engine.setPartInstrument(WorkstationPart.RIGHT_1, createTestInstrument())

    // Trigger 3 simultaneous notes (C major triad)
    engine.noteOn(WorkstationPart.RIGHT_1, 60, 100)
    engine.noteOn(WorkstationPart.RIGHT_1, 64, 100)
    engine.noteOn(WorkstationPart.RIGHT_1, 67, 100)

    var peak = 0
    repeat(6) {
      val block = engine.renderPolyphonicBlock(256)
      for (s in block) {
        val a = abs(s.toInt())
        if (a > peak) peak = a
      }
    }
    assertTrue("Expected audible polyphonic PCM output, got peak=$peak", peak > 500)

    // Sustain pedal holds released notes
    engine.setSustainPedal(WorkstationPart.RIGHT_1, true)
    engine.noteOff(WorkstationPart.RIGHT_1, 60)
    engine.noteOff(WorkstationPart.RIGHT_1, 64)
    engine.noteOff(WorkstationPart.RIGHT_1, 67)
    val sustainedBlock = engine.renderPolyphonicBlock(256)
    val sustainedPeak = sustainedBlock.maxOf { abs(it.toInt()) }
    assertTrue("Expected sustained notes to remain audible, got $sustainedPeak", sustainedPeak > 200)

    // Panic silences all voices immediately
    engine.panic()
    val silentBlock = engine.renderPolyphonicBlock(256)
    val silentPeak = silentBlock.maxOf { abs(it.toInt()) }
    assertEquals(0, silentPeak)

    engine.release()
  }

  @Test
  fun `stream lifecycle initializes callback thread and switches sample rate cleanly`() {
    val engine = AudioEngine(context = null, initialSampleRate = 44100)
    var callbackInvoked = false
    engine.setRenderCallback { outPcm16, frames, sr ->
      assertEquals(256, frames)
      assertTrue(sr == 44100 || sr == 48000)
      outPcm16.fill(100)
      callbackInvoked = true
      true
    }

    assertTrue(engine.start())
    Thread.sleep(40)
    assertTrue("Expected real-time audio callback to be invoked", callbackInvoked)

    engine.setSampleRate(48000)
    assertEquals(48000, engine.streamInfo.value.sampleRate)

    engine.stop()
    assertFalse(engine.streamInfo.value.isRunning)

    engine.release()
    assertFalse(engine.start())
  }
}
