package com.faris.psrs975.style

object BuiltInStyles {

  fun createAll(): List<ArrangerStyle> = listOf(
    buildStyle(
      id = "style_concert_grand_pop",
      name = "ConcertGrand",
      category = StyleCategory.POP_ROCK,
      bpm = 120,
      kickSteps = listOf(0, 8, 10),
      snareSteps = listOf(4, 12),
      hatSteps = listOf(0, 2, 4, 6, 8, 10, 12, 14),
      bassPattern = listOf(0 to 0, 6 to 0, 8 to 2, 12 to 0),
      chordStrumSteps = listOf(0, 6, 10, 14),
      phraseSteps = listOf(0 to 2, 4 to 3, 8 to 2, 12 to 0),
    ),
    buildStyle(
      id = "style_ethio_tizita",
      name = "EthioTizita",
      category = StyleCategory.WORLD,
      bpm = 92,
      kickSteps = listOf(0, 6, 10),
      snareSteps = listOf(4, 12, 14),
      hatSteps = listOf(0, 3, 6, 9, 12, 14),
      bassPattern = listOf(0 to 0, 6 to 2, 10 to 0, 14 to 2),
      chordStrumSteps = listOf(0, 3, 6, 10, 12),
      phraseSteps = listOf(0 to 0, 3 to 1, 6 to 2, 10 to 3, 14 to 1),
      channelVoices = mapOf(
        StyleChannelType.RHYTHM_1 to "drum_std",
        StyleChannelType.RHYTHM_2 to "eth_kebero",
        StyleChannelType.BASS to "syn_bass",
        StyleChannelType.CHORD_1 to "eth_krar",
        StyleChannelType.CHORD_2 to "piano_electric",
        StyleChannelType.PAD to "syn_pad",
        StyleChannelType.PHRASE_1 to "eth_washint",
        StyleChannelType.PHRASE_2 to "eth_masinko",
      ),
      otsVoices = listOf(
        OneTouchSetting(1, "eth_krar", "syn_pad", "str_ensemble", "Krar & Pad"),
        OneTouchSetting(2, "eth_masinko", null, "eth_krar", "Masinko Solo"),
        OneTouchSetting(3, "eth_washint", "str_ensemble", "eth_krar", "Washint"),
        OneTouchSetting(4, "piano_grand", "eth_krar", "syn_bass", "Ethio Grand"),
      ),
    ),
    buildStyle(
      id = "style_euro_dance",
      name = "EuroClubDance",
      category = StyleCategory.DANCE,
      bpm = 128,
      kickSteps = listOf(0, 4, 8, 12),
      snareSteps = listOf(4, 12),
      hatSteps = listOf(2, 6, 10, 14),
      bassPattern = listOf(0 to 0, 2 to 0, 4 to 0, 6 to 2, 8 to 0, 10 to 0, 12 to 2, 14 to 0),
      chordStrumSteps = listOf(2, 6, 10, 14),
      phraseSteps = listOf(0 to 0, 4 to 2, 8 to 3, 12 to 2),
    ),
    buildStyle(
      id = "style_warm_ballad",
      name = "WarmBallad",
      category = StyleCategory.BALLAD,
      bpm = 74,
      kickSteps = listOf(0, 10),
      snareSteps = listOf(4, 12),
      hatSteps = listOf(0, 2, 4, 6, 8, 10, 12, 14),
      bassPattern = listOf(0 to 0, 8 to 2),
      chordStrumSteps = listOf(0, 4, 8, 12),
      phraseSteps = listOf(0 to 1, 8 to 2),
    ),
    buildStyle(
      id = "style_swing_jazz",
      name = "SwingJazz",
      category = StyleCategory.SWING_JAZZ,
      bpm = 132,
      kickSteps = listOf(0, 8),
      snareSteps = listOf(4, 12),
      hatSteps = listOf(0, 3, 4, 7, 8, 11, 12, 15),
      bassPattern = listOf(0 to 0, 4 to 1, 8 to 2, 12 to 3),
      chordStrumSteps = listOf(3, 8, 15),
      phraseSteps = listOf(0 to 2, 7 to 3, 12 to 1),
    ),
    buildStyle(
      id = "style_neo_rnb",
      name = "NeoSoulRnB",
      category = StyleCategory.RNB,
      bpm = 88,
      kickSteps = listOf(0, 7, 10),
      snareSteps = listOf(4, 12),
      hatSteps = listOf(0, 2, 4, 6, 8, 10, 12, 14),
      bassPattern = listOf(0 to 0, 7 to 2, 10 to 0, 14 to 3),
      chordStrumSteps = listOf(0, 6, 12),
      phraseSteps = listOf(2 to 1, 10 to 3),
    ),
    buildStyle(
      id = "style_country_shuffle",
      name = "CountryShuffle",
      category = StyleCategory.COUNTRY,
      bpm = 114,
      kickSteps = listOf(0, 8),
      snareSteps = listOf(4, 12),
      hatSteps = listOf(0, 3, 4, 7, 8, 11, 12, 15),
      bassPattern = listOf(0 to 0, 8 to 2),
      chordStrumSteps = listOf(0, 4, 7, 8, 12, 15),
      phraseSteps = listOf(0 to 0, 8 to 2),
    ),
    buildStyle(
      id = "style_bossa_latin",
      name = "BossaNova",
      category = StyleCategory.LATIN,
      bpm = 118,
      kickSteps = listOf(0, 3, 8, 11),
      snareSteps = listOf(0, 3, 6, 10, 12),
      hatSteps = listOf(0, 2, 4, 6, 8, 10, 12, 14),
      bassPattern = listOf(0 to 0, 6 to 2, 8 to 2, 14 to 0),
      chordStrumSteps = listOf(0, 3, 6, 10, 14),
      phraseSteps = listOf(0 to 2, 6 to 3, 12 to 1),
    ),
    buildStyle(
      id = "style_entertainer_show",
      name = "ShowTimeBand",
      category = StyleCategory.ENTERTAINER,
      bpm = 124,
      kickSteps = listOf(0, 4, 8, 12),
      snareSteps = listOf(4, 12),
      hatSteps = listOf(0, 2, 4, 6, 8, 10, 12, 14),
      bassPattern = listOf(0 to 0, 4 to 2, 8 to 0, 12 to 2),
      chordStrumSteps = listOf(2, 6, 10, 14),
      phraseSteps = listOf(0 to 0, 4 to 2, 8 to 3, 12 to 2),
    ),
  )

