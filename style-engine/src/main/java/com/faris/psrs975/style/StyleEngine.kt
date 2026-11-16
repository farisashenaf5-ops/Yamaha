package com.faris.psrs975.style

import com.faris.psrs975.audio.WorkstationAudioEngine
import com.faris.psrs975.audio.WorkstationPart
import com.faris.psrs975.chord.DetectedChord
import com.faris.psrs975.midi.MidiEventType
import com.faris.psrs975.midi.MidiSong
import com.faris.psrs975.mixer.MixerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Real-time 8-channel arranger style accompaniment engine with chord transposition,
 * quantized fill/intro/ending transitions, Sync Start/Stop, Tap Tempo, Fade In/Out,
 * MIDI style import, and interactive pattern editing.
 */
class StyleEngine(
  private val audioEngine: WorkstationAudioEngine,
  private val mixerEngine: MixerEngine,
  private val onRequestOtsApply: (OneTouchSetting) -> Unit = {},
  private val onStyleChannelVoicesChange: (Map<StyleChannelType, String>) -> Unit = {},
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var clockJob: Job? = null

  private val _state = MutableStateFlow(
    StyleEngineState(
      allStyles = BuiltInStyles.createAll(),
    ),
  )
  val state: StateFlow<StyleEngineState> = _state.asStateFlow()

  @Volatile private var activeChord: DetectedChord = DetectedChord.DEFAULT_C_MAJOR
  @Volatile private var transposeSemitones: Int = 0

  private data class ScheduledNoteOff(val stepDue: Long, val part: WorkstationPart, val note: Int)
  private val activeNoteOffs = mutableListOf<ScheduledNoteOff>()
  private val tapTimestampsMs = mutableListOf<Long>()

  private var mainTargetBeforeFill: StyleSection = StyleSection.MAIN_A

  init {
    _state.value.allStyles.firstOrNull()?.let { selectStyle(it, applyTempo = true) }
  }

  fun selectStyle(style: ArrangerStyle, applyTempo: Boolean = true) {
    _state.update {
      it.copy(
        currentStyle = style,
        tempoBpm = if (applyTempo) style.defaultBpm else it.tempoBpm,
        currentSection = StyleSection.MAIN_A,
        pendingSection = null,
      )
    }
    onStyleChannelVoicesChange(style.channelVoices)
  }

  fun updateChord(chord: DetectedChord) {
    activeChord = chord
  }

  fun setTranspose(semitones: Int) {
    transposeSemitones = semitones.coerceIn(-12, 12)
  }

  fun setTempo(bpm: Int) {
    _state.update { it.copy(tempoBpm = bpm.coerceIn(40, 260)) }
  }

  fun tapTempo() {
    val now = System.currentTimeMillis()
    if (tapTimestampsMs.isNotEmpty() && now - tapTimestampsMs.last() > 2000L) {
      tapTimestampsMs.clear()
    }
    tapTimestampsMs.add(now)
    if (tapTimestampsMs.size > 4) tapTimestampsMs.removeAt(0)
    if (tapTimestampsMs.size >= 2) {
      val intervals = tapTimestampsMs.zipWithNext { a, b -> b - a }
      val avgMs = intervals.average()
      if (avgMs > 180.0) {
        val bpm = (60_000.0 / avgMs).toInt().coerceIn(40, 260)
        setTempo(bpm)
      }
    }
  }

  fun toggleAcmp() {
    _state.update { it.copy(acmpEnabled = !it.acmpEnabled) }
  }

  fun setAcmp(enabled: Boolean) {
    _state.update { it.copy(acmpEnabled = enabled) }
  }

  fun toggleSyncStart() {
    _state.update { it.copy(syncStartEnabled = !it.syncStartEnabled) }
  }

  fun toggleSyncStop() {
    _state.update { it.copy(syncStopEnabled = !it.syncStopEnabled) }
  }

  fun toggleAutoFill() {
    _state.update { it.copy(autoFillEnabled = !it.autoFillEnabled) }
  }

  fun toggleChannelEnabled(ch: StyleChannelType) {
    _state.update { st ->
      val current = st.channelEnabled[ch] ?: true
      val next = !current
      mixerEngine.setMuted(ch.part, !next)
      st.copy(channelEnabled = st.channelEnabled + (ch to next))
    }
  }

  fun onLeftZoneKeyActivity(hasHeldKeys: Boolean) {
    val st = _state.value
    if (hasHeldKeys && st.syncStartEnabled && !st.isPlaying) {
      start()
    } else if (!hasHeldKeys && st.syncStopEnabled && st.isPlaying) {
      stop()
    }
  }

  fun startOrStop() {
    if (_state.value.isPlaying) stop() else start()
  }

  fun start(initialFadeIn: Boolean = false) {
    if (_state.value.currentStyle == null) return
    if (_state.value.isPlaying) return
    _state.update {
      it.copy(
        isPlaying = true,
        currentBar = 1,
        currentBeat = 1,
        activeStepInBar = 0,
        fadeGain = if (initialFadeIn) 0.1f else 1.0f,
        isFading = initialFadeIn,
      )
    }
    launchClock(fadeDirection = if (initialFadeIn) 1 else 0)
  }

  fun stop() {
    clockJob?.cancel()
    clockJob = null
    flushAllStyleNotes()
    _state.update {
      it.copy(
        isPlaying = false,
        pendingSection = null,
        currentBar = 1,
        currentBeat = 1,
        activeStepInBar = 0,
        fadeGain = 1.0f,
        isFading = false,
      )
    }
  }

  fun triggerFadeInOut() {
    val st = _state.value
    if (!st.isPlaying) {
      start(initialFadeIn = true)
    } else {
      _state.update { it.copy(isFading = true) }
      scope.launch {
        var g = _state.value.fadeGain
        while (g > 0.05f && _state.value.isPlaying) {
          g -= 0.08f
          _state.update { it.copy(fadeGain = g.coerceIn(0f, 1f)) }
          delay(120L)
        }
        stop()
      }
    }
  }

  fun selectSection(section: StyleSection) {
    val st = _state.value
    if (!st.isPlaying) {
      _state.update { it.copy(currentSection = section, pendingSection = null) }
      if (!section.isIntro && !section.isEnding && !section.isFill) {
        mainTargetBeforeFill = section
      }
      return
    }

    if (section.isIntro) {
      _state.update { it.copy(currentSection = section, pendingSection = mainTargetBeforeFill) }
      return
    }

    if (section.isEnding) {
      _state.update { it.copy(pendingSection = section) }
      return
    }

    if (section == StyleSection.BREAK) {
      _state.update { it.copy(pendingSection = StyleSection.BREAK) }
      return
    }

    if (section in listOf(StyleSection.MAIN_A, StyleSection.MAIN_B, StyleSection.MAIN_C, StyleSection.MAIN_D)) {
      mainTargetBeforeFill = section
      if (st.autoFillEnabled) {
        val fill = when (section) {
          StyleSection.MAIN_A -> StyleSection.FILL_A
          StyleSection.MAIN_B -> StyleSection.FILL_B
          StyleSection.MAIN_C -> StyleSection.FILL_C
          StyleSection.MAIN_D -> StyleSection.FILL_D
          else -> StyleSection.FILL_A
        }
        _state.update { it.copy(currentSection = fill, pendingSection = section) }
      } else {
        _state.update { it.copy(pendingSection = section) }
      }
    }
  }

  fun selectOts(slot: Int) {
    val ots = _state.value.currentStyle?.otsPresets?.find { it.slot == slot } ?: return
    _state.update { it.copy(activeOtsSlot = slot) }
    onRequestOtsApply(ots)
  }

  /**
   * Toggle or add a step event inside the current style's editable section pattern.
   */
  fun togglePatternStep(
    section: StyleSection,
    stepIndex: Int,
    channel: StyleChannelType,
    fixedMidiNote: Int = 36,
    chordDegree: Int = 0,
  ) {
    val style = _state.value.currentStyle ?: return
    val pat = style.sections[section] ?: return
    val exists = pat.events.any { it.stepIndex == stepIndex && it.channel == channel && it.fixedMidiNote == fixedMidiNote }
    val updatedEvents = if (exists) {
      pat.events.filterNot { it.stepIndex == stepIndex && it.channel == channel && it.fixedMidiNote == fixedMidiNote }
    } else {
      pat.events + StylePatternEvent(
        stepIndex = stepIndex,
        channel = channel,
        fixedMidiNote = fixedMidiNote,
        chordDegree = if (channel == StyleChannelType.RHYTHM_1 || channel == StyleChannelType.RHYTHM_2) -1 else chordDegree,
        octaveOffset = if (channel == StyleChannelType.BASS) -2 else 0,
        velocity = 96,
        durationSteps = if (channel == StyleChannelType.BASS) 2 else 1,
      )
    }
    val updatedStyle = style.copy(
      isUserStyle = true,
      sections = style.sections + (section to pat.copy(events = updatedEvents)),
    )
    replaceOrAddUserStyle(updatedStyle)
  }

  fun importMidiAsStyle(song: MidiSong, styleName: String, category: StyleCategory = StyleCategory.WORLD): ArrangerStyle {
    val events = mutableListOf<StylePatternEvent>()
    val tpqn = max(1, song.ticksPerQuarterNote)
    val stepTicks = tpqn / 4.0
    val totalSteps = 32 // 2-bar loop quantized to 16th notes

    for (ev in song.events) {
      if (ev.type != MidiEventType.NOTE_ON) continue
      val step = ((ev.tick / stepTicks).toInt()) % totalSteps
      val ch = when {
        ev.channel == 9 -> StyleChannelType.RHYTHM_1
        ev.data1 < 48 -> StyleChannelType.BASS
        ev.channel == 1 -> StyleChannelType.CHORD_1
        ev.channel == 2 -> StyleChannelType.PAD
        else -> StyleChannelType.PHRASE_1
      }
      events.add(
        StylePatternEvent(
          stepIndex = step,
          channel = ch,
          fixedMidiNote = ev.data1,
          chordDegree = if (ch == StyleChannelType.RHYTHM_1) -1 else Math.floorMod(ev.data1, 4),
          octaveOffset = if (ch == StyleChannelType.BASS) -2 else 0,
          velocity = ev.data2,
          durationSteps = 2,
        ),
      )
    }

    val base = BuiltInStyles.createAll().first()
    val importedPattern = StyleSectionPattern(StyleSection.MAIN_A, 2, events)
    val newStyle = base.copy(
      id = "user_style_${System.currentTimeMillis()}",
      name = styleName.ifBlank { song.title },
      category = category,
      defaultBpm = song.initialBpm,
      isUserStyle = true,
      sections = base.sections + (StyleSection.MAIN_A to importedPattern),
    )
    replaceOrAddUserStyle(newStyle)
    selectStyle(newStyle, applyTempo = true)
    return newStyle
  }

  fun replaceOrAddUserStyle(style: ArrangerStyle) {
    _state.update { st ->
      val updated = listOf(style) + st.allStyles.filterNot { it.id == style.id }
      st.copy(
        allStyles = updated,
        currentStyle = if (st.currentStyle?.id == style.id) style else st.currentStyle,
      )
    }
  }

  fun removeStylesWithPrefix(prefix: String) {
    _state.update { st ->
      val remaining = st.allStyles.filterNot { it.id.startsWith(prefix) && it.isUserStyle }
      val cur = if (st.currentStyle?.id?.startsWith(prefix) == true) remaining.firstOrNull() else st.currentStyle
      st.copy(allStyles = remaining, currentStyle = cur)
    }
  }

  private fun launchClock(fadeDirection: Int) {
    clockJob?.cancel()
    clockJob = scope.launch {
      var globalStep = 0L
      var sectionStep = 0
      var fadeVal = _state.value.fadeGain

      while (isActive && _state.value.isPlaying) {
        val st = _state.value
        val style = st.currentStyle ?: break
        val pattern = style.sections[st.currentSection] ?: style.sections[StyleSection.MAIN_A] ?: break

        if (fadeDirection > 0 && fadeVal < 1.0f) {
          fadeVal = (fadeVal + 0.05f).coerceAtMost(1.0f)
          _state.update { it.copy(fadeGain = fadeVal, isFading = fadeVal < 1.0f) }
        }

        // Release due notes
        val due = activeNoteOffs.filter { it.stepDue <= globalStep }
        if (due.isNotEmpty()) {
          due.forEach { audioEngine.noteOff(it.part, it.note) }
          activeNoteOffs.removeAll(due)
        }

        val currentStepInBar = sectionStep % 16
        val currentBar = (sectionStep / 16) + 1
        val currentBeat = (currentStepInBar / 4) + 1
        _state.update {
          it.copy(
            currentBar = currentBar,
            currentBeat = currentBeat,
            activeStepInBar = currentStepInBar,
          )
        }

        val chord = activeChord
        val voicing = chord.voicingNotes(baseOctaveMidi = 48 + transposeSemitones)
        val bassRoot = chord.bassMidiNote(bassOctaveMidi = 36 + transposeSemitones)

        // Trigger step events
        val stepEvents = pattern.events.filter { it.stepIndex == sectionStep }
        for (ev in stepEvents) {
          val chEnabled = st.channelEnabled[ev.channel] ?: true
          if (!chEnabled) continue
          // If ACMP is off, only play RHYTHM_1 and RHYTHM_2 drums/percussion
          if (!st.acmpEnabled && ev.channel != StyleChannelType.RHYTHM_1 && ev.channel != StyleChannelType.RHYTHM_2) {
            continue
          }

          val midiNote = if (ev.chordDegree < 0) {
            ev.fixedMidiNote
          } else if (ev.channel == StyleChannelType.BASS) {
            val idx = ev.chordDegree % voicing.size
            if (ev.chordDegree == 0) bassRoot else (voicing[idx] - 12).coerceIn(24, 55)
          } else {
            val base = voicing[ev.chordDegree % voicing.size]
            (base + ev.octaveOffset * 12).coerceIn(36, 96)
          }

          val vel = (ev.velocity * st.fadeGain).toInt().coerceIn(1, 127)
          audioEngine.noteOn(ev.channel.part, midiNote, vel)
          activeNoteOffs.add(ScheduledNoteOff(globalStep + ev.durationSteps.coerceAtLeast(1), ev.channel.part, midiNote))
        }

        val stepMs = (60_000.0 / st.tempoBpm / 4.0).toLong().coerceAtLeast(25L)
        delay(stepMs)

        globalStep++
        sectionStep++

        // Section boundary / bar boundary transitions
        val atBarEnd = (sectionStep % 16) == 0
        val atPatternEnd = sectionStep >= pattern.totalSteps

        val pending = _state.value.pendingSection
        if (atBarEnd && pending != null && !st.currentSection.isIntro && !st.currentSection.isEnding) {
          sectionStep = 0
          _state.update { it.copy(currentSection = pending, pendingSection = if (pending == StyleSection.BREAK) mainTargetBeforeFill else null) }
        } else if (atPatternEnd) {
          sectionStep = 0
          if (st.currentSection.isEnding) {
            stop()
            break
          } else if (st.currentSection.isIntro || st.currentSection.isFill || st.currentSection == StyleSection.BREAK) {
            _state.update { it.copy(currentSection = mainTargetBeforeFill, pendingSection = null) }
          }
        }
      }
    }
  }

  private fun flushAllStyleNotes() {
    activeNoteOffs.forEach { audioEngine.noteOff(it.part, it.note) }
    activeNoteOffs.clear()
    StyleChannelType.entries.forEach { audioEngine.allNotesOff(it.part) }
  }
}
