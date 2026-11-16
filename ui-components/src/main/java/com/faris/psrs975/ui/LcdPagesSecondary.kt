package com.faris.psrs975.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.faris.psrs975.audio.WorkstationPart
import com.faris.psrs975.chord.ChordFingeringMode
import com.faris.psrs975.effects.DspEffectType
import com.faris.psrs975.mixer.MixerConsoleState
import kotlin.math.roundToInt

@Composable
fun MixerLcdPage(state: WorkstationUiState, actions: WorkstationActions) {
  Column(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("12-CHANNEL CONSOLE (VOL / PAN / REV / CHO / DSP)", fontFamily = DisplayFont, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = PsrColors.LcdTextDark)
      LcdChip("RESET MIXER", false) { actions.resetMixer() }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Row(
      modifier = Modifier.weight(1f).fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      MixerConsoleState.mixerParts.forEach { part ->
        val ch = state.mixerState.channels[part] ?: return@forEach
        Column(
          modifier = Modifier
            .width(86.dp)
            .fillMaxHeight()
            .background(PsrColors.LcdCardBg, RoundedCornerShape(5.dp))
            .border(1.dp, PsrColors.LcdBorder, RoundedCornerShape(5.dp))
            .padding(4.dp)
            .verticalScroll(rememberScrollState()),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(part.label, fontFamily = DisplayFont, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = PsrColors.LcdTextDark, maxLines = 1)
          Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            LcdChip("M", ch.muted) { actions.toggleMixerMute(part) }
            LcdChip("S", ch.soloed) { actions.toggleMixerSolo(part) }
          }
          Text("VOL ${(ch.volume * 127).roundToInt()}", fontFamily = MonoFont, fontSize = 8.sp, color = PsrColors.LcdTextMuted)
          Slider(
            value = ch.volume,
            onValueChange = { actions.setMixerVolume(part, it) },
            modifier = Modifier.height(18.dp).testTag("mixer_vol_${part.name.lowercase()}"),
            colors = SliderDefaults.colors(thumbColor = PsrColors.LcdHighlightBlue, activeTrackColor = PsrColors.LcdHighlightBlue),
          )
          Text("PAN ${(ch.pan * 64).roundToInt()}", fontFamily = MonoFont, fontSize = 8.sp, color = PsrColors.LcdTextMuted)
          Slider(value = ch.pan, valueRange = -1f..1f, onValueChange = { actions.setMixerPan(part, it) }, modifier = Modifier.height(18.dp))
          Text("REV ${(ch.reverbSend * 100).roundToInt()}", fontFamily = MonoFont, fontSize = 8.sp, color = PsrColors.LcdTextMuted)
          Slider(value = ch.reverbSend, onValueChange = { actions.setMixerReverbSend(part, it) }, modifier = Modifier.height(18.dp))
          Text("CHO ${(ch.chorusSend * 100).roundToInt()}", fontFamily = MonoFont, fontSize = 8.sp, color = PsrColors.LcdTextMuted)
          Slider(value = ch.chorusSend, onValueChange = { actions.setMixerChorusSend(part, it) }, modifier = Modifier.height(18.dp))
          Text("DSP ${(ch.dspSend * 100).roundToInt()}", fontFamily = MonoFont, fontSize = 8.sp, color = PsrColors.LcdTextMuted)
          Slider(value = ch.dspSend, onValueChange = { actions.setMixerDspSend(part, it) }, modifier = Modifier.height(18.dp))
        }
      }
    }
  }
}

