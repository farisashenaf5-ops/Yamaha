package com.example

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiReceiver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.faris.psrs975.audio.WorkstationAudioEngine
import com.faris.psrs975.audio.WorkstationPart
import com.faris.psrs975.chord.ChordFingeringMode
import com.faris.psrs975.chord.ChordRecognizer
import com.faris.psrs975.chord.DetectedChord
import com.faris.psrs975.effects.ChorusSettings
import com.faris.psrs975.effects.CompressorSettings
import com.faris.psrs975.effects.DelaySettings
import com.faris.psrs975.effects.DspEffectType
import com.faris.psrs975.effects.DspInsertSettings
import com.faris.psrs975.effects.EffectsRackState
import com.faris.psrs975.effects.EqSettings
import com.faris.psrs975.effects.ReverbSettings
import com.faris.psrs975.expansion.ExpansionManager
import com.faris.psrs975.midi.MidiEventType
import com.faris.psrs975.midi.MidiPlaybackSink
import com.faris.psrs975.midi.MidiSong
import com.faris.psrs975.midi.MidiSongPlayer
import com.faris.psrs975.midi.StandardMidiCodec
import com.faris.psrs975.mixer.MixerChannelState
import com.faris.psrs975.mixer.MixerEngine
import com.faris.psrs975.recording.AudioRecordingEngine
import com.faris.psrs975.recording.AudioRecordingItem
import com.faris.psrs975.samples.DrumKitManager
import com.faris.psrs975.samples.GmDrumNoteNames
import com.faris.psrs975.samples.SampleManager
import com.faris.psrs975.storage.ExpansionPackEntity
import com.faris.psrs975.storage.KeyboardSetEntity
import com.faris.psrs975.storage.RegistrationMemoryEntity
import com.faris.psrs975.storage.SessionPreferencesStore
import com.faris.psrs975.storage.UserVoiceEntity
import com.faris.psrs975.storage.WorkstationDatabase
import com.faris.psrs975.storage.WorkstationRepository
import com.faris.psrs975.storage.WorkstationSessionSnapshot
import com.faris.psrs975.style.ArrangerStyle
import com.faris.psrs975.style.StyleCategory
import com.faris.psrs975.style.StyleChannelType
import com.faris.psrs975.style.StyleEngine
import com.faris.psrs975.style.StyleSection
import com.faris.psrs975.ui.LcdPage
import com.faris.psrs975.ui.MultiPadPlayMode
import com.faris.psrs975.ui.WorkstationActions
import com.faris.psrs975.ui.WorkstationUiState
import com.faris.psrs975.voice.ActiveVoiceSlot
import com.faris.psrs975.voice.VoiceCategory
import com.faris.psrs975.voice.VoiceEngine
import com.faris.psrs975.voice.VoicePatch
import com.faris.psrs975.voice.VoiceSlotEditParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

