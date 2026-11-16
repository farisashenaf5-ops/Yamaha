package com.faris.psrs975.samples

import com.faris.psrs975.audio.SampleInstrument
import com.faris.psrs975.audio.SampleZone

enum class DrumSlotCategory(
  val label: String,
  val shortLabel: String,
  val defaultMidiNote: Int,
) {
  KICK("Kick", "KICK", 36),
  SNARE("Snare", "SNARE", 38),
  CLOSED_HI_HAT("Closed Hi-Hat", "CHH", 42),
  OPEN_HI_HAT("Open Hi-Hat", "OHH", 46),
  LOW_TOM("Low Tom", "L.TOM", 41),
  MID_TOM("Mid Tom", "M.TOM", 45),
  HIGH_TOM("High Tom", "H.TOM", 48),
  CRASH("Crash", "CRASH", 49),
  RIDE("Ride", "RIDE", 51),
  PERCUSSION("Percussion", "PERC", 39),
}

data class DrumSampleMapping(
  val id: String,
  val name: String,
  val slotCategory: DrumSlotCategory,
  val midiNote: Int,
  val sourceFormat: String = "FACTORY", // WAV, MP3, OGG, SF2, FACTORY
  val filePath: String? = null,
  val gain: Float = 1.0f,
  val pan: Float = 0.0f,
)

data class CustomDrumKit(
  val id: String,
  val name: String,
  val isFactory: Boolean = false,
  val sourceFormat: String = "CUSTOM",
  val mappings: List<DrumSampleMapping>,
  val createdAtMs: Long = System.currentTimeMillis(),
  val updatedAtMs: Long = System.currentTimeMillis(),
)

data class CustomDrumKitLibraryState(
  val kits: List<CustomDrumKit> = listOf(GmDrumNoteNames.createFactoryStandardKit()),
  val selectedKitId: String = GmDrumNoteNames.FACTORY_KIT_ID,
  val isImporting: Boolean = false,
  val statusMessage: String = "Ready — import WAV, MP3, OGG, or SF2 drum kits",
  val errorMessage: String? = null,
) {
  val selectedKit: CustomDrumKit
    get() = kits.find { it.id == selectedKitId } ?: kits.first()
}

object GmDrumNoteNames {
  const val FACTORY_KIT_ID = "factory_standard_drum_kit"

  private val gmNames = mapOf(
    35 to "Acoustic Kick",
    36 to "Kick 1",
    37 to "Side Stick",
    38 to "Snare 1",
    39 to "Hand Clap",
    40 to "Electric Snare",
    41 to "Low Floor Tom",
    42 to "Closed Hi-Hat",
    43 to "High Floor Tom",
    44 to "Pedal Hi-Hat",
    45 to "Mid Tom 1",
    46 to "Open Hi-Hat",
    47 to "Low-Mid Tom",
    48 to "Hi-Mid Tom",
    49 to "Crash Cymbal 1",
    50 to "High Tom",
    51 to "Ride Cymbal 1",
    52 to "China Cymbal",
    53 to "Ride Bell",
    54 to "Tambourine",
    55 to "Splash Cymbal",
    56 to "Cowbell",
    57 to "Crash Cymbal 2",
    59 to "Ride Cymbal 2",
    60 to "Hi Bongo / Kebero",
    61 to "Low Bongo",
    63 to "Open Hi Conga",
    64 to "Low Conga",
    69 to "Cabasa",
    70 to "Maracas",
    75 to "Claves",
  )

  fun labelForMidiNote(midiNote: Int): String = gmNames[midiNote] ?: "Note $midiNote"

  fun slotForMidiNote(midiNote: Int): DrumSlotCategory = when (midiNote) {
    35, 36 -> DrumSlotCategory.KICK
    37, 38, 39, 40 -> DrumSlotCategory.SNARE
    42, 44 -> DrumSlotCategory.CLOSED_HI_HAT
    46 -> DrumSlotCategory.OPEN_HI_HAT
    41, 43 -> DrumSlotCategory.LOW_TOM
    45, 47 -> DrumSlotCategory.MID_TOM
    48, 50 -> DrumSlotCategory.HIGH_TOM
    49, 52, 55, 57 -> DrumSlotCategory.CRASH
    51, 53, 59 -> DrumSlotCategory.RIDE
    else -> DrumSlotCategory.PERCUSSION
  }