@Composable
fun EffectsLcdPage(state: WorkstationUiState, actions: WorkstationActions) {
  val fx = state.effectsState
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    // Reverb
    EffectBox("REVERB PROCESSOR", fx.reverb.enabled, { en -> actions.updateEffectsState { it.copy(reverb = it.reverb.copy(enabled = en)) } }) {
      LcdSliderRow("Room Size", fx.reverb.roomSize) { v -> actions.updateEffectsState { it.copy(reverb = it.reverb.copy(roomSize = v)) } }
      LcdSliderRow("Damping", fx.reverb.damping) { v -> actions.updateEffectsState { it.copy(reverb = it.reverb.copy(damping = v)) } }
      LcdSliderRow("Wet Level", fx.reverb.level) { v -> actions.updateEffectsState { it.copy(reverb = it.reverb.copy(level = v)) } }
    }

    // Chorus
    EffectBox("CHORUS PROCESSOR", fx.chorus.enabled, { en -> actions.updateEffectsState { it.copy(chorus = it.chorus.copy(enabled = en)) } }) {
      LcdSliderRow("Rate (Hz)", fx.chorus.rateHz, 0.1f..6f) { v -> actions.updateEffectsState { it.copy(chorus = it.chorus.copy(rateHz = v)) } }
      LcdSliderRow("Depth", fx.chorus.depth) { v -> actions.updateEffectsState { it.copy(chorus = it.chorus.copy(depth = v)) } }
      LcdSliderRow("Level", fx.chorus.level) { v -> actions.updateEffectsState { it.copy(chorus = it.chorus.copy(level = v)) } }
    }

    // Stereo Delay
    EffectBox("STEREO / PING-PONG DELAY", fx.delay.enabled, { en -> actions.updateEffectsState { it.copy(delay = it.delay.copy(enabled = en)) } }) {
      LcdSliderRow("Left ms", fx.delay.timeMs, 30f..1200f) { v -> actions.updateEffectsState { it.copy(delay = it.delay.copy(timeMs = v)) } }
      LcdSliderRow("Right ms", fx.delay.rightTimeMs, 30f..1200f) { v -> actions.updateEffectsState { it.copy(delay = it.delay.copy(rightTimeMs = v)) } }
      LcdSliderRow("Feedback", fx.delay.feedback, 0f..0.85f) { v -> actions.updateEffectsState { it.copy(delay = it.delay.copy(feedback = v)) } }
      LcdSliderRow("Mix Level", fx.delay.level) { v -> actions.updateEffectsState { it.copy(delay = it.delay.copy(level = v)) } }
    }

    // Insert DSP (Distortion, Phaser, Flanger, Rotary, Comp, EQ)
    EffectBox("INSERT DSP EFFECT", fx.insertDsp.enabled, { en -> actions.updateEffectsState { it.copy(insertDsp = it.insertDsp.copy(enabled = en)) } }) {
      Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DspEffectType.entries.forEach { t ->
          LcdChip(t.displayName, fx.insertDsp.type == t) {
            actions.updateEffectsState { it.copy(insertDsp = it.insertDsp.copy(type = t, enabled = t != DspEffectType.NONE)) }
          }
        }
      }
      LcdSliderRow("Drive / Gain", fx.insertDsp.drive) { v -> actions.updateEffectsState { it.copy(insertDsp = it.insertDsp.copy(drive = v)) } }
      LcdSliderRow("LFO Rate Hz", fx.insertDsp.rateHz, 0.1f..8f) { v -> actions.updateEffectsState { it.copy(insertDsp = it.insertDsp.copy(rateHz = v)) } }
      LcdSliderRow("Depth", fx.insertDsp.depth) { v -> actions.updateEffectsState { it.copy(insertDsp = it.insertDsp.copy(depth = v)) } }
      LcdSliderRow("DSP Mix", fx.insertDsp.mix) { v -> actions.updateEffectsState { it.copy(insertDsp = it.insertDsp.copy(mix = v)) } }
    }

    // Master EQ & Master Compressor
    EffectBox("MASTER 3-BAND EQ", fx.masterEq.enabled, { en -> actions.updateEffectsState { it.copy(masterEq = it.masterEq.copy(enabled = en)) } }) {
      LcdSliderRow("Low Gain dB", fx.masterEq.lowGainDb, -12f..12f) { v -> actions.updateEffectsState { it.copy(masterEq = it.masterEq.copy(lowGainDb = v)) } }
      LcdSliderRow("Mid Gain dB", fx.masterEq.midGainDb, -12f..12f) { v -> actions.updateEffectsState { it.copy(masterEq = it.masterEq.copy(midGainDb = v)) } }
      LcdSliderRow("High Gain dB", fx.masterEq.highGainDb, -12f..12f) { v -> actions.updateEffectsState { it.copy(masterEq = it.masterEq.copy(highGainDb = v)) } }
    }
    EffectBox("MASTER COMPRESSOR", fx.masterCompressor.enabled, { en -> actions.updateEffectsState { it.copy(masterCompressor = it.masterCompressor.copy(enabled = en)) } }) {
      LcdSliderRow("Threshold dB", fx.masterCompressor.thresholdDb, -36f..0f) { v -> actions.updateEffectsState { it.copy(masterCompressor = it.masterCompressor.copy(thresholdDb = v)) } }
      LcdSliderRow("Ratio", fx.masterCompressor.ratio, 1f..10f) { v -> actions.updateEffectsState { it.copy(masterCompressor = it.masterCompressor.copy(ratio = v)) } }
      LcdSliderRow("Makeup dB", fx.masterCompressor.makeupGainDb, 0f..12f) { v -> actions.updateEffectsState { it.copy(masterCompressor = it.masterCompressor.copy(makeupGainDb = v)) } }
    }
  }
}

