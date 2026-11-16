package com.faris.psrs975.soundfont

import com.faris.psrs975.audio.SampleInstrument
import com.faris.psrs975.audio.SampleZone
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class Sf2PresetInfo(
  val name: String,
  val bank: Int,
  val program: Int,
  val instrument: SampleInstrument,
)

data class Sf2FileData(
  val bankName: String,
  val presets: List<Sf2PresetInfo>,
)

/**
 * Pure Kotlin RIFF SoundFont 2 (.sf2) parser supporting 16-bit smpl and 24-bit sm24 chunks,
 * preset/instrument bags, key/velocity ranges, loop points, tuning, and ADSR generators.
 */
object Sf2Loader {

  private data class PresetHeader(val name: String, val program: Int, val bank: Int, val bagIndex: Int)
  private data class BagEntry(val genIndex: Int)
  private data class GenEntry(val oper: Int, val amountLo: Int, val amountHi: Int, val signedAmount: Short)
  private data class InstHeader(val name: String, val bagIndex: Int)
  private data class SampleHeader(
    val name: String,
    val start: Int,
    val end: Int,
    val startLoop: Int,
    val endLoop: Int,
    val sampleRate: Int,
    val originalPitch: Int,
    val pitchCorrection: Int,
    val sampleLink: Int,
    val sampleType: Int,
  )

  fun isValidSf2(bytes: ByteArray): Boolean =
    bytes.size >= 12 && fourCC(bytes, 0) == "RIFF" && fourCC(bytes, 8) == "sfbk"

  fun load(input: InputStream, fallbackName: String = "Imported SF2"): Sf2FileData {
    val bytes = input.readBytes()
    return parseBytes(bytes, fallbackName)
  }

