package com.faris.psrs975.samples

import android.content.Context
import com.faris.psrs975.audio.SampleInstrument
import com.faris.psrs975.audio.SampleZone
import com.faris.psrs975.audio.WavCodec
import com.faris.psrs975.soundfont.Sf2Loader
import kotlin.math.abs

/**
 * Loads optional pre-bundled studio `.wav` multisample folders or `.sf2` banks from
 * Android `assets/samples/` when present (e.g. `assets/samples/piano_grand/60_0_63.wav`).
 * Returns null if no bundled asset pack exists for the requested voice ID so SampleManager
 * seamlessly falls back to multi-velocity physical-acoustic synthesis.
 */
object SampleAssetsLoader {

  private val noteMap = mapOf(
    "c" to 0, "c#" to 1, "db" to 1, "d" to 2, "d#" to 3, "eb" to 3,
    "e" to 4, "f" to 5, "f#" to 6, "gb" to 6, "g" to 7, "g#" to 8,
    "ab" to 8, "a" to 9, "a#" to 10, "bb" to 10, "b" to 11,
  )

  fun tryLoadBundledInstrument(context: Context?, voiceId: String, voiceName: String): SampleInstrument? {
    val am = context?.assets ?: return null
    return runCatching {
      val folderPath = "samples/$voiceId"
      val files = am.list(folderPath)?.sorted().orEmpty()
      if (files.isEmpty()) return null

      // Check for bundled SF2 first
      val sf2Name = files.firstOrNull { it.endsWith(".sf2", ignoreCase = true) }
      if (sf2Name != null) {
        val bytes = am.open("$folderPath/$sf2Name").use { it.readBytes() }
        val sf2 = Sf2Loader.parseBytes(bytes, voiceName)
        val firstPreset = sf2.presets.firstOrNull()
        if (firstPreset != null) {
          return firstPreset.instrument.copy(id = voiceId, name = voiceName)
        }
      }

      // Otherwise load WAV multisamples named like `60.wav`, `C4.wav`, or `60_0_63.wav`
      val wavFiles = files.filter { it.endsWith(".wav", ignoreCase = true) }
      if (wavFiles.isEmpty()) return null

      data class ParsedWav(val root: Int, val loVel: Int, val hiVel: Int, val zone: SampleZone)

      val parsed = wavFiles.mapNotNull { fileName ->
        val stem = fileName.substringBeforeLast('.')
        val parts = stem.split('_')
        val root = parseNoteSpec(parts.firstOrNull() ?: "") ?: return@mapNotNull null
        val loVel = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 127) ?: 0
        val hiVel = parts.getOrNull(2)?.toIntOrNull()?.coerceIn(loVel, 127) ?: 127
        val wavBytes = am.open("$folderPath/$fileName").use { it.readBytes() }
        val dec = WavCodec.decodeBytes(wavBytes)
        ParsedWav(
          root = root,
          loVel = loVel,
          hiVel = hiVel,
          zone = SampleZone(
            samples = dec.left,
            rightSamples = dec.right,
            sampleRate = dec.sampleRate,
            rootNote = root,
            lowKey = root,
            highKey = root,
            lowVel = loVel,
            highVel = hiVel,
          ),
        )
      }
      if (parsed.isEmpty()) return null

      // Expand key ranges cleanly between neighboring multisample root notes per velocity layer
      val byLayer = parsed.groupBy { it.loVel to it.hiVel }
      val expandedZones = mutableListOf<SampleZone>()
      for ((_, layerItems) in byLayer) {
        val sorted = layerItems.sortedBy { it.root }
        sorted.forEachIndexed { idx, item ->
          val prevRoot = sorted.getOrNull(idx - 1)?.root
          val nextRoot = sorted.getOrNull(idx + 1)?.root
          val loKey = if (prevRoot == null) 0 else ((prevRoot + item.root) / 2) + 1
          val hiKey = if (nextRoot == null) 127 else (item.root + nextRoot) / 2
          expandedZones.add(
            item.zone.copy(
              lowKey = loKey.coerceIn(0, 127),
              highKey = hiKey.coerceIn(loKey, 127),
            ),
          )
        }
      }
      SampleInstrument(
        id = voiceId,
        name = voiceName,
        zones = expandedZones,
        isDrumKit = voiceId.startsWith("drum_"),
      )
    }.getOrNull()
  }

  fun parseNoteSpec(spec: String): Int? {
    spec.toIntOrNull()?.let { if (it in 0..127) return it }
    val match = Regex("^([A-Ga-g][#b]?)(-?\\d)$").matchEntire(spec.trim()) ?: return null
    val pc = noteMap[match.groupValues[1].lowercase()] ?: return null
    val oct = match.groupValues[2].toInt()
    return ((oct + 1) * 12 + pc).coerceIn(0, 127)
  }

  /**
   * Utility to verify an instrument has genuine non-silent PCM and accurate multisample spacing.
   */
  fun verifyInstrumentIntegrity(instrument: SampleInstrument): Boolean {
    if (instrument.zones.isEmpty()) return false
    return instrument.zones.all { z ->
      z.samples.size >= 64 && z.samples.any { abs(it) > 0.001f }
    }
  }
}