@Composable
private fun EffectBox(
  title: String,
  enabled: Boolean,
  onToggle: (Boolean) -> Unit,
  content: @Composable () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(PsrColors.LcdCardBg, RoundedCornerShape(5.dp))
      .border(1.dp, PsrColors.LcdBorder, RoundedCornerShape(5.dp))
      .padding(6.dp),
    verticalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      Text(title, fontFamily = DisplayFont, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PsrColors.LcdTextDark)
      LcdChip(if (enabled) "ON" else "OFF", enabled) { onToggle(!enabled) }
    }
    content()
  }
}

@Composable
fun MultiPadLcdPage(state: WorkstationUiState, actions: WorkstationActions) {
  val bank = state.multiPadBanks.getOrNull(state.activeMultiPadBankIndex) ?: return
  Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      state.multiPadBanks.forEach { b ->
        LcdChip("${b.bankIndex + 1}: ${b.bankName}", b.bankIndex == state.activeMultiPadBankIndex) {
          actions.selectMultiPadBank(b.bankIndex)
        }
      }
      LcdChip("STOP ALL", false) { actions.stopAllMultiPads() }
    }

    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      items(bank.pads, key = { it.padIndex }) { pad ->
        val isRec = state.isRecordingMultiPad == pad.padIndex
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(if (pad.isPlaying) PsrColors.LcdCardActive else PsrColors.LcdCardBg, RoundedCornerShape(5.dp))
            .border(1.dp, PsrColors.LcdBorder, RoundedCornerShape(5.dp))
            .padding(6.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LcdChip("PAD ${pad.padIndex}", pad.isPlaying) { actions.triggerMultiPad(pad.padIndex) }
            Text(pad.label, fontFamily = DisplayFont, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PsrColors.LcdTextDark, modifier = Modifier.weight(1f))
            MultiPadPlayMode.entries.forEach { mode ->
              LcdChip(mode.label, pad.playMode == mode) { actions.setMultiPadMode(pad.padIndex, mode) }
            }
            LcdChip(if (isRec) "SAVE REC" else "REC", isRec) { actions.toggleRecordMultiPad(pad.padIndex) }
          }
          LcdSliderRow("Pad Volume", pad.volume) { actions.setMultiPadVolume(pad.padIndex, it) }
        }
      }
    }
  }
}