  private fun buildStyle(
    id: String,
    name: String,
    category: StyleCategory,
    bpm: Int,
    kickSteps: List<Int>,
    snareSteps: List<Int>,
    hatSteps: List<Int>,
    bassPattern: List<Pair<Int, Int>>,
    chordStrumSteps: List<Int>,
    phraseSteps: List<Pair<Int, Int>>,
    channelVoices: Map<StyleChannelType, String> = ArrangerStyle.defaultChannelVoices(),
    otsVoices: List<OneTouchSetting> = listOf(
      OneTouchSetting(1, "piano_grand", "syn_pad", "str_ensemble", "Grand & Pad"),
      OneTouchSetting(2, "wood_tenor_sax", null, "piano_electric", "Tenor Sax"),
      OneTouchSetting(3, "brass_section", "str_ensemble", "piano_grand", "Brass Lead"),
      OneTouchSetting(4, "gtr_nylon", "str_ensemble", "syn_pad", "Nylon & Str"),
    ),
  ): ArrangerStyle {
    fun buildMain(section: StyleSection, densityBoost: Int): StyleSectionPattern {
      val events = mutableListOf<StylePatternEvent>()
      for (bar in 0 until 2) {
        val offset = bar * 16
        kickSteps.forEach { events.add(StylePatternEvent(offset + it, StyleChannelType.RHYTHM_1, fixedMidiNote = 36, velocity = 104)) }
        snareSteps.forEach { events.add(StylePatternEvent(offset + it, StyleChannelType.RHYTHM_1, fixedMidiNote = 38, velocity = 92)) }
        hatSteps.forEach { events.add(StylePatternEvent(offset + it, StyleChannelType.RHYTHM_1, fixedMidiNote = 42, velocity = 68)) }

        if (densityBoost >= 1) {
          events.add(StylePatternEvent(offset + 0, StyleChannelType.RHYTHM_2, fixedMidiNote = 36, velocity = 82))
          events.add(StylePatternEvent(offset + 6, StyleChannelType.RHYTHM_2, fixedMidiNote = 60, velocity = 76))
          events.add(StylePatternEvent(offset + 12, StyleChannelType.RHYTHM_2, fixedMidiNote = 60, velocity = 84))
        }
        if (densityBoost >= 2 && bar == 0) {
          events.add(StylePatternEvent(offset + 0, StyleChannelType.RHYTHM_1, fixedMidiNote = 49, velocity = 80))
        }

        bassPattern.forEach { (step, deg) ->
          events.add(StylePatternEvent(offset + step, StyleChannelType.BASS, chordDegree = deg, octaveOffset = -2, velocity = 98, durationSteps = 3))
        }

        chordStrumSteps.forEach { step ->
          events.add(StylePatternEvent(offset + step, StyleChannelType.CHORD_1, chordDegree = 0, velocity = 74, durationSteps = 2))
          events.add(StylePatternEvent(offset + step, StyleChannelType.CHORD_1, chordDegree = 1, velocity = 70, durationSteps = 2))
          events.add(StylePatternEvent(offset + step, StyleChannelType.CHORD_1, chordDegree = 2, velocity = 72, durationSteps = 2))
          if (densityBoost >= 1) {
            events.add(StylePatternEvent(offset + step, StyleChannelType.CHORD_2, chordDegree = 1, octaveOffset = 1, velocity = 64, durationSteps = 2))
            events.add(StylePatternEvent(offset + step, StyleChannelType.CHORD_2, chordDegree = 2, octaveOffset = 1, velocity = 66, durationSteps = 2))
          }
        }

        // Sustained pad chord per bar
        if (densityBoost >= 1) {
          events.add(StylePatternEvent(offset + 0, StyleChannelType.PAD, chordDegree = 0, velocity = 60, durationSteps = 14))
          events.add(StylePatternEvent(offset + 0, StyleChannelType.PAD, chordDegree = 2, velocity = 58, durationSteps = 14))
        }

        // Phrase counter-melody in higher variations C & D
        if (densityBoost >= 2) {
          phraseSteps.forEach { (step, deg) ->
            events.add(StylePatternEvent(offset + step, StyleChannelType.PHRASE_1, chordDegree = deg, octaveOffset = 1, velocity = 76, durationSteps = 2))
          }
        }
        if (densityBoost >= 3) {
          phraseSteps.forEach { (step, deg) ->
            events.add(StylePatternEvent(offset + step, StyleChannelType.PHRASE_2, chordDegree = (deg + 1) % 3, octaveOffset = 1, velocity = 68, durationSteps = 3))
          }
        }
      }
      return StyleSectionPattern(section, lengthBars = 2, events = events)
    }

    fun buildFill(section: StyleSection): StyleSectionPattern {
      val events = mutableListOf<StylePatternEvent>()
      events.add(StylePatternEvent(0, StyleChannelType.RHYTHM_1, fixedMidiNote = 36, velocity = 104))
      events.add(StylePatternEvent(4, StyleChannelType.RHYTHM_1, fixedMidiNote = 45, velocity = 96))
      events.add(StylePatternEvent(8, StyleChannelType.RHYTHM_1, fixedMidiNote = 41, velocity = 98))
      events.add(StylePatternEvent(10, StyleChannelType.RHYTHM_1, fixedMidiNote = 38, velocity = 100))
      events.add(StylePatternEvent(12, StyleChannelType.RHYTHM_1, fixedMidiNote = 38, velocity = 106))
      events.add(StylePatternEvent(14, StyleChannelType.RHYTHM_1, fixedMidiNote = 38, velocity = 112))
      events.add(StylePatternEvent(0, StyleChannelType.BASS, chordDegree = 0, octaveOffset = -2, velocity = 95, durationSteps = 4))
      events.add(StylePatternEvent(8, StyleChannelType.BASS, chordDegree = 2, octaveOffset = -2, velocity = 95, durationSteps = 4))
      return StyleSectionPattern(section, lengthBars = 1, events = events)
    }

    fun buildBreak(): StyleSectionPattern {
      val events = listOf(
        StylePatternEvent(0, StyleChannelType.RHYTHM_1, fixedMidiNote = 49, velocity = 96),
        StylePatternEvent(0, StyleChannelType.CHORD_1, chordDegree = 0, velocity = 82, durationSteps = 3),
        StylePatternEvent(0, StyleChannelType.CHORD_1, chordDegree = 1, velocity = 78, durationSteps = 3),
        StylePatternEvent(12, StyleChannelType.RHYTHM_1, fixedMidiNote = 38, velocity = 88),
        StylePatternEvent(14, StyleChannelType.RHYTHM_1, fixedMidiNote = 38, velocity = 98),
      )
      return StyleSectionPattern(StyleSection.BREAK, lengthBars = 1, events = events)
    }

    fun buildIntro(section: StyleSection, bars: Int): StyleSectionPattern {
      val events = mutableListOf<StylePatternEvent>()
      for (b in 0 until bars) {
        val off = b * 16
        events.add(StylePatternEvent(off + 0, StyleChannelType.RHYTHM_1, fixedMidiNote = 42, velocity = 80))
        events.add(StylePatternEvent(off + 4, StyleChannelType.RHYTHM_1, fixedMidiNote = 38, velocity = 88))
        events.add(StylePatternEvent(off + 8, StyleChannelType.RHYTHM_1, fixedMidiNote = 36, velocity = 94))
        events.add(StylePatternEvent(off + 12, StyleChannelType.RHYTHM_1, fixedMidiNote = 38, velocity = 94))
        events.add(StylePatternEvent(off + 0, StyleChannelType.BASS, chordDegree = 0, octaveOffset = -2, velocity = 90, durationSteps = 6))
        events.add(StylePatternEvent(off + 0, StyleChannelType.PHRASE_1, chordDegree = b % 3, octaveOffset = 1, velocity = 84, durationSteps = 6))
        events.add(StylePatternEvent(off + 8, StyleChannelType.CHORD_1, chordDegree = 1, velocity = 74, durationSteps = 4))
      }
      return StyleSectionPattern(section, lengthBars = bars, events = events)
    }

    fun buildEnding(section: StyleSection, bars: Int): StyleSectionPattern {
      val events = mutableListOf<StylePatternEvent>()
      for (b in 0 until bars) {
        val off = b * 16
        if (b < bars - 1) {
          events.add(StylePatternEvent(off + 0, StyleChannelType.RHYTHM_1, fixedMidiNote = 36, velocity = 96))
          events.add(StylePatternEvent(off + 8, StyleChannelType.RHYTHM_1, fixedMidiNote = 38, velocity = 92))
          events.add(StylePatternEvent(off + 0, StyleChannelType.CHORD_1, chordDegree = 0, velocity = 78, durationSteps = 8))
        } else {
          events.add(StylePatternEvent(off + 0, StyleChannelType.RHYTHM_1, fixedMidiNote = 49, velocity = 104))
          events.add(StylePatternEvent(off + 0, StyleChannelType.RHYTHM_1, fixedMidiNote = 36, velocity = 104))
          events.add(StylePatternEvent(off + 0, StyleChannelType.BASS, chordDegree = 0, octaveOffset = -2, velocity = 98, durationSteps = 12))
          events.add(StylePatternEvent(off + 0, StyleChannelType.CHORD_1, chordDegree = 0, velocity = 82, durationSteps = 12))
          events.add(StylePatternEvent(off + 0, StyleChannelType.CHORD_1, chordDegree = 1, velocity = 80, durationSteps = 12))
          events.add(StylePatternEvent(off + 0, StyleChannelType.CHORD_1, chordDegree = 2, velocity = 82, durationSteps = 12))
          events.add(StylePatternEvent(off + 0, StyleChannelType.PAD, chordDegree = 0, velocity = 70, durationSteps = 14))
        }
      }
      return StyleSectionPattern(section, lengthBars = bars, events = events)
    }

    val sections = mapOf(
      StyleSection.INTRO_1 to buildIntro(StyleSection.INTRO_1, 1),
      StyleSection.INTRO_2 to buildIntro(StyleSection.INTRO_2, 2),
      StyleSection.INTRO_3 to buildIntro(StyleSection.INTRO_3, 4),
      StyleSection.MAIN_A to buildMain(StyleSection.MAIN_A, 0),
      StyleSection.MAIN_B to buildMain(StyleSection.MAIN_B, 1),
      StyleSection.MAIN_C to buildMain(StyleSection.MAIN_C, 2),
      StyleSection.MAIN_D to buildMain(StyleSection.MAIN_D, 3),
      StyleSection.FILL_A to buildFill(StyleSection.FILL_A),
      StyleSection.FILL_B to buildFill(StyleSection.FILL_B),
      StyleSection.FILL_C to buildFill(StyleSection.FILL_C),
      StyleSection.FILL_D to buildFill(StyleSection.FILL_D),
      StyleSection.BREAK to buildBreak(),
      StyleSection.ENDING_1 to buildEnding(StyleSection.ENDING_1, 1),
      StyleSection.ENDING_2 to buildEnding(StyleSection.ENDING_2, 2),
      StyleSection.ENDING_3 to buildEnding(StyleSection.ENDING_3, 4),
    )

    return ArrangerStyle(
      id = id,
      name = name,
      category = category,
      defaultBpm = bpm,
      sections = sections,
      otsPresets = otsVoices,
      channelVoices = channelVoices,
    )
  }
}
