package com.faris.psrs975.style

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

data class StyFileData(
  val style: ArrangerStyle,
  val formatTag: String,
  val detectedSections: List<StyleSection>,
  val hasCasmChunk: Boolean,
  val totalEvents: Int,
)

/**
 * Pure Kotlin Yamaha Style File Format (SFF1 / SFF2 `.sty`) importer and parser.
 * Parses SMF `MThd` + `MTrk` chunks, Yamaha `CASM`/`CSEG`/`Sdec`/`Ctab`/`Ctb2` chord & channel
 * mapping tables, section markers (Intro, Main A-D, Fill, Break, Ending), tempo/meter,
 * and GM program assignments into a playable [ArrangerStyle].
 */
object StyLoader {

  private data class RawStyNote(
    val startTick: Long,
    val endTick: Long,
    val channel: Int,
    val note: Int,
    val velocity: Int,
  )

  private data class RawMarker(
    val tick: Long,
    val label: String,
  )

  private data class CasmChannelRule(
    val destChannelType: StyleChannelType,
    val sourceRootPc: Int = 0, // 0 = C
    val sourceChordIntervals: IntArray = intArrayOf(0, 4, 7, 11),
  )

  fun isValidSty(bytes: ByteArray): Boolean =
    bytes.size >= 14 && String(bytes, 0, 4, Charsets.US_ASCII) == "MThd"

  fun load(
    input: InputStream,
    id: String,
    fallbackName: String = "Imported Style",
  ): StyFileData = parseBytes(input.readBytes(), id, fallbackName)