sealed interface WorkstationUiEvent {
  data object LaunchSf2FilePicker : WorkstationUiEvent
  data object LaunchStyFilePicker : WorkstationUiEvent
  data object LaunchExpansionFilePicker : WorkstationUiEvent
  data object LaunchDrumKitFilesPicker : WorkstationUiEvent
  data class LaunchDrumSlotSamplePicker(val kitId: String, val mappingId: String) : WorkstationUiEvent
  data object RequestMicrophonePermission : WorkstationUiEvent
  data class ShareFile(val file: File, val mimeType: String, val title: String) : WorkstationUiEvent
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkstationViewModel(application: Application) : AndroidViewModel(application), WorkstationActions {

  private val appContext = application.applicationContext
  private val db = WorkstationDatabase.getInstance(appContext)
  private val prefs = SessionPreferencesStore(appContext)
  private val repository = WorkstationRepository(db.workstationDao(), prefs)

  val audioEngine = WorkstationAudioEngine(appContext, initialSampleRate = 44100)
  private val sampleManager = SampleManager(context = appContext)
  private val mixerEngine = MixerEngine(audioEngine)
  private val voiceEngine = VoiceEngine(audioEngine, sampleManager, mixerEngine)
  private val recordingEngine = AudioRecordingEngine(appContext, audioEngine)
  private val expansionManager = ExpansionManager(appContext, repository, sampleManager)
  private val drumKitManager = DrumKitManager(appContext, repository, sampleManager)

  @Volatile private var activeDrumKitIdBacking: String = GmDrumNoteNames.FACTORY_KIT_ID
  @Volatile private var currentStyleIdBacking: String? = null
  private val styleDrumBindingsBacking = ConcurrentHashMap<String, String>()

  private val midiPlaybackSink = object : MidiPlaybackSink {
    override fun onMidiNoteOn(channel: Int, note: Int, velocity: Int) {
      val part = midiChannelToPart(channel)
      audioEngine.noteOn(part, note, velocity)
    }

    override fun onMidiNoteOff(channel: Int, note: Int) {
      val part = midiChannelToPart(channel)
      audioEngine.noteOff(part, note)
    }

    override fun onMidiControlChange(channel: Int, cc: Int, value: Int) {
      if (cc == 64) {
        audioEngine.setSustainPedal(midiChannelToPart(channel), value >= 64)
      }
    }

    override fun onMidiProgramChange(channel: Int, program: Int) {
      // Optionally map GM program index to built-in catalog
      val voices = voiceEngine.state.value.allVoices
      if (voices.isNotEmpty() && channel != 9) {
        val patch = voices[program % voices.size]
        val inst = sampleManager.getOrBuildInstrument(patch.sampleInstrumentId, patch.name, patch.synthesisModel)
        audioEngine.setPartInstrument(midiChannelToPart(channel), inst)
      }
    }

    override fun onMidiPanic() {
      audioEngine.allNotesOff(WorkstationPart.SONG_MIDI)
      audioEngine.allNotesOff(WorkstationPart.RHYTHM_1)
    }
  }

  private val midiPlayer = MidiSongPlayer(midiPlaybackSink)

  private val styleEngine = StyleEngine(
    audioEngine = audioEngine,
    mixerEngine = mixerEngine,
    onRequestOtsApply = { ots ->
      voiceEngine.findVoiceById(ots.voiceR1Id)?.let { voiceEngine.assignVoiceToSlot(ActiveVoiceSlot.RIGHT_1, it) }
      val r2Id = ots.voiceR2Id
      if (r2Id != null) {
        voiceEngine.findVoiceById(r2Id)?.let {
          voiceEngine.assignVoiceToSlot(ActiveVoiceSlot.RIGHT_2, it)
          voiceEngine.setPartEnabled(ActiveVoiceSlot.RIGHT_2, true)
        }
      } else {
        voiceEngine.setPartEnabled(ActiveVoiceSlot.RIGHT_2, false)
      }
      voiceEngine.findVoiceById(ots.voiceLeftId)?.let { voiceEngine.assignVoiceToSlot(ActiveVoiceSlot.LEFT, it) }
      toast("OTS ${ots.slot}: ${ots.label}")
    },
    onStyleChannelVoicesChange = { map ->
      map.forEach { (ch, voiceId) ->
        voiceEngine.loadPartInstrument(ch.part, voiceId)
      }
      val styleId = currentStyleIdBacking
      val kitId = (styleId?.let { styleDrumBindingsBacking[it] } ?: activeDrumKitIdBacking)
      if (kitId != GmDrumNoteNames.FACTORY_KIT_ID) {
        val customDrumInst = drumKitManager.buildPlayableInstrument(kitId)
        audioEngine.setPartInstrument(WorkstationPart.RHYTHM_1, customDrumInst)
        audioEngine.setPartInstrument(WorkstationPart.RHYTHM_2, customDrumInst)
      }
    },
  )

  private val _uiState = MutableStateFlow(WorkstationUiState())
  val uiState: StateFlow<WorkstationUiState> = _uiState.asStateFlow()

  private val _events = MutableSharedFlow<WorkstationUiEvent>(extraBufferCapacity = 8)
  val events: SharedFlow<WorkstationUiEvent> = _events.asSharedFlow()

  private val activeBankFlow = MutableStateFlow(0)
  private val multiPadJobs = mutableMapOf<Int, Job>()
  private var voicePreviewJob: Job? = null
  private val recordedPadNotes = mutableListOf<Int>()
  private var hasMicPermission = false
  private var saveSessionJob: Job? = null

  init {
    audioEngine.start()
    observeSubEngines()
    restoreSessionAndDatabase()
    setupExternalMidiListener()
  }

  private fun observeSubEngines() {
    viewModelScope.launch {
      audioEngine.stats.collect { stats ->
        _uiState.update { it.copy(audioStats = stats) }
      }
    }
    viewModelScope.launch {
      voiceEngine.state.collect { vs ->
        _uiState.update { it.copy(voiceState = vs) }
      }
    }
    viewModelScope.launch {
      styleEngine.state.collect { ss ->
        currentStyleIdBacking = ss.currentStyle?.id
        _uiState.update { it.copy(styleState = ss) }
      }
    }
    viewModelScope.launch {
      mixerEngine.state.collect { mx ->
        _uiState.update { it.copy(mixerState = mx) }
      }
    }
    viewModelScope.launch {
      midiPlayer.state.collect { ms ->
        _uiState.update { it.copy(midiState = ms) }
      }
    }
    viewModelScope.launch {
      midiPlayer.songs.collect { list ->
        _uiState.update { it.copy(availableSongs = list) }
      }
    }
    viewModelScope.launch {
      recordingEngine.state.collect { rs ->
        _uiState.update { it.copy(recordingState = rs) }
      }
    }
    viewModelScope.launch {
      expansionManager.state.collect { es ->
        _uiState.update { it.copy(expansionState = es) }
      }
    }
    viewModelScope.launch {
      drumKitManager.state.collect { dks ->
        _uiState.update { it.copy(drumKitState = dks) }
        val activeId = currentStyleIdBacking?.let { styleDrumBindingsBacking[it] } ?: activeDrumKitIdBacking
        if (activeId != GmDrumNoteNames.FACTORY_KIT_ID && dks.kits.any { it.id == activeId }) {
          val customDrumInst = drumKitManager.buildPlayableInstrument(activeId)
          audioEngine.setPartInstrument(WorkstationPart.RHYTHM_1, customDrumInst)
          audioEngine.setPartInstrument(WorkstationPart.RHYTHM_2, customDrumInst)
        }
      }
    }
    viewModelScope.launch {
      repository.favorites.collect { favs ->
        voiceEngine.setFavorites(favs.map { it.voiceId }.toSet())
      }
    }
    viewModelScope.launch {
      activeBankFlow.flatMapLatest { bank ->
        repository.observeRegistrations(bank)
      }.collect { regs ->
        _uiState.update { it.copy(registrationSlots = regs) }
      }
    }
    viewModelScope.launch {
      repository.keyboardSets.collect { sets ->
        _uiState.update { st ->
          val nextActiveId = if (st.activeKeyboardSetId != null && sets.any { it.id == st.activeKeyboardSetId }) {
            st.activeKeyboardSetId
          } else {
            null
          }
          st.copy(keyboardSets = sets, activeKeyboardSetId = nextActiveId)
        }
      }
    }
  }

  private fun restoreSessionAndDatabase() {
    viewModelScope.launch {
      val snap = repository.loadSession()
      audioEngine.masterVolume = snap.masterVolume
      audioEngine.masterBalance = snap.balance
      audioEngine.setSampleRate(snap.sampleRate)
      styleEngine.setTempo(snap.tempoBpm)
      styleEngine.setTranspose(snap.transpose)
      styleEngine.setAcmp(snap.acmpEnabled)
      if (styleEngine.state.value.autoFillEnabled != snap.autoFillEnabled) {
        styleEngine.toggleAutoFill()
      }
      styleEngine.state.value.allStyles.find { it.id == snap.styleId }?.let {
        styleEngine.selectStyle(it, applyTempo = false)
      }

      val mode = runCatching { ChordFingeringMode.valueOf(snap.chordFingeringMode) }
        .getOrDefault(ChordFingeringMode.FINGERED)

      if (snap.right1VoiceId.isEmpty()) {
        voiceEngine.clearSlot(ActiveVoiceSlot.RIGHT_1)
      } else {
        voiceEngine.findVoiceById(snap.right1VoiceId)?.let { voiceEngine.assignVoiceToSlot(ActiveVoiceSlot.RIGHT_1, it) }
        voiceEngine.setPartEnabled(ActiveVoiceSlot.RIGHT_1, snap.right1Enabled)
      }
      if (snap.right2VoiceId.isEmpty()) {
        voiceEngine.clearSlot(ActiveVoiceSlot.RIGHT_2)
      } else {
        voiceEngine.findVoiceById(snap.right2VoiceId)?.let { voiceEngine.assignVoiceToSlot(ActiveVoiceSlot.RIGHT_2, it) }
        voiceEngine.setPartEnabled(ActiveVoiceSlot.RIGHT_2, snap.right2Enabled)
      }
      if (snap.leftVoiceId.isEmpty()) {
        voiceEngine.clearSlot(ActiveVoiceSlot.LEFT)
      } else {
        voiceEngine.findVoiceById(snap.leftVoiceId)?.let { voiceEngine.assignVoiceToSlot(ActiveVoiceSlot.LEFT, it) }
        voiceEngine.setPartEnabled(ActiveVoiceSlot.LEFT, snap.leftEnabled)
      }

      val loadedBindings = deserializeStyleDrumBindings(snap.styleDrumKitBindingsJson)
      styleDrumBindingsBacking.clear()
      styleDrumBindingsBacking.putAll(loadedBindings)
      activeDrumKitIdBacking = snap.activeDrumKitId
      currentStyleIdBacking = snap.styleId

      activeBankFlow.value = snap.activeRegBank
      _uiState.update {
        it.copy(
          keySizeScale = snap.keySizeScale,
          upperOctave = snap.octaveShift,
          transpose = snap.transpose,
          splitPointMidi = snap.splitPointMidi,
          fingeringMode = mode,
          activeRegBank = snap.activeRegBank,
          activeKeyboardSetId = snap.activeKeyboardSetId.takeIf { id -> id.isNotBlank() },
          activeArrangerDrumKitId = snap.activeDrumKitId,
          styleDrumKitBindings = loadedBindings,
        )
      }
      applyDrumKitToAudioEngine(
        styleId = styleEngine.state.value.currentStyle?.id,
        kitId = loadedBindings[styleEngine.state.value.currentStyle?.id] ?: snap.activeDrumKitId,
      )

      // Reload user voices from Room
      repository.userVoices.collect { userEntities ->
        userEntities.forEach { entity ->
          if (voiceEngine.findVoiceById(entity.id) == null) {
            voiceEngine.findVoiceById(entity.basePatchId)?.let { base ->
              voiceEngine.saveAsUserVoice(
                base,
                entity.name,
                VoiceSlotEditParams(entity.attackScale, entity.releaseScale, entity.brightnessScale, entity.octaveOffset),
              )
            }
          }
        }
      }
    }

    viewModelScope.launch {
      // Reload previously imported SF2/STY/SFZ/WAV/MIDI expansion packs offline
      delay(300L)
      val persisted = expansionManager.reloadPersistedPacks()
      persisted.instruments.forEach { inst ->
        voiceEngine.registerImportedVoice(inst, VoiceCategory.USER_EXPANSION, "EXP")
      }
      persisted.midiSongs.forEach { midiPlayer.addImportedSong(it) }
      persisted.styles.forEach { style ->
        styleEngine.replaceOrAddUserStyle(style)
      }
    }
  }

  private fun scheduleSessionSave() {
    saveSessionJob?.cancel()
    saveSessionJob = viewModelScope.launch {
      delay(400L)
      val st = _uiState.value
      val vs = st.voiceState
      val ss = st.styleState
      repository.saveSession(
        WorkstationSessionSnapshot(
          masterVolume = st.audioStats.masterVolume,
          balance = st.audioStats.balance,
          sampleRate = st.audioStats.sampleRate,
          keySizeScale = st.keySizeScale,
          octaveShift = st.upperOctave,
          transpose = st.transpose,
          splitPointMidi = st.splitPointMidi,
          chordFingeringMode = st.fingeringMode.name,
          right1VoiceId = vs.right1Voice?.id ?: "",
          right2VoiceId = vs.right2Voice?.id ?: "",
          leftVoiceId = vs.leftVoice?.id ?: "",
          right1Enabled = vs.right1Enabled,
          right2Enabled = vs.right2Enabled,
          leftEnabled = vs.leftEnabled,
          styleId = ss.currentStyle?.id ?: "style_concert_grand_pop",
          tempoBpm = ss.tempoBpm,
          acmpEnabled = ss.acmpEnabled,
          autoFillEnabled = ss.autoFillEnabled,
          activeRegBank = st.activeRegBank,
          activeDrumKitId = st.activeArrangerDrumKitId,
          styleDrumKitBindingsJson = serializeStyleDrumBindings(st.styleDrumKitBindings),
          activeKeyboardSetId = st.activeKeyboardSetId ?: "",
        ),
      )
    }
  }

  fun onMicrophonePermissionResult(granted: Boolean) {
    hasMicPermission = granted
    recordingEngine.setIncludeMicrophone(granted)
    toast(if (granted) "Microphone input enabled for WAV recording" else "Microphone permission denied — recording internal mix only")
  }

  fun importSf2Uri(uri: Uri) {
    importExpansionUri(uri = uri, expectedExtensions = setOf("sf2"), autoApplyImported = true)
  }

  fun importStyUri(uri: Uri) {
    importExpansionUri(uri = uri, expectedExtensions = setOf("sty", "prs"), autoApplyImported = true)
  }

  fun importDrumKitUris(uris: List<Uri>) {
    viewModelScope.launch {
      val result = drumKitManager.importDrumKitFromUris(uris)
      result.onSuccess { kit ->
        assignDrumKitToStyle(kit.id, _uiState.value.styleState.currentStyle?.id)
        toast("Imported Drum Kit '${kit.name}' & linked to Arranger")
      }.onFailure { err ->
        toast(err.message ?: "Drum kit import failed")
      }
    }
  }

  fun importDrumSlotSampleUri(kitId: String, mappingId: String, uri: Uri) {
    viewModelScope.launch {
      val result = drumKitManager.importSampleIntoMapping(kitId, mappingId, uri)
      result.onSuccess { kit ->
        if (_uiState.value.activeStyleDrumKitId == kit.id) {
          applyDrumKitToAudioEngine(_uiState.value.styleState.currentStyle?.id, kit.id)
        }
        toast("Sample assigned in '${kit.name}'")
      }.onFailure { err ->
        toast(err.message ?: "Sample import failed")
      }
    }
  }

  fun importExpansionUri(
    uri: Uri,
    expectedExtensions: Set<String>? = null,
    autoApplyImported: Boolean = true,
  ) {
    viewModelScope.launch {
      val result = expansionManager.importFromUri(uri, expectedExtensions = expectedExtensions)
      result.onSuccess { res ->
        val badge = if (res.entity.format == "SF2") "SF2" else res.entity.format
        val importedPatches = res.instruments.map { inst ->
          voiceEngine.registerImportedVoice(inst, VoiceCategory.USER_EXPANSION, badge)
        }
        res.midiSongs.forEach { song ->
          midiPlayer.addImportedSong(song)
        }
        res.styles.forEach { style ->
          styleEngine.replaceOrAddUserStyle(style)
        }
        if (autoApplyImported) {
          if (res.styles.isNotEmpty()) {
            val importedStyle = res.styles.first()
            currentStyleIdBacking = importedStyle.id
            styleEngine.selectStyle(importedStyle, applyTempo = true)
            // Keep custom drum kit if active, and prompt user with Drum Kit Selector for this imported STY
            val currentKitId = _uiState.value.activeArrangerDrumKitId
            applyDrumKitToAudioEngine(importedStyle.id, styleDrumBindingsBacking[importedStyle.id] ?: currentKitId)
            _uiState.update { it.copy(pendingStyDrumSelectionStyleId = importedStyle.id) }
          }
          if (importedPatches.isNotEmpty() && res.entity.format == "SF2") {
            voiceEngine.assignVoiceToActiveSlot(importedPatches.first())
          }
        }
        val msg = res.summaryDetail.ifBlank {
          "Imported ${res.entity.name} (${res.entity.presetCount} presets)"
        }
        toast(msg)
      }.onFailure { err ->
        toast(err.message ?: "Import failed")
      }
    }
  }

  // ---- WorkstationActions Implementation ----

  override fun togglePower() {
    val next = !_uiState.value.powerOn
    if (!next) {
      styleEngine.stop()
      midiPlayer.stop()
      stopAllMultiPads()
      audioEngine.panic()
    }
    _uiState.update { it.copy(powerOn = next) }
  }

  override fun navigateTo(page: LcdPage) {
    _uiState.update { it.copy(currentLcdPage = page, showMenuOverlay = false) }
  }

  override fun toggleMenuOverlay() {
    _uiState.update { it.copy(showMenuOverlay = !it.showMenuOverlay) }
  }

  override fun toggleHelpOverlay() {
    _uiState.update { it.copy(showHelpOverlay = !it.showHelpOverlay) }
  }

  override fun dismissToast() {
    _uiState.update { it.copy(statusToast = null) }
  }

  override fun onKeyDown(midiNote: Int, velocity: Int) {
    if (!_uiState.value.powerOn) return
    val st = _uiState.value
    val nextHeld = st.activePressedNotes + midiNote
    _uiState.update { it.copy(activePressedNotes = nextHeld) }
    logMidi("NOTE ON  ch1  note=$midiNote  vel=$velocity")

    val recordingPad = st.isRecordingMultiPad
    if (recordingPad != null) {
      recordedPadNotes.add(midiNote)
    }

    val vs = st.voiceState
    val ss = st.styleState
    val isLeftZone = ss.acmpEnabled && midiNote < st.splitPointMidi

    if (isLeftZone || st.fingeringMode == ChordFingeringMode.FULL_KEYBOARD || st.fingeringMode == ChordFingeringMode.AI_FINGERED) {
      val chord = ChordRecognizer.detect(nextHeld, st.fingeringMode, st.splitPointMidi, st.detectedChord)
      if (chord != null && chord != st.detectedChord) {
        _uiState.update { it.copy(detectedChord = chord) }
        styleEngine.updateChord(chord)
      }
    }

    if (isLeftZone) {
      styleEngine.onLeftZoneKeyActivity(hasHeldKeys = true)
      if (vs.leftEnabled && vs.leftVoice != null) {
        val note = (midiNote + st.transpose + vs.leftEdit.octaveOffset * 12).coerceIn(0, 127)
        audioEngine.noteOn(WorkstationPart.LEFT, note, velocity)
      }
      if (!ss.acmpEnabled) return
    }

    if (!isLeftZone) {
      val upperNote = (midiNote + st.transpose + st.upperOctave * 12).coerceIn(0, 127)
      if (vs.right1Enabled && vs.right1Voice != null) {
        val r1Note = (upperNote + vs.right1Edit.octaveOffset * 12).coerceIn(0, 127)
        audioEngine.noteOn(WorkstationPart.RIGHT_1, r1Note, velocity)
        if (vs.harmonyEnabled) {
          audioEngine.noteOn(WorkstationPart.RIGHT_1, (r1Note + 4).coerceIn(0, 127), (velocity * 0.72f).toInt().coerceAtLeast(1))
        }
      }
      if (vs.right2Enabled && vs.right2Voice != null) {
        val r2Note = (upperNote + vs.right2Edit.octaveOffset * 12).coerceIn(0, 127)
        audioEngine.noteOn(WorkstationPart.RIGHT_2, r2Note, velocity)
      }
      midiPlayer.recordLiveEvent(0, MidiEventType.NOTE_ON, upperNote, velocity)
    }
  }

  override fun onKeyUp(midiNote: Int) {
    val st = _uiState.value
    val nextHeld = st.activePressedNotes - midiNote
    _uiState.update { it.copy(activePressedNotes = nextHeld) }

    val vs = st.voiceState
    val ss = st.styleState
    val isLeftZone = ss.acmpEnabled && midiNote < st.splitPointMidi

    if (isLeftZone) {
      val leftStillHeld = nextHeld.any { it < st.splitPointMidi }
      styleEngine.onLeftZoneKeyActivity(hasHeldKeys = leftStillHeld)
      val note = (midiNote + st.transpose + vs.leftEdit.octaveOffset * 12).coerceIn(0, 127)
      audioEngine.noteOff(WorkstationPart.LEFT, note)
    } else {
      val upperNote = (midiNote + st.transpose + st.upperOctave * 12).coerceIn(0, 127)
      val r1Note = (upperNote + vs.right1Edit.octaveOffset * 12).coerceIn(0, 127)
      val r2Note = (upperNote + vs.right2Edit.octaveOffset * 12).coerceIn(0, 127)
      audioEngine.noteOff(WorkstationPart.RIGHT_1, r1Note)
      if (vs.harmonyEnabled) {
        audioEngine.noteOff(WorkstationPart.RIGHT_1, (r1Note + 4).coerceIn(0, 127))
      }
      audioEngine.noteOff(WorkstationPart.RIGHT_2, r2Note)
      midiPlayer.recordLiveEvent(0, MidiEventType.NOTE_OFF, upperNote, 0)
    }
  }

  override fun setMasterVolume(volume: Float) {
    audioEngine.masterVolume = volume.coerceIn(0f, 1.2f)
    _uiState.update { it.copy(audioStats = it.audioStats.copy(masterVolume = audioEngine.masterVolume)) }
    scheduleSessionSave()
  }

  override fun setMasterBalance(balance: Float) {
    audioEngine.masterBalance = balance.coerceIn(-1f, 1f)
    _uiState.update { it.copy(audioStats = it.audioStats.copy(balance = audioEngine.masterBalance)) }
    scheduleSessionSave()
  }

  override fun setMicGain(gain: Float) {
    recordingEngine.setMicGain(gain * 1.5f)
  }

  override fun setTranspose(semitones: Int) {
    val clamped = semitones.coerceIn(-12, 12)
    styleEngine.setTranspose(clamped)
    midiPlayer.setTranspose(clamped)
    _uiState.update { it.copy(transpose = clamped) }
    scheduleSessionSave()
  }

  override fun setUpperOctave(octaves: Int) {
    val clamped = octaves.coerceIn(-2, 2)
    _uiState.update { it.copy(upperOctave = clamped) }
    scheduleSessionSave()
  }

  override fun setSplitPoint(midiNote: Int) {
    val clamped = midiNote.coerceIn(42, 72)
    _uiState.update { it.copy(splitPointMidi = clamped) }
    scheduleSessionSave()
  }

  override fun setFingeringMode(mode: ChordFingeringMode) {
    _uiState.update { it.copy(fingeringMode = mode) }
    scheduleSessionSave()
  }

  override fun setKeySizeScale(scale: Float) {
    _uiState.update { it.copy(keySizeScale = scale.coerceIn(1f, 3f)) }
    scheduleSessionSave()
  }

  override fun setKeyViewportOffset(offset: Float) {
    _uiState.update { it.copy(keyViewportOffset = offset.coerceIn(0f, 1f)) }
  }

  override fun setPitchBend(semitones: Float) {
    val clamped = semitones.coerceIn(-12f, 12f)
    audioEngine.setPitchBend(WorkstationPart.RIGHT_1, clamped)
    audioEngine.setPitchBend(WorkstationPart.RIGHT_2, clamped)
    audioEngine.setPitchBend(WorkstationPart.LEFT, clamped)
    _uiState.update { it.copy(pitchBendSemis = clamped) }
  }

  override fun setModWheel(depth: Float) {
    val clamped = depth.coerceIn(0f, 1f)
    audioEngine.setModulation(WorkstationPart.RIGHT_1, clamped)
    audioEngine.setModulation(WorkstationPart.RIGHT_2, clamped)
    audioEngine.setModulation(WorkstationPart.LEFT, clamped)
    _uiState.update { it.copy(modWheelDepth = clamped) }
  }

  override fun setLiveKnobCutoff(value: Float) {
    val v = value.coerceIn(0f, 1f)
    _uiState.update { it.copy(liveKnobCutoff = v) }
    val slot = _uiState.value.voiceState.activePartSelection
    val cur = when (slot) {
      ActiveVoiceSlot.RIGHT_1 -> _uiState.value.voiceState.right1Edit
      ActiveVoiceSlot.RIGHT_2 -> _uiState.value.voiceState.right2Edit
      ActiveVoiceSlot.LEFT -> _uiState.value.voiceState.leftEdit
    }
    voiceEngine.updateSlotEditParams(slot, cur.copy(brightnessScale = 0.35f + v * 1.45f))
  }

  override fun setLiveKnobResonance(value: Float) {
    val v = value.coerceIn(0f, 1f)
    _uiState.update { it.copy(liveKnobResonance = v) }
    updateEffectsState { it.copy(reverb = it.reverb.copy(level = v * 0.75f)) }
  }

  override fun panicAllNotesOff() {
    styleEngine.stop()
    midiPlayer.stop()
    stopAllMultiPads()
    audioEngine.panic()
    _uiState.update { it.copy(activePressedNotes = emptySet()) }
    toast("All Notes Off (Panic)")
  }

  override fun selectVoiceCategory(category: VoiceCategory) {
    _uiState.update { it.copy(selectedVoiceCategory = category) }
  }

  override fun setVoiceSearch(query: String) {
    _uiState.update { it.copy(voiceSearchQuery = query) }
  }

  override fun selectVoicePatch(patch: VoicePatch) {
    val activeSlot = voiceEngine.state.value.activePartSelection
    voiceEngine.assignVoiceToSlot(activeSlot, patch)
    voiceEngine.setPartEnabled(activeSlot, true)
    _uiState.value.activeKeyboardSetId?.let { activeSetId ->
      updateKeyboardSetFromLive(activeSetId)
    }
    scheduleSessionSave()
  }

  override fun previewVoicePatch(patch: VoicePatch) {
    val activeSlot = voiceEngine.state.value.activePartSelection
    voiceEngine.assignVoiceToSlot(activeSlot, patch)
    voiceEngine.setPartEnabled(activeSlot, true)
    _uiState.value.activeKeyboardSetId?.let { activeSetId ->
      updateKeyboardSetFromLive(activeSetId)
    }
    scheduleSessionSave()
    val slot = _uiState.value.voiceState.activePartSelection
    val part = when (slot) {
      ActiveVoiceSlot.RIGHT_1 -> WorkstationPart.RIGHT_1
      ActiveVoiceSlot.RIGHT_2 -> WorkstationPart.RIGHT_2
      ActiveVoiceSlot.LEFT -> WorkstationPart.LEFT
    }
    voicePreviewJob?.cancel()
    voicePreviewJob = viewModelScope.launch {
      val notes = when (patch.category) {
        VoiceCategory.DRUMS -> listOf(36, 42, 38, 42, 36, 49)
        else -> when {
          patch.id.startsWith("bass_") || patch.id == "syn_bass" -> listOf(36, 43, 48, 43, 36)
          patch.id == "str_cello" -> listOf(48, 55, 60, 55)
          else -> listOf(60, 64, 67, 72)
        }
      }
      val stepMs = if (patch.category == VoiceCategory.DRUMS) 170L else 210L
      notes.forEachIndexed { idx, midi ->
        val vel = if (idx == 0 || idx == notes.lastIndex) 108 else 92
        audioEngine.noteOn(part, midi, vel)
        delay(stepMs)
        audioEngine.noteOff(part, midi)
      }
    }
    toast("Previewing ${patch.name}")
  }

  override fun cycleVoice(delta: Int) {
    val vs = _uiState.value.voiceState
    val list = vs.allVoices
    if (list.isEmpty()) return
    val currentId = when (vs.activePartSelection) {
      ActiveVoiceSlot.RIGHT_1 -> vs.right1Voice?.id
      ActiveVoiceSlot.RIGHT_2 -> vs.right2Voice?.id
      ActiveVoiceSlot.LEFT -> vs.leftVoice?.id
    }
    val idx = list.indexOfFirst { it.id == currentId }.let { if (it < 0) 0 else it }
    val nextIdx = Math.floorMod(idx + delta, list.size)
    val nextPatch = list[nextIdx]
    voiceEngine.assignVoiceToActiveSlot(nextPatch)
    _uiState.update { it.copy(selectedVoiceCategory = nextPatch.category) }
    scheduleSessionSave()
    toast("${vs.activePartSelection.label}: ${nextPatch.name}")
  }

  override fun setActiveVoiceSlot(slot: ActiveVoiceSlot) {
    voiceEngine.setActiveSlot(slot)
  }

  override fun toggleVoiceSlotEnabled(slot: ActiveVoiceSlot) {
    voiceEngine.togglePartEnabled(slot)
    scheduleSessionSave()
  }

  override fun toggleFavoriteVoice(voiceId: String) {
    val isFav = voiceId in _uiState.value.voiceState.favoriteIds
    voiceEngine.toggleFavorite(voiceId)
    viewModelScope.launch { repository.setFavorite(voiceId, !isFav) }
  }

  override fun updateVoiceSlotEdit(slot: ActiveVoiceSlot, params: VoiceSlotEditParams) {
    voiceEngine.updateSlotEditParams(slot, params)
  }

  override fun saveUserVoice(name: String) {
    val vs = _uiState.value.voiceState
    val slot = vs.activePartSelection
    val base = when (slot) {
      ActiveVoiceSlot.RIGHT_1 -> vs.right1Voice
      ActiveVoiceSlot.RIGHT_2 -> vs.right2Voice
      ActiveVoiceSlot.LEFT -> vs.leftVoice
    } ?: return
    val params = when (slot) {
      ActiveVoiceSlot.RIGHT_1 -> vs.right1Edit
      ActiveVoiceSlot.RIGHT_2 -> vs.right2Edit
      ActiveVoiceSlot.LEFT -> vs.leftEdit
    }
    val created = voiceEngine.saveAsUserVoice(base, name, params)
    voiceEngine.assignVoiceToActiveSlot(created)
    viewModelScope.launch {
      repository.insertUserVoice(
        UserVoiceEntity(
          id = created.id,
          name = created.name,
          basePatchId = base.id,
          attackScale = params.attackScale,
          releaseScale = params.releaseScale,
          brightnessScale = params.brightnessScale,
          octaveOffset = params.octaveOffset,
        ),
      )
    }
    toast("Saved User Voice: ${created.name}")
  }

  override fun toggleHarmony() = voiceEngine.toggleHarmony()
  override fun toggleSustain() = voiceEngine.toggleSustain()
  override fun toggleDsp() = voiceEngine.toggleDspForRight1()

  override fun selectStyleCategory(category: StyleCategory) {
    _uiState.update { it.copy(selectedStyleCategory = category) }
  }

  override fun selectStyle(style: ArrangerStyle) {
    currentStyleIdBacking = style.id
    styleEngine.selectStyle(style, applyTempo = true)
    val kitId = styleDrumBindingsBacking[style.id] ?: _uiState.value.activeArrangerDrumKitId
    applyDrumKitToAudioEngine(style.id, kitId)
    scheduleSessionSave()
  }

  override fun toggleStyleStartStop() = styleEngine.startOrStop()
  override fun selectStyleSection(section: StyleSection) = styleEngine.selectSection(section)

  override fun triggerFillIn() {
    val cur = _uiState.value.styleState.currentSection
    val fill = when (cur) {
      StyleSection.MAIN_B, StyleSection.FILL_B -> StyleSection.FILL_B
      StyleSection.MAIN_C, StyleSection.FILL_C -> StyleSection.FILL_C
      StyleSection.MAIN_D, StyleSection.FILL_D -> StyleSection.FILL_D
      else -> StyleSection.FILL_A
    }
    styleEngine.selectSection(fill)
    toast("Fill-In: ${fill.label}")
  }

  override fun selectChordPreset(chord: DetectedChord) {
    styleEngine.updateChord(chord)
    _uiState.update { it.copy(detectedChord = chord) }
    toast("Chord: ${chord.displaySymbol}")
  }

  override fun toggleAcmp() {
    styleEngine.toggleAcmp()
    scheduleSessionSave()
  }

  override fun toggleSyncStart() = styleEngine.toggleSyncStart()
  override fun toggleSyncStop() = styleEngine.toggleSyncStop()
  override fun toggleAutoFill() {
    styleEngine.toggleAutoFill()
    scheduleSessionSave()
  }

  override fun triggerFadeInOut() = styleEngine.triggerFadeInOut()

  override fun setTempo(bpm: Int) {
    styleEngine.setTempo(bpm)
    midiPlayer.setTempo(bpm)
    scheduleSessionSave()
  }

  override fun tapTempo() = styleEngine.tapTempo()

  override fun resetTempo() {
    val def = _uiState.value.styleState.currentStyle?.defaultBpm ?: 120
    setTempo(def)
  }

  override fun toggleStyleChannel(channel: StyleChannelType) = styleEngine.toggleChannelEnabled(channel)
  override fun selectOts(slot: Int) = styleEngine.selectOts(slot)

  override fun toggleStylePatternStep(section: StyleSection, stepIndex: Int, channel: StyleChannelType, midiNote: Int) {
    styleEngine.togglePatternStep(section, stepIndex, channel, midiNote)
  }

  override fun importCurrentMidiAsStyle(styleName: String) {
    val song = _uiState.value.midiState.currentSong ?: return
    val created = styleEngine.importMidiAsStyle(song, styleName)
    toast("Created Style '${created.name}' from MIDI")
  }

  // ---- Multi Pads ----

  override fun selectMultiPadBank(bankIndex: Int) {
    stopAllMultiPads()
    _uiState.update { it.copy(activeMultiPadBankIndex = bankIndex.coerceIn(0, 3)) }
  }

  override fun triggerMultiPad(padIndex: Int) {
    val bankIdx = _uiState.value.activeMultiPadBankIndex
    val bank = _uiState.value.multiPadBanks.getOrNull(bankIdx) ?: return
    val pad = bank.pads.find { it.padIndex == padIndex } ?: return

    if (pad.isPlaying) {
      stopMultiPad(bankIdx, padIndex)
      return
    }

    voiceEngine.loadPartInstrument(WorkstationPart.MULTI_PAD, pad.voiceId)
    mixerEngine.setVolume(WorkstationPart.MULTI_PAD, pad.volume)
    setPadPlayingFlag(bankIdx, padIndex, true)

    val job = viewModelScope.launch(Dispatchers.Default) {
      do {
        val bpm = _uiState.value.styleState.tempoBpm
        val stepMs = (60_000.0 / bpm / 2.0).toLong().coerceAtLeast(80L)
        val chord = _uiState.value.detectedChord
        val rootShift = if (pad.isAudioPhrase) 0 else chord.rootPitchClass

        for (rawNote in pad.notes) {
          if (!isActive) break
          val note = (rawNote + rootShift + _uiState.value.transpose).coerceIn(24, 108)
          audioEngine.noteOn(WorkstationPart.MULTI_PAD, note, 100)
          delay(stepMs)
          audioEngine.noteOff(WorkstationPart.MULTI_PAD, note)
        }
      } while (isActive && (pad.playMode == MultiPadPlayMode.LOOP || pad.playMode == MultiPadPlayMode.SYNC))
      setPadPlayingFlag(bankIdx, padIndex, false)
    }
    multiPadJobs[padIndex] = job
  }

  private fun stopMultiPad(bankIdx: Int, padIndex: Int) {
    multiPadJobs.remove(padIndex)?.cancel()
    audioEngine.allNotesOff(WorkstationPart.MULTI_PAD)
    setPadPlayingFlag(bankIdx, padIndex, false)
  }

  override fun stopAllMultiPads() {
    multiPadJobs.values.forEach { it.cancel() }
    multiPadJobs.clear()
    audioEngine.allNotesOff(WorkstationPart.MULTI_PAD)
    _uiState.update { st ->
      st.copy(
        multiPadBanks = st.multiPadBanks.map { b ->
          b.copy(pads = b.pads.map { it.copy(isPlaying = false) })
        },
      )
    }
  }

  override fun setMultiPadMode(padIndex: Int, mode: MultiPadPlayMode) {
    val bankIdx = _uiState.value.activeMultiPadBankIndex
    _uiState.update { st ->
      st.copy(
        multiPadBanks = st.multiPadBanks.mapIndexed { idx, b ->
          if (idx == bankIdx) b.copy(pads = b.pads.map { if (it.padIndex == padIndex) it.copy(playMode = mode) else it })
          else b
        },
      )
    }
  }

  override fun setMultiPadVolume(padIndex: Int, volume: Float) {
    val bankIdx = _uiState.value.activeMultiPadBankIndex
    val v = volume.coerceIn(0f, 1f)
    mixerEngine.setVolume(WorkstationPart.MULTI_PAD, v)
    _uiState.update { st ->
      st.copy(
        multiPadBanks = st.multiPadBanks.mapIndexed { idx, b ->
          if (idx == bankIdx) b.copy(pads = b.pads.map { if (it.padIndex == padIndex) it.copy(volume = v) else it })
          else b
        },
      )
    }
  }

  override fun toggleRecordMultiPad(padIndex: Int) {
    val curRec = _uiState.value.isRecordingMultiPad
    if (curRec == padIndex) {
      val bankIdx = _uiState.value.activeMultiPadBankIndex
      val captured = recordedPadNotes.toList()
      recordedPadNotes.clear()
      _uiState.update { st ->
        val updatedBanks = if (captured.isNotEmpty()) {
          st.multiPadBanks.mapIndexed { idx, b ->
            if (idx == bankIdx) b.copy(pads = b.pads.map { if (it.padIndex == padIndex) it.copy(notes = captured, label = "Rec Pad $padIndex") else it })
            else b
          }
        } else st.multiPadBanks
        st.copy(isRecordingMultiPad = null, multiPadBanks = updatedBanks)
      }
      toast("Saved Multi Pad $padIndex (${captured.size} notes)")
    } else {
      recordedPadNotes.clear()
      _uiState.update { it.copy(isRecordingMultiPad = padIndex) }
      toast("Recording Multi Pad $padIndex — play keyboard notes then tap SAVE REC")
    }
  }

  private fun setPadPlayingFlag(bankIdx: Int, padIndex: Int, playing: Boolean) {
    _uiState.update { st ->
      st.copy(
        multiPadBanks = st.multiPadBanks.mapIndexed { idx, b ->
          if (idx == bankIdx) b.copy(pads = b.pads.map { if (it.padIndex == padIndex) it.copy(isPlaying = playing) else it })
          else b
        },
      )
    }
  }

  // ---- Registration Memory ----

  override fun selectRegBank(deltaOrIndex: Int, isRelative: Boolean) {
    val next = if (isRelative) Math.floorMod(_uiState.value.activeRegBank + deltaOrIndex, 8) else deltaOrIndex.coerceIn(0, 7)
    activeBankFlow.value = next
    _uiState.update { it.copy(activeRegBank = next, activeRegSlot = null) }
    scheduleSessionSave()
  }

  override fun pressRegistrationSlot(slotIndex: Int) {
    val st = _uiState.value
    if (st.regMemoryArmed) {
      val vs = st.voiceState
      val ss = st.styleState
      val entity = RegistrationMemoryEntity(
        bankIndex = st.activeRegBank,
        slotIndex = slotIndex,
        label = vs.right1Voice?.name?.take(12) ?: "Reg $slotIndex",
        right1VoiceId = vs.right1Voice?.id ?: "piano_grand",
        right2VoiceId = vs.right2Voice?.id,
        leftVoiceId = vs.leftVoice?.id ?: "str_ensemble",
        right1Enabled = vs.right1Enabled,
        right2Enabled = vs.right2Enabled,
        leftEnabled = vs.leftEnabled,
        styleId = ss.currentStyle?.id ?: "style_concert_grand_pop",
        tempoBpm = ss.tempoBpm,
        transpose = st.transpose,
        splitPointMidi = st.splitPointMidi,
        mixerJson = serializeMixer(st.mixerState.channels),
        effectsJson = serializeEffects(st.effectsState),
        multiPadBankIndex = st.activeMultiPadBankIndex,
      )
      viewModelScope.launch { repository.saveRegistration(entity) }
      _uiState.update { it.copy(regMemoryArmed = false, activeRegSlot = slotIndex) }
      toast("Saved Registration B${st.activeRegBank + 1} Slot $slotIndex")
    } else {
      val saved = st.registrationSlots.find { it.slotIndex == slotIndex }
      if (saved == null) {
        toast("Slot $slotIndex is empty — press MEM then $slotIndex to store")
        return
      }
      voiceEngine.findVoiceById(saved.right1VoiceId)?.let { voiceEngine.assignVoiceToSlot(ActiveVoiceSlot.RIGHT_1, it) }
      saved.right2VoiceId?.let { r2Id -> voiceEngine.findVoiceById(r2Id)?.let { voiceEngine.assignVoiceToSlot(ActiveVoiceSlot.RIGHT_2, it) } }
      voiceEngine.findVoiceById(saved.leftVoiceId)?.let { voiceEngine.assignVoiceToSlot(ActiveVoiceSlot.LEFT, it) }
      voiceEngine.setPartEnabled(ActiveVoiceSlot.RIGHT_1, saved.right1Enabled)
      voiceEngine.setPartEnabled(ActiveVoiceSlot.RIGHT_2, saved.right2Enabled)
      voiceEngine.setPartEnabled(ActiveVoiceSlot.LEFT, saved.leftEnabled)

      if (!st.regFreezeEnabled) {
        st.styleState.allStyles.find { it.id == saved.styleId }?.let { styleEngine.selectStyle(it, applyTempo = false) }
        setTempo(saved.tempoBpm)
        setTranspose(saved.transpose)
        setSplitPoint(saved.splitPointMidi)
      }
      deserializeMixer(saved.mixerJson)?.let { mixerEngine.applySnapshot(it) }
      deserializeEffects(saved.effectsJson)?.let { fx ->
        audioEngine.setEffectsState(fx)
        _uiState.update { it.copy(effectsState = fx) }
      }
      _uiState.update { it.copy(activeRegSlot = slotIndex, activeMultiPadBankIndex = saved.multiPadBankIndex) }
      toast("Recalled Registration B${st.activeRegBank + 1} Slot $slotIndex")
    }
  }

  override fun toggleRegMemoryArm() {
    _uiState.update { it.copy(regMemoryArmed = !it.regMemoryArmed) }
  }

  override fun toggleRegFreeze() {
    _uiState.update { it.copy(regFreezeEnabled = !it.regFreezeEnabled) }
  }

  override fun exportRegistrationsJson(): String {
    val arr = JSONArray()
    _uiState.value.registrationSlots.forEach { r ->
      arr.put(
        JSONObject().apply {
          put("bankIndex", r.bankIndex)
          put("slotIndex", r.slotIndex)
          put("label", r.label)
          put("right1VoiceId", r.right1VoiceId)
          put("right2VoiceId", r.right2VoiceId ?: "")
          put("leftVoiceId", r.leftVoiceId)
          put("right1Enabled", r.right1Enabled)
          put("right2Enabled", r.right2Enabled)
          put("leftEnabled", r.leftEnabled)
          put("styleId", r.styleId)
          put("tempoBpm", r.tempoBpm)
          put("transpose", r.transpose)
          put("splitPointMidi", r.splitPointMidi)
          put("mixerJson", r.mixerJson)
          put("effectsJson", r.effectsJson)
          put("multiPadBankIndex", r.multiPadBankIndex)
        },
      )
    }
    return arr.toString(2)
  }

  override fun importRegistrationsJson(json: String) {
    viewModelScope.launch {
      runCatching {
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
          val o = arr.getJSONObject(i)
          repository.saveRegistration(
            RegistrationMemoryEntity(
              bankIndex = _uiState.value.activeRegBank,
              slotIndex = o.getInt("slotIndex").coerceIn(1, 8),
              label = o.optString("label", "Imported"),
              right1VoiceId = o.optString("right1VoiceId", "piano_grand"),
              right2VoiceId = o.optString("right2VoiceId").ifEmpty { null },
              leftVoiceId = o.optString("leftVoiceId", "str_ensemble"),
              right1Enabled = o.optBoolean("right1Enabled", true),
              right2Enabled = o.optBoolean("right2Enabled", false),
              leftEnabled = o.optBoolean("leftEnabled", true),
              styleId = o.optString("styleId", "style_concert_grand_pop"),
              tempoBpm = o.optInt("tempoBpm", 120),
              transpose = o.optInt("transpose", 0),
              splitPointMidi = o.optInt("splitPointMidi", 54),
              mixerJson = o.optString("mixerJson", "{}"),
              effectsJson = o.optString("effectsJson", "{}"),
              multiPadBankIndex = o.optInt("multiPadBankIndex", 0),
            ),
          )
        }
        toast("Imported ${arr.length()} Registration slots")
      }.onFailure {
        toast("Invalid Registration JSON")
      }
    }
  }

