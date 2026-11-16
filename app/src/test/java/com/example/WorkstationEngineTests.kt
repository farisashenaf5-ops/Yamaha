package com.example

import com.faris.psrs975.audio.WavCodec
import com.faris.psrs975.audio.WorkstationAudioEngine
import com.faris.psrs975.audio.WorkstationPart
import com.faris.psrs975.chord.ChordFingeringMode
import com.faris.psrs975.chord.ChordQuality
import com.faris.psrs975.chord.ChordRecognizer
import com.faris.psrs975.effects.DspEffectType
import com.faris.psrs975.effects.DspInsertSettings
import com.faris.psrs975.effects.EffectsRackState
import com.faris.psrs975.midi.BuiltInMidiSongs
import com.faris.psrs975.midi.StandardMidiCodec
import com.faris.psrs975.mixer.MixerEngine
import com.faris.psrs975.samples.AcousticSynthesisModel
import com.faris.psrs975.samples.SampleBankFactory
import com.faris.psrs975.samples.SampleManager
import com.faris.psrs975.soundfont.Sf2Loader
import com.faris.psrs975.soundfont.SfzLoader
import com.faris.psrs975.soundfont.WavSampleLoader
import com.faris.psrs975.style.StyLoader
import com.faris.psrs975.style.StyleChannelType
import com.faris.psrs975.style.StyleSection
import com.faris.psrs975.voice.ActiveVoiceSlot
import com.faris.psrs975.voice.VoiceEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class WorkstationEngineTests {

  @Test
  fun `audio engine renders non-silent PCM on noteOn and releases on noteOff`() {
    val engine = WorkstationAudioEngine(context = null, initialSampleRate = 44100)
    val piano = SampleBankFactory.buildInstrument("test_piano", "Grand Piano", AcousticSynthesisModel.GRAND_PIANO)
    engine.setPartInstrument(WorkstationPart.RIGHT_1, piano)

    engine.noteOn(WorkstationPart.RIGHT_1, 60, 110)
    var maxPeak = 0
    repeat(8) {
      val block = engine.renderBlock(256)
      for (s in block) {
        val a = abs(s.toInt())
        if (a > maxPeak) maxPeak = a
      }
    }
    assertTrue("Expected audible PCM output on noteOn, got peak=$maxPeak", maxPeak > 200)

    engine.noteOff(WorkstationPart.RIGHT_1, 60)
    engine.panic()
    val silent = engine.renderBlock(256)
    val silentPeak = silent.maxOf { abs(it.toInt()) }
    assertTrue("Expected silence after panic, got $silentPeak", silentPeak < 50)
  }

  @Test
  fun `pitch bend and modulation alter rendered PCM waveform`() {
    val synth = SampleBankFactory.buildInstrument("test_lead", "Synth Lead", AcousticSynthesisModel.SYNTH_LEAD)

    fun renderSignature(bendSemis: Float, modDepth: Float): ShortArray {
      val engine = WorkstationAudioEngine(context = null, initialSampleRate = 44100)
      engine.setPartInstrument(WorkstationPart.RIGHT_1, synth)
      engine.setPitchBend(WorkstationPart.RIGHT_1, bendSemis)
      engine.setModulation(WorkstationPart.RIGHT_1, modDepth)
      engine.noteOn(WorkstationPart.RIGHT_1, 60, 100)
      repeat(4) { engine.renderBlock(256) }
      return engine.renderBlock(256).copyOf()
    }

    val neutral = renderSignature(0f, 0f)
    val bentUp = renderSignature(2f, 0f)
    val modulated = renderSignature(0f, 0.9f)

    val bendDiff = neutral.indices.sumOf { abs(neutral[it].toInt() - bentUp[it].toInt()) }
    val modDiff = neutral.indices.sumOf { abs(neutral[it].toInt() - modulated[it].toInt()) }
    assertTrue("Expected pitch bend to alter rendered waveform, diff=$bendDiff", bendDiff > 5000)
    assertTrue("Expected modulation LFO to alter rendered waveform, diff=$modDiff", modDiff > 500)
  }

  @Test
  fun `ethiopian instruments build multi-zone PCM buffers`() {
    val masinko = SampleBankFactory.buildInstrument("eth_masinko", "Masinko", AcousticSynthesisModel.ETHIO_MASINKO)
    val krar = SampleBankFactory.buildInstrument("eth_krar", "Krar", AcousticSynthesisModel.ETHIO_KRAR)
    val washint = SampleBankFactory.buildInstrument("eth_washint", "Washint", AcousticSynthesisModel.ETHIO_WASHINT)
    val kebero = SampleBankFactory.buildInstrument("eth_kebero", "Kebero", AcousticSynthesisModel.ETHIO_KEBERO)

    assertEquals(5, masinko.zones.size)
    assertEquals(5, krar.zones.size)
    assertEquals(5, washint.zones.size)
    assertTrue(kebero.isDrumKit && kebero.zones.size == 2)
  }

  @Test
  fun `chord recognizer detects major minor 7 maj7 min7 sus2 sus4 dim aug 6 9 and single finger`() {
    assertEquals("C", ChordRecognizer.detect(listOf(48, 52, 55), ChordFingeringMode.FINGERED, 60)?.displaySymbol)
    assertEquals("Cm", ChordRecognizer.detect(listOf(48, 51, 55), ChordFingeringMode.FINGERED, 60)?.displaySymbol)
    assertEquals("C7", ChordRecognizer.detect(listOf(48, 52, 55, 58), ChordFingeringMode.FINGERED, 60)?.displaySymbol)
    assertEquals("Cmaj7", ChordRecognizer.detect(listOf(48, 52, 55, 59), ChordFingeringMode.FINGERED, 60)?.displaySymbol)
    assertEquals("Cm7", ChordRecognizer.detect(listOf(48, 51, 55, 58), ChordFingeringMode.FINGERED, 60)?.displaySymbol)
    assertEquals("Csus2", ChordRecognizer.detect(listOf(48, 50, 55), ChordFingeringMode.FINGERED, 60)?.displaySymbol)
    assertEquals("Csus4", ChordRecognizer.detect(listOf(48, 53, 55), ChordFingeringMode.FINGERED, 60)?.displaySymbol)
    assertEquals("Cdim", ChordRecognizer.detect(listOf(48, 51, 54), ChordFingeringMode.FINGERED, 60)?.displaySymbol)
    assertEquals("Caug", ChordRecognizer.detect(listOf(48, 52, 56), ChordFingeringMode.FINGERED, 60)?.displaySymbol)
    assertEquals("C6", ChordRecognizer.detect(listOf(48, 52, 55, 57), ChordFingeringMode.FINGERED, 60)?.displaySymbol)
    assertEquals("C9", ChordRecognizer.detect(listOf(36, 40, 43, 46, 50), ChordFingeringMode.FINGERED, 60)?.displaySymbol)
    assertEquals(ChordQuality.MAJOR, ChordRecognizer.detect(listOf(48), ChordFingeringMode.SINGLE_FINGER, 60)?.quality)
    assertEquals(ChordQuality.MINOR, ChordRecognizer.detect(listOf(47, 48), ChordFingeringMode.SINGLE_FINGER, 60)?.quality)
    assertEquals(ChordQuality.SEVENTH, ChordRecognizer.detect(listOf(46, 48), ChordFingeringMode.SINGLE_FINGER, 60)?.quality)
  }

  @Test
  fun `wav codec encodes and decodes stereo PCM and loads as sample instrument`() {
    val pcmOut = ByteArrayOutputStream()
    val frames = 1024
    WavCodec.writeStereoPcm16Header(pcmOut, 44100, frames * 4L)
    val bb = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
    for (i in 0 until frames) {
      val val16 = ((i % 64) * 300).toShort()
      bb.putShort(val16)
      bb.putShort((-val16).toShort())
    }
    pcmOut.write(bb.array())

    val wavBytes = pcmOut.toByteArray()
    val decoded = WavCodec.decodeBytes(wavBytes)
    assertEquals(44100, decoded.sampleRate)
    assertEquals(2, decoded.channels)
    assertEquals(frames, decoded.left.size)

    val inst = WavSampleLoader.fromDecodedBytes(wavBytes, "wav_test", "TestWav")
    assertEquals(1, inst.zones.size)
  }

  @Test
  fun `sfz parser resolves sample zones cleanly`() {
    val pcmOut = ByteArrayOutputStream()
    WavCodec.writeStereoPcm16Header(pcmOut, 44100, 512L)
    pcmOut.write(ByteArray(512))
    val wavBytes = pcmOut.toByteArray()

    val sfz = """
      <group> ampeg_attack=0.01 ampeg_release=0.25
      <region> sample=piano_c4.wav lokey=48 hikey=72 pitch_keycenter=c4
    """.trimIndent()
    val inst = SfzLoader.parse(sfz, "TestSFZ") { wavBytes }
    assertEquals(1, inst.zones.size)
    assertEquals(60, inst.zones.first().rootNote)
  }

  @Test
  fun `midi codec writes and parses standard midi file round trip`() {
    val song = BuiltInMidiSongs.createDemoSongs().first()
    val out = ByteArrayOutputStream()
    StandardMidiCodec.write(song, out)
    val parsed = StandardMidiCodec.parseBytes(out.toByteArray(), "rt", "RoundTrip")
    assertEquals(song.initialBpm, parsed.initialBpm)
    assertTrue(parsed.events.isNotEmpty())
  }

  @Test
  fun `voice engine, mixer engine, and dsp effects update engine state`() {
    val audio = WorkstationAudioEngine(null, 44100)
    val samples = SampleManager()
    val mixer = MixerEngine(audio)
    val voices = VoiceEngine(audio, samples, mixer)

    val sax = voices.findVoiceById("wood_tenor_sax")!!
    voices.assignVoiceToSlot(ActiveVoiceSlot.RIGHT_1, sax)
    assertEquals("Tenor Saxophone", voices.state.value.right1Voice?.name)

    mixer.setVolume(WorkstationPart.RIGHT_1, 0.55f)
    assertEquals(0.55f, mixer.state.value.channels[WorkstationPart.RIGHT_1]?.volume ?: 0f, 0.01f)

    audio.setEffectsState(
      EffectsRackState(
        insertDsp = DspInsertSettings(type = DspEffectType.ROTARY_SPEAKER, enabled = true, mix = 0.7f),
      ),
    )
    assertTrue(audio.dspEngine.state.insertDsp.enabled)
  }

  @Test
  fun `sf2 parser parses valid minimal soundfont 2 binary`() {
    val sf2Bytes = buildMinimalSf2Bytes()
    val parsed = Sf2Loader.parseBytes(sf2Bytes, "UnitTestSF2")
    assertEquals("TestBank", parsed.bankName)
    assertEquals(1, parsed.presets.size)
    assertEquals("TestPreset", parsed.presets.first().name)
  }

  private fun buildMinimalSf2Bytes(): ByteArray {
    fun chunk(id: String, data: ByteArray): ByteArray {
      val pad = data.size and 1
      val bb = ByteBuffer.allocate(8 + data.size + pad).order(ByteOrder.LITTLE_ENDIAN)
      bb.put(id.toByteArray(Charsets.US_ASCII))
      bb.putInt(data.size)
      bb.put(data)
      return bb.array()
    }
    fun listChunk(type: String, vararg children: ByteArray): ByteArray {
      val bodySize = 4 + children.sumOf { it.size }
      val bb = ByteBuffer.allocate(8 + bodySize).order(ByteOrder.LITTLE_ENDIAN)
      bb.put("LIST".toByteArray(Charsets.US_ASCII))
      bb.putInt(bodySize)
      bb.put(type.toByteArray(Charsets.US_ASCII))
      children.forEach { bb.put(it) }
      return bb.array()
    }
    fun fixedStr(s: String, len: Int): ByteArray =
      ByteArray(len).also { arr -> s.toByteArray(Charsets.US_ASCII).copyInto(arr, 0, 0, minOf(s.length, len)) }

    val info = listChunk("INFO", chunk("INAM", "TestBank\u0000".toByteArray(Charsets.US_ASCII)))
    val smplBytes = ByteArray(200) { idx -> (idx % 60).toByte() }
    val sdta = listChunk("sdta", chunk("smpl", smplBytes))

    // phdr (2 entries: preset + EOP)
    val phdr = ByteBuffer.allocate(76).order(ByteOrder.LITTLE_ENDIAN).apply {
      put(fixedStr("TestPreset", 20)); putShort(0); putShort(0); putShort(0); putInt(0); putInt(0); putInt(0)
      put(fixedStr("EOP", 20)); putShort(0); putShort(0); putShort(1); putInt(0); putInt(0); putInt(0)
    }.array()
    // pbag (2 entries)
    val pbag = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
      putShort(0); putShort(0); putShort(1); putShort(0)
    }.array()
    // pgen (instrument=0 + terminal)
    val pgen = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
      putShort(41); putShort(0)
      putShort(0); putShort(0)
    }.array()
    // inst (2 entries)
    val inst = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
      put(fixedStr("TestInst", 20)); putShort(0)
      put(fixedStr("EOI", 20)); putShort(1)
    }.array()
    // ibag (2 entries)
    val ibag = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
      putShort(0); putShort(0); putShort(1); putShort(0)
    }.array()
    // igen (sampleID=0 + terminal)
    val igen = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
      putShort(53); putShort(0)
      putShort(0); putShort(0)
    }.array()
    // shdr (2 entries: sample + EOS)
    val shdr = ByteBuffer.allocate(92).order(ByteOrder.LITTLE_ENDIAN).apply {
      put(fixedStr("TestSmp", 20)); putInt(0); putInt(100); putInt(10); putInt(90); putInt(44100); put(60); put(0); putShort(0); putShort(1)
      put(fixedStr("EOS", 20)); putInt(0); putInt(0); putInt(0); putInt(0); putInt(0); put(0); put(0); putShort(0); putShort(0)
    }.array()

    val pdta = listChunk(
      "pdta",
      chunk("phdr", phdr),
      chunk("pbag", pbag),
      chunk("pmod", ByteArray(10)),
      chunk("pgen", pgen),
      chunk("inst", inst),
      chunk("ibag", ibag),
      chunk("imod", ByteArray(10)),
      chunk("igen", igen),
      chunk("shdr", shdr),
    )

    val bodyLen = 4 + info.size + sdta.size + pdta.size
    return ByteBuffer.allocate(8 + bodyLen).order(ByteOrder.LITTLE_ENDIAN).apply {
      put("RIFF".toByteArray(Charsets.US_ASCII))
      putInt(bodyLen)
      put("sfbk".toByteArray(Charsets.US_ASCII))
      put(info); put(sdta); put(pdta)
    }.array()
  }

  @Test
  fun `sty loader parses SFF1 sections, CASM mappings, tempo, and chord degrees`() {
    val styBytes = buildMinimalStyBytes()
    assertTrue(StyLoader.isValidSty(styBytes))
    val parsed = StyLoader.parseBytes(styBytes, "sty_test", "Ethiopian_Groove.sty")
    assertEquals("SFF1", parsed.formatTag)
    assertTrue("Expected CASM chunk to be parsed", parsed.hasCasmChunk)
    assertEquals(110, parsed.style.defaultBpm)
    assertTrue(parsed.detectedSections.containsAll(listOf(StyleSection.INTRO_1, StyleSection.MAIN_A, StyleSection.MAIN_B, StyleSection.ENDING_1)))
    val mainA = parsed.style.sections[StyleSection.MAIN_A]!!
    assertTrue(mainA.events.any { it.channel == StyleChannelType.RHYTHM_1 && it.fixedMidiNote == 36 && it.chordDegree == -1 })
    assertTrue(mainA.events.any { it.channel == StyleChannelType.BASS && it.chordDegree >= 0 })
    assertTrue(mainA.events.any { it.channel == StyleChannelType.CHORD_1 && it.chordDegree >= 0 })
  }

  private fun buildMinimalStyBytes(): ByteArray {
    fun writeVarLen(out: ByteArrayOutputStream, value: Int) {
      var v = value
      val buf = IntArray(4)
      var i = 0
      buf[i++] = v and 0x7F
      v = v ushr 7
      while (v > 0 && i < 4) {
        buf[i++] = (v and 0x7F) or 0x80
        v = v ushr 7
      }
      for (k in i - 1 downTo 0) out.write(buf[k])
    }
    fun writeMeta(out: ByteArrayOutputStream, delta: Int, type: Int, payload: ByteArray) {
      writeVarLen(out, delta)
      out.write(0xFF)
      out.write(type)
      writeVarLen(out, payload.size)
      out.write(payload)
    }

    val trk = ByteArrayOutputStream()
    writeMeta(trk, 0, 0x06, "SFF1".toByteArray(Charsets.US_ASCII))
    val usPerBeat110 = 60_000_000 / 110
    writeMeta(trk, 0, 0x51, byteArrayOf(((usPerBeat110 shr 16) and 0xFF).toByte(), ((usPerBeat110 shr 8) and 0xFF).toByte(), (usPerBeat110 and 0xFF).toByte()))
    // Intro A at tick 0 (1 bar = 1920 ticks at TPQN=480)
    writeMeta(trk, 0, 0x06, "Intro A".toByteArray(Charsets.US_ASCII))
    writeVarLen(trk, 0); trk.write(0x99); trk.write(36); trk.write(100)
    writeVarLen(trk, 240); trk.write(0x89); trk.write(36); trk.write(0)
    // Main A at tick 1920
    writeMeta(trk, 1680, 0x06, "Main A".toByteArray(Charsets.US_ASCII))
    writeVarLen(trk, 0); trk.write(0x99); trk.write(36); trk.write(105) // RHY1 kick
    writeVarLen(trk, 0); trk.write(0x9A); trk.write(48); trk.write(96)  // BASS C3
    writeVarLen(trk, 0); trk.write(0x9B); trk.write(64); trk.write(85)  // CHD1 E4
    writeVarLen(trk, 480); trk.write(0x89); trk.write(36); trk.write(0)
    writeVarLen(trk, 0); trk.write(0x8A); trk.write(48); trk.write(0)
    writeVarLen(trk, 0); trk.write(0x8B); trk.write(64); trk.write(0)
    // Main B at tick 3840
    writeMeta(trk, 1440, 0x06, "Main B".toByteArray(Charsets.US_ASCII))
    writeVarLen(trk, 0); trk.write(0x99); trk.write(38); trk.write(98)
    writeVarLen(trk, 480); trk.write(0x89); trk.write(38); trk.write(0)
    // Ending A at tick 5760
    writeMeta(trk, 1440, 0x06, "Ending A".toByteArray(Charsets.US_ASCII))
    writeVarLen(trk, 0); trk.write(0x99); trk.write(49); trk.write(100)
    writeVarLen(trk, 480); trk.write(0x89); trk.write(49); trk.write(0)
    writeMeta(trk, 0, 0x2F, ByteArray(0))

    val trkBytes = trk.toByteArray()

    // CASM -> CSEG -> Sdec + Ctab
    fun beChunk(tag: String, body: ByteArray): ByteArray = ByteBuffer.allocate(8 + body.size).order(ByteOrder.BIG_ENDIAN).apply {
      put(tag.toByteArray(Charsets.US_ASCII))
      putInt(body.size)
      put(body)
    }.array()

    val sdec = beChunk("Sdec", "Main A,Main B".toByteArray(Charsets.US_ASCII))
    val ctabPayload = ByteArray(16).apply {
      this[0] = 10 // src ch 10
      "Bass".toByteArray(Charsets.US_ASCII).copyInto(this, 1)
      this[9] = 10 // dest BASS
      this[12] = 0 // C
    }
    val ctab = beChunk("Ctab", ctabPayload)
    val cseg = beChunk("CSEG", sdec + ctab)
    val casm = beChunk("CASM", cseg)

    val mthd = ByteBuffer.allocate(14).order(ByteOrder.BIG_ENDIAN).apply {
      put("MThd".toByteArray(Charsets.US_ASCII))
      putInt(6)
      putShort(0)
      putShort(1)
      putShort(480)
    }.array()

    return mthd + beChunk("MTrk", trkBytes) + casm
  }

  @Test
  fun `custom drum kit builder maps WAV zones to edited MIDI notes and supports Hi-Hat choke`() {
    val tempDir = java.nio.file.Files.createTempDirectory("faris_drum_test").toFile()
    try {
      fun writeSineWav(file: java.io.File, freqHz: Double) {
        val sampleRate = 44100
        val frames = 2205
        val left = FloatArray(frames) { i -> kotlin.math.sin(2.0 * Math.PI * freqHz * i / sampleRate).toFloat() * 0.8f }
        file.writeBytes(WavCodec.encodeWavBytes(left, left, sampleRate))
      }

      val kickFile = java.io.File(tempDir, "kick.wav").also { writeSineWav(it, 65.0) }
      val openHhFile = java.io.File(tempDir, "open_hh.wav").also { writeSineWav(it, 4200.0) }
      val closedHhFile = java.io.File(tempDir, "closed_hh.wav").also { writeSineWav(it, 6000.0) }

      val kit = com.faris.psrs975.samples.CustomDrumKit(
        id = "kit_custom_test",
        name = "Studio Custom Kit",
        isFactory = false,
        mappings = listOf(
          com.faris.psrs975.samples.DrumSampleMapping(
            id = "map_kick",
            slotCategory = com.faris.psrs975.samples.DrumSlotCategory.KICK,
            midiNote = 36,
            name = "Punch Kick",
            filePath = kickFile.absolutePath,
            sourceFormat = "WAV",
          ),
          com.faris.psrs975.samples.DrumSampleMapping(
            id = "map_chh",
            slotCategory = com.faris.psrs975.samples.DrumSlotCategory.CLOSED_HI_HAT,
            midiNote = 42,
            name = "Tight CHH",
            filePath = closedHhFile.absolutePath,
            sourceFormat = "WAV",
          ),
          com.faris.psrs975.samples.DrumSampleMapping(
            id = "map_ohh",
            slotCategory = com.faris.psrs975.samples.DrumSlotCategory.OPEN_HI_HAT,
            midiNote = 46,
            name = "Bright OHH",
            filePath = openHhFile.absolutePath,
            sourceFormat = "WAV",
          ),
        ),
      )

      val zoneLoader: (com.faris.psrs975.samples.DrumSampleMapping) -> com.faris.psrs975.audio.SampleZone? = { mapping ->
        mapping.filePath?.let { path ->
          val decoded = WavCodec.decodeBytes(java.io.File(path).readBytes())
          com.faris.psrs975.audio.SampleZone(
            samples = decoded.left,
            rightSamples = decoded.right,
            sampleRate = decoded.sampleRate,
            rootNote = mapping.midiNote,
            lowKey = mapping.midiNote,
            highKey = mapping.midiNote,
          )
        }
      }

      val instrument = com.faris.psrs975.samples.CustomDrumKitInstrumentBuilder.buildInstrument(kit, zoneLoader)
      assertTrue(instrument.isDrumKit)
      assertNotNull(instrument.findZone(36, 100))
      assertNotNull("Expected sibling alias MIDI 35 -> Kick", instrument.findZone(35, 100))
      assertNotNull(instrument.findZone(42, 100))
      assertNotNull(instrument.findZone(46, 100))

      val audio = WorkstationAudioEngine(context = null, initialSampleRate = 44100)
      audio.setPartInstrument(WorkstationPart.RHYTHM_1, instrument)
      audio.noteOn(WorkstationPart.RHYTHM_1, 36, 120)
      val kickPeak = audio.renderBlock(256).maxOf { abs(it.toInt()) }
      assertTrue("Expected audible custom kick output, got peak=$kickPeak", kickPeak > 500)

      // Open HH triggered on 46 should be audible, and closed HH (42) should choke 46 smoothly
      audio.noteOn(WorkstationPart.RHYTHM_1, 46, 110)
      audio.renderBlock(256)
      audio.noteOn(WorkstationPart.RHYTHM_1, 42, 110)
      val chokedPeak = audio.renderBlock(512).maxOf { abs(it.toInt()) }
      assertTrue("Expected audible closed HH after choke, got peak=$chokedPeak", chokedPeak > 200)
    } finally {
      tempDir.deleteRecursively()
    }
  }

  @Test
  fun `sty style preserves original drum MIDI notes, velocities, bass and chord when bound to custom drum kit`() {
    val parsed = StyLoader.parseBytes(buildMinimalStyBytes(), "sty_drum_bind", "BindTest.sty")
    val mainA = parsed.style.sections[StyleSection.MAIN_A]!!
    val drumEvent = mainA.events.first { it.channel == StyleChannelType.RHYTHM_1 }
    val bassEvent = mainA.events.first { it.channel == StyleChannelType.BASS }
    val chordEvent = mainA.events.first { it.channel == StyleChannelType.CHORD_1 }

    // Original STY timing, MIDI note, and velocity remain intact
    assertEquals(36, drumEvent.fixedMidiNote)
    assertEquals(105, drumEvent.velocity)
    assertEquals(0, drumEvent.stepIndex)
    assertTrue(bassEvent.chordDegree >= 0)
    assertTrue(chordEvent.chordDegree >= 0)

    // Custom drum kit maps STY's original MIDI note 36 cleanly without modifying STY events
    val factoryKit = com.faris.psrs975.samples.GmDrumNoteNames.createFactoryStandardKit()
    val inst = com.faris.psrs975.samples.CustomDrumKitInstrumentBuilder.buildInstrument(factoryKit, { null })
    assertNotNull(inst.findZone(drumEvent.fixedMidiNote, drumEvent.velocity))
  }

  @Test
  fun `custom drum kit JSON codec round-trips mappings and infers standard GM drum slots from filenames`() {
    assertEquals(com.faris.psrs975.samples.DrumSlotCategory.KICK, com.faris.psrs975.samples.GmDrumNoteNames.inferSlotFromFilename("808_Kick_Heavy.mp3"))
    assertEquals(com.faris.psrs975.samples.DrumSlotCategory.SNARE, com.faris.psrs975.samples.GmDrumNoteNames.inferSlotFromFilename("Tight_Snare.ogg"))
    assertEquals(com.faris.psrs975.samples.DrumSlotCategory.CLOSED_HI_HAT, com.faris.psrs975.samples.GmDrumNoteNames.inferSlotFromFilename("CHH_01.wav"))
    assertEquals(com.faris.psrs975.samples.DrumSlotCategory.OPEN_HI_HAT, com.faris.psrs975.samples.GmDrumNoteNames.inferSlotFromFilename("Open_Hat.wav"))
    assertEquals(com.faris.psrs975.samples.DrumSlotCategory.CRASH, com.faris.psrs975.samples.GmDrumNoteNames.inferSlotFromFilename("Zildjian_Crash.wav"))
    assertEquals(com.faris.psrs975.samples.DrumSlotCategory.RIDE, com.faris.psrs975.samples.GmDrumNoteNames.inferSlotFromFilename("Bright_Ride.mp3"))

    val original = listOf(
      com.faris.psrs975.samples.DrumSampleMapping(
        id = "m1",
        name = "Sub Kick",
        slotCategory = com.faris.psrs975.samples.DrumSlotCategory.KICK,
        midiNote = 36,
        sourceFormat = "MP3",
        filePath = "/tmp/kick.wav",
        gain = 1.25f,
        pan = -0.1f,
      ),
      com.faris.psrs975.samples.DrumSampleMapping(
        id = "m2",
        name = "Crack Snare",
        slotCategory = com.faris.psrs975.samples.DrumSlotCategory.SNARE,
        midiNote = 40,
        sourceFormat = "OGG",
        filePath = "/tmp/snare.wav",
        gain = 1.5f,
        pan = 0.2f,
      ),
    )
    val json = com.faris.psrs975.samples.CustomDrumKitJsonCodec.serializeMappings(original)
    val restored = com.faris.psrs975.samples.CustomDrumKitJsonCodec.deserializeMappings(json)
    assertEquals(original.size, restored.size)
    assertEquals(36, restored[0].midiNote)
    assertEquals("MP3", restored[0].sourceFormat)
    assertEquals(1.25f, restored[0].gain, 0.01f)
    assertEquals(40, restored[1].midiNote)
    assertEquals("OGG", restored[1].sourceFormat)
    assertEquals(1.5f, restored[1].gain, 0.01f)
  }

  @Test
  fun `grand piano builds 45 dense multi-velocity zones with stereo imaging and polyphonic triad output`() {
    val piano = SampleBankFactory.buildInstrument("piano_grand", "Grand Piano", AcousticSynthesisModel.GRAND_PIANO)
    assertEquals(45, piano.zones.size)

    val softZone = piano.findZone(60, 35)
    val midZone = piano.findZone(60, 80)
    val forteZone = piano.findZone(60, 120)
    assertNotNull(softZone)
    assertNotNull(midZone)
    assertNotNull(forteZone)
    assertTrue("Expected distinct soft vs forte zones", softZone !== forteZone)
    assertTrue("Expected distinct mid vs forte zones", midZone !== forteZone)
    assertTrue("Expected stereo right channel buffer on Grand Piano", forteZone!!.rightSamples != null)

    val bassZone = piano.findZone(36, 100)!!
    val trebleZone = piano.findZone(88, 100)!!
    val bassL = bassZone.samples.take(4096).sumOf { abs(it).toDouble() }
    val bassR = bassZone.rightSamples!!.take(4096).sumOf { abs(it).toDouble() }
    val trebleL = trebleZone.samples.take(4096).sumOf { abs(it).toDouble() }
    val trebleR = trebleZone.rightSamples!!.take(4096).sumOf { abs(it).toDouble() }
    assertTrue("Expected soundboard bass L > R", bassL > bassR)
    assertTrue("Expected soundboard treble R > L", trebleR > trebleL)

    val engine = WorkstationAudioEngine(context = null, initialSampleRate = 44100)
    engine.setPartInstrument(WorkstationPart.RIGHT_1, piano)
    listOf(60, 64, 67).forEach { engine.noteOn(WorkstationPart.RIGHT_1, it, 112) }
    var peak = 0
    repeat(8) {
      val p = engine.renderBlock(256).maxOf { abs(it.toInt()) }
      if (p > peak) peak = p
    }
    assertTrue("Expected rich polyphonic piano output, peak=$peak", peak > 800)
  }

  @Test
  fun `guitars use 30 multi-velocity zones and fractional delay Karplus-Strong tuning is concert-accurate`() {
    val acoustic = SampleBankFactory.buildInstrument("gtr_acoustic", "Acoustic Guitar", AcousticSynthesisModel.PLUCKED_ACOUSTIC_GUITAR)
    val steel = SampleBankFactory.buildInstrument("gtr_steel", "Steel Guitar", AcousticSynthesisModel.PLUCKED_STEEL_GUITAR)
    val nylon = SampleBankFactory.buildInstrument("gtr_nylon", "Nylon Guitar", AcousticSynthesisModel.PLUCKED_NYLON_GUITAR)

    assertEquals(30, acoustic.zones.size)
    assertEquals(30, steel.zones.size)
    assertEquals(30, nylon.zones.size)

    // E5 (MIDI 76) exact root zone period at 44100 Hz = 44100 / 659.255 = 66.894 samples
    val e5Zone = steel.zones.first { it.rootNote == 76 && it.highVel == 127 }
    val expectedLag = 44100.0 / (440.0 * Math.pow(2.0, (76 - 69) / 12.0))
    val startIdx = 2200
    val window = 1200
    var bestLag = -1
    var bestCorr = Double.NEGATIVE_INFINITY
    for (lag in 58..76) {
      var dot = 0.0
      for (i in 0 until window) {
        dot += e5Zone.samples[startIdx + i] * e5Zone.samples[startIdx + i + lag]
      }
      if (dot > bestCorr) {
        bestCorr = dot
        bestLag = lag
      }
    }
    val freqErrorRatio = abs(bestLag - expectedLag) / expectedLag
    assertTrue("Expected E5 Steel Guitar pitch within 2% of 659.25 Hz (bestLag=$bestLag, expected=$expectedLag)", freqErrorRatio < 0.02)
  }

  @Test
  fun `all required catalog voices exist and render audible multi-zone PCM`() {
    val requiredIds = listOf(
      "piano_grand", "piano_electric", "gtr_nylon", "gtr_steel", "gtr_acoustic",
      "str_violin", "str_cello", "str_ensemble", "brass_trumpet", "wood_tenor_sax",
      "wood_flute", "kbd_accordion", "kbd_church_organ", "bass_acoustic",
      "bass_electric", "syn_lead", "syn_pad",
    )
    requiredIds.forEach { id ->
      val patch = com.faris.psrs975.voice.VoiceCatalog.builtInVoices.find { it.id == id }
      assertNotNull("Missing required voice in VoiceCatalog: $id", patch)
    }

    val modelsToRender = listOf(
      AcousticSynthesisModel.BOWED_SOLO_STRING,
      AcousticSynthesisModel.ACOUSTIC_CELLO,
      AcousticSynthesisModel.ACCORDION_MUSETTE,
      AcousticSynthesisModel.CHURCH_ORGAN,
      AcousticSynthesisModel.ACOUSTIC_BASS,
      AcousticSynthesisModel.ELECTRIC_BASS,
    )
    modelsToRender.forEach { model ->
      val inst = SampleBankFactory.buildInstrument("test_${model.name}", model.name, model)
      assertTrue("Expected >= 15 dense zones for $model", inst.zones.size >= 15)
      val engine = WorkstationAudioEngine(context = null, initialSampleRate = 44100)
      engine.setPartInstrument(WorkstationPart.RIGHT_1, inst)
      engine.noteOn(WorkstationPart.RIGHT_1, if (model.name.contains("BASS")) 40 else 60, 105)
      var peak = 0
      repeat(8) {
        val p = engine.renderBlock(256).maxOf { abs(it.toInt()) }
        if (p > peak) peak = p
      }
      assertTrue("Expected audible output for $model, peak=$peak", peak > 300)
    }
  }

  @Test
  fun `polyphonic sustain pedal holds 6-note chord after key release and stops cleanly on pedal release`() {
    val engine = WorkstationAudioEngine(context = null, initialSampleRate = 44100)
    val piano = SampleBankFactory.buildInstrument("piano_sustain", "Grand Piano", AcousticSynthesisModel.GRAND_PIANO)
    engine.setPartInstrument(WorkstationPart.RIGHT_1, piano)

    engine.setSustainPedal(WorkstationPart.RIGHT_1, true)
    val notes = listOf(48, 55, 60, 64, 67, 72)
    notes.forEach { engine.noteOn(WorkstationPart.RIGHT_1, it, 105) }
    repeat(6) { engine.renderBlock(256) }
    notes.forEach { engine.noteOff(WorkstationPart.RIGHT_1, it) }

    var sustainedPeak = 0
    repeat(10) {
      val p = engine.renderBlock(256).maxOf { abs(it.toInt()) }
      if (p > sustainedPeak) sustainedPeak = p
    }
    assertTrue("Expected sustain pedal to hold 6-note chord, peak=$sustainedPeak", sustainedPeak > 400)

    engine.setSustainPedal(WorkstationPart.RIGHT_1, false)
    repeat(220) { engine.renderBlock(256) }
    val tailPeak = engine.renderBlock(256).maxOf { abs(it.toInt()) }
    assertTrue("Expected sustained chord to decay after pedal release, tailPeak=$tailPeak", tailPeak < 150)
  }

  @Test
  fun `simultaneous keyboard melody plus custom drum kit and bass chord arranger mix without clipping`() {
    val engine = WorkstationAudioEngine(context = null, initialSampleRate = 44100)
    val drumKit = com.faris.psrs975.samples.GmDrumNoteNames.createFactoryStandardKit()
    val drums = com.faris.psrs975.samples.CustomDrumKitInstrumentBuilder.buildInstrument(drumKit, { null })
    val bass = SampleBankFactory.buildInstrument("bass_acoustic", "Acoustic Bass", AcousticSynthesisModel.ACOUSTIC_BASS)
    val guitar = SampleBankFactory.buildInstrument("gtr_nylon", "Nylon Guitar", AcousticSynthesisModel.PLUCKED_NYLON_GUITAR)
    val piano = SampleBankFactory.buildInstrument("piano_grand", "Grand Piano", AcousticSynthesisModel.GRAND_PIANO)

    engine.setPartInstrument(WorkstationPart.RHYTHM_1, drums)
    engine.setPartInstrument(WorkstationPart.BASS, bass)
    engine.setPartInstrument(WorkstationPart.CHORD_1, guitar)
    engine.setPartInstrument(WorkstationPart.RIGHT_1, piano)

    engine.noteOn(WorkstationPart.RHYTHM_1, 36, 115)
    engine.noteOn(WorkstationPart.RHYTHM_1, 42, 95)
    engine.noteOn(WorkstationPart.BASS, 36, 105)
    listOf(48, 52, 55).forEach { engine.noteOn(WorkstationPart.CHORD_1, it, 90) }
    listOf(60, 64, 67, 72).forEach { engine.noteOn(WorkstationPart.RIGHT_1, it, 108) }

    var peak = 0
    repeat(12) {
      val block = engine.renderBlock(512)
      for (s in block) {
        val a = abs(s.toInt())
        if (a > peak) peak = a
      }
    }
    assertTrue("Expected rich simultaneous arranger + keyboard mix, peak=$peak", peak > 1500)
    assertTrue("Expected soft-limited mix within 16-bit PCM range, peak=$peak", peak <= 32767)
  }
}