  fun parseBytes(
    bytes: ByteArray,
    id: String,
    fallbackName: String = "Imported Style",
  ): StyFileData {
    require(bytes.size >= 14) { "Invalid STY file: too short" }
    val bb = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
    val headerTag = String(bytes, 0, 4, Charsets.US_ASCII)
    require(headerTag == "MThd") { "Invalid STY file: missing MThd header" }

    val headerLen = bb.getInt(4).coerceAtLeast(6)
    val trackCount = (bb.getShort(10).toInt() and 0xFFFF).coerceAtLeast(1)
    val division = bb.getShort(12).toInt() and 0xFFFF
    val tpqn = if ((division and 0x8000) == 0) max(24, division) else 480

    var pos = 8 + headerLen
    var initialBpm = 120
    var tsNum = 4
    var tsDen = 4
    var formatTag = "SFF1"
    var parsedTitle = ""
    var maxTick = 0L

    val markers = mutableListOf<RawMarker>()
    val completedNotes = mutableListOf<RawStyNote>()
    val activeNotes = mutableMapOf<Long, MutableList<Pair<Long, Int>>>() // (ch shl 8 | note) -> [(tick, vel)]
    val channelPrograms = mutableMapOf<Int, Int>()
    val casmRules = mutableMapOf<Pair<StyleSection?, Int>, CasmChannelRule>()
    var hasCasm = false

    // 1. Parse all top-level chunks (MTrk, CASM, OTSc, etc.)
    var tracksRead = 0
    while (pos + 8 <= bytes.size) {
      val chunkTag = String(bytes, pos, 4, Charsets.US_ASCII)
      val chunkLen = bb.getInt(pos + 4)
      pos += 8
      if (chunkLen < 0 || pos + chunkLen > bytes.size) break
      val endPos = pos + chunkLen

      when (chunkTag) {
        "MTrk" -> {
          tracksRead++
          var cursor = pos
          var tick = 0L
          var runningStatus = 0

          while (cursor < endPos) {
            val (delta, nextCursor) = readVarLen(bytes, cursor, endPos)
            cursor = nextCursor
            tick += delta
            if (tick > maxTick) maxTick = tick
            if (cursor >= endPos) break

            var statusByte = bytes[cursor].toInt() and 0xFF
            if (statusByte < 0x80) {
              statusByte = runningStatus
            } else {
              cursor++
              if (statusByte < 0xF0) runningStatus = statusByte
            }

            when {
              statusByte == 0xFF -> {
                if (cursor >= endPos) break
                val metaType = bytes[cursor++].toInt() and 0xFF
                val (metaLen, afterLen) = readVarLen(bytes, cursor, endPos)
                cursor = afterLen
                val dataEnd = (cursor + metaLen.toInt()).coerceAtMost(endPos)
                when (metaType) {
                  0x01, 0x06, 0x07 -> {
                    if (dataEnd > cursor) {
                      val text = String(bytes, cursor, dataEnd - cursor, Charsets.UTF_8).trim()
                      if (text.equals("SFF1", ignoreCase = true) || text.equals("SFF2", ignoreCase = true)) {
                        formatTag = text.uppercase()
                      } else if (parseSectionMarker(text) != null) {
                        markers.add(RawMarker(tick, text))
                      } else if (metaType == 0x01 && parsedTitle.isBlank() && text.length in 2..32 && !text.startsWith("MH")) {
                        parsedTitle = text
                      }
                    }
                  }
                  0x03 -> {
                    if (dataEnd > cursor) {
                      val text = String(bytes, cursor, dataEnd - cursor, Charsets.UTF_8).trim()
                      if (parsedTitle.isBlank() &&
                        text.isNotBlank() &&
                        !text.equals("SFF1", ignoreCase = true) &&
                        !text.equals("SFF2", ignoreCase = true) &&
                        !text.startsWith("Track", ignoreCase = true)
                      ) {
                        parsedTitle = text
                      }
                    }
                  }
                  0x51 -> {
                    if (cursor + 3 <= dataEnd) {
                      val us = ((bytes[cursor].toInt() and 0xFF) shl 16) or
                        ((bytes[cursor + 1].toInt() and 0xFF) shl 8) or
                        (bytes[cursor + 2].toInt() and 0xFF)
                      if (us > 0) {
                        val bpm = (60_000_000.0 / us).roundToInt().coerceIn(40, 260)
                        if (tick == 0L || initialBpm == 120) initialBpm = bpm
                      }
                    }
                  }
                  0x58 -> {
                    if (cursor + 2 <= dataEnd && tick == 0L) {
                      tsNum = (bytes[cursor].toInt() and 0xFF).coerceIn(2, 12)
                      tsDen = (1 shl (bytes[cursor + 1].toInt() and 0x07)).coerceIn(2, 16)
                    }
                  }
                }
                cursor = dataEnd
              }
              statusByte == 0xF0 || statusByte == 0xF7 -> {
                val (sysexLen, afterLen) = readVarLen(bytes, cursor, endPos)
                cursor = (afterLen + sysexLen.toInt()).coerceAtMost(endPos)
              }
              else -> {
                val cmd = statusByte and 0xF0
                val ch = statusByte and 0x0F
                when (cmd) {
                  0x80 -> {
                    if (cursor + 2 <= endPos) {
                      val note = bytes[cursor].toInt() and 0x7F
                      val key = ((ch.toLong()) shl 8) or note.toLong()
                      val start = activeNotes[key]?.removeFirstOrNull()
                      if (start != null) {
                        completedNotes.add(RawStyNote(start.first, max(start.first + 1L, tick), ch, note, start.second))
                      }
                      cursor += 2
                    } else cursor = endPos
                  }
                  0x90 -> {
                    if (cursor + 2 <= endPos) {
                      val note = bytes[cursor].toInt() and 0x7F
                      val vel = bytes[cursor + 1].toInt() and 0x7F
                      val key = ((ch.toLong()) shl 8) or note.toLong()
                      if (vel == 0) {
                        val start = activeNotes[key]?.removeFirstOrNull()
                        if (start != null) {
                          completedNotes.add(RawStyNote(start.first, max(start.first + 1L, tick), ch, note, start.second))
                        }
                      } else {
                        activeNotes.getOrPut(key) { mutableListOf() }.add(tick to vel)
                      }
                      cursor += 2
                    } else cursor = endPos
                  }
                  0xC0 -> {
                    if (cursor + 1 <= endPos) {
                      val prog = bytes[cursor].toInt() and 0x7F
                      channelPrograms.putIfAbsent(ch, prog)
                      cursor += 1
                    } else cursor = endPos
                  }
                  0xA0, 0xB0, 0xE0 -> cursor = (cursor + 2).coerceAtMost(endPos)
                  0xD0 -> cursor = (cursor + 1).coerceAtMost(endPos)
                  else -> cursor = endPos
                }
              }
            }
          }
        }
        "CASM" -> {
          hasCasm = true
          parseCasmChunk(bytes, pos, endPos, casmRules)
        }
      }
      pos = endPos
      if (tracksRead >= trackCount && pos + 8 <= bytes.size &&
        String(bytes, pos, 4, Charsets.US_ASCII) != "MTrk" &&
        String(bytes, pos, 4, Charsets.US_ASCII) != "CASM" &&
        String(bytes, pos, 4, Charsets.US_ASCII) != "OTSc" &&
        String(bytes, pos, 4, Charsets.US_ASCII) != "FNRP"
      ) {
        // Scan ahead briefly in case non-RIFF padding precedes CASM
        val casmIdx = findFourCc(bytes, pos, "CASM")
        if (casmIdx >= 0 && casmIdx + 8 <= bytes.size) {
          pos = casmIdx
        } else {
          break
        }
      }
    }

    // Close any dangling note-ons at maxTick
    val defaultNoteDur = max(1L, tpqn / 4L)
    activeNotes.forEach { (key, list) ->
      val ch = (key shr 8).toInt()
      val note = (key and 0xFF).toInt()
      list.forEach { (startTick, vel) ->
        completedNotes.add(RawStyNote(startTick, startTick + defaultNoteDur, ch, note, vel))
      }
    }

    val barTicks = max(tpqn.toLong(), tpqn.toLong() * tsNum.toLong() * (4L / tsDen.coerceAtLeast(1)))
    val stepTicks = max(1.0, tpqn / 4.0)
    val stepsPerBar = ((tsNum * 16) / tsDen.coerceAtLeast(1)).coerceIn(8, 24)

    // 2. Slice notes into section buckets using markers (or single Main A if markerless)
    val sectionBoundaries = mutableListOf<Triple<StyleSection, Long, Long>>()
    val parsedMarkers = markers
      .mapNotNull { m -> parseSectionMarker(m.label)?.let { sec -> sec to m.tick } }
      .sortedBy { it.second }

    if (parsedMarkers.isEmpty()) {
      val endT = max(barTicks * 2L, maxTick + 1L)
      sectionBoundaries.add(Triple(StyleSection.MAIN_A, 0L, endT))
    } else {
      for (i in parsedMarkers.indices) {
        val (sec, startT) = parsedMarkers[i]
        val nextT = if (i + 1 < parsedMarkers.size) parsedMarkers[i + 1].second else max(startT + barTicks, maxTick + 1L)
        if (nextT > startT) {
          sectionBoundaries.add(Triple(sec, startT, nextT))
        }
      }
    }

    val parsedSections = mutableMapOf<StyleSection, StyleSectionPattern>()
    val detectedSections = mutableListOf<StyleSection>()

    for ((section, startTick, endTick) in sectionBoundaries) {
      val sectionNotes = completedNotes.filter { it.startTick in startTick until endTick }
      val spanTicks = (endTick - startTick).coerceAtLeast(barTicks)
      val bars = ((spanTicks.toDouble() / barTicks.toDouble()).roundToInt()).coerceIn(1, 8)
      val totalSteps = bars * stepsPerBar

      val patternEvents = sectionNotes.map { note ->
        val relTick = (note.startTick - startTick).coerceAtLeast(0L)
        val stepIdx = (relTick / stepTicks).toInt().coerceIn(0, totalSteps - 1)
        val durSteps = ((note.endTick - note.startTick) / stepTicks).roundToInt().coerceIn(1, totalSteps)

        val rule = casmRules[section to note.channel]
          ?: casmRules[null to note.channel]
          ?: inferChannelRule(note.channel, note.note)

        val isDrum = rule.destChannelType == StyleChannelType.RHYTHM_1 ||
          rule.destChannelType == StyleChannelType.RHYTHM_2

        if (isDrum) {
          StylePatternEvent(
            stepIndex = stepIdx,
            channel = rule.destChannelType,
            fixedMidiNote = note.note,
            chordDegree = -1,
            velocity = note.velocity.coerceIn(1, 127),
            durationSteps = 1,
          )
        } else {
          val (deg, oct) = mapNoteToChordDegree(note.note, rule.sourceRootPc, rule.sourceChordIntervals)
          StylePatternEvent(
            stepIndex = stepIdx,
            channel = rule.destChannelType,
            fixedMidiNote = note.note,
            chordDegree = deg,
            octaveOffset = if (rule.destChannelType == StyleChannelType.BASS) -2 else oct,
            velocity = note.velocity.coerceIn(1, 127),
            durationSteps = durSteps,
          )
        }
      }

      if (patternEvents.isNotEmpty() || !parsedSections.containsKey(section)) {
        parsedSections[section] = StyleSectionPattern(
          section = section,
          lengthBars = bars,
          events = patternEvents,
        )
        if (section !in detectedSections) detectedSections.add(section)
      }
    }

    // 3. Ensure complete PSR workstation section map (fallback derived sections if partial .sty)
    val baseFallback = BuiltInStyles.createAll().first()
    val primaryMain = parsedSections[StyleSection.MAIN_A]
      ?: parsedSections.values.firstOrNull()
      ?: baseFallback.sections[StyleSection.MAIN_A]!!

    val completedSections = buildMap {
      putAll(baseFallback.sections)
      // Scale missing Main/Fill/Intro/Ending from imported primary pattern when absent
      put(StyleSection.MAIN_A, parsedSections[StyleSection.MAIN_A] ?: primaryMain.copy(section = StyleSection.MAIN_A))
      put(StyleSection.MAIN_B, parsedSections[StyleSection.MAIN_B] ?: primaryMain.copy(section = StyleSection.MAIN_B))
      put(StyleSection.MAIN_C, parsedSections[StyleSection.MAIN_C] ?: (parsedSections[StyleSection.MAIN_B] ?: primaryMain).copy(section = StyleSection.MAIN_C))
      put(StyleSection.MAIN_D, parsedSections[StyleSection.MAIN_D] ?: (parsedSections[StyleSection.MAIN_C] ?: primaryMain).copy(section = StyleSection.MAIN_D))
      parsedSections.forEach { (sec, pat) -> put(sec, pat) }
    }

    val channelVoices = resolveChannelVoices(channelPrograms, casmRules)
    val cleanTitle = cleanStyleTitle(fallbackName.ifBlank { parsedTitle })
    val category = inferCategory(cleanTitle)

    val style = ArrangerStyle(
      id = id,
      name = cleanTitle,
      category = category,
      defaultBpm = initialBpm,
      timeSignatureNumerator = tsNum,
      timeSignatureDenominator = tsDen,
      isUserStyle = true,
      sections = completedSections,
      otsPresets = baseFallback.otsPresets,
      channelVoices = channelVoices,
    )

    return StyFileData(
      style = style,
      formatTag = formatTag,
      detectedSections = detectedSections.ifEmpty { listOf(StyleSection.MAIN_A) },
      hasCasmChunk = hasCasm,
      totalEvents = completedNotes.size,
    )
  }

