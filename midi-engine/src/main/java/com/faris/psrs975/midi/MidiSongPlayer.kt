package com.faris.psrs975.midi

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

interface MidiPlaybackSink {
  fun onMidiNoteOn(channel: Int, note: Int, velocity: Int)
  fun onMidiNoteOff(channel: Int, note: Int)
  fun onMidiControlChange(channel: Int, cc: Int, value: Int)
  fun onMidiProgramChange(channel: Int, program: Int)
  fun onMidiPanic()
}

data class MidiPlayerState(
  val currentSong: MidiSong? = null,
  val isPlaying: Boolean = false,
  val isPaused: Boolean = false,
  val isRecording: Boolean = false,
  val currentTick: Long = 0L,
  val totalTicks: Long = 1920L,
  val currentBar: Int = 1,
  val currentBeat: Int = 1,
  val tempoBpm: Int = 120,
  val transpose: Int = 0,
  val loopEnabled: Boolean = false,
  val tracks: List<MidiTrackInfo> = emptyList(),
  val recordedEventCount: Int = 0,
)

/**
 * Multi-track Standard MIDI File sequencer and recorder supporting track mute/solo,
 * tempo override, transpose, loop playback, seeking, and live MIDI recording.
 */
class MidiSongPlayer(
  private val sink: MidiPlaybackSink,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var playbackJob: Job? = null

  private val _state = MutableStateFlow(MidiPlayerState())
  val state: StateFlow<MidiPlayerState> = _state.asStateFlow()

  private val _songs = MutableStateFlow(BuiltInMidiSongs.createDemoSongs())
  val songs: StateFlow<List<MidiSong>> = _songs.asStateFlow()

  private val recordedEvents = mutableListOf<MidiEvent>()
  private var recordStartMs = 0L

  init {
    _songs.value.firstOrNull()?.let { selectSong(it) }
  }

  fun addImportedSong(song: MidiSong) {
    _songs.update { list -> (listOf(song) + list.filterNot { it.id == song.id }) }
    selectSong(song)
  }

  fun selectSong(song: MidiSong) {
    stop()
    _state.update {
      it.copy(
        currentSong = song,
        currentTick = 0L,
        totalTicks = max(1L, song.totalTicks),
        currentBar = 1,
        currentBeat = 1,
        tempoBpm = song.initialBpm,
        tracks = song.tracks,
      )
    }
  }

  fun playOrPause() {
    val st = _state.value
    if (st.currentSong == null) return
    if (st.isPlaying) {
      pause()
    } else {
      startPlayback()
    }
  }

  fun startPlayback() {
    val song = _state.value.currentSong ?: return
    if (_state.value.isPlaying) return
    _state.update { it.copy(isPlaying = true, isPaused = false) }
    playbackJob?.cancel()
    playbackJob = scope.launch {
      var cursorTick = _state.value.currentTick
      var eventIdx = song.events.indexOfFirst { it.tick >= cursorTick }.let { if (it < 0) song.events.size else it }
      var lastStepTimeNs = System.nanoTime()

      while (isActive && _state.value.isPlaying) {
        val nowNs = System.nanoTime()
        val elapsedMs = (nowNs - lastStepTimeNs) / 1_000_000.0
        lastStepTimeNs = nowNs

        val st = _state.value
        val tpqn = song.ticksPerQuarterNote.toDouble()
        val ticksPerMs = (st.tempoBpm.toDouble() / 60_000.0) * tpqn
        val nextTick = cursorTick + (elapsedMs * ticksPerMs).toLong().coerceAtLeast(1L)

        val anySolo = st.tracks.any { it.soloed }
        val trackMap = st.tracks.associateBy { it.trackIndex }

        while (eventIdx < song.events.size && song.events[eventIdx].tick <= nextTick) {
          val ev = song.events[eventIdx++]
          val trk = trackMap[ev.trackIndex]
          val audible = trk == null || (!trk.muted && (!anySolo || trk.soloed))
          when (ev.type) {
            MidiEventType.NOTE_ON -> if (audible) sink.onMidiNoteOn(ev.channel, (ev.data1 + st.transpose).coerceIn(0, 127), ev.data2)
            MidiEventType.NOTE_OFF -> sink.onMidiNoteOff(ev.channel, (ev.data1 + st.transpose).coerceIn(0, 127))
            MidiEventType.CONTROL_CHANGE -> sink.onMidiControlChange(ev.channel, ev.data1, ev.data2)
            MidiEventType.PROGRAM_CHANGE -> sink.onMidiProgramChange(ev.channel, ev.data1)
            MidiEventType.PITCH_BEND -> Unit
            MidiEventType.TEMPO -> Unit
          }
        }

        cursorTick = nextTick
        val beatsPerBar = max(1, song.timeSignatureNumerator)
        val totalBeats = (cursorTick / max(1, song.ticksPerQuarterNote)).toInt()
        val bar = (totalBeats / beatsPerBar) + 1
        val beat = (totalBeats % beatsPerBar) + 1

        _state.update {
          it.copy(currentTick = cursorTick.coerceAtMost(song.totalTicks), currentBar = bar, currentBeat = beat)
        }

        if (cursorTick >= song.totalTicks) {
          sink.onMidiPanic()
          if (_state.value.loopEnabled) {
            cursorTick = 0L
            eventIdx = 0
          } else {
            _state.update { it.copy(isPlaying = false, isPaused = false, currentTick = 0L, currentBar = 1, currentBeat = 1) }
            break
          }
        }
        delay(8L)
      }
    }
  }

  fun pause() {
    playbackJob?.cancel()
    sink.onMidiPanic()
    _state.update { it.copy(isPlaying = false, isPaused = true) }
  }

  fun stop() {
    playbackJob?.cancel()
    sink.onMidiPanic()
    _state.update { it.copy(isPlaying = false, isPaused = false, currentTick = 0L, currentBar = 1, currentBeat = 1) }
  }

  fun seekToFraction(fraction: Float) {
    val song = _state.value.currentSong ?: return
    val targetTick = (fraction.coerceIn(0f, 1f) * song.totalTicks).toLong()
    sink.onMidiPanic()
    val beatsPerBar = max(1, song.timeSignatureNumerator)
    val totalBeats = (targetTick / max(1, song.ticksPerQuarterNote)).toInt()
    _state.update {
      it.copy(
        currentTick = targetTick,
        currentBar = (totalBeats / beatsPerBar) + 1,
        currentBeat = (totalBeats % beatsPerBar) + 1,
      )
    }
  }

  fun setTempo(bpm: Int) {
    _state.update { it.copy(tempoBpm = bpm.coerceIn(40, 260)) }
  }

  fun setTranspose(semitones: Int) {
    _state.update { it.copy(transpose = semitones.coerceIn(-12, 12)) }
  }

  fun toggleLoop() {
    _state.update { it.copy(loopEnabled = !it.loopEnabled) }
  }

  fun toggleTrackMute(trackIndex: Int) {
    _state.update { st ->
      st.copy(tracks = st.tracks.map { if (it.trackIndex == trackIndex) it.copy(muted = !it.muted) else it })
    }
  }

  fun toggleTrackSolo(trackIndex: Int) {
    _state.update { st ->
      st.copy(tracks = st.tracks.map { if (it.trackIndex == trackIndex) it.copy(soloed = !it.soloed) else it })
    }
  }

  fun startRecording() {
    synchronized(recordedEvents) {
      recordedEvents.clear()
      recordStartMs = System.currentTimeMillis()
    }
    _state.update { it.copy(isRecording = true, recordedEventCount = 0) }
  }

  fun recordLiveEvent(channel: Int, type: MidiEventType, data1: Int, data2: Int) {
    if (!_state.value.isRecording) return
    val elapsedMs = System.currentTimeMillis() - recordStartMs
    val bpm = _state.value.tempoBpm
    val tick = ((elapsedMs / 60_000.0) * bpm * 480.0).toLong().coerceAtLeast(0L)
    synchronized(recordedEvents) {
      recordedEvents.add(MidiEvent(tick = tick, trackIndex = 0, channel = channel, type = type, data1 = data1, data2 = data2))
      _state.update { it.copy(recordedEventCount = recordedEvents.size) }
    }
  }

  fun stopRecordingAndBuildSong(title: String): MidiSong? {
    _state.update { it.copy(isRecording = false) }
    val snapshot = synchronized(recordedEvents) { recordedEvents.toList() }
    if (snapshot.isEmpty()) return null
    val maxTick = max(1920L, snapshot.maxOf { it.tick } + 480L)
    val song = MidiSong(
      id = "rec_${System.currentTimeMillis()}",
      title = title,
      ticksPerQuarterNote = 480,
      initialBpm = _state.value.tempoBpm,
      totalTicks = maxTick,
      tracks = listOf(MidiTrackInfo(0, "Recorded Performance", 0, 0, snapshot.count { it.type == MidiEventType.NOTE_ON })),
      events = snapshot,
    )
    addImportedSong(song)
    return song
  }
}