  // ---- Keyboard Sets & Empty Keyboard Set Management ----

  override fun createEmptyKeyboardSet(name: String?, activateImmediately: Boolean) {
    val existingCount = _uiState.value.keyboardSets.size + 1
    val cleanName = name?.trim()?.takeIf { it.isNotBlank() } ?: "Empty Set $existingCount"
    val now = System.currentTimeMillis()
    val entity = KeyboardSetEntity(
      id = "kbdset_empty_$now",
      name = cleanName,
      isEmpty = true,
      right1VoiceId = null,
      right2VoiceId = null,
      leftVoiceId = null,
      right1Enabled = false,
      right2Enabled = false,
      leftEnabled = false,
      right1Octave = 0,
      right2Octave = 0,
      leftOctave = 0,
      transpose = 0,
      splitPointMidi = 54,
      harmonyEnabled = false,
      sustainEnabled = false,
      dspEnabled = false,
      createdAtMs = now,
      updatedAtMs = now,
    )
    _uiState.update { st ->
      val nextList = listOf(entity) + st.keyboardSets.filterNot { it.id == entity.id }
      st.copy(keyboardSets = nextList)
    }
    viewModelScope.launch { repository.upsertKeyboardSet(entity) }
    if (activateImmediately) {
      applyKeyboardSetEntity(entity)
    }
    toast("Created Empty Keyboard Set '$cleanName'")
  }