  fun inferSlotFromFilename(filename: String, fallbackIndex: Int = 0): DrumSlotCategory {
    val lower = filename.lowercase()
    return when {
      lower.contains("kick") || lower.contains("bd") || lower.contains("bassdrum") || lower.contains("bass_drum") -> DrumSlotCategory.KICK
      lower.contains("snare") || lower.contains("sd") || lower.contains("rim") -> DrumSlotCategory.SNARE
      lower.contains("open") && (lower.contains("hat") || lower.contains("hh") || lower.contains("ohh")) -> DrumSlotCategory.OPEN_HI_HAT
      lower.contains("ohh") -> DrumSlotCategory.OPEN_HI_HAT
      lower.contains("hat") || lower.contains("hh") || lower.contains("chh") || lower.contains("closed") -> DrumSlotCategory.CLOSED_HI_HAT
      lower.contains("low") && lower.contains("tom") -> DrumSlotCategory.LOW_TOM
      (lower.contains("hi") || lower.contains("high")) && lower.contains("tom") -> DrumSlotCategory.HIGH_TOM
      lower.contains("tom") -> DrumSlotCategory.MID_TOM
      lower.contains("crash") -> DrumSlotCategory.CRASH
      lower.contains("ride") -> DrumSlotCategory.RIDE
      else -> {
        val standardSlots = listOf(
          DrumSlotCategory.KICK,
          DrumSlotCategory.SNARE,
          DrumSlotCategory.CLOSED_HI_HAT,
          DrumSlotCategory.OPEN_HI_HAT,
          DrumSlotCategory.LOW_TOM,
          DrumSlotCategory.MID_TOM,
          DrumSlotCategory.HIGH_TOM,
          DrumSlotCategory.CRASH,
          DrumSlotCategory.RIDE,
        )
        standardSlots.getOrElse(fallbackIndex) { DrumSlotCategory.PERCUSSION }
      }
    }
  }

  fun defaultTemplateMappings(): List<DrumSampleMapping> = listOf(
    DrumSampleMapping("slot_kick", "Standard Kick", DrumSlotCategory.KICK, 36, "FACTORY"),
    DrumSampleMapping("slot_snare", "Standard Snare", DrumSlotCategory.SNARE, 38, "FACTORY"),
    DrumSampleMapping("slot_chh", "Closed Hi-Hat", DrumSlotCategory.CLOSED_HI_HAT, 42, "FACTORY"),
    DrumSampleMapping("slot_ohh", "Open Hi-Hat", DrumSlotCategory.OPEN_HI_HAT, 46, "FACTORY"),
    DrumSampleMapping("slot_ltom", "Low Tom", DrumSlotCategory.LOW_TOM, 41, "FACTORY"),
    DrumSampleMapping("slot_mtom", "Mid Tom", DrumSlotCategory.MID_TOM, 45, "FACTORY"),
    DrumSampleMapping("slot_htom", "High Tom", DrumSlotCategory.HIGH_TOM, 48, "FACTORY"),
    DrumSampleMapping("slot_crash", "Crash Cymbal", DrumSlotCategory.CRASH, 49, "FACTORY"),
    DrumSampleMapping("slot_ride", "Ride Cymbal", DrumSlotCategory.RIDE, 51, "FACTORY"),
  )

  fun createFactoryStandardKit(): CustomDrumKit = CustomDrumKit(
    id = FACTORY_KIT_ID,
    name = "Factory Standard Kit",
    isFactory = true,
    sourceFormat = "FACTORY",
    mappings = defaultTemplateMappings(),
  )

  fun quickAssignTargets(): List<Pair<Int, String>> = listOf(
    36 to "36 Kick",
    38 to "38 Snare",
    42 to "42 Closed HH",
    46 to "46 Open HH",
    41 to "41 Low Tom",
    45 to "45 Mid Tom",
    48 to "48 High Tom",
    49 to "49 Crash",
    51 to "51 Ride",
    39 to "39 Clap",
    37 to "37 Side Stick",
    56 to "56 Cowbell",
  )
}

object CustomDrumKitJsonCodec {

  private fun escapeJson(value: String): String = buildString(value.length + 4) {
    append('"')
    for (ch in value) {
      when (ch) {
        '\\' -> append("\\\\")
        '"' -> append("\\\"")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> append(ch)
      }
    }
    append('"')
  }