@Composable
fun RegistrationLcdPage(state: WorkstationUiState, actions: WorkstationActions) {
  var jsonExchangeText by remember { mutableStateOf("") }
  Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text("BANK ${state.activeRegBank + 1}/8", fontFamily = DisplayFont, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PsrColors.LcdTextDark)
      LcdChip("BANK -", false) { actions.selectRegBank(-1, true) }
      LcdChip("BANK +", false) { actions.selectRegBank(1, true) }
      LcdChip(if (state.regMemoryArmed) "MEMORY ARMED" else "MEMORY", state.regMemoryArmed) { actions.toggleRegMemoryArm() }
      LcdChip(if (state.regFreezeEnabled) "FREEZE ON" else "FREEZE", state.regFreezeEnabled) { actions.toggleRegFreeze() }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      for (slot in 1..8) {
        val saved = state.registrationSlots.find { it.slotIndex == slot }
        val active = state.activeRegSlot == slot
        Column(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) PsrColors.LcdHighlightBlue else if (saved != null) Color(0xFFCCE0D4) else PsrColors.LcdCardBg)
            .border(1.dp, PsrColors.LcdBorder, RoundedCornerShape(4.dp))
            .clickable { actions.pressRegistrationSlot(slot) }
            .padding(vertical = 6.dp, horizontal = 3.dp)
            .testTag("lcd_reg_slot_$slot"),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text("$slot", fontFamily = DisplayFont, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (active) Color.White else PsrColors.LcdTextDark)
          Text(saved?.label?.take(6) ?: "Empty", fontFamily = MonoFont, fontSize = 7.sp, color = if (active) Color.White else PsrColors.LcdTextMuted, maxLines = 1)
        }
      }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      LcdChip("EXPORT BANK JSON", false) { jsonExchangeText = actions.exportRegistrationsJson() }
      if (jsonExchangeText.isNotBlank()) {
        LcdChip("IMPORT JSON", true) { actions.importRegistrationsJson(jsonExchangeText) }
      }
    }
    OutlinedTextField(
      value = jsonExchangeText,
      onValueChange = { jsonExchangeText = it },
      placeholder = { Text("Paste exported Registration JSON to import...", fontSize = 9.sp) },
      maxLines = 3,
      modifier = Modifier.fillMaxWidth(),
      colors = OutlinedTextFieldDefaults.colors(focusedTextColor = PsrColors.LcdTextDark, unfocusedTextColor = PsrColors.LcdTextDark),
    )
  }
}