  override fun saveCurrentAsKeyboardSet(name: String?) {
    val st = _uiState.value
    val vs = st.voiceState
    val now = System.currentTimeMillis()
    val hasAnyVoice = vs.right1Voice != null || vs.right2Voice != null || vs.leftVoice != null
    val defaultName = name?.trim()?.takeIf { it.isNotBlank() }
      ?: vs.right1Voice?.name?.take(16)?.let { "$it Set" }
      ?: "Empty Set ${st.keyboardSets.size + 1}"
    val entity = KeyboardSetEntity(
      id = "kbdset_$now",
      name = defaultName,
      isEmpty = !hasAnyVoice,
      right1VoiceId = vs.right1Voice?.id,
      right2VoiceId = vs.right2Voice?.id,
      leftVoiceId = vs.leftVoice?.id,
      right1Enabled = vs.right1Enabled && vs.right1Voice != null,
      right2Enabled = vs.right2Enabled && vs.right2Voice != null,
      leftEnabled = vs.leftEnabled && vs.leftVoice != null,
      right1Octave = vs.right1Edit.octaveOffset,
      right2Octave = vs.right2Edit.octaveOffset,
      leftOctave = vs.leftEdit.octaveOffset,
      transpose = st.transpose,
      splitPointMidi = st.splitPointMidi,
      harmonyEnabled = vs.harmonyEnabled,
      sustainEnabled = vs.sustainEnabled,
      dspEnabled = vs.dspEnabled,
      createdAtMs = now,
      updatedAtMs = now,
    )
    _uiState.update { cur ->
      val nextList = listOf(entity) + cur.keyboardSets.filterNot { it.id == entity.id }
      cur.copy(keyboardSets = nextList, activeKeyboardSetId = entity.id)
    }
    viewModelScope.launch { repository.upsertKeyboardSet(entity) }
    scheduleSessionSave()
    toast("Saved Keyboard Set '${entity.name}'")
  }

