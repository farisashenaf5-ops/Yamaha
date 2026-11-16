package com.faris.psrs975.chord

enum class ChordFingeringMode(val displayName: String) {
  SINGLE_FINGER("Single Finger"),
  FINGERED("Fingered"),
  FINGERED_ON_BASS("Fingered On Bass"),
  FULL_KEYBOARD("Full Keyboard"),
  AI_FINGERED("AI Fingered"),
}

enum class ChordQuality(
  val symbol: String,
  val displayName: String,
  val intervals: IntArray,
) {
  MAJOR("", "Major", intArrayOf(0, 4, 7)),
  MINOR("m", "Minor", intArrayOf(0, 3, 7)),
  SEVENTH("7", "Dominant 7th", intArrayOf(0, 4, 7, 10)),
  MAJ7("maj7", "Major 7th", intArrayOf(0, 4, 7, 11)),
  MIN7("m7", "Minor 7th", intArrayOf(0, 3, 7, 10)),
  SUS2("sus2", "Suspended 2nd", intArrayOf(0, 2, 7)),
  SUS4("sus4", "Suspended 4th", intArrayOf(0, 5, 7)),
  DIM("dim", "Diminished", intArrayOf(0, 3, 6)),
  AUG("aug", "Augmented", intArrayOf(0, 4, 8)),
  SIXTH("6", "Major 6th", intArrayOf(0, 4, 7, 9)),
  MIN6("m6", "Minor 6th", intArrayOf(0, 3, 7, 9)),
  NINTH("9", "Dominant 9th", intArrayOf(0, 4, 7, 10, 14)),
  MAJ9("maj9", "Major 9th", intArrayOf(0, 4, 7, 11, 14)),
  MIN9("m9", "Minor 9th", intArrayOf(0, 3, 7, 10, 14)),
  ELEVENTH("11", "11th", intArrayOf(0, 4, 7, 10, 14, 17)),
  THIRTEENTH("13", "13th", intArrayOf(0, 4, 7, 10, 14, 21)),
}

data class DetectedChord(
  val rootPitchClass: Int, // 0 = C .. 11 = B
  val quality: ChordQuality,
  val bassPitchClass: Int = rootPitchClass,
) {
  val rootName: String
    get() = NOTE_NAMES[Math.floorMod(rootPitchClass, 12)]

  val bassName: String
    get() = NOTE_NAMES[Math.floorMod(bassPitchClass, 12)]

  val displaySymbol: String
    get() {
      val base = "$rootName${quality.symbol}"
      return if (Math.floorMod(bassPitchClass, 12) != Math.floorMod(rootPitchClass, 12)) {
        "$base/$bassName"
      } else {
        base
      }
    }

  fun transposed(semitones: Int): DetectedChord = copy(
    rootPitchClass = Math.floorMod(rootPitchClass + semitones, 12),
    bassPitchClass = Math.floorMod(bassPitchClass + semitones, 12),
  )

  /** Generates chord tone MIDI notes centered around [baseOctaveMidi] (e.g. 48 = C3). */
  fun voicingNotes(baseOctaveMidi: Int = 48): List<Int> {
    val rootMidi = baseOctaveMidi + Math.floorMod(rootPitchClass, 12)
    return quality.intervals.map { interval -> rootMidi + interval }
  }

  /** Generates bass MIDI note around [bassOctaveMidi] (e.g. 36 = C2). */
  fun bassMidiNote(bassOctaveMidi: Int = 36): Int =
    bassOctaveMidi + Math.floorMod(bassPitchClass, 12)

  companion object {
    val NOTE_NAMES = arrayOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
    val DEFAULT_C_MAJOR = DetectedChord(0, ChordQuality.MAJOR, 0)
  }
}