  fun serializeMappings(mappings: List<DrumSampleMapping>): String = buildString {
    append('[')
    mappings.forEachIndexed { idx, m ->
      if (idx > 0) append(',')
      append('{')
      append("\"id\":").append(escapeJson(m.id)).append(',')
      append("\"name\":").append(escapeJson(m.name)).append(',')
      append("\"slot\":").append(escapeJson(m.slotCategory.name)).append(',')
      append("\"note\":").append(m.midiNote).append(',')
      append("\"fmt\":").append(escapeJson(m.sourceFormat)).append(',')
      append("\"path\":").append(escapeJson(m.filePath ?: "")).append(',')
      append("\"gain\":").append(m.gain).append(',')
      append("\"pan\":").append(m.pan)
      append('}')
    }
    append(']')
  }

  fun deserializeMappings(mappingsJson: String): List<DrumSampleMapping> {
    val objects = runCatching { parseJsonArrayOfObjects(mappingsJson) }.getOrDefault(emptyList())
    val list = mutableListOf<DrumSampleMapping>()
    for (o in objects) {
      val id = o["id"] ?: continue
      val slot = runCatching { DrumSlotCategory.valueOf(o["slot"] ?: "PERCUSSION") }
        .getOrDefault(DrumSlotCategory.PERCUSSION)
      val note = o["note"]?.toIntOrNull()?.coerceIn(0, 127) ?: slot.defaultMidiNote
      val gain = o["gain"]?.toFloatOrNull()?.coerceIn(0.1f, 2.0f) ?: 1.0f
      val pan = o["pan"]?.toFloatOrNull()?.coerceIn(-1f, 1f) ?: 0.0f
      list.add(
        DrumSampleMapping(
          id = id,
          name = o["name"] ?: slot.label,
          slotCategory = slot,
          midiNote = note,
          sourceFormat = o["fmt"] ?: "FACTORY",
          filePath = o["path"]?.takeIf { it.isNotBlank() },
          gain = gain,
          pan = pan,
        ),
      )
    }
    return list
  }

  private fun parseJsonArrayOfObjects(raw: String): List<Map<String, String>> {
    val result = mutableListOf<Map<String, String>>()
    var i = 0
    val n = raw.length
    fun skipWs() {
      while (i < n && raw[i].isWhitespace()) i++
    }
    fun readString(): String {
      if (i >= n || raw[i] != '"') return ""
      i++ // skip opening quote
      val sb = StringBuilder()
      while (i < n) {
        val c = raw[i++]
        when (c) {
          '"' -> return sb.toString()
          '\\' -> if (i < n) {
            val esc = raw[i++]
            when (esc) {
              'n' -> sb.append('\n')
              'r' -> sb.append('\r')
              't' -> sb.append('\t')
              else -> sb.append(esc)
            }
          }
          else -> sb.append(c)
        }
      }
      return sb.toString()
    }
    fun readPrimitive(): String {
      val start = i
      while (i < n && raw[i] != ',' && raw[i] != '}' && raw[i] != ']' && !raw[i].isWhitespace()) i++
      return raw.substring(start, i)
    }

    skipWs()
    if (i >= n || raw[i] != '[') return emptyList()
    i++
    while (i < n) {
      skipWs()
      if (i >= n || raw[i] == ']') break
      if (raw[i] == ',') {
        i++
        continue
      }
      if (raw[i] == '{') {
        i++
        val map = mutableMapOf<String, String>()
        while (i < n) {
          skipWs()
          if (i >= n || raw[i] == '}') {
            i++
            break
          }
          if (raw[i] == ',') {
            i++
            continue
          }
          val key = readString()
          skipWs()
          if (i < n && raw[i] == ':') i++
          skipWs()
          val value = if (i < n && raw[i] == '"') readString() else readPrimitive()
          if (key.isNotEmpty()) map[key] = value
        }
        result.add(map)
      } else {
        i++
      }
    }
    return result
  }

  fun serializeStringMap(map: Map<String, String>): String = buildString {
    append('{')
    var first = true
    map.forEach { (k, v) ->
      if (!first) append(',')
      first = false
      append(escapeJson(k)).append(':').append(escapeJson(v))
    }
    append('}')
  }