  private fun parseCasmChunk(
    bytes: ByteArray,
    start: Int,
    end: Int,
    outRules: MutableMap<Pair<StyleSection?, Int>, CasmChannelRule>,
  ) {
    val bb = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
    var pos = start
    while (pos + 8 <= end) {
      val tag = String(bytes, pos, 4, Charsets.US_ASCII)
      val len = bb.getInt(pos + 4)
      pos += 8
      if (len < 0 || pos + len > end) break
      val subEnd = pos + len
      if (tag == "CSEG") {
        parseCsegSubchunk(bytes, pos, subEnd, outRules)
      }
      pos = subEnd
    }
  }

  private fun parseCsegSubchunk(
    bytes: ByteArray,
    start: Int,
    end: Int,
    outRules: MutableMap<Pair<StyleSection?, Int>, CasmChannelRule>,
  ) {
    val bb = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
    var pos = start
    val sections = mutableListOf<StyleSection>()
    val pendingTabs = mutableListOf<Pair<Int, CasmChannelRule>>()

    while (pos + 8 <= end) {
      val tag = String(bytes, pos, 4, Charsets.US_ASCII)
      val len = bb.getInt(pos + 4)
      pos += 8
      if (len < 0 || pos + len > end) break
      val subEnd = pos + len

      when (tag) {
        "Sdec" -> {
          val text = String(bytes, pos, len, Charsets.US_ASCII).trim('\u0000', ' ')
          text.split(',').forEach { token ->
            parseSectionMarker(token.trim())?.let { sections.add(it) }
          }
        }
        "Ctab", "Ctb2" -> {
          if (len >= 13) {
            val srcCh = bytes[pos].toInt() and 0x0F
            val chName = String(bytes, pos + 1, minOf(8, len - 1), Charsets.US_ASCII).trim('\u0000', ' ')
            val destCh = bytes[pos + 9].toInt() and 0x0F
            val srcRoot = (bytes[pos + 12].toInt() and 0xFF) % 12
            val destType = destMidiChannelToStyleType(destCh, chName)
            pendingTabs.add(srcCh to CasmChannelRule(destType, srcRoot))
          }
        }
      }
      pos = subEnd
    }

    if (sections.isEmpty()) {
      pendingTabs.forEach { (srcCh, rule) -> outRules[null to srcCh] = rule }
    } else {
      for (sec in sections) {
        for ((srcCh, rule) in pendingTabs) {
          outRules[sec to srcCh] = rule
        }
      }
    }
  }

