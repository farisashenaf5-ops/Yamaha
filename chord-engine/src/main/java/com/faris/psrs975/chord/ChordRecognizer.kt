package com.faris.psrs975.chord

/**
 * Yamaha-style real-time arranger chord recognition supporting Single Finger,
 * Fingered, Fingered On Bass, Full Keyboard, and AI Fingered modes.
 */
object ChordRecognizer {

  private data class ChordTemplate(
    val quality: ChordQuality,
    val pitchClassMask: Int,
    val priority: Int,
  )

  private val templates: List<ChordTemplate> = buildList {
    // Ordered so richer extensions match cleanly when all required intervals are present
    add(ChordTemplate(ChordQuality.THIRTEENTH, maskOf(0, 4, 7, 10, 2, 9), 1))
    add(ChordTemplate(ChordQuality.THIRTEENTH, maskOf(0, 4, 10, 2, 9), 2))
    add(ChordTemplate(ChordQuality.ELEVENTH, maskOf(0, 4, 7, 10, 2, 5), 3))
    add(ChordTemplate(ChordQuality.ELEVENTH, maskOf(0, 7, 10, 2, 5), 4))
    add(ChordTemplate(ChordQuality.MAJ9, maskOf(0, 4, 7, 11, 2), 5))
    add(ChordTemplate(ChordQuality.MIN9, maskOf(0, 3, 7, 10, 2), 6))
    add(ChordTemplate(ChordQuality.NINTH, maskOf(0, 4, 7, 10, 2), 7))
    add(ChordTemplate(ChordQuality.MAJ7, maskOf(0, 4, 7, 11), 8))
    add(ChordTemplate(ChordQuality.MAJ7, maskOf(0, 4, 11), 9))
    add(ChordTemplate(ChordQuality.MIN7, maskOf(0, 3, 7, 10), 10))
    add(ChordTemplate(ChordQuality.SEVENTH, maskOf(0, 4, 7, 10), 11))
    add(ChordTemplate(ChordQuality.SEVENTH, maskOf(0, 4, 10), 12))
    add(ChordTemplate(ChordQuality.SIXTH, maskOf(0, 4, 7, 9), 13))
    add(ChordTemplate(ChordQuality.MIN6, maskOf(0, 3, 7, 9), 14))
    add(ChordTemplate(ChordQuality.MAJOR, maskOf(0, 4, 7), 15))
    add(ChordTemplate(ChordQuality.MINOR, maskOf(0, 3, 7), 16))
    add(ChordTemplate(ChordQuality.SUS4, maskOf(0, 5, 7), 17))
    add(ChordTemplate(ChordQuality.SUS2, maskOf(0, 2, 7), 18))
    add(ChordTemplate(ChordQuality.DIM, maskOf(0, 3, 6), 19))
    add(ChordTemplate(ChordQuality.AUG, maskOf(0, 4, 8), 20))
  }