  override fun updateKeyboardSetFromLive(setId: String) {
    val existing = _uiState.value.keyboardSets.find { it.id == setId } ?: return
    val st = _uiState.value
    val vs = st.voiceState
    val hasAnyVoice = vs.right1Voice != null || vs.right2Voice != null || vs.leftVoice != null
    val updated = existing.copy(
      isEmpty = !hasAnyVoice,
      right1VoiceId = vs.right1Voice?.id,
      right2VoiceId = vs.right2Voice?.id,
      leftVoiceId = vs.leftVoice?.id,
      right1Enabled = vs.right1Enabled && vs.right1Voice != null,
      right2Enabled = vs.right2Enabled && vs.right2Voice != null,
      leftEnabled = vs.leftEnabled && vs.leftVoice != null,
      right1Octave = vs.right1Edit.octaveOffset,
      right2Octave = vs.right2Edit.octaveOffset,
      leftOctave = vs.leftEdit.octaveOffset,
      transpose = st.transpose,
      splitPointMidi = st.splitPointMidi,
      harmonyEnabled = vs.harmonyEnabled,
      sustainEnabled = vs.sustainEnabled,
      dspEnabled = vs.dspEnabled,
      updatedAtMs = System.currentTimeMillis(),
    )
    _uiState.update { cur ->
      val nextList = listOf(updated) + cur.keyboardSets.filterNot { it.id == updated.id }
      cur.copy(keyboardSets = nextList)
    }
    viewModelScope.launch { repository.upsertKeyboardSet(updated) }
  }