  private fun destMidiChannelToStyleType(destCh: Int, label: String): StyleChannelType {
    val upper = label.uppercase()
    return when {
      upper.contains("RHY1") || upper.contains("RHY 1") -> StyleChannelType.RHYTHM_1
      upper.contains("RHY2") || upper.contains("RHY 2") -> StyleChannelType.RHYTHM_2
      upper.contains("BASS") -> StyleChannelType.BASS
      upper.contains("CHD1") || upper.contains("CHORD1") -> StyleChannelType.CHORD_1
      upper.contains("CHD2") || upper.contains("CHORD2") -> StyleChannelType.CHORD_2
      upper.contains("PAD") -> StyleChannelType.PAD
      upper.contains("PHR1") -> StyleChannelType.PHRASE_1
      upper.contains("PHR2") -> StyleChannelType.PHRASE_2
      else -> when (destCh) {
        8 -> StyleChannelType.RHYTHM_2
        9 -> StyleChannelType.RHYTHM_1
        10 -> StyleChannelType.BASS
        11 -> StyleChannelType.CHORD_1
        12 -> StyleChannelType.CHORD_2
        13 -> StyleChannelType.PAD
        14 -> StyleChannelType.PHRASE_1
        15 -> StyleChannelType.PHRASE_2
        else -> StyleChannelType.CHORD_1
      }
    }
  }