object BuiltInMidiSongs {
  fun createDemoSongs(): List<MidiSong> = listOf(
    buildNewSongDemo(),
    buildEthioTizitaEtude(),
    buildConcertJazzBallad(),
  )

  private fun buildNewSongDemo(): MidiSong {
    val events = mutableListOf<MidiEvent>()
    val tpqn = 480
    // 8-bar pop piano + bass + drum demo
    val melody = listOf(
      0 to 72, 240 to 74, 480 to 76, 960 to 79, 1440 to 76, 1920 to 74, 2400 to 72, 2880 to 74,
      3840 to 76, 4320 to 79, 4800 to 81, 5280 to 79, 5760 to 76, 6240 to 74, 6720 to 72,
    )
    for ((tick, note) in melody) {
      events.add(MidiEvent(tick.toLong(), 0, 0, MidiEventType.NOTE_ON, note, 98))
      events.add(MidiEvent(tick.toLong() + 380L, 0, 0, MidiEventType.NOTE_OFF, note, 0))
    }
    val chords = listOf(
      0L to intArrayOf(48, 52, 55, 60),
      1920L to intArrayOf(45, 48, 52, 57),
      3840L to intArrayOf(41, 45, 48, 53),
      5760L to intArrayOf(43, 47, 50, 55),
    )
    for ((barTick, notes) in chords) {
      for (n in notes) {
        events.add(MidiEvent(barTick, 1, 1, MidiEventType.NOTE_ON, n, 74))
        events.add(MidiEvent(barTick + 1800L, 1, 1, MidiEventType.NOTE_OFF, n, 0))
      }
      // Bass root notes on beats 1 & 3
      val bass = notes[0] - 12
      events.add(MidiEvent(barTick, 2, 2, MidiEventType.NOTE_ON, bass, 92))
      events.add(MidiEvent(barTick + 840L, 2, 2, MidiEventType.NOTE_OFF, bass, 0))
      events.add(MidiEvent(barTick + 960L, 2, 2, MidiEventType.NOTE_ON, bass, 86))
      events.add(MidiEvent(barTick + 1800L, 2, 2, MidiEventType.NOTE_OFF, bass, 0))
    }
    // Kick & hi-hat on ch 9
    for (beat in 0 until 16) {
      val t = beat.toLong() * tpqn
      val drum = if (beat % 2 == 0) 36 else 38
      events.add(MidiEvent(t, 3, 9, MidiEventType.NOTE_ON, drum, 95))
      events.add(MidiEvent(t + 120L, 3, 9, MidiEventType.NOTE_OFF, drum, 0))
      events.add(MidiEvent(t, 3, 9, MidiEventType.NOTE_ON, 42, 72))
      events.add(MidiEvent(t + 100L, 3, 9, MidiEventType.NOTE_OFF, 42, 0))
      events.add(MidiEvent(t + 240L, 3, 9, MidiEventType.NOTE_ON, 42, 60))
      events.add(MidiEvent(t + 340L, 3, 9, MidiEventType.NOTE_OFF, 42, 0))
    }
    return MidiSong(
      id = "demo_newsong",
      title = "NewSong",
      ticksPerQuarterNote = tpqn,
      initialBpm = 120,
      totalTicks = 7680L,
      tracks = listOf(
        MidiTrackInfo(0, "TR1 Melody", 0, 0, melody.size),
        MidiTrackInfo(1, "TR2 Backing Chords", 1, 48, 16),
        MidiTrackInfo(2, "TR3 Bass", 2, 32, 8),
        MidiTrackInfo(3, "TR4 Rhythm Kit", 9, 0, 48),
      ),
      events = events.sortedBy { it.tick },
    )
  }