@Composable
fun RecordingLcdPage(state: WorkstationUiState, actions: WorkstationActions) {
  val rec = state.recordingState
  var renamingItemId by remember { mutableStateOf<String?>(null) }
  var renameText by remember { mutableStateOf("") }

  Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(PsrColors.LcdCardBg, RoundedCornerShape(5.dp))
        .border(1.dp, PsrColors.LcdBorder, RoundedCornerShape(5.dp))
        .padding(6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      LcdChip(
        text = if (rec.isRecording) "STOP REC (${rec.elapsedMs / 1000}s)" else "START STEREO WAV REC",
        selected = rec.isRecording,
      ) { actions.toggleAudioRecording() }
      LcdChip(
        text = if (rec.includeMicrophone) "MIC: ON" else "MIC: OFF",
        selected = rec.includeMicrophone,
      ) { actions.toggleRecordMicrophone(!rec.includeMicrophone) }
      Text("44.1/48kHz 16-bit Stereo WAV", fontFamily = MonoFont, fontSize = 8.sp, color = PsrColors.LcdTextMuted)
    }

    rec.errorMessage?.let {
      Text(it, fontFamily = BodyFont, fontSize = 9.sp, color = PsrColors.LedRed)
    }

    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      items(rec.recordings, key = { it.id }) { item ->
        val playing = rec.playingRecordingId == item.id
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(PsrColors.LcdCardBg, RoundedCornerShape(4.dp))
            .border(1.dp, PsrColors.LcdBorder, RoundedCornerShape(4.dp))
            .padding(6.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              if (playing) Icons.Default.Stop else Icons.Default.PlayArrow,
              contentDescription = "Play Recording",
              tint = PsrColors.LcdHighlightBlue,
              modifier = Modifier.size(20.dp).clickable { actions.playOrStopRecording(item) },
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(item.title, fontFamily = DisplayFont, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PsrColors.LcdTextDark)
              Text("${item.durationMs / 1000}s • ${item.sizeBytes / 1024} KB • ${item.sampleRate} Hz", fontFamily = MonoFont, fontSize = 8.sp, color = PsrColors.LcdTextMuted)
            }
            LcdChip("RENAME", false) {
              renamingItemId = item.id
              renameText = item.title
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Default.Share, contentDescription = "Share WAV", tint = PsrColors.LcdHighlightBlue, modifier = Modifier.size(18.dp).clickable { actions.shareRecording(item) })
            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.Default.Delete, contentDescription = "Delete WAV", tint = PsrColors.LedRed, modifier = Modifier.size(18.dp).clickable { actions.deleteRecording(item) })
          }
          if (playing) {
            LinearProgressIndicator(progress = { rec.playbackProgress }, modifier = Modifier.fillMaxWidth().padding(top = 3.dp))
          }
          if (renamingItemId == item.id) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
              OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true, modifier = Modifier.weight(1f))
              LcdChip("SAVE", true) {
                actions.renameRecording(item, renameText)
                renamingItemId = null
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun ExpansionLcdPage(state: WorkstationUiState, actions: WorkstationActions) {
  val exp = state.expansionState
  Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .background(PsrColors.LcdCardBg, RoundedCornerShape(5.dp))
        .border(1.dp, PsrColors.LcdBorder, RoundedCornerShape(5.dp))
        .padding(6.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            exp.statusMessage,
            fontFamily = DisplayFont,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = PsrColors.LcdTextDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            "Used: ${exp.totalStorageBytes / 1024} KB • Packs: ${exp.packs.size} (SF2 / STY / WAV / MP3 / OGG / MID)",
            fontFamily = MonoFont,
            fontSize = 8.sp,
            color = PsrColors.LcdTextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        LcdChip("IMPORT .SF2", true, testTag = "btn_exp_import_sf2") { actions.requestImportSf2File() }
        LcdChip("IMPORT .STY", true, testTag = "btn_exp_import_sty") { actions.requestImportStyFile() }
        LcdChip("IMPORT DRUM KIT", true, testTag = "btn_exp_import_drum_kit") { actions.requestImportDrumKitFiles() }
        LcdChip("CUSTOM KITS ↗", false, testTag = "btn_exp_open_drum_kits") { actions.navigateTo(LcdPage.DRUM_KIT) }
        LcdChip("IMPORT PACK", false, testTag = "btn_exp_import_pack") { actions.requestImportExpansionFile() }
      }
      exp.lastImportSummary?.let { detail ->
        Text(detail, fontFamily = MonoFont, fontSize = 8.sp, color = PsrColors.LcdHighlightBlue, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      exp.errorMessage?.let {
        Text(it, fontFamily = BodyFont, fontSize = 9.sp, color = PsrColors.LedRed)
      }
    }

    if (exp.isImporting) {
      LinearProgressIndicator(progress = { exp.progress }, modifier = Modifier.fillMaxWidth())
    }

    if (exp.packs.isEmpty()) {
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .background(PsrColors.LcdCardBg.copy(alpha = 0.7f), RoundedCornerShape(5.dp))
          .border(1.dp, PsrColors.LcdBorder, RoundedCornerShape(5.dp))
          .padding(10.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text("SF2, STY & CUSTOM DRUM KIT IMPORTER", fontFamily = DisplayFont, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PsrColors.LcdTextDark)
        Spacer(modifier = Modifier.height(3.dp))
        Text(
          "Tap IMPORT DRUM KIT (WAV/MP3/OGG/SF2), IMPORT .STY for Yamaha styles, or IMPORT .SF2 for SoundFonts.",
          fontFamily = BodyFont,
          fontSize = 9.sp,
          color = PsrColors.LcdTextMuted,
        )
      }
    } else {
      LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items(exp.packs, key = { it.id }) { pack ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(PsrColors.LcdCardBg, RoundedCornerShape(4.dp))
              .border(1.dp, PsrColors.LcdBorder, RoundedCornerShape(4.dp))
              .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(if (pack.format == "STY") PsrColors.GoldAccent.copy(alpha = 0.25f) else PsrColors.LcdHighlightBlue.copy(alpha = 0.16f))
                .border(1.dp, if (pack.format == "STY") PsrColors.GoldAccent else PsrColors.LcdHighlightBlue, RoundedCornerShape(3.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
              Text(pack.format, fontFamily = MonoFont, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = PsrColors.LcdTextDark)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(pack.name, fontFamily = DisplayFont, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PsrColors.LcdTextDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text("${pack.presetCount} items • ${pack.sizeBytes / 1024} KB • ${pack.libraryGroup}", fontFamily = MonoFont, fontSize = 8.sp, color = PsrColors.LcdTextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            LcdChip(if (pack.format == "STY") "PLAY STY" else "PREVIEW", false) { actions.previewExpansionPack(pack) }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Default.Delete, contentDescription = "Delete Pack", tint = PsrColors.LedRed, modifier = Modifier.size(18.dp).clickable { actions.deleteExpansionPack(pack) })
          }
        }
      }
    }
  }
}

@Composable
fun MidiLcdPage(state: WorkstationUiState, actions: WorkstationActions) {
  Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      Text("HARDWARE MIDI & LIVE MONITOR", fontFamily = DisplayFont, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PsrColors.LcdTextDark)
      LcdChip("ALL NOTES OFF (PANIC)", false) { actions.panicAllNotesOff() }
    }
    Text(
      text = if (state.hardwareMidiDevices.isEmpty()) "External MIDI Devices: None connected (USB/BLE MIDI auto-detected)" else "Connected: ${state.hardwareMidiDevices.joinToString()}",
      fontFamily = MonoFont,
      fontSize = 9.sp,
      color = PsrColors.LcdTextMuted,
    )
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF101926), RoundedCornerShape(5.dp))
        .padding(6.dp),
    ) {
      if (state.midiMonitorLog.isEmpty()) {
        Text("Play keys or send MIDI events to inspect live messages...", fontFamily = MonoFont, fontSize = 8.sp, color = PsrColors.SilverMuted)
      } else {
        state.midiMonitorLog.takeLast(8).forEach { line ->
          Text(line, fontFamily = MonoFont, fontSize = 8.sp, color = PsrColors.LedCyan)
        }
      }
    }
  }
}