  private fun inferChannelRule(channel: Int, note: Int): CasmChannelRule {
    val type = when (channel) {
      8 -> StyleChannelType.RHYTHM_2
      9 -> StyleChannelType.RHYTHM_1
      10 -> StyleChannelType.BASS
      11 -> StyleChannelType.CHORD_1
      12 -> StyleChannelType.CHORD_2
      13 -> StyleChannelType.PAD
      14 -> StyleChannelType.PHRASE_1
      15 -> StyleChannelType.PHRASE_2
      0 -> if (note < 48) StyleChannelType.BASS else StyleChannelType.CHORD_1
      1 -> StyleChannelType.CHORD_1
      2 -> StyleChannelType.BASS
      3 -> StyleChannelType.CHORD_2
      4 -> StyleChannelType.PAD
      5 -> StyleChannelType.PHRASE_1
      else -> StyleChannelType.PHRASE_2
    }
    return CasmChannelRule(destChannelType = type, sourceRootPc = 0)
  }

  private fun mapNoteToChordDegree(midiNote: Int, sourceRootPc: Int, chordIntervals: IntArray): Pair<Int, Int> {
    val pc = ((midiNote - sourceRootPc) % 12 + 12) % 12
    var bestIdx = 0
    var bestDist = Int.MAX_VALUE
    chordIntervals.forEachIndexed { idx, interval ->
      val d = minOf(abs(pc - interval), 12 - abs(pc - interval))
      if (d < bestDist) {
        bestDist = d
        bestIdx = idx
      }
    }
    val relOctave = ((midiNote - 60) / 12).coerceIn(-1, 1)
    return bestIdx to relOctave
  }

  private fun resolveChannelVoices(
    programs: Map<Int, Int>,
    rules: Map<Pair<StyleSection?, Int>, CasmChannelRule>,
  ): Map<StyleChannelType, String> {
    val base = ArrangerStyle.defaultChannelVoices().toMutableMap()
    programs.forEach { (ch, prog) ->
      val type = rules[null to ch]?.destChannelType ?: inferChannelRule(ch, 60).destChannelType
      if (type != StyleChannelType.RHYTHM_1 && type != StyleChannelType.RHYTHM_2) {
        base[type] = gmProgramToVoiceId(prog)
      }
    }
    return base
  }

  private fun gmProgramToVoiceId(program: Int): String = when (program) {
    in 0..3 -> "piano_grand"
    in 4..7 -> "piano_electric"
    in 8..15 -> "kbd_clavinet"
    in 16..23 -> "kbd_organ"
    in 24..25 -> "gtr_nylon"
    in 26..31 -> "gtr_clean"
    in 32..39 -> "syn_bass"
    in 40..51 -> "str_ensemble"
    in 56..63 -> "brass_section"
    in 64..71 -> "wood_tenor_sax"
    in 72..79 -> "wood_flute"
    in 80..87 -> "syn_lead"
    in 88..95 -> "syn_pad"
    else -> "piano_grand"
  }