  fun detect(
    heldNotes: Collection<Int>,
    mode: ChordFingeringMode,
    splitPointMidi: Int = 54,
    lastChord: DetectedChord? = null,
  ): DetectedChord? {
    val zoneNotes = when (mode) {
      ChordFingeringMode.FULL_KEYBOARD,
      ChordFingeringMode.AI_FINGERED -> heldNotes.sorted()
      else -> heldNotes.filter { it < splitPointMidi }.sorted()
    }
    if (zoneNotes.isEmpty()) return lastChord

    val lowestNote = zoneNotes.first()
    val lowestPc = Math.floorMod(lowestNote, 12)
    val distinctPcs = zoneNotes.map { Math.floorMod(it, 12) }.distinct()

    if (mode == ChordFingeringMode.SINGLE_FINGER) {
      return detectSingleFinger(zoneNotes, lowestPc, distinctPcs) ?: lastChord
    }

    if (distinctPcs.size == 1) {
      // Single key in fingered modes defaults to Major only in AI_FINGERED if nothing held, else keep last or major
      return if (mode == ChordFingeringMode.AI_FINGERED) {
        DetectedChord(lowestPc, ChordQuality.MAJOR, lowestPc)
      } else {
        lastChord
      }
    }

    if (distinctPcs.size == 2) {
      val otherPc = distinctPcs.first { it != lowestPc }
      val interval = Math.floorMod(otherPc - lowestPc, 12)
      val quality = when (interval) {
        4 -> ChordQuality.MAJOR
        3 -> ChordQuality.MINOR
        10 -> ChordQuality.SEVENTH
        7 -> if (mode == ChordFingeringMode.AI_FINGERED) ChordQuality.MAJOR else null
        else -> null
      }
      if (quality != null) {
        return DetectedChord(lowestPc, quality, lowestPc)
      }
    }

    val inputMask = distinctPcs.fold(0) { acc, pc -> acc or (1 shl pc) }

    // Try every candidate root among the held pitch classes (prefer lowest note on ties)
    var bestMatch: DetectedChord? = null
    var bestScore = Int.MAX_VALUE

    val orderedRoots = listOf(lowestPc) + distinctPcs.filter { it != lowestPc }
    for (root in orderedRoots) {
      val rotatedMask = rotateMaskToRoot(inputMask, root)
      for (tpl in templates) {
        if (rotatedMask == tpl.pitchClassMask) {
          val inversionPenalty = if (root == lowestPc) 0 else 25
          val score = tpl.priority + inversionPenalty
          if (score < bestScore) {
            bestScore = score
            val bass = if (mode == ChordFingeringMode.FINGERED_ON_BASS) lowestPc else root
            bestMatch = DetectedChord(root, tpl.quality, bass)
          }
        }
      }
    }

    if (bestMatch != null) return bestMatch

    // AI Fingered / Full Keyboard fuzzy subset matching
    if (mode == ChordFingeringMode.AI_FINGERED || mode == ChordFingeringMode.FULL_KEYBOARD) {
      for (root in orderedRoots) {
        val rotatedMask = rotateMaskToRoot(inputMask, root)
        for (tpl in templates) {
          val overlap = Integer.bitCount(rotatedMask and tpl.pitchClassMask)
          val extra = Integer.bitCount(rotatedMask and tpl.pitchClassMask.inv())
          if (overlap >= 3 && extra == 0) {
            val bass = if (root != lowestPc) lowestPc else root
            return DetectedChord(root, tpl.quality, bass)
          }
        }
      }
    }

    return lastChord
  }

  /**
   * Yamaha Single-Finger convention:
   * - 1 key: Major
   * - Root + black key to its left (or 1-2 semitones below): Minor
   * - Root + white key to its left (or 2 semitones below / flat 7): 7th
   * - Root + black + white to its left (minor + 7th): Minor 7th
   */
  private fun detectSingleFinger(
    sortedNotes: List<Int>,
    lowestPc: Int,
    distinctPcs: List<Int>,
  ): DetectedChord? {
    if (sortedNotes.size == 1) {
      return DetectedChord(lowestPc, ChordQuality.MAJOR, lowestPc)
    }
    // Highest note in Yamaha single finger is often the root when pressing left-hand helper keys below it
    val highestNote = sortedNotes.last()
    val rootPc = Math.floorMod(highestNote, 12)
    val intervalsBelow = sortedNotes.dropLast(1).map { highestNote - it }

    val hasMinorHelper = intervalsBelow.any { it == 1 || it == 3 }
    val hasSeventhHelper = intervalsBelow.any { it == 2 }

    return when {
      hasMinorHelper && hasSeventhHelper -> DetectedChord(rootPc, ChordQuality.MIN7, rootPc)
      hasMinorHelper -> DetectedChord(rootPc, ChordQuality.MINOR, rootPc)
      hasSeventhHelper -> DetectedChord(rootPc, ChordQuality.SEVENTH, rootPc)
      distinctPcs.size >= 3 -> {
        // User actually played a full chord in Single Finger mode
        detect(sortedNotes, ChordFingeringMode.FINGERED, 128, null)
      }
      else -> DetectedChord(lowestPc, ChordQuality.MAJOR, lowestPc)
    }
  }

  private fun maskOf(vararg pcs: Int): Int =
    pcs.fold(0) { acc, pc -> acc or (1 shl Math.floorMod(pc, 12)) }

  private fun rotateMaskToRoot(mask: Int, root: Int): Int {
    var out = 0
    for (pc in 0 until 12) {
      if ((mask and (1 shl pc)) != 0) {
        val interval = Math.floorMod(pc - root, 12)
        out = out or (1 shl interval)
      }
    }
    return out
  }
}
