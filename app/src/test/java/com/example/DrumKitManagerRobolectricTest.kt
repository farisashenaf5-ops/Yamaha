package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.faris.psrs975.audio.WavCodec
import com.faris.psrs975.audio.WorkstationAudioEngine
import com.faris.psrs975.audio.WorkstationPart
import com.faris.psrs975.samples.DrumKitManager
import com.faris.psrs975.samples.DrumSlotCategory
import com.faris.psrs975.samples.GmDrumNoteNames
import com.faris.psrs975.samples.SampleManager
import com.faris.psrs975.storage.SessionPreferencesStore
import com.faris.psrs975.storage.WorkstationDatabase
import com.faris.psrs975.storage.WorkstationRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DrumKitManagerRobolectricTest {

  private lateinit var context: Context
  private lateinit var db: WorkstationDatabase
  private lateinit var repository: WorkstationRepository
  private lateinit var sampleManager: SampleManager
  private lateinit var drumKitManager: DrumKitManager

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, WorkstationDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repository = WorkstationRepository(db.workstationDao(), SessionPreferencesStore(context))
    sampleManager = SampleManager(context = context, maxBuiltInCacheSize = 4)
    drumKitManager = DrumKitManager(context = context, repository = repository, sampleManager = sampleManager)
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `drumKitManager parses WAV samples, persists kit metadata in Room and DataStore, and plays via AudioEngine`() = runBlocking {
    fun sineWav(freqHz: Double): ByteArray {
      val sr = 44100
      val frames = 2048
      val buf = FloatArray(frames) { i -> (sin(2.0 * PI * freqHz * i / sr) * 0.85).toFloat() }
      return WavCodec.encodeWavBytes(buf, buf, sr)
    }

    val result = drumKitManager.parseAndImportWavBytes(
      files = listOf(
        "Sub_Kick.wav" to sineWav(60.0),
        "Crisp_Snare.wav" to sineWav(220.0),
        "Tight_CHH.wav" to sineWav(4800.0),
      ),
      customKitName = "Studio WAV Kit",
    )
    assertTrue("Expected WAV drum kit import to succeed: ${result.exceptionOrNull()}", result.isSuccess)
    val kit = result.getOrThrow()
    assertEquals("Studio WAV Kit", kit.name)
    assertEquals("WAV", kit.sourceFormat)

    // Verify metadata persisted in Room
    val roomKits = repository.getAllCustomDrumKits()
    assertTrue(roomKits.any { it.id == kit.id && it.name == "Studio WAV Kit" && it.sourceFormat == "WAV" })

    // Verify DataStore persistence for active kit and style binding
    drumKitManager.persistArrangerDrumKitSelection(kit.id, styleId = "style_test_pop")
    val session = repository.loadSession()
    assertEquals(kit.id, session.activeDrumKitId)
    assertTrue(session.styleDrumKitBindingsJson.contains("style_test_pop"))
    assertTrue(session.styleDrumKitBindingsJson.contains(kit.id))

    // Update MIDI mapping & verify Room update
    val kickSlot = kit.mappings.first { it.slotCategory == DrumSlotCategory.KICK }
    val remapped = drumKitManager.updateMappingMidiNote(kit.id, kickSlot.id, 35)
    assertNotNull(remapped)
    assertEquals(35, remapped!!.mappings.first { it.id == kickSlot.id }.midiNote)

    // Build playable SampleInstrument & render through WorkstationAudioEngine
    val instrument = drumKitManager.buildPlayableInstrument(kit.id)
    assertTrue(instrument.isDrumKit)
    assertNotNull(instrument.findZone(35, 110))
    assertNotNull(instrument.findZone(38, 110))

    val audioEngine = WorkstationAudioEngine(context = null, initialSampleRate = 44100)
    audioEngine.setPartInstrument(WorkstationPart.RHYTHM_1, instrument)
    audioEngine.noteOn(WorkstationPart.RHYTHM_1, 35, 118)
    val peak = audioEngine.renderBlock(256).maxOf { abs(it.toInt()) }
    assertTrue("Expected audible WAV drum playback, peak=$peak", peak > 400)

    // Delete kit and confirm Room & DataStore cleanup
    assertTrue(drumKitManager.deleteKit(kit.id))
    assertTrue(repository.getAllCustomDrumKits().none { it.id == kit.id })
    assertEquals(GmDrumNoteNames.FACTORY_KIT_ID, repository.loadSession().activeDrumKitId)
  }

  @Test
  fun `drumKitManager parses SF2 bank 128 drum kit, stores metadata in Room, and builds playable zones`() = runBlocking {
    val sf2Bytes = buildDrumBank128Sf2Bytes()
    val result = drumKitManager.parseAndImportSf2Bytes(
      rawBytes = sf2Bytes,
      fileName = "RockDrums.sf2",
      customKitName = "Rock SF2 Kit",
    )
    assertTrue("Expected SF2 drum kit import to succeed: ${result.exceptionOrNull()}", result.isSuccess)
    val kit = result.getOrThrow()
    assertEquals("Rock SF2 Kit", kit.name)
    assertEquals("SF2", kit.sourceFormat)

    val persisted = repository.getAllCustomDrumKits().firstOrNull { it.id == kit.id }
    assertNotNull(persisted)
    assertEquals("SF2", persisted!!.sourceFormat)

    val instrument = drumKitManager.buildPlayableInstrument(kit.id)
    assertTrue(instrument.isDrumKit)
    assertNotNull("Expected SF2 Kick note 36 zone", instrument.findZone(36, 100))

    val audioEngine = WorkstationAudioEngine(context = null, initialSampleRate = 44100)
    audioEngine.setPartInstrument(WorkstationPart.RHYTHM_1, instrument)
    audioEngine.noteOn(WorkstationPart.RHYTHM_1, 36, 115)
    val peak = audioEngine.renderBlock(256).maxOf { abs(it.toInt()) }
    assertTrue("Expected audible SF2 drum playback, peak=$peak", peak > 300)
  }

  private fun buildDrumBank128Sf2Bytes(): ByteArray {
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

    val info = listChunk("INFO", chunk("INAM", "DrumBank\u0000".toByteArray(Charsets.US_ASCII)))
    val sampleCount = 512
    val smplBb = ByteBuffer.allocate(sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
    for (i in 0 until sampleCount) {
      val s = (sin(2.0 * PI * 75.0 * i / 44100.0) * 24000.0).toInt().toShort()
      smplBb.putShort(s)
    }
    val sdta = listChunk("sdta", chunk("smpl", smplBb.array()))

    // phdr: bank=128 drum preset
    val phdr = ByteBuffer.allocate(76).order(ByteOrder.LITTLE_ENDIAN).apply {
      put(fixedStr("StandardKit", 20)); putShort(0); putShort(128); putShort(0); putInt(0); putInt(0); putInt(0)
      put(fixedStr("EOP", 20)); putShort(0); putShort(0); putShort(1); putInt(0); putInt(0); putInt(0)
    }.array()
    val pbag = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
      putShort(0); putShort(0); putShort(1); putShort(0)
    }.array()
    val pgen = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
      putShort(41); putShort(0) // instrument = 0
      putShort(0); putShort(0)
    }.array()
    val inst = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
      put(fixedStr("DrumInst", 20)); putShort(0)
      put(fixedStr("EOI", 20)); putShort(1)
    }.array()
    val ibag = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
      putShort(0); putShort(0); putShort(2); putShort(0)
    }.array()
    // igen: keyRange = 36..36 (oper 43, bytes lo=36, hi=36), sampleID=0 (oper 53), terminal
    val igen = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).apply {
      putShort(43); put(36.toByte()); put(36.toByte())
      putShort(53); putShort(0)
      putShort(0); putShort(0)
    }.array()
    val shdr = ByteBuffer.allocate(92).order(ByteOrder.LITTLE_ENDIAN).apply {
      put(fixedStr("Kick36", 20)); putInt(0); putInt(sampleCount); putInt(0); putInt(sampleCount); putInt(44100); put(36); put(0); putShort(0); putShort(1)
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
}