  fun parseSectionMarker(label: String): StyleSection? {
    val norm = label.trim().uppercase().replace("_", " ").replace(Regex("\\s+"), " ")
    return when {
      norm == "MAIN A" -> StyleSection.MAIN_A
      norm == "MAIN B" -> StyleSection.MAIN_B
      norm == "MAIN C" -> StyleSection.MAIN_C
      norm == "MAIN D" -> StyleSection.MAIN_D
      norm == "INTRO A" || norm == "INTRO 1" || norm == "INTRO I" -> StyleSection.INTRO_1
      norm == "INTRO B" || norm == "INTRO 2" || norm == "INTRO II" -> StyleSection.INTRO_2
      norm == "INTRO C" || norm == "INTRO 3" || norm == "INTRO III" -> StyleSection.INTRO_3
      norm == "FILL IN AA" || norm == "FILL AA" || norm == "FILL A" -> StyleSection.FILL_A
      norm == "FILL IN BB" || norm == "FILL BB" || norm == "FILL B" -> StyleSection.FILL_B
      norm == "FILL IN CC" || norm == "FILL CC" || norm == "FILL C" -> StyleSection.FILL_C
      norm == "FILL IN DD" || norm == "FILL DD" || norm == "FILL D" -> StyleSection.FILL_D
      norm == "FILL IN BA" || norm == "FILL IN AB" || norm == "BREAK" || norm == "BREAK FILL" -> StyleSection.BREAK
      norm == "ENDING A" || norm == "ENDING 1" || norm == "ENDING I" -> StyleSection.ENDING_1
      norm == "ENDING B" || norm == "ENDING 2" || norm == "ENDING II" -> StyleSection.ENDING_2
      norm == "ENDING C" || norm == "ENDING 3" || norm == "ENDING III" -> StyleSection.ENDING_3
      else -> null
    }
  }

  private fun cleanStyleTitle(raw: String): String =
    raw.substringBeforeLast('.').replace('_', ' ').trim().ifBlank { "Imported Style" }

  private fun inferCategory(name: String): StyleCategory {
    val l = name.lowercase()
    return when {
      listOf("dance", "club", "house", "trance", "techno", "edm", "euro", "disco").any { it in l } -> StyleCategory.DANCE
      listOf("ballad", "slow", "love", "romantic").any { it in l } -> StyleCategory.BALLAD
      listOf("jazz", "swing", "bebop", "bigband", "ragtime").any { it in l } -> StyleCategory.SWING_JAZZ
      listOf("rnb", "r&b", "soul", "funk", "hiphop", "groove").any { it in l } -> StyleCategory.RNB
      listOf("country", "western", "bluegrass").any { it in l } -> StyleCategory.COUNTRY
      listOf("latin", "bossa", "samba", "salsa", "rumba", "cha", "tango").any { it in l } -> StyleCategory.LATIN
      listOf("polka", "waltz", "march", "show", "entertain", "schlager").any { it in l } -> StyleCategory.ENTERTAINER
      listOf("ethio", "world", "tizita", "ambassel", "bati", "anchihoye", "arab", "orient", "reggae", "afro").any { it in l } -> StyleCategory.WORLD
      else -> StyleCategory.POP_ROCK
    }
  }

  private fun readVarLen(bytes: ByteArray, start: Int, limit: Int): Pair<Long, Int> {
    var value = 0L
    var cursor = start
    var count = 0
    while (cursor < limit && count < 4) {
      val b = bytes[cursor++].toInt() and 0xFF
      value = (value shl 7) or (b and 0x7F).toLong()
      count++
      if ((b and 0x80) == 0) break
    }
    return value to cursor
  }

  private fun findFourCc(bytes: ByteArray, from: Int, tag: String): Int {
    val t0 = tag[0].code.toByte()
    val t1 = tag[1].code.toByte()
    val t2 = tag[2].code.toByte()
    val t3 = tag[3].code.toByte()
    for (i in from..(bytes.size - 4)) {
      if (bytes[i] == t0 && bytes[i + 1] == t1 && bytes[i + 2] == t2 && bytes[i + 3] == t3) {
        return i
      }
    }
    return -1
  }
}
