package com.faris.psrs975.expansion

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.faris.psrs975.audio.SampleInstrument
import com.faris.psrs975.midi.MidiSong
import com.faris.psrs975.midi.StandardMidiCodec
import com.faris.psrs975.samples.SampleManager
import com.faris.psrs975.soundfont.Sf2Loader
import com.faris.psrs975.soundfont.SfzLoader
import com.faris.psrs975.soundfont.WavSampleLoader
import com.faris.psrs975.storage.ExpansionPackEntity
import com.faris.psrs975.storage.WorkstationRepository
import com.faris.psrs975.style.ArrangerStyle
import com.faris.psrs975.style.StyLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Manages SAF expansion pack importing, format validation, SHA-256 duplicate detection,
 * SF2 SoundFont & Yamaha STY Style importing, ZIP extraction, storage tracking, and persistence.
 */
class ExpansionManager(
  private val context: Context,
  private val repository: WorkstationRepository,
  private val sampleManager: SampleManager,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val expansionDir = File(context.filesDir, "expansions").apply { mkdirs() }

  private val _state = MutableStateFlow(ExpansionImportState())
  val state: StateFlow<ExpansionImportState> = _state.asStateFlow()

  init {
    scope.launch {
      repository.expansionPacks.collect { list ->
        val bytes = list.sumOf { it.sizeBytes }
        _state.update { it.copy(packs = list, totalStorageBytes = bytes) }
      }
    }
  }

  suspend fun importFromUri(
    uri: Uri,
    libraryGroup: String = "User Expansion",
    expectedExtensions: Set<String>? = null,
  ): Result<ExpansionImportResult> = withContext(Dispatchers.IO) {
    runCatching {
      _state.update { it.copy(isImporting = true, progress = 0.08f, errorMessage = null, statusMessage = "Reading file...") }

      val displayName = resolveDisplayName(uri)
      val rawExt = displayName.substringAfterLast('.', "").lowercase()
      val ext = when {
        rawExt in setOf("sf2", "sty", "prs", "sfz", "wav", "mid", "midi", "zip") -> rawExt
        else -> rawExt
      }

      if (expectedExtensions != null) {
        require(ext in expectedExtensions) {
          "Expected ${expectedExtensions.joinToString("/") { ".${it.uppercase()}" }} file, got '.$ext'"
        }
      } else {
        require(ext in setOf("sf2", "sty", "prs", "sfz", "wav", "mid", "midi", "zip")) {
          "Unsupported format '.$ext'. Supported: SF2, STY, SFZ, WAV, MID, ZIP"
        }
      }

      val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("Unable to open selected file")

      require(rawBytes.isNotEmpty()) { "Selected file is empty" }
      _state.update { it.copy(progress = 0.25f, statusMessage = "Checking duplicate & validating...") }

      val sha256 = sha256Hex(rawBytes)
      val duplicate = repository.findExpansionByHash(sha256)
      require(duplicate == null) { "Duplicate pack already imported: '${duplicate?.name}'" }

      validateHeader(ext, rawBytes)
      _state.update { it.copy(progress = 0.45f, statusMessage = "Parsing presets & patterns...") }

      val packId = "exp_${sha256.take(12)}"
      val packFolder = File(expansionDir, packId).apply { mkdirs() }
      val targetFile = File(packFolder, displayName)
      targetFile.writeBytes(rawBytes)

      val cleanTitle = displayName.substringBeforeLast('.')
      val instruments = mutableListOf<SampleInstrument>()
      val songs = mutableListOf<MidiSong>()
      val styles = mutableListOf<ArrangerStyle>()
      var detail = ""

      when (ext) {
        "sf2" -> {
          val sf2 = Sf2Loader.parseBytes(rawBytes, cleanTitle)
          require(sf2.presets.isNotEmpty()) { "SF2 contained no valid playable presets" }
          sf2.presets.forEach { preset ->
            val inst = preset.instrument.copy(id = "${packId}_${preset.bank}_${preset.program}")
            sampleManager.registerImportedInstrument(inst)
            instruments.add(inst)
          }
          val totalZones = instruments.sumOf { it.zones.size }
          detail = "SF2 '${sf2.bankName}' • ${instruments.size} presets • $totalZones sample zones"
        }
        "sty", "prs" -> {
          val sty = StyLoader.parseBytes(rawBytes, "${packId}_sty", cleanTitle)
          styles.add(sty.style)
          val secLabels = sty.detectedSections.joinToString(", ") { it.label }
          detail = "STY (${sty.formatTag}) • ${sty.style.defaultBpm} BPM • ${sty.detectedSections.size} sections ($secLabels)"
        }
        "wav" -> {
          val inst = WavSampleLoader.fromDecodedBytes(rawBytes, packId, cleanTitle)
          sampleManager.registerImportedInstrument(inst)
          instruments.add(inst)
          detail = "WAV sample • ${inst.zones.firstOrNull()?.sampleRate ?: 44100} Hz"
        }
        "mid", "midi" -> {
          val song = StandardMidiCodec.parseBytes(rawBytes, packId, cleanTitle, targetFile.absolutePath)
          songs.add(song)
          detail = "MIDI Song • ${song.initialBpm} BPM • ${song.tracks.size} tracks"
        }
        "sfz" -> {
          val sfzText = String(rawBytes, Charsets.UTF_8)
          val inst = SfzLoader.parse(sfzText, cleanTitle) { relPath ->
            val local = File(packFolder, relPath)
            if (local.exists()) local.readBytes() else null
          }
          sampleManager.registerImportedInstrument(inst)
          instruments.add(inst)
          detail = "SFZ instrument • ${inst.zones.size} regions"
        }
        "zip" -> {
          val extracted = extractAndParseZip(rawBytes, packId, packFolder)
          instruments.addAll(extracted.instruments)
          songs.addAll(extracted.midiSongs)
          styles.addAll(extracted.styles)
          require(instruments.isNotEmpty() || songs.isNotEmpty() || styles.isNotEmpty()) {
            "ZIP package contained no valid SF2, STY, SFZ+WAV, WAV, or MIDI assets"
          }
          detail = "ZIP Pack • ${instruments.size} voices, ${styles.size} styles, ${songs.size} songs"
        }
      }

      _state.update { it.copy(progress = 0.85f, statusMessage = "Saving to library database...") }
      val normalizedFormat = if (ext == "prs") "STY" else ext.uppercase()
      val effectiveGroup = when {
        libraryGroup != "User Expansion" -> libraryGroup
        normalizedFormat == "SF2" -> "SoundFont SF2"
        normalizedFormat == "STY" -> "Yamaha STY"
        else -> libraryGroup
      }
      val entity = ExpansionPackEntity(
        id = packId,
        name = cleanTitle,
        format = normalizedFormat,
        sha256 = sha256,
        filePath = targetFile.absolutePath,
        sizeBytes = rawBytes.size.toLong(),
        libraryGroup = effectiveGroup,
        presetCount = instruments.size + songs.size + styles.size,
      )
      repository.insertExpansionPack(entity)

      _state.update {
        it.copy(
          isImporting = false,
          progress = 1f,
          statusMessage = "Imported '${entity.name}' ($normalizedFormat)",
          lastImportSummary = detail,
        )
      }
      ExpansionImportResult(
        entity = entity,
        instruments = instruments,
        midiSongs = songs,
        styles = styles,
        summaryDetail = detail,
      )
    }.onFailure { err ->
      _state.update {
        it.copy(
          isImporting = false,
          progress = 0f,
          errorMessage = err.message ?: "Import failed",
          statusMessage = "Import failed",
        )
      }
    }
  }

  suspend fun reloadPersistedPacks(): PersistedExpansionAssets = withContext(Dispatchers.IO) {
    val instruments = mutableListOf<SampleInstrument>()
    val songs = mutableListOf<MidiSong>()
    val styles = mutableListOf<ArrangerStyle>()
    val current = _state.value.packs
    for (pack in current) {
      val f = File(pack.filePath)
      if (!f.exists()) continue
      val bytes = runCatching { f.readBytes() }.getOrNull() ?: continue
      when (pack.format.lowercase()) {
        "sf2" -> runCatching {
          Sf2Loader.parseBytes(bytes, pack.name).presets.forEach { p ->
            val inst = p.instrument.copy(id = "${pack.id}_${p.bank}_${p.program}")
            sampleManager.registerImportedInstrument(inst)
            instruments.add(inst)
          }
        }
        "sty", "prs" -> runCatching {
          styles.add(StyLoader.parseBytes(bytes, "${pack.id}_sty", pack.name).style)
        }
        "wav" -> runCatching {
          val inst = WavSampleLoader.fromDecodedBytes(bytes, pack.id, pack.name)
          sampleManager.registerImportedInstrument(inst)
          instruments.add(inst)
        }
        "mid", "midi" -> runCatching {
          songs.add(StandardMidiCodec.parseBytes(bytes, pack.id, pack.name, f.absolutePath))
        }
        "zip" -> runCatching {
          val z = extractAndParseZip(bytes, pack.id, f.parentFile ?: expansionDir)
          instruments.addAll(z.instruments)
          songs.addAll(z.midiSongs)
          styles.addAll(z.styles)
        }
      }
    }
    PersistedExpansionAssets(instruments = instruments, midiSongs = songs, styles = styles)
  }

  suspend fun updatePackGroup(id: String, group: String) {
    repository.updateExpansionGroup(id, group)
  }

  suspend fun deletePack(pack: ExpansionPackEntity) = withContext(Dispatchers.IO) {
    File(pack.filePath).parentFile?.deleteRecursively()
    sampleManager.listImportedIds()
      .filter { it.startsWith(pack.id) }
      .forEach { sampleManager.removeInstrument(it) }
    repository.deleteExpansionPack(pack.id)
  }

  private fun extractAndParseZip(
    zipBytes: ByteArray,
    packId: String,
    packFolder: File,
  ): PersistedExpansionAssets {
    val fileMap = mutableMapOf<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
      var entry = zis.nextEntry
      while (entry != null) {
        if (!entry.isDirectory) {
          val safeName = entry.name.replace('\\', '/')
          if (!safeName.contains("..")) {
            fileMap[safeName] = zis.readBytes()
          }
        }
        zis.closeEntry()
        entry = zis.nextEntry
      }
    }

    val instruments = mutableListOf<SampleInstrument>()
    val songs = mutableListOf<MidiSong>()
    val styles = mutableListOf<ArrangerStyle>()

    // 1. SF2 SoundFont entries
    fileMap.filterKeys { it.endsWith(".sf2", ignoreCase = true) }.forEach { (name, bytes) ->
      runCatching {
        val sf2 = Sf2Loader.parseBytes(bytes, name.substringAfterLast('/').substringBeforeLast('.'))
        sf2.presets.forEach { p ->
          val inst = p.instrument.copy(id = "${packId}_${p.bank}_${p.program}")
          sampleManager.registerImportedInstrument(inst)
          instruments.add(inst)
        }
      }
    }

    // 2. STY Yamaha Style entries
    fileMap.filterKeys { it.endsWith(".sty", ignoreCase = true) || it.endsWith(".prs", ignoreCase = true) }.forEach { (name, bytes) ->
      runCatching {
        val clean = name.substringAfterLast('/').substringBeforeLast('.')
        styles.add(StyLoader.parseBytes(bytes, "${packId}_sty_${clean.hashCode()}", clean).style)
      }
    }

    // 3. SFZ entries with relative WAV resolution inside ZIP
    val sfzReferencedWavs = mutableSetOf<String>()
    fileMap.filterKeys { it.endsWith(".sfz", ignoreCase = true) }.forEach { (sfzPath, bytes) ->
      val sfzDir = sfzPath.substringBeforeLast('/', "")
      runCatching {
        val inst = SfzLoader.parse(String(bytes, Charsets.UTF_8), sfzPath.substringAfterLast('/').substringBeforeLast('.')) { rel ->
          val normalized = rel.replace('\\', '/')
          val candidate = if (sfzDir.isNotEmpty()) "$sfzDir/$normalized" else normalized
          val resolvedKey = fileMap.keys.firstOrNull { it.equals(candidate, ignoreCase = true) || it.endsWith("/$normalized", ignoreCase = true) }
          if (resolvedKey != null) {
            sfzReferencedWavs.add(resolvedKey)
            fileMap[resolvedKey]
          } else null
        }
        val stamped = inst.copy(id = "${packId}_${inst.id}")
        sampleManager.registerImportedInstrument(stamped)
        instruments.add(stamped)
      }
    }

    // 4. Standalone WAVs not already consumed by an SFZ
    fileMap.filterKeys { it.endsWith(".wav", ignoreCase = true) && it !in sfzReferencedWavs }.forEach { (name, bytes) ->
      runCatching {
        val clean = name.substringAfterLast('/').substringBeforeLast('.')
        val inst = WavSampleLoader.fromDecodedBytes(bytes, "${packId}_wav_${clean.hashCode()}", clean)
        sampleManager.registerImportedInstrument(inst)
        instruments.add(inst)
      }
    }

    // 5. MIDI files
    fileMap.filterKeys { it.endsWith(".mid", ignoreCase = true) || it.endsWith(".midi", ignoreCase = true) }.forEach { (name, bytes) ->
      runCatching {
        val clean = name.substringAfterLast('/').substringBeforeLast('.')
        songs.add(StandardMidiCodec.parseBytes(bytes, "${packId}_mid_${clean.hashCode()}", clean))
      }
    }

    return PersistedExpansionAssets(instruments = instruments, midiSongs = songs, styles = styles)
  }

  private fun validateHeader(ext: String, bytes: ByteArray) {
    when (ext) {
      "sf2" -> {
        require(Sf2Loader.isValidSf2(bytes)) {
          "Invalid SF2 file: missing RIFF/sfbk signature"
        }
      }
      "sty", "prs" -> {
        require(StyLoader.isValidSty(bytes)) {
          "Invalid STY file: missing MThd SFF header"
        }
      }
      "wav" -> {
        require(bytes.size >= 44 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE") {
          "Invalid WAV file: missing RIFF/WAVE signature"
        }
      }
      "mid", "midi" -> {
        require(bytes.size >= 14 && String(bytes, 0, 4, Charsets.US_ASCII) == "MThd") {
          "Invalid MIDI file: missing MThd header"
        }
      }
      "sfz" -> {
        val text = String(bytes, Charsets.UTF_8)
        require(text.contains("<region>", ignoreCase = true) || text.contains("sample=", ignoreCase = true)) {
          "Invalid SFZ file: missing <region> or sample= opcode"
        }
      }
      "zip" -> {
        require(bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
          "Invalid ZIP archive"
        }
      }
    }
  }

  private fun resolveDisplayName(uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) {
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0) return cursor.getString(idx) ?: "expansion.bin"
      }
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "expansion.bin"
  }

  private fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString("") { "%02x".format(it) }
  }
}
