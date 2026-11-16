package com.faris.psrs975.soundfont

import com.faris.psrs975.audio.SampleInstrument
import com.faris.psrs975.audio.SampleZone
import com.faris.psrs975.audio.WavCodec
import java.io.File
import kotlin.math.pow

/**
 * Parses SFZ instrument text definitions (<global>, <group>, <region>) and loads
 * referenced WAV samples into multi-zone SampleInstrument objects.
 */
object SfzLoader {

  fun loadFromFile(sfzFile: File): SampleInstrument {
    val text = sfzFile.readText(Charsets.UTF_8)
    val baseDir = sfzFile.parentFile
    return parse(text, sfzFile.nameWithoutExtension) { samplePath ->
      if (baseDir != null) {
        val candidate = File(baseDir, samplePath.replace('\\', File.separatorChar))
        if (candidate.exists()) candidate.readBytes() else null
      } else null
    }
  }

  fun parse(
    sfzText: String,
    instrumentName: String,
    sampleResolver: (String) -> ByteArray?,
  ): SampleInstrument {
    val globalProps = mutableMapOf<String, String>()
    val groupProps = mutableMapOf<String, String>()
    var currentSection = ""
    var currentRegion = mutableMapOf<String, String>()
    val regions = mutableListOf<Map<String, String>>()

    fun flushRegion() {
      if (currentSection == "region" && currentRegion.isNotEmpty()) {
        val merged = LinkedHashMap<String, String>()
        merged.putAll(globalProps)
        merged.putAll(groupProps)
        merged.putAll(currentRegion)
        regions.add(merged)
        currentRegion = mutableMapOf()
      }
    }

    val cleaned = sfzText.lines().joinToString("\n") { line ->
      val commentIdx = line.indexOf("//")
      if (commentIdx >= 0) line.substring(0, commentIdx) else line
    }

    val tokenRegex = Regex("""<(global|group|region|control)>|([a-zA-Z0-9_]+)=([^\s<>]+(?:\s+[^\s<>=]+)*?)(?=\s+[a-zA-Z0-9_]+=|\s*<|$)""")
    for (match in tokenRegex.findAll(cleaned)) {
      val tag = match.groupValues[1]
      if (tag.isNotEmpty()) {
        flushRegion()
        currentSection = tag.lowercase()
        if (currentSection == "group") groupProps.clear()
      } else {
        val key = match.groupValues[2].lowercase()
        val value = match.groupValues[3].trim()
        when (currentSection) {
          "global" -> globalProps[key] = value
          "group" -> groupProps[key] = value
          "region" -> currentRegion[key] = value
        }
      }
    }
    flushRegion()

    val zones = mutableListOf<SampleZone>()
    val sampleCache = mutableMapOf<String, ByteArray>()

    for (reg in regions) {
      val sampleRel = reg["sample"] ?: continue
      val wavBytes = sampleCache.getOrPut(sampleRel) {
        sampleResolver(sampleRel) ?: ByteArray(0)
      }
      if (wavBytes.isEmpty()) continue
      val decoded = runCatching { WavCodec.decodeBytes(wavBytes) }.getOrNull() ?: continue

      val lokey = parseNoteOrInt(reg["lokey"] ?: reg["key"], 0)
      val hikey = parseNoteOrInt(reg["hikey"] ?: reg["key"], 127)
      val root = parseNoteOrInt(reg["pitch_keycenter"] ?: reg["key"], 60)
      val lovel = (reg["lovel"]?.toIntOrNull() ?: 0).coerceIn(0, 127)
      val hivel = (reg["hivel"]?.toIntOrNull() ?: 127).coerceIn(0, 127)
      val tune = reg["tune"]?.toFloatOrNull() ?: 0f
      val volDb = reg["volume"]?.toFloatOrNull() ?: 0f
      val gain = 10f.pow(volDb / 20f).coerceIn(0.05f, 4f)
      val pan = ((reg["pan"]?.toFloatOrNull() ?: 0f) / 100f).coerceIn(-1f, 1f)
      val attack = (reg["ampeg_attack"]?.toFloatOrNull() ?: 0.005f).coerceIn(0.002f, 5f)
      val decay = (reg["ampeg_decay"]?.toFloatOrNull() ?: 0.35f).coerceIn(0.02f, 10f)
      val sustain = ((reg["ampeg_sustain"]?.toFloatOrNull() ?: 75f) / 100f).coerceIn(0f, 1f)
      val release = (reg["ampeg_release"]?.toFloatOrNull() ?: 0.30f).coerceIn(0.02f, 8f)

      val loopMode = reg["loop_mode"]?.lowercase()
      val loopStart = reg["loop_start"]?.toIntOrNull() ?: decoded.loopStart
      val loopEnd = reg["loop_end"]?.toIntOrNull() ?: decoded.loopEnd
      val loopEnabled = (loopMode == "loop_continuous" || loopMode == "loop_sustain" || decoded.hasLoop) &&
        loopEnd > loopStart + 4 && loopEnd < decoded.left.size

      zones.add(
        SampleZone(
          samples = decoded.left,
          rightSamples = decoded.right,
          sampleRate = decoded.sampleRate,
          rootNote = root,
          lowKey = lokey,
          highKey = hikey,
          lowVel = lovel,
          highVel = hivel,
          loopEnabled = loopEnabled,
          loopStart = loopStart,
          loopEnd = loopEnd,
          fineTuneCents = tune,
          attackSec = attack,
          decaySec = decay,
          sustainLevel = sustain,
          releaseSec = release,
          baseGain = gain,
          basePan = pan,
        ),
      )
    }

    require(zones.isNotEmpty()) { "No playable WAV zones resolved in SFZ '$instrumentName'" }
    return SampleInstrument(
      id = "sfz_${instrumentName.lowercase().replace(Regex("[^a-z0-9]"), "_")}",
      name = instrumentName,
      zones = zones,
    )
  }

  private fun parseNoteOrInt(raw: String?, defaultVal: Int): Int {
    if (raw == null) return defaultVal
    raw.toIntOrNull()?.let { return it.coerceIn(0, 127) }
    val match = Regex("""^([a-gA-G])([#b]?)(-?\d)$""").matchEntire(raw.trim()) ?: return defaultVal
    val base = when (match.groupValues[1].uppercase()) {
      "C" -> 0
      "D" -> 2
      "E" -> 4
      "F" -> 5
      "G" -> 7
      "A" -> 9
      "B" -> 11
      else -> 0
    }
    val acc = when (match.groupValues[2]) {
      "#" -> 1
      "b" -> -1
      else -> 0
    }
    val oct = match.groupValues[3].toInt()
    return ((oct + 1) * 12 + base + acc).coerceIn(0, 127)
  }
}