@Composable
fun UtilityLcdPage(state: WorkstationUiState, actions: WorkstationActions) {
  Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
    Text("CHORD FINGERING MODE", fontFamily = DisplayFont, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PsrColors.LcdTextDark)
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      ChordFingeringMode.entries.forEach { mode ->
        LcdChip(mode.displayName, state.fingeringMode == mode) { actions.setFingeringMode(mode) }
      }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("SPLIT POINT: ${midiNoteName(state.splitPointMidi)}", fontFamily = MonoFont, fontSize = 10.sp, color = PsrColors.LcdTextDark)
      LcdChip("-1", false) { actions.setSplitPoint(state.splitPointMidi - 1) }
      LcdChip("+1", false) { actions.setSplitPoint(state.splitPointMidi + 1) }
      Spacer(modifier = Modifier.width(8.dp))
      Text("SAMPLE RATE:", fontFamily = MonoFont, fontSize = 9.sp, color = PsrColors.LcdTextDark)
      LcdChip("44.1 kHz", state.audioStats.sampleRate == 44100) { actions.setSampleRate(44100) }
      LcdChip("48 kHz", state.audioStats.sampleRate == 48000) { actions.setSampleRate(48000) }
    }

    LcdSliderRow("Pitch Bend (st)", state.pitchBendSemis, -2f..2f) { actions.setPitchBend(it) }
    LcdSliderRow("Modulation LFO", state.modWheelDepth, 0f..1f) { actions.setModWheel(it) }

    Text(
      "Engine: ${if (state.audioStats.isRunning) "RUNNING" else "STOPPED"} | Polyphony: ${state.audioStats.activeVoices}/${state.audioStats.maxPolyphony} | SampleRate: ${state.audioStats.sampleRate}Hz",
      fontFamily = MonoFont,
      fontSize = 9.sp,
      color = PsrColors.LcdTextMuted,
    )
  }
}

@Composable
fun SettingsLcdPage(state: WorkstationUiState, actions: WorkstationActions) {
  Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text("FARIS YAMAHA PSR-S975 WORKSTATION SETTINGS", fontFamily = DisplayFont, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PsrColors.LcdTextDark)
    Text("Offline Sample-Based Arranger • Multi-Zone PCM + SF2/SFZ/WAV + 8-Ch Styles + Stereo DSP", fontFamily = BodyFont, fontSize = 9.sp, color = PsrColors.LcdTextMuted)

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      LcdChip("LAUNCH DEMO", true) { actions.launchQuickDemo() }
      LcdChip("PANIC (ALL NOTES OFF)", false) { actions.panicAllNotesOff() }
      LcdChip("RESET SESSION", false) { actions.resetSessionToDefaults() }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
      Text("Auto Accompaniment (ACMP)", fontFamily = BodyFont, fontSize = 10.sp, color = PsrColors.LcdTextDark)
      Switch(checked = state.styleState.acmpEnabled, onCheckedChange = { actions.toggleAcmp() })
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
      Text("Auto Fill-In on Section Change", fontFamily = BodyFont, fontSize = 10.sp, color = PsrColors.LcdTextDark)
      Switch(checked = state.styleState.autoFillEnabled, onCheckedChange = { actions.toggleAutoFill() })
    }
  }
}