  override fun selectAndApplyKeyboardSet(setId: String) {
    val entity = _uiState.value.keyboardSets.find { it.id == setId } ?: return
    applyKeyboardSetEntity(entity)
    toast("Loaded Keyboard Set '${entity.name}'")
  }

  override fun clearLiveKeyboardToEmpty() {
    voiceEngine.clearAllKeyboardSlots()
    setTranspose(0)
    setUpperOctave(0)
    setSplitPoint(54)
    _uiState.value.activeKeyboardSetId?.let { activeId ->
      updateKeyboardSetFromLive(activeId)
    }
    scheduleSessionSave()
    toast("Keyboard Set cleared to Empty")
  }

  override fun clearKeyboardSetSlot(setId: String?, slot: ActiveVoiceSlot) {
    voiceEngine.clearSlot(slot)
    val targetId = setId ?: _uiState.value.activeKeyboardSetId
    if (targetId != null) {
      updateKeyboardSetFromLive(targetId)
    }
    scheduleSessionSave()
    toast("${slot.label} set to Empty")
  }

  override fun renameKeyboardSet(setId: String, newName: String) {
    val clean = newName.trim()
    if (clean.isEmpty()) return
    val existing = _uiState.value.keyboardSets.find { it.id == setId } ?: return
    val updated = existing.copy(name = clean, updatedAtMs = System.currentTimeMillis())
    _uiState.update { cur ->
      val nextList = listOf(updated) + cur.keyboardSets.filterNot { it.id == updated.id }
      cur.copy(keyboardSets = nextList)
    }
    viewModelScope.launch { repository.upsertKeyboardSet(updated) }
    toast("Renamed Keyboard Set to '$clean'")
  }

  override fun duplicateKeyboardSet(setId: String) {
    val source = _uiState.value.keyboardSets.find { it.id == setId } ?: return
    val now = System.currentTimeMillis()
    val copy = source.copy(
      id = "kbdset_copy_$now",
      name = "${source.name} Copy",
      createdAtMs = now,
      updatedAtMs = now,
    )
    _uiState.update { cur ->
      cur.copy(keyboardSets = listOf(copy) + cur.keyboardSets)
    }
    viewModelScope.launch { repository.upsertKeyboardSet(copy) }
    applyKeyboardSetEntity(copy)
    toast("Duplicated '${source.name}'")
  }

  override fun deleteKeyboardSet(setId: String) {
    _uiState.update { cur ->
      cur.copy(
        keyboardSets = cur.keyboardSets.filterNot { it.id == setId },
        activeKeyboardSetId = if (cur.activeKeyboardSetId == setId) null else cur.activeKeyboardSetId,
      )
    }
    viewModelScope.launch { repository.deleteKeyboardSet(setId) }
    scheduleSessionSave()
    toast("Deleted Keyboard Set")
  }

  private fun applyKeyboardSetEntity(entity: KeyboardSetEntity) {
    val r1Id = entity.right1VoiceId
    if (r1Id.isNullOrBlank()) {
      voiceEngine.clearSlot(ActiveVoiceSlot.RIGHT_1)
    } else {
      val patch = voiceEngine.findVoiceById(r1Id)
      if (patch != null) {
        voiceEngine.assignVoiceToSlot(ActiveVoiceSlot.RIGHT_1, patch)
        val cur = voiceEngine.state.value.right1Edit
        voiceEngine.updateSlotEditParams(ActiveVoiceSlot.RIGHT_1, cur.copy(octaveOffset = entity.right1Octave))
        voiceEngine.setPartEnabled(ActiveVoiceSlot.RIGHT_1, entity.right1Enabled)
      } else {
        voiceEngine.clearSlot(ActiveVoiceSlot.RIGHT_1)
      }
    }

    val r2Id = entity.right2VoiceId
    if (r2Id.isNullOrBlank()) {
      voiceEngine.clearSlot(ActiveVoiceSlot.RIGHT_2)
    } else {
      val patch = voiceEngine.findVoiceById(r2Id)
      if (patch != null) {
        voiceEngine.assignVoiceToSlot(ActiveVoiceSlot.RIGHT_2, patch)
        val cur = voiceEngine.state.value.right2Edit
        voiceEngine.updateSlotEditParams(ActiveVoiceSlot.RIGHT_2, cur.copy(octaveOffset = entity.right2Octave))
        voiceEngine.setPartEnabled(ActiveVoiceSlot.RIGHT_2, entity.right2Enabled)
      } else {
        voiceEngine.clearSlot(ActiveVoiceSlot.RIGHT_2)
      }
    }

    val leftId = entity.leftVoiceId
    if (leftId.isNullOrBlank()) {
      voiceEngine.clearSlot(ActiveVoiceSlot.LEFT)
    } else {
      val patch = voiceEngine.findVoiceById(leftId)
      if (patch != null) {
        voiceEngine.assignVoiceToSlot(ActiveVoiceSlot.LEFT, patch)
        val cur = voiceEngine.state.value.leftEdit
        voiceEngine.updateSlotEditParams(ActiveVoiceSlot.LEFT, cur.copy(octaveOffset = entity.leftOctave))
        voiceEngine.setPartEnabled(ActiveVoiceSlot.LEFT, entity.leftEnabled)
      } else {
        voiceEngine.clearSlot(ActiveVoiceSlot.LEFT)
      }
    }

    voiceEngine.setHarmonyEnabled(entity.harmonyEnabled)
    voiceEngine.setSustainEnabled(entity.sustainEnabled)
    voiceEngine.setDspEnabled(entity.dspEnabled)
    setTranspose(entity.transpose)
    setSplitPoint(entity.splitPointMidi)
    _uiState.update { it.copy(activeKeyboardSetId = entity.id) }
    scheduleSessionSave()
  }

  // ---- Mixer & Effects ----

  override fun setMixerVolume(part: WorkstationPart, volume: Float) = mixerEngine.setVolume(part, volume)
  override fun setMixerPan(part: WorkstationPart, pan: Float) = mixerEngine.setPan(part, pan)
  override fun setMixerReverbSend(part: WorkstationPart, level: Float) = mixerEngine.setReverbSend(part, level)
  override fun setMixerChorusSend(part: WorkstationPart, level: Float) = mixerEngine.setChorusSend(part, level)
  override fun setMixerDspSend(part: WorkstationPart, level: Float) = mixerEngine.setDspSend(part, level)
  override fun toggleMixerMute(part: WorkstationPart) = mixerEngine.toggleMute(part)
  override fun toggleMixerSolo(part: WorkstationPart) = mixerEngine.toggleSolo(part)
  override fun resetMixer() = mixerEngine.resetAll()

