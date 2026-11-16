package com.faris.psrs975.voice

import com.faris.psrs975.samples.AcousticSynthesisModel

object VoiceCatalog {
  val builtInVoices: List<VoicePatch> = listOf(
    // PIANO
    VoicePatch("piano_grand", "Grand Piano", VoiceCategory.PIANO, AcousticSynthesisModel.GRAND_PIANO, "S.Art!", sourceFormat = "15Z • 3V PCM"),
    VoicePatch("piano_live_grand", "Live! Grand Piano", VoiceCategory.PIANO, AcousticSynthesisModel.GRAND_PIANO, "Live!", defaultBrightness = 1.12f, sourceFormat = "15Z • 3V PCM"),
    VoicePatch("piano_bright", "Bright Piano", VoiceCategory.PIANO, AcousticSynthesisModel.BRIGHT_PIANO, "Cool!", sourceFormat = "15Z • 3V PCM"),
    VoicePatch("piano_electric", "Electric Piano", VoiceCategory.PIANO, AcousticSynthesisModel.ELECTRIC_TINE, "Cool!", sourceFormat = "15Z • 2V PCM"),
    VoicePatch("piano_stage", "Stage Piano", VoiceCategory.PIANO, AcousticSynthesisModel.ELECTRIC_TINE, "S.Art!", defaultBrightness = 1.18f, sourceFormat = "15Z • 2V PCM"),
    VoicePatch("piano_honky", "Honky Tonk", VoiceCategory.PIANO, AcousticSynthesisModel.HONKY_TONK, "Vintage", sourceFormat = "15Z • PCM"),

    // KEYBOARD
    VoicePatch("kbd_epiano", "Suitcase E.Piano", VoiceCategory.KEYBOARD, AcousticSynthesisModel.ELECTRIC_TINE, "Cool!", sourceFormat = "15Z • 2V PCM"),
    VoicePatch("kbd_clavinet", "Clavinet", VoiceCategory.KEYBOARD, AcousticSynthesisModel.CLAVINET, "Funk", sourceFormat = "15Z • PCM"),
    VoicePatch("kbd_harpsichord", "Harpsichord", VoiceCategory.KEYBOARD, AcousticSynthesisModel.HARPSICHORD, "Baroque", sourceFormat = "15Z • PCM"),
    VoicePatch("kbd_church_organ", "Church Organ", VoiceCategory.KEYBOARD, AcousticSynthesisModel.CHURCH_ORGAN, "Organ!", sourceFormat = "15Z • Pipe PCM"),
    VoicePatch("kbd_organ", "Pipe Organ", VoiceCategory.KEYBOARD, AcousticSynthesisModel.PIPE_ORGAN, "Organ!", sourceFormat = "15Z • Pipe PCM"),
    VoicePatch("kbd_drawbar", "Drawbar Organ", VoiceCategory.KEYBOARD, AcousticSynthesisModel.DRAWBAR_ORGAN, "Organ!", sourceFormat = "15Z • PCM"),
    VoicePatch("kbd_accordion", "Accordion", VoiceCategory.KEYBOARD, AcousticSynthesisModel.ACCORDION_MUSETTE, "S.Art!", sourceFormat = "15Z • 2V PCM"),

    // WOODWIND
    VoicePatch("wood_tenor_sax", "Tenor Saxophone", VoiceCategory.WOODWIND, AcousticSynthesisModel.SAX_REED, "Sweet!", defaultOctaveOffset = -1, sourceFormat = "15Z • 2V PCM"),
    VoicePatch("wood_alto_sax", "Alto Saxophone", VoiceCategory.WOODWIND, AcousticSynthesisModel.SAX_REED, "Sweet!", sourceFormat = "15Z • 2V PCM"),
    VoicePatch("wood_soprano_sax", "Soprano Saxophone", VoiceCategory.WOODWIND, AcousticSynthesisModel.SAX_REED, "Sweet!", defaultBrightness = 1.2f, sourceFormat = "15Z • 2V PCM"),
    VoicePatch("wood_flute", "Flute", VoiceCategory.WOODWIND, AcousticSynthesisModel.FLUTE_BREATH, "Sweet!", sourceFormat = "15Z • PCM"),
    VoicePatch("wood_clarinet", "Clarinet", VoiceCategory.WOODWIND, AcousticSynthesisModel.CLARINET_HOLLOW, "Sweet!", sourceFormat = "15Z • PCM"),
    VoicePatch("wood_oboe", "Oboe", VoiceCategory.WOODWIND, AcousticSynthesisModel.OBOE_DOUBLE_REED, "Sweet!", sourceFormat = "15Z • PCM"),

    // BRASS
    VoicePatch("brass_trumpet", "Trumpet", VoiceCategory.BRASS, AcousticSynthesisModel.BRASS_HORN, "Sweet!", sourceFormat = "15Z • 2V PCM"),
    VoicePatch("brass_trombone", "Trombone", VoiceCategory.BRASS, AcousticSynthesisModel.BRASS_HORN, "Live!", defaultOctaveOffset = -1, defaultBrightness = 0.85f, sourceFormat = "15Z • 2V PCM"),
    VoicePatch("brass_french_horn", "French Horn", VoiceCategory.BRASS, AcousticSynthesisModel.BRASS_HORN, "Live!", defaultAttackScale = 1.5f, defaultBrightness = 0.78f, sourceFormat = "15Z • 2V PCM"),
    VoicePatch("brass_section", "Brass Section", VoiceCategory.BRASS, AcousticSynthesisModel.BRASS_ENSEMBLE, "Live!", sourceFormat = "15Z • PCM"),
    VoicePatch("brass_synth", "Synth Brass", VoiceCategory.BRASS, AcousticSynthesisModel.SYNTH_LEAD, "Cool!", defaultAttackScale = 1.6f, sourceFormat = "15Z • PCM"),

    // STRINGS
    VoicePatch("str_violin", "Violin", VoiceCategory.STRINGS, AcousticSynthesisModel.BOWED_SOLO_STRING, "S.Art!", sourceFormat = "15Z • 2V PCM"),
    VoicePatch("str_viola", "Viola", VoiceCategory.STRINGS, AcousticSynthesisModel.BOWED_SOLO_STRING, "Live!", defaultBrightness = 0.88f, sourceFormat = "15Z • 2V PCM"),
    VoicePatch("str_cello", "Cello", VoiceCategory.STRINGS, AcousticSynthesisModel.ACOUSTIC_CELLO, "Live!", defaultOctaveOffset = -1, defaultBrightness = 0.90f, sourceFormat = "15Z • 2V PCM"),
    VoicePatch("str_ensemble", "Concert Strings", VoiceCategory.STRINGS, AcousticSynthesisModel.STRING_ENSEMBLE, "S.Art!", sourceFormat = "15Z • Stereo"),
    VoicePatch("str_pizzicato", "Pizzicato Strings", VoiceCategory.STRINGS, AcousticSynthesisModel.PIZZICATO, "Live!", sourceFormat = "15Z • PCM"),

    // GUITAR & BASS
    VoicePatch("gtr_acoustic", "Acoustic Guitar", VoiceCategory.GUITAR, AcousticSynthesisModel.PLUCKED_ACOUSTIC_GUITAR, "S.Art!", sourceFormat = "15Z • 2V PCM"),
    VoicePatch("gtr_nylon", "Nylon Guitar", VoiceCategory.GUITAR, AcousticSynthesisModel.PLUCKED_NYLON_GUITAR, "Sweet!", sourceFormat = "15Z • 2V PCM"),
    VoicePatch("gtr_steel", "Steel Guitar", VoiceCategory.GUITAR, AcousticSynthesisModel.PLUCKED_STEEL_GUITAR, "S.Art!", sourceFormat = "15Z • 2V PCM"),
    VoicePatch("gtr_electric", "Electric Guitar", VoiceCategory.GUITAR, AcousticSynthesisModel.PLUCKED_ELECTRIC_CLEAN, "Cool!", sourceFormat = "15Z • 2V PCM"),
    VoicePatch("gtr_clean", "Clean Guitar", VoiceCategory.GUITAR, AcousticSynthesisModel.PLUCKED_ELECTRIC_CLEAN, "Cool!", sourceFormat = "15Z • 2V PCM"),
    VoicePatch("gtr_overdrive", "Overdrive Guitar", VoiceCategory.GUITAR, AcousticSynthesisModel.OVERDRIVE_GUITAR, "Mega!", sourceFormat = "15Z • PCM"),
    VoicePatch("bass_acoustic", "Acoustic Bass", VoiceCategory.GUITAR, AcousticSynthesisModel.ACOUSTIC_BASS, "S.Art!", sourceFormat = "15Z • 2V PCM"),
    VoicePatch("bass_electric", "Electric Bass", VoiceCategory.GUITAR, AcousticSynthesisModel.ELECTRIC_BASS, "Cool!", sourceFormat = "15Z • 2V PCM"),

    // SYNTH
    VoicePatch("syn_lead", "Synth Lead", VoiceCategory.SYNTH, AcousticSynthesisModel.SYNTH_LEAD, "Cool!", sourceFormat = "15Z • PCM"),
    VoicePatch("syn_pad", "Warm Pad", VoiceCategory.SYNTH, AcousticSynthesisModel.SYNTH_PAD, "S.Art!", sourceFormat = "15Z • Stereo"),
    VoicePatch("syn_bass", "Synth Bass", VoiceCategory.SYNTH, AcousticSynthesisModel.SYNTH_BASS, "Cool!", defaultOctaveOffset = -1, sourceFormat = "15Z • PCM"),
    VoicePatch("syn_analog", "Analog Synth", VoiceCategory.SYNTH, AcousticSynthesisModel.SYNTH_LEAD, "Vintage", defaultBrightness = 0.92f, sourceFormat = "15Z • PCM"),
    VoicePatch("syn_atmos", "Atmospheric Pad", VoiceCategory.SYNTH, AcousticSynthesisModel.SYNTH_PAD, "Dream", defaultAttackScale = 1.8f, defaultReleaseScale = 1.7f, sourceFormat = "15Z • Stereo"),

    // ETHIOPIAN INSTRUMENTS
    VoicePatch("eth_masinko", "Masinko", VoiceCategory.ETHIOPIAN, AcousticSynthesisModel.ETHIO_MASINKO, "Ethio!"),
    VoicePatch("eth_krar", "Krar", VoiceCategory.ETHIOPIAN, AcousticSynthesisModel.ETHIO_KRAR, "Ethio!"),
    VoicePatch("eth_washint", "Washint", VoiceCategory.ETHIOPIAN, AcousticSynthesisModel.ETHIO_WASHINT, "Ethio!"),
    VoicePatch("eth_kebero", "Kebero", VoiceCategory.ETHIOPIAN, AcousticSynthesisModel.ETHIO_KEBERO, "Ethio!"),

    // DRUMS
    VoicePatch("drum_std", "Standard Kit", VoiceCategory.DRUMS, AcousticSynthesisModel.GM_DRUM_KIT, "Kit"),
    VoicePatch("drum_ethio", "Ethio Perc Kit", VoiceCategory.DRUMS, AcousticSynthesisModel.ETHIO_KEBERO, "Kit"),
  )
}
