package com.faris.psrs975.samples

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.faris.psrs975.audio.SampleInstrument
import com.faris.psrs975.audio.SampleZone
import com.faris.psrs975.audio.WavCodec
import com.faris.psrs975.soundfont.AudioSampleDecoder
import com.faris.psrs975.soundfont.Sf2Loader
import com.faris.psrs975.storage.CustomDrumKitEntity
import com.faris.psrs975.storage.WorkstationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * Core DrumKitManager in `sample-manager`:
 * - Parses WAV, SF2, MP3, and OGG drum samples & multi-zone drum kits into normalized 32-bit PCM [SampleZone]s.
 * - Persists custom drum kit metadata & note/gain/pan mappings in Room (`custom_drum_kits`), and persists
 *   active kit selection and per-style drum bindings in DataStore (`WorkstationSessionSnapshot`).
 * - Builds playable [SampleInstrument] objects (`isDrumKit = true`) compatible with [WorkstationAudioEngine].
 */
class DrumKitManager(
  private val context: Context,
  private val repository: WorkstationRepository,
  private val sampleManager: SampleManager,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val kitsRootDir = File(context.filesDir, "custom_drum_kits").apply { mkdirs() }
  private val zoneCache = ConcurrentHashMap<String, SampleZone>()

  private val _state = MutableStateFlow(CustomDrumKitLibraryState())
  val state: StateFlow<CustomDrumKitLibraryState> = _state.asStateFlow()

  private val _activeKitIdFlow = MutableStateFlow(GmDrumNoteNames.FACTORY_KIT_ID)
  val activeKitIdFlow: StateFlow<String> = _activeKitIdFlow.asStateFlow()

  private val _styleBindingsFlow = MutableStateFlow<Map<String, String>>(emptyMap())
  val styleBindingsFlow: StateFlow<Map<String, String>> = _styleBindingsFlow.asStateFlow()

  init {
    scope.launch {
      repository.customDrumKits.collect { entities ->
        val customKits = entities.map { entity -> entityToModel(entity) }
        preloadMissingZones(customKits)
        val combined = listOf(GmDrumNoteNames.createFactoryStandardKit()) + customKits
        _state.update { st ->
          val nextSelected = if (combined.any { it.id == st.selectedKitId }) st.selectedKitId else combined.first().id
          st.copy(kits = combined, selectedKitId = nextSelected)
        }
      }
    }
    scope.launch {
      repository.sessionFlow.collect { snap ->
        _activeKitIdFlow.value = snap.activeDrumKitId
        _styleBindingsFlow.value = CustomDrumKitJsonCodec.deserializeStringMap(snap.styleDrumKitBindingsJson)
      }
    }
  }

  fun selectKitInEditor(kitId: String) {
    if (_state.value.kits.any { it.id == kitId }) {
      _state.update { it.copy(selectedKitId = kitId, errorMessage = null) }
    }
  }

  fun findKitById(kitId: String): CustomDrumKit? = _state.value.kits.find { it.id == kitId }

  /**
   * Builds a multi-zone [SampleInstrument] compatible with [WorkstationAudioEngine.setPartInstrument]
   * (`RHYTHM_1`, `RHYTHM_2`, `MULTI_PAD`), keeping unmapped GM percussion playable from factory fallback.
   */
  fun buildPlayableInstrument(kitId: String): SampleInstrument {
    val kit = findKitById(kitId) ?: GmDrumNoteNames.createFactoryStandardKit()
    val factory = sampleManager.getOrBuildInstrument(
      "drum_std",
      "Standard Kit",
      AcousticSynthesisModel.GM_DRUM_KIT,
    )
    if (kit.isFactory) return factory

    return CustomDrumKitInstrumentBuilder.buildInstrument(
      kit = kit,
      zoneProvider = { mapping -> resolveZoneForMapping(mapping) },
      factoryFallback = factory,
    )
  }

  /**
   * Persists the selected arranger drum kit ID and optional style->kit binding into DataStore.
   */
  suspend fun persistArrangerDrumKitSelection(kitId: String, styleId: String? = null) = withContext(Dispatchers.IO) {
    val currentSnap = repository.loadSession()
    val existingBindings = CustomDrumKitJsonCodec.deserializeStringMap(currentSnap.styleDrumKitBindingsJson).toMutableMap()
    if (styleId != null) {
      existingBindings[styleId] = kitId
    }
    val nextJson = CustomDrumKitJsonCodec.serializeStringMap(existingBindings)
    repository.saveSession(
      currentSnap.copy(
        activeDrumKitId = kitId,
        styleDrumKitBindingsJson = nextJson,
      ),
    )
    _activeKitIdFlow.value = kitId
    _styleBindingsFlow.value = existingBindings
  }

  suspend fun createNewKit(rawName: String): CustomDrumKit = withContext(Dispatchers.IO) {
    val cleanName = rawName.trim().ifBlank { "Custom Kit ${(_state.value.kits.size)}" }
    val kitId = "kit_${System.currentTimeMillis()}"
    File(kitsRootDir, kitId).mkdirs()
    val kit = CustomDrumKit(
      id = kitId,
      name = cleanName,
      isFactory = false,
      sourceFormat = "CUSTOM",
      mappings = GmDrumNoteNames.defaultTemplateMappings().map {
        it.copy(id = "${kitId}_${it.slotCategory.name.lowercase()}", sourceFormat = "FACTORY", filePath = null)
      },
    )
    upsertAndSyncKit(kit, statusMessage = "Created '${kit.name}'", selectKit = true)
    kit
  }

  suspend fun renameKit(kitId: String, newName: String): CustomDrumKit? = withContext(Dispatchers.IO) {
    val kit = findKitById(kitId) ?: return@withContext null
    if (kit.isFactory) return@withContext kit
    val clean = newName.trim().ifBlank { return@withContext kit }
    val updated = kit.copy(name = clean, updatedAtMs = System.currentTimeMillis())
    upsertAndSyncKit(updated, statusMessage = "Renamed kit to '$clean'")
    updated
  }

  suspend fun saveKit(kitId: String): CustomDrumKit? = withContext(Dispatchers.IO) {
    val kit = findKitById(kitId) ?: return@withContext null
    if (kit.isFactory) return@withContext kit
    val updated = kit.copy(updatedAtMs = System.currentTimeMillis())
    upsertAndSyncKit(updated, statusMessage = "Saved '${updated.name}' permanently")
    updated
  }

  suspend fun deleteKit(kitId: String): Boolean = withContext(Dispatchers.IO) {
    val kit = findKitById(kitId) ?: return@withContext false
    if (kit.isFactory) return@withContext false
    kit.mappings.forEach { zoneCache.remove(it.id) }
    File(kitsRootDir, kitId).deleteRecursively()
    repository.deleteCustomDrumKit(kitId)

    // Clean DataStore active kit / style bindings if they pointed to deleted kit
    val snap = repository.loadSession()
    val cleanedBindings = CustomDrumKitJsonCodec.deserializeStringMap(snap.styleDrumKitBindingsJson)
      .filterValues { it != kitId }
    val nextActiveId = if (snap.activeDrumKitId == kitId) GmDrumNoteNames.FACTORY_KIT_ID else snap.activeDrumKitId
    val nextJson = CustomDrumKitJsonCodec.serializeStringMap(cleanedBindings)
    repository.saveSession(
      snap.copy(
        activeDrumKitId = nextActiveId,
        styleDrumKitBindingsJson = nextJson,
      ),
    )
    _activeKitIdFlow.value = nextActiveId
    _styleBindingsFlow.value = cleanedBindings

    _state.update { st ->
      val nextKits = st.kits.filterNot { it.id == kitId }
      val nextSel = if (st.selectedKitId == kitId) GmDrumNoteNames.FACTORY_KIT_ID else st.selectedKitId
      st.copy(
        kits = nextKits,
        selectedKitId = nextSel,
        statusMessage = "Deleted '${kit.name}'",
        errorMessage = null,
      )
    }
    true
  }

  suspend fun updateMappingMidiNote(kitId: String, mappingId: String, newMidiNote: Int): CustomDrumKit? = withContext(Dispatchers.IO) {
    val originalKit = findKitById(kitId)
    val kit = ensureEditableKit(kitId)
    val resolvedId = resolveMappingId(originalKit, kit, mappingId) ?: return@withContext kit
    val note = newMidiNote.coerceIn(0, 127)
    val updatedMappings = kit.mappings.map { m ->
      if (m.id == resolvedId) {
        m.copy(
          midiNote = note,
          slotCategory = GmDrumNoteNames.slotForMidiNote(note),
        )
      } else m
    }
    val updated = kit.copy(mappings = updatedMappings, updatedAtMs = System.currentTimeMillis())
    upsertAndSyncKit(updated)
    updated
  }

  suspend fun updateMappingGain(kitId: String, mappingId: String, gain: Float): CustomDrumKit? = withContext(Dispatchers.IO) {
    val originalKit = findKitById(kitId)
    val kit = ensureEditableKit(kitId)
    val resolvedId = resolveMappingId(originalKit, kit, mappingId) ?: return@withContext kit
    val g = gain.coerceIn(0.1f, 2.0f)
    val updated = kit.copy(
      mappings = kit.mappings.map { if (it.id == resolvedId) it.copy(gain = g) else it },
      updatedAtMs = System.currentTimeMillis(),
    )
    upsertAndSyncKit(updated)
    updated
  }

  suspend fun updateMappingPan(kitId: String, mappingId: String, pan: Float): CustomDrumKit? = withContext(Dispatchers.IO) {
    val originalKit = findKitById(kitId)
    val kit = ensureEditableKit(kitId)
    val resolvedId = resolveMappingId(originalKit, kit, mappingId) ?: return@withContext kit
    val p = pan.coerceIn(-1f, 1f)
    val updated = kit.copy(
      mappings = kit.mappings.map { if (it.id == resolvedId) it.copy(pan = p) else it },
      updatedAtMs = System.currentTimeMillis(),
    )
    upsertAndSyncKit(updated)
    updated
  }

  suspend fun addCustomMappingSlot(kitId: String, midiNote: Int): CustomDrumKit = withContext(Dispatchers.IO) {
    val kit = ensureEditableKit(kitId)
    val clamped = midiNote.coerceIn(0, 127)
    val slot = DrumSampleMapping(
      id = "${kit.id}_slot_${System.currentTimeMillis()}",
      name = GmDrumNoteNames.labelForMidiNote(clamped),
      slotCategory = GmDrumNoteNames.slotForMidiNote(clamped),
      midiNote = clamped,
      sourceFormat = "FACTORY",
    )
    val updated = kit.copy(mappings = kit.mappings + slot, updatedAtMs = System.currentTimeMillis())
    upsertAndSyncKit(updated)
    updated
  }

  suspend fun removeMappingSlot(kitId: String, mappingId: String): CustomDrumKit? = withContext(Dispatchers.IO) {
    val kit = findKitById(kitId) ?: return@withContext null
    if (kit.isFactory) return@withContext kit
    zoneCache.remove(mappingId)
    val updated = kit.copy(
      mappings = kit.mappings.filterNot { it.id == mappingId },
      updatedAtMs = System.currentTimeMillis(),
    )
    upsertAndSyncKit(updated)
    updated
  }

  /**
   * Parses and imports an SF2 SoundFont drum kit from raw [ByteArray] contents, storing extracted zones
   * and persisting kit metadata to Room.
   */
  suspend fun parseAndImportSf2Bytes(
    rawBytes: ByteArray,
    fileName: String,
    customKitName: String? = null,
  ): Result<CustomDrumKit> = importDrumKitFromFiles(listOf(fileName to rawBytes), customKitName)

  /**
   * Parses and imports one or more WAV/MP3/OGG drum files from raw [ByteArray] payloads, storing decoded
   * PCM sample files and persisting kit metadata to Room.
   */
  suspend fun parseAndImportWavBytes(
    files: List<Pair<String, ByteArray>>,
    customKitName: String? = null,
  ): Result<CustomDrumKit> = importDrumKitFromFiles(files, customKitName)

  /**
   * Unified Android SAF URI entry point supporting `.sf2`, `.wav`, `.mp3`, `.ogg`, `.m4a`, `.flac`.
   */
  suspend fun importDrumKitFromUris(uris: List<Uri>): Result<CustomDrumKit> = withContext(Dispatchers.IO) {
    runCatching {
      require(uris.isNotEmpty()) { "No files selected" }
      val payloads = uris.map { uri ->
        val name = resolveDisplayName(uri)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
          ?: error("Unable to read '$name'")
        require(bytes.isNotEmpty()) { "'$name' is empty" }
        name to bytes
      }
      importDrumKitFromFiles(payloads).getOrThrow()
    }
  }

  /**
   * Core pure-byte / file parsing pipeline shared across URIs, local files, and tests.
   */
  suspend fun importDrumKitFromFiles(
    files: List<Pair<String, ByteArray>>,
    customKitName: String? = null,
  ): Result<CustomDrumKit> = withContext(Dispatchers.IO) {
    runCatching {
      require(files.isNotEmpty()) { "No files provided" }
      _state.update { it.copy(isImporting = true, errorMessage = null, statusMessage = "Parsing drum kit...") }

      val firstFile = files.first().first
      val firstBytes = files.first().second
      val firstExt = firstFile.substringAfterLast('.', "").lowercase()

      // Case 1: SF2 SoundFont Drum Kit
      if (firstExt == "sf2" || Sf2Loader.isValidSf2(firstBytes)) {
        require(Sf2Loader.isValidSf2(firstBytes)) { "Invalid SF2 file: missing RIFF/sfbk signature" }
        val cleanKitName = (customKitName?.trim()?.ifBlank { null })
          ?: firstFile.substringBeforeLast('.').ifBlank { "SF2 Drum Kit" }
        val sf2 = Sf2Loader.parseBytes(firstBytes, cleanKitName)
        require(sf2.presets.isNotEmpty()) { "SF2 contained no presets" }

        val drumPreset = sf2.presets.firstOrNull { it.bank == 128 }
          ?: sf2.presets.firstOrNull { it.name.contains("drum", ignoreCase = true) || it.name.contains("kit", ignoreCase = true) }
          ?: sf2.presets.maxByOrNull { it.instrument.zones.size }
          ?: sf2.presets.first()

        val kitId = "kit_sf2_${System.currentTimeMillis()}"
        val kitDir = File(kitsRootDir, kitId).apply { mkdirs() }
        val extractedMappings = mutableListOf<DrumSampleMapping>()
        val usedNotes = mutableSetOf<Int>()

        drumPreset.instrument.zones.forEachIndexed { idx, zone ->
          val note = when {
            zone.lowKey == zone.highKey -> zone.lowKey
            zone.rootNote in zone.lowKey..zone.highKey -> zone.rootNote
            else -> ((zone.lowKey + zone.highKey) / 2).coerceIn(0, 127)
          }
          if (!usedNotes.add(note)) return@forEachIndexed
          val mappingId = "${kitId}_m_${note}_$idx"
          val wavBytes = WavCodec.encodeWavBytes(zone.samples, zone.rightSamples, zone.sampleRate)
          val outFile = File(kitDir, "note_$note.wav")
          outFile.writeBytes(wavBytes)
          val normalizedZone = zone.copy(
            rootNote = note,
            lowKey = note,
            highKey = note,
            loopEnabled = false,
          )
          zoneCache[mappingId] = normalizedZone
          val slotCat = GmDrumNoteNames.slotForMidiNote(note)
          extractedMappings.add(
            DrumSampleMapping(
              id = mappingId,
              name = "${GmDrumNoteNames.labelForMidiNote(note)} (SF2)",
              slotCategory = slotCat,
              midiNote = note,
              sourceFormat = "SF2",
              filePath = outFile.absolutePath,
              gain = zone.baseGain.coerceIn(0.2f, 1.8f),
              pan = zone.basePan.coerceIn(-1f, 1f),
            ),
          )
        }

        require(extractedMappings.isNotEmpty()) { "SF2 preset contained no usable drum sample zones" }
        val mergedMappings = mergeWithStandardTemplate(kitId, extractedMappings)
        val kit = CustomDrumKit(
          id = kitId,
          name = customKitName?.takeIf { it.isNotBlank() } ?: "$cleanKitName (${drumPreset.name.take(14)})",
          isFactory = false,
          sourceFormat = "SF2",
          mappings = mergedMappings,
        )
        upsertAndSyncKit(
          kit,
          statusMessage = "Imported SF2 Drum Kit '${kit.name}' (${extractedMappings.size} custom notes)",
          selectKit = true,
        )
        return@runCatching kit
      }

      // Case 2: One or multiple WAV / MP3 / OGG / FLAC / M4A drum sample files
      val kitId = "kit_${System.currentTimeMillis()}"
      val kitDir = File(kitsRootDir, kitId).apply { mkdirs() }
      val importedMappings = mutableListOf<DrumSampleMapping>()
      val usedStandardNotes = mutableSetOf<Int>()

      files.forEachIndexed { idx, (displayName, rawBytes) ->
        val ext = displayName.substringAfterLast('.', "").lowercase()
        require(ext in setOf("wav", "wave", "mp3", "ogg", "m4a", "flac")) {
          "Unsupported drum file '.$ext'. Supported: WAV, MP3, OGG, SF2"
        }
        require(rawBytes.isNotEmpty()) { "'$displayName' is empty" }

        val scratchFile = File(kitDir, "tmp_$idx.$ext")
        val decoded = AudioSampleDecoder.decodeBytes(rawBytes, ext, scratchFile)
        runCatching { scratchFile.delete() }

        val cleanSampleName = displayName.substringBeforeLast('.').ifBlank { "Drum Sample ${idx + 1}" }
        val inferredSlot = GmDrumNoteNames.inferSlotFromFilename(cleanSampleName, idx)
        var targetNote = inferredSlot.defaultMidiNote
        if (!usedStandardNotes.add(targetNote)) {
          val fallbackOrder = listOf(36, 38, 42, 46, 41, 45, 48, 49, 51, 37, 39, 43, 47, 50, 54, 56, 57, 60, 61)
          targetNote = fallbackOrder.firstOrNull { usedStandardNotes.add(it) } ?: (60 + idx).coerceAtMost(127)
        }

        val mappingId = "${kitId}_s_${idx}_$targetNote"
        val persistedWavFile = File(kitDir, "sample_${idx}_$targetNote.wav")
        persistedWavFile.writeBytes(WavCodec.encodeWavBytes(decoded.left, decoded.right, decoded.sampleRate))

        val durSec = max(0.25f, decoded.left.size.toFloat() / decoded.sampleRate.toFloat())
        zoneCache[mappingId] = SampleZone(
          samples = decoded.left,
          rightSamples = decoded.right,
          sampleRate = decoded.sampleRate,
          rootNote = targetNote,
          lowKey = targetNote,
          highKey = targetNote,
          loopEnabled = false,
          attackSec = 0.001f,
          decaySec = durSec,
          sustainLevel = 0.9f,
          releaseSec = durSec,
        )

        importedMappings.add(
          DrumSampleMapping(
            id = mappingId,
            name = cleanSampleName,
            slotCategory = GmDrumNoteNames.slotForMidiNote(targetNote),
            midiNote = targetNote,
            sourceFormat = ext.uppercase(),
            filePath = persistedWavFile.absolutePath,
          ),
        )
      }

      val kitTitle = customKitName?.trim()?.takeIf { it.isNotBlank() } ?: if (files.size == 1) {
        "${firstFile.substringBeforeLast('.').take(18)} Kit"
      } else {
        "Custom Kit (${importedMappings.size} Samples)"
      }
      val merged = mergeWithStandardTemplate(kitId, importedMappings)
      val kit = CustomDrumKit(
        id = kitId,
        name = kitTitle,
        isFactory = false,
        sourceFormat = importedMappings.map { it.sourceFormat }.distinct().joinToString("/"),
        mappings = merged,
      )
      upsertAndSyncKit(
        kit,
        statusMessage = "Imported '${kit.name}' (${importedMappings.size} custom samples)",
        selectKit = true,
      )
      kit
    }.onFailure { err ->
      _state.update {
        it.copy(
          isImporting = false,
          errorMessage = err.message ?: "Drum kit import failed",
          statusMessage = "Import failed",
        )
      }
    }
  }

  /**
   * Assigns a single WAV/MP3/OGG/SF2 URI directly to a specific drum slot (auto-cloning the Factory Kit if needed).
   */
  suspend fun importSampleIntoMapping(
    kitId: String,
    mappingId: String,
    uri: Uri,
  ): Result<CustomDrumKit> = withContext(Dispatchers.IO) {
    runCatching {
      val displayName = resolveDisplayName(uri)
      val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("Unable to open selected file")
      importSampleBytesIntoMapping(kitId, mappingId, displayName, rawBytes).getOrThrow()
    }
  }

  /**
   * Assigns a single raw audio/SF2 file payload directly into [mappingId] of [kitId] and persists metadata.
   */
  suspend fun importSampleBytesIntoMapping(
    kitId: String,
    mappingId: String,
    fileName: String,
    rawBytes: ByteArray,
  ): Result<CustomDrumKit> = withContext(Dispatchers.IO) {
    runCatching {
      _state.update { it.copy(isImporting = true, errorMessage = null, statusMessage = "Loading drum sample...") }
      val originalKit = findKitById(kitId)
      val editableKit = ensureEditableKit(kitId)
      val resolvedId = resolveMappingId(originalKit, editableKit, mappingId)
      val targetMapping = editableKit.mappings.find { it.id == resolvedId }
        ?: editableKit.mappings.firstOrNull()
        ?: error("Drum slot not found")

      val ext = fileName.substringAfterLast('.', "").lowercase()
      require(ext in setOf("wav", "wave", "mp3", "ogg", "m4a", "flac", "sf2")) {
        "Unsupported file '.$ext'. Choose WAV, MP3, OGG, or SF2"
      }
      require(rawBytes.isNotEmpty()) { "Selected audio file is empty" }

      val kitDir = File(kitsRootDir, editableKit.id).apply { mkdirs() }
      val cleanName = fileName.substringBeforeLast('.').ifBlank { targetMapping.slotCategory.label }

      val (left, right, sr) = if (ext == "sf2" || Sf2Loader.isValidSf2(rawBytes)) {
        val sf2 = Sf2Loader.parseBytes(rawBytes, cleanName)
        val drumPreset = sf2.presets.firstOrNull { it.bank == 128 } ?: sf2.presets.firstOrNull()
          ?: error("SF2 contained no presets")
        val matchingZone = drumPreset.instrument.zones.find { targetMapping.midiNote in it.lowKey..it.highKey }
          ?: drumPreset.instrument.zones.firstOrNull()
          ?: error("SF2 contained no playable sample zones")
        Triple(matchingZone.samples, matchingZone.rightSamples, matchingZone.sampleRate)
      } else {
        val scratch = File(kitDir, "tmp_slot.$ext")
        val dec = AudioSampleDecoder.decodeBytes(rawBytes, ext, scratch)
        runCatching { scratch.delete() }
        Triple(dec.left, dec.right, dec.sampleRate)
      }

      val outFile = File(kitDir, "${targetMapping.id}.wav")
      outFile.writeBytes(WavCodec.encodeWavBytes(left, right, sr))
      val durSec = max(0.25f, left.size.toFloat() / sr.toFloat())
      zoneCache[targetMapping.id] = SampleZone(
        samples = left,
        rightSamples = right,
        sampleRate = sr,
        rootNote = targetMapping.midiNote,
        lowKey = targetMapping.midiNote,
        highKey = targetMapping.midiNote,
        loopEnabled = false,
        attackSec = 0.001f,
        decaySec = durSec,
        sustainLevel = 0.9f,
        releaseSec = durSec,
      )

      val updatedMappings = editableKit.mappings.map { m ->
        if (m.id == targetMapping.id) {
          m.copy(
            name = cleanName,
            sourceFormat = ext.uppercase(),
            filePath = outFile.absolutePath,
          )
        } else m
      }
      val customCount = updatedMappings.count { it.filePath != null }
      val updatedKit = editableKit.copy(
        sourceFormat = if (customCount > 0) ext.uppercase() else editableKit.sourceFormat,
        mappings = updatedMappings,
        updatedAtMs = System.currentTimeMillis(),
      )
      upsertAndSyncKit(
        updatedKit,
        statusMessage = "Loaded '$cleanName' → ${targetMapping.slotCategory.label} (MIDI ${targetMapping.midiNote})",
        selectKit = true,
      )
      updatedKit
    }.onFailure { err ->
      _state.update {
        it.copy(
          isImporting = false,
          errorMessage = err.message ?: "Failed to load drum sample",
          statusMessage = "Sample import failed",
        )
      }
    }
  }

  private suspend fun upsertAndSyncKit(
    kit: CustomDrumKit,
    statusMessage: String? = null,
    selectKit: Boolean = false,
  ) {
    repository.upsertCustomDrumKit(modelToEntity(kit))
    _state.update { st ->
      val existing = st.kits.filterNot { it.id == kit.id }
      val nextKits = listOf(GmDrumNoteNames.createFactoryStandardKit()) +
        (existing.filterNot { it.isFactory } + kit).sortedByDescending { it.updatedAtMs }
      st.copy(
        kits = nextKits,
        selectedKitId = if (selectKit) kit.id else st.selectedKitId,
        isImporting = false,
        statusMessage = statusMessage ?: st.statusMessage,
        errorMessage = null,
      )
    }
  }

  private suspend fun ensureEditableKit(kitId: String): CustomDrumKit {
    val existing = findKitById(kitId)
    if (existing != null && !existing.isFactory) return existing
    return createNewKit("Custom Drum Kit")
  }

  private fun resolveMappingId(
    originalKit: CustomDrumKit?,
    editableKit: CustomDrumKit,
    requestedMappingId: String,
  ): String? {
    editableKit.mappings.find { it.id == requestedMappingId }?.let { return it.id }
    val orig = originalKit?.mappings?.find { it.id == requestedMappingId } ?: return null
    return (editableKit.mappings.find { it.slotCategory == orig.slotCategory && it.midiNote == orig.midiNote }
      ?: editableKit.mappings.find { it.slotCategory == orig.slotCategory })?.id
  }

  private fun mergeWithStandardTemplate(
    kitId: String,
    imported: List<DrumSampleMapping>,
  ): List<DrumSampleMapping> {
    val template = GmDrumNoteNames.defaultTemplateMappings().map { tpl ->
      val match = imported.firstOrNull { it.midiNote == tpl.midiNote }
        ?: imported.firstOrNull { it.slotCategory == tpl.slotCategory }
      match ?: tpl.copy(id = "${kitId}_${tpl.slotCategory.name.lowercase()}", sourceFormat = "FACTORY", filePath = null)
    }
    val includedIds = template.map { it.id }.toSet()
    val extras = imported.filterNot { it.id in includedIds }
    return (template + extras).sortedBy { it.midiNote }
  }

  private fun resolveZoneForMapping(mapping: DrumSampleMapping): SampleZone? {
    zoneCache[mapping.id]?.let { return it }
    val path = mapping.filePath ?: return null
    val f = File(path)
    if (!f.exists()) return null
    return runCatching {
      val dec = AudioSampleDecoder.decodeFile(f, "wav")
      val durSec = max(0.25f, dec.left.size.toFloat() / dec.sampleRate.toFloat())
      val z = SampleZone(
        samples = dec.left,
        rightSamples = dec.right,
        sampleRate = dec.sampleRate,
        rootNote = mapping.midiNote,
        lowKey = mapping.midiNote,
        highKey = mapping.midiNote,
        loopEnabled = false,
        attackSec = 0.001f,
        decaySec = durSec,
        sustainLevel = 0.9f,
        releaseSec = durSec,
      )
      zoneCache[mapping.id] = z
      z
    }.getOrNull()
  }

  private fun preloadMissingZones(kits: List<CustomDrumKit>) {
    kits.forEach { kit ->
      kit.mappings.forEach { m ->
        if (m.filePath != null && !zoneCache.containsKey(m.id)) {
          resolveZoneForMapping(m)
        }
      }
    }
  }

  private fun modelToEntity(kit: CustomDrumKit): CustomDrumKitEntity = CustomDrumKitEntity(
    id = kit.id,
    name = kit.name,
    sourceFormat = kit.sourceFormat,
    mappingsJson = CustomDrumKitJsonCodec.serializeMappings(kit.mappings),
    createdAtMs = kit.createdAtMs,
    updatedAtMs = kit.updatedAtMs,
  )

  private fun entityToModel(entity: CustomDrumKitEntity): CustomDrumKit {
    val list = CustomDrumKitJsonCodec.deserializeMappings(entity.mappingsJson)
    return CustomDrumKit(
      id = entity.id,
      name = entity.name,
      isFactory = false,
      sourceFormat = entity.sourceFormat,
      mappings = list.ifEmpty { GmDrumNoteNames.defaultTemplateMappings() },
      createdAtMs = entity.createdAtMs,
      updatedAtMs = entity.updatedAtMs,
    )
  }

  private fun resolveDisplayName(uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) {
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0) return cursor.getString(idx) ?: "drum_sample.wav"
      }
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "drum_sample.wav"
  }
}