  override fun updateEffectsState(transform: (EffectsRackState) -> EffectsRackState) {
    val next = transform(_uiState.value.effectsState)
    audioEngine.setEffectsState(next)
    _uiState.update { it.copy(effectsState = next) }
  }

  // ---- MIDI & Song ----

  override fun selectMidiSong(song: MidiSong) = midiPlayer.selectSong(song)
  override fun toggleSongPlayPause() = midiPlayer.playOrPause()
  override fun stopSong() = midiPlayer.stop()
  override fun seekSong(fraction: Float) = midiPlayer.seekToFraction(fraction)
  override fun toggleSongLoop() = midiPlayer.toggleLoop()
  override fun toggleSongTrackMute(trackIndex: Int) = midiPlayer.toggleTrackMute(trackIndex)
  override fun toggleSongTrackSolo(trackIndex: Int) = midiPlayer.toggleTrackSolo(trackIndex)

  override fun toggleMidiRecording() {
    if (_uiState.value.midiState.isRecording) {
      val saved = midiPlayer.stopRecordingAndBuildSong("PSR_MIDI_${System.currentTimeMillis() % 10000}")
      toast(if (saved != null) "Recorded MIDI Song: ${saved.title}" else "No MIDI notes recorded")
    } else {
      midiPlayer.startRecording()
      toast("MIDI Recording Started — play keyboard")
    }
  }

