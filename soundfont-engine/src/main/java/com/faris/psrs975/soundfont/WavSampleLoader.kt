package com.faris.psrs975.soundfont

import com.faris.psrs975.audio.SampleInstrument
import com.faris.psrs975.audio.SampleZone
import com.faris.psrs975.audio.WavCodec
import java.io.InputStream

object WavSampleLoader {
  fun loadSingleSample(
    input: InputStream,
    id: String,
    name: String,
    rootNote: Int = 60,
  ): SampleInstrument {
    val wav = WavCodec.decode(input)
    return fromDecoded(id, name, wav, rootNote)
  }

  fun fromDecodedBytes(
    bytes: ByteArray,
    id: String,
    name: String,
    rootNote: Int = 60,
  ): SampleInstrument {
    val wav = WavCodec.decodeBytes(bytes)
    return fromDecoded(id, name, wav, rootNote)
  }

  private fun fromDecoded(
    id: String,
    name: String,
    wav: com.faris.psrs975.audio.DecodedWav,
    rootNote: Int,
  ): SampleInstrument {
    val zone = SampleZone(
      samples = wav.left,
      rightSamples = wav.right,
      sampleRate = wav.sampleRate,
      rootNote = rootNote,
      lowKey = 0,
      highKey = 127,
      loopEnabled = wav.hasLoop,
      loopStart = wav.loopStart,
      loopEnd = wav.loopEnd,
      attackSec = 0.005f,
      decaySec = 0.4f,
      sustainLevel = 0.8f,
      releaseSec = 0.35f,
    )
    return SampleInstrument(id = id, name = name, zones = listOf(zone))
  }
}