  fun deserializeStringMap(raw: String): Map<String, String> {
    val result = mutableMapOf<String, String>()
    var i = 0
    val n = raw.length
    fun skipWs() {
      while (i < n && raw[i].isWhitespace()) i++
    }
    fun readString(): String {
      if (i >= n || raw[i] != '"') return ""
      i++
      val sb = StringBuilder()
      while (i < n) {
        val c = raw[i++]
        when (c) {
          '"' -> return sb.toString()
          '\\' -> if (i < n) {
            val esc = raw[i++]
            when (esc) {
              'n' -> sb.append('\n')
              'r' -> sb.append('\r')
              't' -> sb.append('\t')
              else -> sb.append(esc)
            }
          }
          else -> sb.append(c)
        }
      }
      return sb.toString()
    }
    skipWs()
    if (i >= n || raw[i] != '{') return emptyMap()
    i++
    while (i < n) {
      skipWs()
      if (i >= n || raw[i] == '}') break
      if (raw[i] == ',') {
        i++
        continue
      }
      val key = readString()
      skipWs()
      if (i < n && raw[i] == ':') i++
      skipWs()
      val value = readString()
      if (key.isNotBlank() && value.isNotBlank()) {
        result[key] = value
      }
    }
    return result
  }
}

object CustomDrumKitInstrumentBuilder {

  /**
   * Builds a playable multi-zone SampleInstrument for [kit].
   * Every custom-mapped sample replaces its assigned MIDI note (plus unmapped GM sibling articulations
   * such as Kick 35->36, Electric Snare 40->38, Pedal Hat 44->42) while keeping remaining GM percussion playable.
   */
  fun buildInstrument(
    kit: CustomDrumKit,
    zoneProvider: (mapping: DrumSampleMapping) -> SampleZone?,
    factoryFallback: SampleInstrument = SampleBankFactory.buildInstrument("drum_std", "Standard Kit", AcousticSynthesisModel.GM_DRUM_KIT),
  ): SampleInstrument {
    if (kit.isFactory) return factoryFallback

    val customZonesByNote = mutableMapOf<Int, SampleZone>()
    for (mapping in kit.mappings) {
      val baseZone = zoneProvider(mapping) ?: continue
      val note = mapping.midiNote.coerceIn(0, 127)
      val durSec = (baseZone.samples.size.toFloat() / baseZone.sampleRate.coerceAtLeast(8000)).coerceIn(0.25f, 6.0f)
      customZonesByNote[note] = baseZone.copy(
        rootNote = note,
        lowKey = note,
        highKey = note,
        lowVel = 0,
        highVel = 127,
        loopEnabled = false,
        attackSec = 0.001f,
        decaySec = durSec,
        sustainLevel = 0.9f,
        releaseSec = durSec.coerceAtLeast(0.45f),
        baseGain = mapping.gain.coerceIn(0.1f, 2.0f),
        basePan = mapping.pan.coerceIn(-1f, 1f),
      )
    }

    // Add sibling GM drum aliases when exact sibling notes were not explicitly mapped by user
    val siblingAliases = listOf(
      35 to 36, // Acoustic Bass Drum -> Kick
      37 to 38, // Side Stick -> Snare
      39 to 38, // Clap -> Snare
      40 to 38, // Electric Snare -> Snare
      44 to 42, // Pedal Hi-Hat -> Closed Hi-Hat
      43 to 41, // High Floor Tom -> Low Tom
      47 to 45, // Low-Mid Tom -> Mid Tom
      50 to 48, // High Tom -> Hi-Mid Tom
      55 to 49, // Splash -> Crash
      57 to 49, // Crash 2 -> Crash
      53 to 51, // Ride Bell -> Ride
      59 to 51, // Ride 2 -> Ride
    )
    for ((aliasNote, primaryNote) in siblingAliases) {
      if (!customZonesByNote.containsKey(aliasNote)) {
        val primaryZone = customZonesByNote[primaryNote]
        if (primaryZone != null) {
          customZonesByNote[aliasNote] = primaryZone.copy(
            rootNote = aliasNote,
            lowKey = aliasNote,
            highKey = aliasNote,
          )
        }
      }
    }

    // Keep factory GM zones only for unmapped MIDI notes so auxiliary percussion still works
    val finalZones = mutableListOf<SampleZone>()
    finalZones.addAll(customZonesByNote.values.sortedBy { it.lowKey })
    for (fz in factoryFallback.zones) {
      for (n in fz.lowKey..fz.highKey) {
        if (!customZonesByNote.containsKey(n)) {
          finalZones.add(fz.copy(lowKey = n, highKey = n, rootNote = n))
        }
      }
    }

    return SampleInstrument(
      id = "custom_drum_${kit.id}",
      name = kit.name,
      zones = finalZones,
      isDrumKit = true,
    )
  }
}