  fun parseBytes(bytes: ByteArray, fallbackName: String = "Imported SF2"): Sf2FileData {
    require(bytes.size >= 12) { "Invalid SF2: file too small" }
    val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val riff = fourCC(bytes, 0)
    val sfbk = fourCC(bytes, 8)
    require(riff == "RIFF" && sfbk == "sfbk") { "Invalid SF2 RIFF/sfbk header" }

    var bankName = fallbackName
    var smplOffset = -1
    var smplSize = 0
    var sm24Offset = -1
    var sm24Size = 0

    val phdrList = mutableListOf<PresetHeader>()
    val pbagList = mutableListOf<BagEntry>()
    val pgenList = mutableListOf<GenEntry>()
    val instList = mutableListOf<InstHeader>()
    val ibagList = mutableListOf<BagEntry>()
    val igenList = mutableListOf<GenEntry>()
    val shdrList = mutableListOf<SampleHeader>()

    fun walkList(start: Int, end: Int) {
      var pos = start
      while (pos + 8 <= end) {
        val id = fourCC(bytes, pos)
        val size = bb.getInt(pos + 4)
        val payload = pos + 8
        if (size < 0 || payload + size > bytes.size) break
        when (id) {
          "LIST" -> {
            if (size >= 4) {
              walkList(payload + 4, payload + size)
            }
          }
          "INAM" -> {
            bankName = readAsciiZ(bytes, payload, size).ifBlank { bankName }
          }
          "smpl" -> {
            smplOffset = payload
            smplSize = size
          }
          "sm24" -> {
            sm24Offset = payload
            sm24Size = size
          }
          "phdr" -> {
            for (o in payload until payload + size step 38) {
              if (o + 38 > payload + size) break
              phdrList.add(
                PresetHeader(
                  name = readAsciiZ(bytes, o, 20),
                  program = bb.getShort(o + 20).toInt() and 0xFFFF,
                  bank = bb.getShort(o + 22).toInt() and 0xFFFF,
                  bagIndex = bb.getShort(o + 24).toInt() and 0xFFFF,
                ),
              )
            }
          }
          "pbag" -> {
            for (o in payload until payload + size step 4) {
              if (o + 4 > payload + size) break
              pbagList.add(BagEntry(bb.getShort(o).toInt() and 0xFFFF))
            }
          }
          "pgen" -> {
            for (o in payload until payload + size step 4) {
              if (o + 4 > payload + size) break
              pgenList.add(
                GenEntry(
                  oper = bb.getShort(o).toInt() and 0xFFFF,
                  amountLo = bytes[o + 2].toInt() and 0xFF,
                  amountHi = bytes[o + 3].toInt() and 0xFF,
                  signedAmount = bb.getShort(o + 2),
                ),
              )
            }
          }
          "inst" -> {
            for (o in payload until payload + size step 22) {
              if (o + 22 > payload + size) break
              instList.add(
                InstHeader(
                  name = readAsciiZ(bytes, o, 20),
                  bagIndex = bb.getShort(o + 20).toInt() and 0xFFFF,
                ),
              )
            }
          }
          "ibag" -> {
            for (o in payload until payload + size step 4) {
              if (o + 4 > payload + size) break
              ibagList.add(BagEntry(bb.getShort(o).toInt() and 0xFFFF))
            }
          }
          "igen" -> {
            for (o in payload until payload + size step 4) {
              if (o + 4 > payload + size) break
              igenList.add(
                GenEntry(
                  oper = bb.getShort(o).toInt() and 0xFFFF,
                  amountLo = bytes[o + 2].toInt() and 0xFF,
                  amountHi = bytes[o + 3].toInt() and 0xFF,
                  signedAmount = bb.getShort(o + 2),
                ),
              )
            }
          }
          "shdr" -> {
            for (o in payload until payload + size step 46) {
              if (o + 46 > payload + size) break
              shdrList.add(
                SampleHeader(
                  name = readAsciiZ(bytes, o, 20),
                  start = bb.getInt(o + 20),
                  end = bb.getInt(o + 24),
                  startLoop = bb.getInt(o + 28),
                  endLoop = bb.getInt(o + 32),
                  sampleRate = bb.getInt(o + 36).coerceIn(8000, 96000),
                  originalPitch = bytes[o + 40].toInt() and 0xFF,
                  pitchCorrection = bytes[o + 41].toInt(), // signed byte
                  sampleLink = bb.getShort(o + 42).toInt() and 0xFFFF,
                  sampleType = bb.getShort(o + 44).toInt() and 0xFFFF,
                ),
              )
            }
          }
        }
        pos = payload + size + (size and 1)
      }
    }

    walkList(12, bytes.size)
    require(smplOffset >= 0 && smplSize > 0) { "SF2 missing sdta/smpl sample chunk" }

    val totalSamples = smplSize / 2
    val has24 = sm24Offset >= 0 && sm24Size >= totalSamples
    val pcmFloats = FloatArray(totalSamples)
    for (i in 0 until totalSamples) {
      val s16 = bb.getShort(smplOffset + i * 2).toInt()
      pcmFloats[i] = if (has24) {
        val lsb = bytes[sm24Offset + i].toInt() and 0xFF
        val s24 = (s16 shl 8) or lsb
        s24.toFloat() / 8388608f
      } else {
        s16.toFloat() / 32768f
      }
    }

    val presets = mutableListOf<Sf2PresetInfo>()
    val presetCount = max(0, phdrList.size - 1) // last is EOP terminal
    for (pIdx in 0 until presetCount) {
      val ph = phdrList[pIdx]
      val pBagStart = ph.bagIndex
      val pBagEnd = phdrList[pIdx + 1].bagIndex.coerceAtMost(pbagList.size)
      val zones = mutableListOf<SampleZone>()

      // Check preset-level global zone (first pbag without instrument generator 41)
      var presetGlobalLoKey = 0
      var presetGlobalHiKey = 127
      var presetGlobalLoVel = 0
      var presetGlobalHiVel = 127

      for (pb in pBagStart until pBagEnd) {
        val gStart = pbagList[pb].genIndex
        val gEnd = if (pb + 1 < pbagList.size) pbagList[pb + 1].genIndex else pgenList.size
        var pLoKey = presetGlobalLoKey
        var pHiKey = presetGlobalHiKey
        var pLoVel = presetGlobalLoVel
        var pHiVel = presetGlobalHiVel
        var instIdx = -1

        for (g in gStart until gEnd.coerceAtMost(pgenList.size)) {
          val gen = pgenList[g]
          when (gen.oper) {
            43 -> {
              pLoKey = gen.amountLo
              pHiKey = gen.amountHi
            }
            44 -> {
              pLoVel = gen.amountLo
              pHiVel = gen.amountHi
            }
            41 -> instIdx = gen.signedAmount.toInt() and 0xFFFF
          }
        }
        if (instIdx < 0) {
          if (pb == pBagStart) {
            presetGlobalLoKey = pLoKey
            presetGlobalHiKey = pHiKey
            presetGlobalLoVel = pLoVel
            presetGlobalHiVel = pHiVel
          }
          continue
        }
        if (instIdx + 1 >= instList.size) continue

        val ih = instList[instIdx]
        val iBagStart = ih.bagIndex
        val iBagEnd = instList[instIdx + 1].bagIndex.coerceAtMost(ibagList.size)

        // Check global instrument zone (first bag without sampleID generator 53)
        var globalGens = emptyList<GenEntry>()
        for (ib in iBagStart until iBagEnd) {
          val igStart = ibagList[ib].genIndex
          val igEnd = if (ib + 1 < ibagList.size) ibagList[ib + 1].genIndex else igenList.size
          val slice = igenList.subList(igStart.coerceAtMost(igenList.size), igEnd.coerceAtMost(igenList.size))
          val sampleGen = slice.lastOrNull { it.oper == 53 }
          if (sampleGen == null && ib == iBagStart) {
            globalGens = slice
            continue
          }
          val sIdx = sampleGen?.signedAmount?.toInt()?.and(0xFFFF) ?: continue
          if (sIdx >= shdrList.size - 1) continue
          val sh = shdrList[sIdx]
          if (sh.end <= sh.start || sh.end > totalSamples) continue

          var loKey = pLoKey
          var hiKey = pHiKey
          var loVel = pLoVel
          var hiVel = pHiVel
          var rootKey = if (sh.originalPitch in 0..127) sh.originalPitch else 60
          var fineTune = sh.pitchCorrection.toFloat()
          var coarseTune = 0
          var loopMode = 0
          var attackTc = -12000
          var decayTc = -3000
          var sustainCb = 250
          var releaseTc = -2400
          var pan = 0f

          fun applyGens(list: List<GenEntry>) {
            for (g in list) {
              when (g.oper) {
                17 -> pan = (g.signedAmount / 500f).coerceIn(-1f, 1f)
                34 -> attackTc = g.signedAmount.toInt()
                36 -> decayTc = g.signedAmount.toInt()
                37 -> sustainCb = g.signedAmount.toInt()
                38 -> releaseTc = g.signedAmount.toInt()
                43 -> {
                  loKey = max(pLoKey, g.amountLo)
                  hiKey = min(pHiKey, g.amountHi)
                }
                44 -> {
                  loVel = max(pLoVel, g.amountLo)
                  hiVel = min(pHiVel, g.amountHi)
                }
                51 -> coarseTune = g.signedAmount.toInt()
                52 -> fineTune = g.signedAmount.toFloat() + sh.pitchCorrection
                54 -> loopMode = g.signedAmount.toInt()
                58 -> if (g.signedAmount in 0..127) rootKey = g.signedAmount.toInt()
              }
            }
          }
          applyGens(globalGens)
          applyGens(slice)

          // Skip duplicate right-channel stereo sibling when left-channel pairs it via sampleLink
          val isRightLinked = (sh.sampleType and 0x0F) == 2 &&
            sh.sampleLink in 0 until (shdrList.size - 1) &&
            (shdrList[sh.sampleLink].sampleType and 0x0F) == 4
          if (isRightLinked && sh.sampleLink != sIdx) {
            continue
          }

          val sliceSamples = pcmFloats.copyOfRange(sh.start, sh.end)
          val rightSliceSamples = if ((sh.sampleType and 0x0F) == 4 &&
            sh.sampleLink in 0 until (shdrList.size - 1)
          ) {
            val rSh = shdrList[sh.sampleLink]
            if (rSh.end > rSh.start && rSh.end <= totalSamples) {
              pcmFloats.copyOfRange(rSh.start, rSh.end)
            } else null
          } else null

          val relLoopStart = (sh.startLoop - sh.start).coerceIn(0, sliceSamples.size - 1)
          val relLoopEnd = (sh.endLoop - sh.start).coerceIn(0, sliceSamples.size - 1)
          val loopEnabled = (loopMode == 1 || loopMode == 3) && relLoopEnd > relLoopStart + 4

          zones.add(
            SampleZone(
              samples = sliceSamples,
              rightSamples = rightSliceSamples,
              sampleRate = sh.sampleRate,
              rootNote = (rootKey - coarseTune).coerceIn(0, 127),
              lowKey = loKey.coerceIn(0, 127),
              highKey = hiKey.coerceIn(0, 127),
              lowVel = loVel.coerceIn(0, 127),
              highVel = hiVel.coerceIn(0, 127),
              loopEnabled = loopEnabled,
              loopStart = relLoopStart,
              loopEnd = relLoopEnd,
              fineTuneCents = fineTune,
              attackSec = timecentsToSeconds(attackTc).coerceIn(0.002f, 4.0f),
              decaySec = timecentsToSeconds(decayTc).coerceIn(0.02f, 8.0f),
              sustainLevel = (1f - (sustainCb.coerceIn(0, 1000) / 1000f)).coerceIn(0f, 1f),
              releaseSec = timecentsToSeconds(releaseTc).coerceIn(0.02f, 6.0f),
              basePan = pan,
            ),
          )
        }
      }

      if (zones.isNotEmpty()) {
        val cleanName = ph.name.ifBlank { "SF2 Preset ${ph.program}" }
        presets.add(
          Sf2PresetInfo(
            name = cleanName,
            bank = ph.bank,
            program = ph.program,
            instrument = SampleInstrument(
              id = "sf2_${ph.bank}_${ph.program}_${cleanName.lowercase().replace(Regex("[^a-z0-9]"), "_")}",
              name = cleanName,
              zones = zones,
              isDrumKit = ph.bank == 128,
            ),
          ),
        )
      }
    }

    return Sf2FileData(bankName = bankName, presets = presets)
  }

  private fun timecentsToSeconds(tc: Int): Float = 2f.pow(tc / 1200f)

  private fun fourCC(bytes: ByteArray, offset: Int): String =
    String(bytes, offset, 4, Charsets.US_ASCII)

  private fun readAsciiZ(bytes: ByteArray, offset: Int, maxLen: Int): String {
    var len = 0
    while (len < maxLen && offset + len < bytes.size && bytes[offset + len] != 0.toByte()) {
      len++
    }
    return String(bytes, offset, len, Charsets.US_ASCII).trim()
  }
}