  override fun exportActiveSongMidi() {
    val song = _uiState.value.midiState.currentSong ?: return
    viewModelScope.launch(Dispatchers.IO) {
      val dir = File(appContext.filesDir, "midi_exports").apply { mkdirs() }
      val outFile = File(dir, "${song.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")}.mid")
      FileOutputStream(outFile).use { StandardMidiCodec.write(song, it) }
      _events.tryEmit(WorkstationUiEvent.ShareFile(outFile, "audio/midi", "Export MIDI: ${song.title}"))
    }
  }

  override fun launchQuickDemo() {
    styleEngine.selectStyle(styleEngine.state.value.allStyles.first(), applyTempo = true)
    styleEngine.updateChord(DetectedChord.DEFAULT_C_MAJOR)
    if (!styleEngine.state.value.isPlaying) {
      styleEngine.start()
    }
    toast("Demo Groove Started (ConcertGrand 120 BPM)")
  }

  // ---- Audio Recording ----

  override fun toggleAudioRecording() {
    if (_uiState.value.recordingState.isRecording) {
      val saved = recordingEngine.stopRecording()
      toast(if (saved != null) "Saved WAV: ${saved.title}" else "Recording stopped")
    } else {
      recordingEngine.startRecording(hasMicPermission)
      toast("Recording Stereo WAV...")
    }
  }

  override fun toggleRecordMicrophone(enabled: Boolean) {
    if (enabled && !hasMicPermission) {
      _events.tryEmit(WorkstationUiEvent.RequestMicrophonePermission)
    } else {
      recordingEngine.setIncludeMicrophone(enabled)
    }
  }

  override fun playOrStopRecording(item: AudioRecordingItem) = recordingEngine.playRecording(item)
  override fun renameRecording(item: AudioRecordingItem, newName: String) = recordingEngine.renameRecording(item, newName)
  override fun deleteRecording(item: AudioRecordingItem) = recordingEngine.deleteRecording(item)

  override fun shareRecording(item: AudioRecordingItem) {
    _events.tryEmit(WorkstationUiEvent.ShareFile(File(item.filePath), "audio/wav", "Share Recording: ${item.title}"))
  }

  // ---- Expansion, SF2/STY Importer & Utility ----

  override fun requestImportSf2File() {
    _events.tryEmit(WorkstationUiEvent.LaunchSf2FilePicker)
  }

  override fun requestImportStyFile() {
    _events.tryEmit(WorkstationUiEvent.LaunchStyFilePicker)
  }

  override fun requestImportExpansionFile() {
    _events.tryEmit(WorkstationUiEvent.LaunchExpansionFilePicker)
  }

  override fun requestImportDrumKitFiles() {
    _events.tryEmit(WorkstationUiEvent.LaunchDrumKitFilesPicker)
  }

  override fun requestImportDrumSampleForSlot(kitId: String, mappingId: String) {
    _events.tryEmit(WorkstationUiEvent.LaunchDrumSlotSamplePicker(kitId, mappingId))
  }

  override fun selectDrumKitInEditor(kitId: String) {
    drumKitManager.selectKitInEditor(kitId)
  }

  override fun assignDrumKitToStyle(kitId: String, styleId: String?) {
    val targetStyleId = styleId ?: _uiState.value.styleState.currentStyle?.id
    activeDrumKitIdBacking = kitId
    if (targetStyleId != null) {
      styleDrumBindingsBacking[targetStyleId] = kitId
    }
    _uiState.update { st ->
      val nextMap = if (targetStyleId != null) st.styleDrumKitBindings + (targetStyleId to kitId) else st.styleDrumKitBindings
      st.copy(
        activeArrangerDrumKitId = kitId,
        styleDrumKitBindings = nextMap,
        pendingStyDrumSelectionStyleId = if (st.pendingStyDrumSelectionStyleId == targetStyleId) null else st.pendingStyDrumSelectionStyleId,
      )
    }
    applyDrumKitToAudioEngine(targetStyleId, kitId)
    val kitName = drumKitManager.findKitById(kitId)?.name ?: "Drum Kit"
    scheduleSessionSave()
    toast("Arranger Drum Kit: $kitName")
  }

  override fun createCustomDrumKit(name: String) {
    viewModelScope.launch {
      val created = drumKitManager.createNewKit(name)
      toast("Created Custom Drum Kit '${created.name}'")
    }
  }

  override fun renameCustomDrumKit(kitId: String, newName: String) {
    viewModelScope.launch {
      drumKitManager.renameKit(kitId, newName)
    }
  }

  override fun saveCustomDrumKit(kitId: String) {
    viewModelScope.launch {
      val saved = drumKitManager.saveKit(kitId)
      if (saved != null) {
        if (_uiState.value.activeStyleDrumKitId == kitId) {
          applyDrumKitToAudioEngine(_uiState.value.styleState.currentStyle?.id, kitId)
        }
        toast("Saved '${saved.name}'")
      }
    }
  }

  override fun deleteCustomDrumKit(kitId: String) {
    viewModelScope.launch {
      if (drumKitManager.deleteKit(kitId)) {
        val cleaned = styleDrumBindingsBacking.filterValues { it != kitId }
        styleDrumBindingsBacking.clear()
        styleDrumBindingsBacking.putAll(cleaned)
        val fallbackId = GmDrumNoteNames.FACTORY_KIT_ID
        val curActive = if (_uiState.value.activeArrangerDrumKitId == kitId) fallbackId else _uiState.value.activeArrangerDrumKitId
        activeDrumKitIdBacking = curActive
        _uiState.update { st ->
          st.copy(
            activeArrangerDrumKitId = curActive,
            styleDrumKitBindings = cleaned,
          )
        }
        applyDrumKitToAudioEngine(_uiState.value.styleState.currentStyle?.id, curActive)
        scheduleSessionSave()
        toast("Deleted custom drum kit")
      }
    }
  }

  override fun updateDrumMappingMidiNote(kitId: String, mappingId: String, midiNote: Int) {
    viewModelScope.launch {
      val updated = drumKitManager.updateMappingMidiNote(kitId, mappingId, midiNote)
      if (updated != null && _uiState.value.activeStyleDrumKitId == updated.id) {
        applyDrumKitToAudioEngine(_uiState.value.styleState.currentStyle?.id, updated.id)
      }
    }
  }

  override fun updateDrumMappingGain(kitId: String, mappingId: String, gain: Float) {
    viewModelScope.launch {
      val updated = drumKitManager.updateMappingGain(kitId, mappingId, gain)
      if (updated != null && _uiState.value.activeStyleDrumKitId == updated.id) {
        applyDrumKitToAudioEngine(_uiState.value.styleState.currentStyle?.id, updated.id)
      }
    }
  }

  override fun addCustomDrumMappingSlot(kitId: String, midiNote: Int) {
    viewModelScope.launch {
      val updated = drumKitManager.addCustomMappingSlot(kitId, midiNote)
      if (_uiState.value.activeStyleDrumKitId == updated.id) {
        applyDrumKitToAudioEngine(_uiState.value.styleState.currentStyle?.id, updated.id)
      }
    }
  }

  override fun removeCustomDrumMappingSlot(kitId: String, mappingId: String) {
    viewModelScope.launch {
      val updated = drumKitManager.removeMappingSlot(kitId, mappingId)
      if (updated != null && _uiState.value.activeStyleDrumKitId == updated.id) {
        applyDrumKitToAudioEngine(_uiState.value.styleState.currentStyle?.id, updated.id)
      }
    }
  }

  override fun previewDrumPad(kitId: String, midiNote: Int, velocity: Int) {
    val inst = drumKitManager.buildPlayableInstrument(kitId)
    audioEngine.setPartInstrument(WorkstationPart.MULTI_PAD, inst)
    audioEngine.noteOn(WorkstationPart.MULTI_PAD, midiNote.coerceIn(0, 127), velocity.coerceIn(1, 127))
  }

  override fun dismissPendingStyDrumPrompt() {
    _uiState.update { it.copy(pendingStyDrumSelectionStyleId = null) }
  }

  private fun applyDrumKitToAudioEngine(styleId: String?, kitId: String) {
    if (kitId == GmDrumNoteNames.FACTORY_KIT_ID) {
      val curStyle = _uiState.value.styleState.allStyles.find { it.id == styleId } ?: _uiState.value.styleState.currentStyle
      val r1Voice = curStyle?.channelVoices?.get(StyleChannelType.RHYTHM_1) ?: "drum_std"
      val r2Voice = curStyle?.channelVoices?.get(StyleChannelType.RHYTHM_2) ?: "eth_kebero"
      voiceEngine.loadPartInstrument(WorkstationPart.RHYTHM_1, r1Voice)
      voiceEngine.loadPartInstrument(WorkstationPart.RHYTHM_2, r2Voice)
    } else {
      val customInst = drumKitManager.buildPlayableInstrument(kitId)
      audioEngine.setPartInstrument(WorkstationPart.RHYTHM_1, customInst)
      audioEngine.setPartInstrument(WorkstationPart.RHYTHM_2, customInst)
    }
  }

  override fun previewExpansionPack(pack: ExpansionPackEntity) {
    val styleMatch = _uiState.value.styleState.allStyles.find { it.id.startsWith(pack.id) }
    if (styleMatch != null) {
      styleEngine.selectStyle(styleMatch, applyTempo = true)
      if (!_uiState.value.styleState.isPlaying) {
        styleEngine.start()
      }
      toast("Loaded & Playing STY: ${styleMatch.name} (${styleMatch.defaultBpm} BPM)")
      return
    }
    val match = _uiState.value.voiceState.allVoices.find { it.id.startsWith(pack.id) }
    if (match != null) {
      voiceEngine.assignVoiceToSlot(ActiveVoiceSlot.RIGHT_1, match)
      viewModelScope.launch {
        listOf(60, 64, 67, 72).forEach { n ->
          audioEngine.noteOn(WorkstationPart.RIGHT_1, n, 98)
          delay(180L)
          audioEngine.noteOff(WorkstationPart.RIGHT_1, n)
        }
      }
      toast("Previewing ${match.name}")
    } else {
      val song = _uiState.value.availableSongs.find { it.id.startsWith(pack.id) }
      if (song != null) {
        midiPlayer.selectSong(song)
        midiPlayer.startPlayback()
        toast("Playing imported MIDI: ${song.title}")
      } else {
        toast("No previewable presets found in ${pack.name}")
      }
    }
  }

  override fun updateExpansionGroup(id: String, group: String) {
    viewModelScope.launch { expansionManager.updatePackGroup(id, group) }
  }

  override fun deleteExpansionPack(pack: ExpansionPackEntity) {
    viewModelScope.launch {
      expansionManager.deletePack(pack)
      voiceEngine.removeVoicesWithPrefix(pack.id)
      styleEngine.removeStylesWithPrefix(pack.id)
      toast("Deleted ${pack.name}")
    }
  }

  override fun setSampleRate(sampleRate: Int) {
    audioEngine.setSampleRate(sampleRate)
    scheduleSessionSave()
  }

  override fun resetSessionToDefaults() {
    styleEngine.stop()
    midiPlayer.stop()
    stopAllMultiPads()
    mixerEngine.resetAll()
    val defaultFx = EffectsRackState()
    audioEngine.setEffectsState(defaultFx)
    _uiState.update {
      it.copy(
        effectsState = defaultFx,
        transpose = 0,
        upperOctave = 0,
        splitPointMidi = 54,
        keySizeScale = 1.0f,
      )
    }
    setMasterVolume(0.85f)
    setMasterBalance(0f)
    scheduleSessionSave()
    toast("Session reset to factory defaults")
  }

  // ---- Helpers ----

  private fun midiChannelToPart(channel: Int): WorkstationPart = when (channel) {
    9 -> WorkstationPart.RHYTHM_1
    1 -> WorkstationPart.CHORD_1
    2 -> WorkstationPart.BASS
    else -> WorkstationPart.SONG_MIDI
  }

  private fun setupExternalMidiListener() {
    try {
      if (!appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)) return
      val midiManager = appContext.getSystemService(Context.MIDI_SERVICE) as? MidiManager ?: return
      val handler = Handler(Looper.getMainLooper())
      @Suppress("DEPRECATION")
      val devices = midiManager.devices
      _uiState.update { it.copy(hardwareMidiDevices = devices.map { d -> d.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "MIDI Device" }) }
      devices.forEach { info ->
        if (info.outputPortCount > 0) {
          midiManager.openDevice(info, { device ->
            val port = device?.openOutputPort(0)
            port?.connect(object : MidiReceiver() {
              override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                if (count >= 3) {
                  val status = msg[offset].toInt() and 0xF0
                  val d1 = msg[offset + 1].toInt() and 0x7F
                  val d2 = msg[offset + 2].toInt() and 0x7F
                  when (status) {
                    0x90 -> if (d2 > 0) onKeyDown(d1, d2) else onKeyUp(d1)
                    0x80 -> onKeyUp(d1)
                    0xB0 -> {
                      if (d1 == 64) audioEngine.setSustainPedal(WorkstationPart.RIGHT_1, d2 >= 64)
                      if (d1 == 1) setModWheel(d2 / 127f)
                    }
                    0xE0 -> {
                      val raw14 = (d2 shl 7) or d1
                      val semis = ((raw14 - 8192) / 8192f) * 2f
                      setPitchBend(semis)
                    }
                  }
                }
              }
            })
          }, handler)
        }
      }
    } catch (_: Throwable) {
    }
  }

  private fun logMidi(msg: String) {
    _uiState.update { st ->
      st.copy(midiMonitorLog = (st.midiMonitorLog + msg).takeLast(12))
    }
  }

  private fun toast(msg: String) {
    _uiState.update { it.copy(statusToast = msg) }
  }

  private fun serializeMixer(map: Map<WorkstationPart, MixerChannelState>): String {
    val root = JSONObject()
    map.forEach { (part, ch) ->
      root.put(
        part.name,
        JSONObject().apply {
          put("vol", ch.volume.toDouble())
          put("pan", ch.pan.toDouble())
          put("rev", ch.reverbSend.toDouble())
          put("cho", ch.chorusSend.toDouble())
          put("dsp", ch.dspSend.toDouble())
          put("mute", ch.muted)
          put("solo", ch.soloed)
        },
      )
    }
    return root.toString()
  }

  private fun deserializeMixer(json: String): Map<WorkstationPart, MixerChannelState>? = runCatching {
    val root = JSONObject(json)
    val result = mutableMapOf<WorkstationPart, MixerChannelState>()
    for (part in WorkstationPart.entries) {
      val o = root.optJSONObject(part.name) ?: continue
      result[part] = MixerChannelState(
        part = part,
        volume = o.optDouble("vol", 0.85).toFloat(),
        pan = o.optDouble("pan", 0.0).toFloat(),
        reverbSend = o.optDouble("rev", 0.25).toFloat(),
        chorusSend = o.optDouble("cho", 0.15).toFloat(),
        dspSend = o.optDouble("dsp", 0.0).toFloat(),
        muted = o.optBoolean("mute", false),
        soloed = o.optBoolean("solo", false),
      )
    }
    result
  }.getOrNull()

  private fun serializeEffects(fx: EffectsRackState): String = JSONObject().apply {
    put("revEn", fx.reverb.enabled)
    put("revRoom", fx.reverb.roomSize.toDouble())
    put("revDamp", fx.reverb.damping.toDouble())
    put("revLvl", fx.reverb.level.toDouble())
    put("choEn", fx.chorus.enabled)
    put("choRate", fx.chorus.rateHz.toDouble())
    put("choDepth", fx.chorus.depth.toDouble())
    put("choLvl", fx.chorus.level.toDouble())
    put("delEn", fx.delay.enabled)
    put("delMs", fx.delay.timeMs.toDouble())
    put("delFb", fx.delay.feedback.toDouble())
    put("delLvl", fx.delay.level.toDouble())
    put("dspType", fx.insertDsp.type.name)
    put("dspEn", fx.insertDsp.enabled)
    put("dspMix", fx.insertDsp.mix.toDouble())
    put("eqLow", fx.masterEq.lowGainDb.toDouble())
    put("eqMid", fx.masterEq.midGainDb.toDouble())
    put("eqHigh", fx.masterEq.highGainDb.toDouble())
  }.toString()

  private fun deserializeEffects(json: String): EffectsRackState? = runCatching {
    val o = JSONObject(json)
    if (o.length() == 0) return null
    EffectsRackState(
      reverb = ReverbSettings(
        enabled = o.optBoolean("revEn", true),
        roomSize = o.optDouble("revRoom", 0.58).toFloat(),
        damping = o.optDouble("revDamp", 0.42).toFloat(),
        level = o.optDouble("revLvl", 0.32).toFloat(),
      ),
      chorus = ChorusSettings(
        enabled = o.optBoolean("choEn", true),
        rateHz = o.optDouble("choRate", 0.85).toFloat(),
        depth = o.optDouble("choDepth", 0.45).toFloat(),
        level = o.optDouble("choLvl", 0.25).toFloat(),
      ),
      delay = DelaySettings(
        enabled = o.optBoolean("delEn", false),
        timeMs = o.optDouble("delMs", 320.0).toFloat(),
        feedback = o.optDouble("delFb", 0.35).toFloat(),
        level = o.optDouble("delLvl", 0.28).toFloat(),
      ),
      insertDsp = DspInsertSettings(
        type = runCatching { DspEffectType.valueOf(o.optString("dspType", "ROTARY_SPEAKER")) }.getOrDefault(DspEffectType.ROTARY_SPEAKER),
        enabled = o.optBoolean("dspEn", false),
        mix = o.optDouble("dspMix", 0.5).toFloat(),
      ),
      masterEq = EqSettings(
        enabled = true,
        lowGainDb = o.optDouble("eqLow", 1.5).toFloat(),
        midGainDb = o.optDouble("eqMid", 0.5).toFloat(),
        highGainDb = o.optDouble("eqHigh", 2.0).toFloat(),
      ),
      masterCompressor = CompressorSettings(),
    )
  }.getOrNull()

  private fun serializeStyleDrumBindings(map: Map<String, String>): String {
    val root = JSONObject()
    map.forEach { (styleId, kitId) -> root.put(styleId, kitId) }
    return root.toString()
  }

  private fun deserializeStyleDrumBindings(json: String): Map<String, String> = runCatching {
    val root = JSONObject(json)
    val result = mutableMapOf<String, String>()
    val keys = root.keys()
    while (keys.hasNext()) {
      val k = keys.next()
      val v = root.optString(k)
      if (k.isNotBlank() && v.isNotBlank()) result[k] = v
    }
    result
  }.getOrDefault(emptyMap())

  override fun onCleared() {
    super.onCleared()
    styleEngine.stop()
    midiPlayer.stop()
    stopAllMultiPads()
    audioEngine.stop()
  }
}