  private fun buildEthioTizitaEtude(): MidiSong {
    // Pentatonic Tizita scale: C D Eb G Ab
    val notes = listOf(60, 62, 63, 67, 68, 72, 68, 67, 63, 62, 60)
    val events = mutableListOf<MidiEvent>()
    notes.forEachIndexed { idx, n ->
      val t = idx * 480L
      events.add(MidiEvent(t, 0, 0, MidiEventType.NOTE_ON, n, 92))
      events.add(MidiEvent(t + 420L, 0, 0, MidiEventType.NOTE_OFF, n, 0))
    }
    return MidiSong(
      id = "demo_tizita",
      title = "Ethio Tizita Etude",
      ticksPerQuarterNote = 480,
      initialBpm = 88,
      totalTicks = notes.size * 480L,
      tracks = listOf(MidiTrackInfo(0, "Krar / Washint Lead", 0, 0, notes.size)),
      events = events,
    )
  }

  private fun buildConcertJazzBallad(): MidiSong {
    val progression = listOf(
      intArrayOf(50, 53, 57, 60), // Dm7
      intArrayOf(43, 47, 53, 57), // G13
      intArrayOf(48, 52, 55, 59), // Cmaj7
      intArrayOf(45, 48, 52, 55), // Am7
    )
    val events = mutableListOf<MidiEvent>()
    progression.forEachIndexed { i, chord ->
      val t = i * 1920L
      for (n in chord) {
        events.add(MidiEvent(t, 0, 0, MidiEventType.NOTE_ON, n, 76))
        events.add(MidiEvent(t + 1780L, 0, 0, MidiEventType.NOTE_OFF, n, 0))
      }
    }
    return MidiSong(
      id = "demo_jazz",
      title = "Concert Grand Ballad",
      ticksPerQuarterNote = 480,
      initialBpm = 76,
      totalTicks = 7680L,
      tracks = listOf(MidiTrackInfo(0, "Grand Piano Voicings", 0, 0, 16)),
      events = events.sortedBy { it.tick },
    )
  }
}
