package com.faris.psrs975.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.faris.psrs975.chord.ChordQuality
import com.faris.psrs975.chord.DetectedChord
import com.faris.psrs975.style.StyleCategory
import com.faris.psrs975.style.StyleSection
import com.faris.psrs975.voice.ActiveVoiceSlot
import com.faris.psrs975.voice.VoiceCategory
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun WorkstationScreen(
  state: WorkstationUiState,
  actions: WorkstationActions,
) {
  LaunchedEffect(state.statusToast) {
    if (state.statusToast != null) {
      delay(2800L)
      actions.dismissToast()
    }
  }

  Surface(
    color = PsrColors.ChassisBlack,
    modifier = Modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.safeDrawing),
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideLandscape = maxWidth >= 680.dp
        if (isWideLandscape) {
          LandscapeWorkstationConsole(state = state, actions = actions, totalHeight = maxHeight)
        } else {
          PortraitWorkstationConsole(state = state, actions = actions, totalHeight = maxHeight)
        }
      }

      // Floating status toast
      state.statusToast?.let { msg ->
        Box(
          modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 34.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xEE10243E))
            .border(1.dp, PsrColors.GoldAccent, RoundedCornerShape(16.dp))
            .clickable { actions.dismissToast() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
          Text(msg, fontFamily = DisplayFont, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
      }

      // Interactive Help Overlay
      if (state.showHelpOverlay) {
        HelpOverlayDialog(onDismiss = actions::toggleHelpOverlay)
      }
    }
  }
}

@Composable
private fun TopChassisHeaderBar(
  state: WorkstationUiState,
  actions: WorkstationActions,
  compact: Boolean,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(if (compact) 36.dp else 32.dp)
      .background(
        Brush.verticalGradient(listOf(Color(0xFF20242E), Color(0xFF12151C))),
      )
      .border(0.5.dp, PsrColors.ChassisBorder)
      .padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier
          .size(8.dp)
          .clip(CircleShape)
          .background(if (state.powerOn) PsrColors.LedGreen else PsrColors.LedRed),
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = if (compact) "FARIS PSR-S975" else "FARIS YAMAHA PSR-S975",
        fontFamily = DisplayFont,
        fontSize = if (compact) 12.sp else 14.sp,
        fontWeight = FontWeight.Bold,
        color = PsrColors.SilverAccent,
        letterSpacing = 1.sp,
      )
      if (!compact) {
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "ARRANGER WORKSTATION",
          fontFamily = DisplayFont,
          fontSize = 8.sp,
          color = PsrColors.GoldAccent,
          letterSpacing = 0.8.sp,
        )
      }
    }

    Row(
      modifier = Modifier
        .weight(1f, fill = false)
        .horizontalScroll(rememberScrollState()),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      TopBarIconChip("MENU", Icons.Default.Menu, state.showMenuOverlay, "top_menu_button") {
        actions.toggleMenuOverlay()
      }
      TopBarIconChip("KBD SET", Icons.Default.Layers, state.currentLcdPage == LcdPage.KEYBOARD_SET, "top_kbd_set_button") {
        actions.navigateTo(LcdPage.KEYBOARD_SET)
      }
      TopBarIconChip("DRUM KIT", Icons.Default.LibraryMusic, state.currentLcdPage == LcdPage.DRUM_KIT, "top_drum_kit_button") {
        actions.navigateTo(LcdPage.DRUM_KIT)
      }
      TopBarIconChip("DEMO", Icons.Default.PlayArrow, false, "top_demo_button") {
        actions.launchQuickDemo()
      }
      TopBarIconChip(
        label = if (state.recordingState.isRecording) "REC*" else "REC",
        icon = Icons.Default.FiberManualRecord,
        active = state.recordingState.isRecording,
        testTag = "top_rec_button",
        activeTint = PsrColors.LedRed,
      ) {
        actions.navigateTo(LcdPage.RECORDING)
      }
      if (!compact) {
        TopBarIconChip("HELP", Icons.AutoMirrored.Filled.HelpOutline, state.showHelpOverlay, "top_help_button") {
          actions.toggleHelpOverlay()
        }
      }
      TopBarIconChip("SETTINGS", Icons.Default.Settings, state.currentLcdPage == LcdPage.SETTINGS, "top_settings_button") {
        actions.navigateTo(LcdPage.SETTINGS)
      }
      StereoVuMeter(
        peakLeft = state.audioStats.peakLeft,
        peakRight = state.audioStats.peakRight,
        modifier = Modifier.width(if (compact) 68.dp else 96.dp),
      )
    }
  }
}

@Composable
private fun TopBarIconChip(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  active: Boolean,
  testTag: String,
  activeTint: Color = PsrColors.GoldAccent,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .clip(RoundedCornerShape(4.dp))
      .background(if (active) Color(0xFF2B3242) else Color(0xFF181C24))
      .border(1.dp, if (active) activeTint else PsrColors.ChassisBorder, RoundedCornerShape(4.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 6.dp, vertical = 3.dp)
      .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = label, tint = if (active) activeTint else PsrColors.SilverAccent, modifier = Modifier.size(12.dp))
    Spacer(modifier = Modifier.width(3.dp))
    Text(label, fontFamily = DisplayFont, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (active) Color.White else PsrColors.SilverAccent)
  }
}

@Composable
private fun LandscapeWorkstationConsole(
  state: WorkstationUiState,
  actions: WorkstationActions,
  totalHeight: androidx.compose.ui.unit.Dp,
) {
  val keybedHeight = (totalHeight * 0.29f).coerceIn(88.dp, 155.dp)

  Column(modifier = Modifier.fillMaxSize()) {
    TopChassisHeaderBar(state = state, actions = actions, compact = false)

    // Upper Console Deck (Master Knobs + Song/Style Deck + LCD + Voice Deck)
    Row(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(horizontal = 4.dp, vertical = 3.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      // Far Left Column: Power, Master Volume, Balance, Mic Gain
      Column(
        modifier = Modifier
          .width(56.dp)
          .fillMaxHeight()
          .background(PsrColors.ChassisPanel, RoundedCornerShape(6.dp))
          .border(1.dp, PsrColors.ChassisBorder, RoundedCornerShape(6.dp))
          .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        HardwareButton(
          label = "POWER",
          onClick = actions::togglePower,
          ledOn = state.powerOn,
          ledColor = if (state.powerOn) PsrColors.LedGreen else PsrColors.LedRed,
          testTag = "btn_power",
          fontSize = 8.sp,
          height = 24.dp,
          modifier = Modifier.fillMaxWidth(),
        )
        RotaryKnob(
          label = "MASTER",
          value = state.audioStats.masterVolume,
          onValueChange = actions::setMasterVolume,
          size = 34.dp,
          testTag = "knob_master_vol",
          valueText = "${(state.audioStats.masterVolume * 100).roundToInt()}%",
        )
        RotaryKnob(
          label = "BALANCE",
          value = (state.audioStats.balance + 1f) / 2f,
          onValueChange = { actions.setMasterBalance(it * 2f - 1f) },
          size = 30.dp,
          accentColor = PsrColors.LedCyan,
        )
        RotaryKnob(
          label = "MIC",
          value = 0.55f,
          onValueChange = actions::setMicGain,
          size = 30.dp,
          accentColor = PsrColors.SilverAccent,
        )
      }

      // Left Deck: SONG + STYLE CATEGORIES + TEMPO/TRANSPOSE
      Column(
        modifier = Modifier.weight(0.24f).fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        PanelSectionFrame(title = "SONG", modifier = Modifier.fillMaxWidth()) {
          Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
            HardwareButton("SELECT", { actions.navigateTo(LcdPage.SONG) }, showLedBar = false, height = 22.dp, modifier = Modifier.weight(1f))
            HardwareButton(
              if (state.midiState.isPlaying) "PAUSE" else "PLAY",
              actions::toggleSongPlayPause,
              ledOn = state.midiState.isPlaying,
              height = 22.dp,
              modifier = Modifier.weight(1f),
            )
            HardwareButton("STOP", actions::stopSong, showLedBar = false, height = 22.dp, modifier = Modifier.weight(1f))
          }
        }

        PanelSectionFrame(title = "STYLE", modifier = Modifier.weight(1f).fillMaxWidth()) {
          Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxSize()) {
            val cats = StyleCategory.entries
            for (rowIdx in 0 until 3) {
              Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
                for (colIdx in 0 until 3) {
                  val cat = cats[rowIdx * 3 + colIdx]
                  HardwareButton(
                    label = cat.displayName.uppercase(),
                    onClick = {
                      actions.selectStyleCategory(cat)
                      actions.navigateTo(LcdPage.STYLE)
                    },
                    ledOn = state.styleState.currentStyle?.category == cat,
                    fontSize = 7.5.sp,
                    height = 24.dp,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                  )
                }
              }
            }
            // Tempo / Tap / Transpose
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
              HardwareButton("BPM-", { actions.setTempo(state.styleState.tempoBpm - 2) }, showLedBar = false, height = 22.dp, modifier = Modifier.weight(1f))
              HardwareButton("TAP", actions::tapTempo, isPrimaryAccent = true, showLedBar = false, height = 22.dp, modifier = Modifier.weight(1f), testTag = "btn_tap_tempo")
              HardwareButton("BPM+", { actions.setTempo(state.styleState.tempoBpm + 2) }, showLedBar = false, height = 22.dp, modifier = Modifier.weight(1f))
              HardwareButton("TR-", { actions.setTranspose(state.transpose - 1) }, showLedBar = false, height = 22.dp, modifier = Modifier.weight(0.8f))
              HardwareButton("TR+", { actions.setTranspose(state.transpose + 1) }, showLedBar = false, height = 22.dp, modifier = Modifier.weight(0.8f))
            }
          }
        }
      }

      // Center: LCD Display with flanking hardware soft-keys
      Row(
        modifier = Modifier.weight(0.46f).fillMaxHeight(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Column(
          modifier = Modifier.width(34.dp).fillMaxHeight(),
          verticalArrangement = Arrangement.SpaceEvenly,
        ) {
          HardwareButton("MAIN", { actions.navigateTo(LcdPage.MAIN) }, ledOn = state.currentLcdPage == LcdPage.MAIN, fontSize = 7.sp, height = 26.dp, modifier = Modifier.fillMaxWidth())
          HardwareButton("MIX", { actions.navigateTo(LcdPage.MIXER) }, ledOn = state.currentLcdPage == LcdPage.MIXER, fontSize = 7.sp, height = 26.dp, modifier = Modifier.fillMaxWidth())
          HardwareButton("FX", { actions.navigateTo(LcdPage.EFFECTS) }, ledOn = state.currentLcdPage == LcdPage.EFFECTS, fontSize = 7.sp, height = 26.dp, modifier = Modifier.fillMaxWidth())
          HardwareButton("EXP", { actions.navigateTo(LcdPage.EXPANSION_MANAGER) }, ledOn = state.currentLcdPage == LcdPage.EXPANSION_MANAGER, fontSize = 7.sp, height = 26.dp, modifier = Modifier.fillMaxWidth())
        }

        LcdDisplayFrame(
          state = state,
          actions = actions,
          modifier = Modifier.weight(1f).fillMaxHeight(),
        )

        Column(
          modifier = Modifier.width(34.dp).fillMaxHeight(),
          verticalArrangement = Arrangement.SpaceEvenly,
        ) {
          HardwareButton("VOICE", { actions.navigateTo(LcdPage.VOICE) }, ledOn = state.currentLcdPage == LcdPage.VOICE, fontSize = 7.sp, height = 26.dp, modifier = Modifier.fillMaxWidth())
          HardwareButton("STYLE", { actions.navigateTo(LcdPage.STYLE) }, ledOn = state.currentLcdPage == LcdPage.STYLE, fontSize = 7.sp, height = 26.dp, modifier = Modifier.fillMaxWidth())
          HardwareButton("DRUMS", { actions.navigateTo(LcdPage.DRUM_KIT) }, ledOn = state.currentLcdPage == LcdPage.DRUM_KIT, fontSize = 7.sp, height = 26.dp, modifier = Modifier.fillMaxWidth(), testTag = "btn_landscape_drums")
          HardwareButton("MIDI", { actions.navigateTo(LcdPage.MIDI) }, ledOn = state.currentLcdPage == LcdPage.MIDI, fontSize = 7.sp, height = 26.dp, modifier = Modifier.fillMaxWidth())
        }
      }

      // Right Deck: VOICE CATEGORIES + VOICE CONTROL + PART SELECT
      Column(
        modifier = Modifier.weight(0.25f).fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        PanelSectionFrame(title = "VOICE", modifier = Modifier.weight(1f).fillMaxWidth()) {
          Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxSize()) {
            val gridCats = listOf(
              VoiceCategory.PIANO, VoiceCategory.KEYBOARD, VoiceCategory.WOODWIND,
              VoiceCategory.BRASS, VoiceCategory.STRINGS, VoiceCategory.GUITAR,
              VoiceCategory.SYNTH, VoiceCategory.ETHIOPIAN, VoiceCategory.DRUMS,
            )
            for (r in 0 until 3) {
              Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
                for (c in 0 until 3) {
                  val cat = gridCats[r * 3 + c]
                  HardwareButton(
                    label = cat.displayName.uppercase(),
                    onClick = {
                      actions.selectVoiceCategory(cat)
                      actions.navigateTo(LcdPage.VOICE)
                    },
                    ledOn = state.selectedVoiceCategory == cat && state.currentLcdPage == LcdPage.VOICE,
                    fontSize = 7.5.sp,
                    height = 24.dp,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    testTag = "btn_voice_cat_${cat.name.lowercase()}",
                  )
                }
              }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
              HardwareButton("V◀", { actions.cycleVoice(-1) }, showLedBar = false, height = 22.dp, modifier = Modifier.weight(0.55f), testTag = "btn_voice_prev")
              HardwareButton("V▶", { actions.cycleVoice(1) }, showLedBar = false, height = 22.dp, modifier = Modifier.weight(0.55f), testTag = "btn_voice_next")
              HardwareButton("EXP", {
                actions.selectVoiceCategory(VoiceCategory.USER_EXPANSION)
                actions.navigateTo(LcdPage.VOICE)
              }, showLedBar = false, fontSize = 7.5.sp, height = 22.dp, modifier = Modifier.weight(0.75f))
              HardwareButton("OCT-", { actions.setUpperOctave(state.upperOctave - 1) }, showLedBar = false, height = 22.dp, modifier = Modifier.weight(0.6f))
              HardwareButton("OCT+", { actions.setUpperOctave(state.upperOctave + 1) }, showLedBar = false, height = 22.dp, modifier = Modifier.weight(0.6f))
              HardwareButton("STOP", actions::panicAllNotesOff, ledColor = PsrColors.LedRed, showLedBar = false, height = 22.dp, modifier = Modifier.weight(0.7f))
            }
          }
        }

        PanelSectionFrame(title = "VOICE CTRL & PART SELECT", modifier = Modifier.fillMaxWidth()) {
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
              HardwareButton("HARMONY", actions::toggleHarmony, ledOn = state.voiceState.harmonyEnabled, fontSize = 7.5.sp, height = 22.dp, modifier = Modifier.weight(1f))
              HardwareButton("SUSTAIN", actions::toggleSustain, ledOn = state.voiceState.sustainEnabled, fontSize = 7.5.sp, height = 22.dp, modifier = Modifier.weight(1f), testTag = "btn_sustain")
              HardwareButton("DSP", actions::toggleDsp, ledOn = state.voiceState.dspEnabled, fontSize = 7.5.sp, height = 22.dp, modifier = Modifier.weight(0.8f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
              HardwareButton(
                "LEFT",
                { actions.toggleVoiceSlotEnabled(ActiveVoiceSlot.LEFT) },
                ledOn = state.voiceState.leftEnabled,
                fontSize = 8.sp,
                height = 22.dp,
                modifier = Modifier.weight(1f),
                testTag = "btn_part_left",
              )
              HardwareButton(
                "RIGHT 1",
                { actions.toggleVoiceSlotEnabled(ActiveVoiceSlot.RIGHT_1) },
                ledOn = state.voiceState.right1Enabled,
                fontSize = 8.sp,
                height = 22.dp,
                modifier = Modifier.weight(1f),
                testTag = "btn_part_r1",
              )
              HardwareButton(
                "RIGHT 2",
                { actions.toggleVoiceSlotEnabled(ActiveVoiceSlot.RIGHT_2) },
                ledOn = state.voiceState.right2Enabled,
                fontSize = 8.sp,
                height = 22.dp,
                modifier = Modifier.weight(1f),
                testTag = "btn_part_r2",
              )
            }
          }
        }
      }
    }

    // Middle Hardware Control Strip: Live Knobs, Style Control, Registration Memory, Multi Pad, OTS
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp, vertical = 2.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Live Control Knobs
      Row(
        modifier = Modifier
          .background(PsrColors.ChassisPanel, RoundedCornerShape(5.dp))
          .border(1.dp, PsrColors.ChassisBorder, RoundedCornerShape(5.dp))
          .padding(horizontal = 5.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        RotaryKnob(state.liveKnobAssignLabelA, state.liveKnobCutoff, actions::setLiveKnobCutoff, size = 26.dp, accentColor = PsrColors.LedCyan)
        RotaryKnob(state.liveKnobAssignLabelB, state.liveKnobResonance, actions::setLiveKnobResonance, size = 26.dp, accentColor = PsrColors.LedCyan)
      }

      // Style Control Strip
      Row(
        modifier = Modifier
          .weight(1.65f)
          .background(PsrColors.ChassisPanel, RoundedCornerShape(5.dp))
          .border(1.dp, PsrColors.ChassisBorder, RoundedCornerShape(5.dp))
          .padding(3.dp)
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val st = state.styleState
        HardwareButton("ACMP", actions::toggleAcmp, ledOn = st.acmpEnabled, ledColor = PsrColors.LedCyan, height = 28.dp, modifier = Modifier.width(38.dp), testTag = "btn_acmp")
        HardwareButton(
          label = if (st.isPlaying) "STOP" else "START",
          onClick = actions::toggleStyleStartStop,
          ledOn = st.isPlaying,
          ledColor = if (st.isPlaying && st.activeStepInBar % 4 == 0) PsrColors.LedRed else PsrColors.LedGreen,
          isPrimaryAccent = true,
          height = 28.dp,
          modifier = Modifier.width(46.dp),
          testTag = "btn_style_start_stop",
        )
        HardwareButton("A", { actions.selectStyleSection(StyleSection.MAIN_A) }, ledOn = st.currentSection == StyleSection.MAIN_A, height = 28.dp, modifier = Modifier.width(26.dp), testTag = "btn_main_a")
        HardwareButton("B", { actions.selectStyleSection(StyleSection.MAIN_B) }, ledOn = st.currentSection == StyleSection.MAIN_B, height = 28.dp, modifier = Modifier.width(26.dp), testTag = "btn_main_b")
        HardwareButton("C", { actions.selectStyleSection(StyleSection.MAIN_C) }, ledOn = st.currentSection == StyleSection.MAIN_C, height = 28.dp, modifier = Modifier.width(26.dp), testTag = "btn_main_c")
        HardwareButton("D", { actions.selectStyleSection(StyleSection.MAIN_D) }, ledOn = st.currentSection == StyleSection.MAIN_D, height = 28.dp, modifier = Modifier.width(26.dp), testTag = "btn_main_d")
        HardwareButton("SYNC ST", actions::toggleSyncStart, ledOn = st.syncStartEnabled, ledColor = PsrColors.LedAmber, fontSize = 7.sp, height = 28.dp, modifier = Modifier.width(38.dp))
        HardwareButton("SYNC SP", actions::toggleSyncStop, ledOn = st.syncStopEnabled, ledColor = PsrColors.LedAmber, fontSize = 7.sp, height = 28.dp, modifier = Modifier.width(38.dp))
        HardwareButton("AUTO FILL", actions::toggleAutoFill, ledOn = st.autoFillEnabled, fontSize = 7.sp, height = 28.dp, modifier = Modifier.width(42.dp))
        HardwareButton("BREAK", { actions.selectStyleSection(StyleSection.BREAK) }, ledOn = st.currentSection == StyleSection.BREAK, fontSize = 7.5.sp, height = 28.dp, modifier = Modifier.width(38.dp))
        HardwareButton("I-1", { actions.selectStyleSection(StyleSection.INTRO_1) }, ledOn = st.currentSection == StyleSection.INTRO_1, height = 28.dp, modifier = Modifier.width(26.dp))
        HardwareButton("I-2", { actions.selectStyleSection(StyleSection.INTRO_2) }, ledOn = st.currentSection == StyleSection.INTRO_2, height = 28.dp, modifier = Modifier.width(26.dp))
        HardwareButton("I-3", { actions.selectStyleSection(StyleSection.INTRO_3) }, ledOn = st.currentSection == StyleSection.INTRO_3, height = 28.dp, modifier = Modifier.width(26.dp))
        HardwareButton("E-1", { actions.selectStyleSection(StyleSection.ENDING_1) }, ledOn = st.currentSection == StyleSection.ENDING_1, height = 28.dp, modifier = Modifier.width(26.dp))
        HardwareButton("E-2", { actions.selectStyleSection(StyleSection.ENDING_2) }, ledOn = st.currentSection == StyleSection.ENDING_2, height = 28.dp, modifier = Modifier.width(26.dp))
        HardwareButton("E-3", { actions.selectStyleSection(StyleSection.ENDING_3) }, ledOn = st.currentSection == StyleSection.ENDING_3, height = 28.dp, modifier = Modifier.width(26.dp))
        HardwareButton("FADE", actions::triggerFadeInOut, ledOn = st.isFading, fontSize = 7.5.sp, height = 28.dp, modifier = Modifier.width(34.dp))
      }

      // Registration Memory 1..8
      Row(
        modifier = Modifier
          .weight(1.05f)
          .background(PsrColors.ChassisPanel, RoundedCornerShape(5.dp))
          .border(1.dp, PsrColors.ChassisBorder, RoundedCornerShape(5.dp))
          .padding(3.dp)
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        HardwareButton("B-", { actions.selectRegBank(-1, true) }, showLedBar = false, height = 28.dp, modifier = Modifier.width(24.dp))
        HardwareButton("B${state.activeRegBank + 1}+", { actions.selectRegBank(1, true) }, showLedBar = false, height = 28.dp, modifier = Modifier.width(28.dp))
        for (slot in 1..8) {
          val hasSaved = state.registrationSlots.any { it.slotIndex == slot }
          HardwareButton(
            label = "$slot",
            onClick = { actions.pressRegistrationSlot(slot) },
            ledOn = state.activeRegSlot == slot || hasSaved,
            ledColor = if (state.activeRegSlot == slot) PsrColors.LedGreen else PsrColors.LedAmber,
            height = 28.dp,
            modifier = Modifier.width(24.dp),
            testTag = "btn_reg_$slot",
          )
        }
        HardwareButton("FRZ", actions::toggleRegFreeze, ledOn = state.regFreezeEnabled, ledColor = PsrColors.LedCyan, fontSize = 7.5.sp, height = 28.dp, modifier = Modifier.width(28.dp))
        HardwareButton("MEM", actions::toggleRegMemoryArm, ledOn = state.regMemoryArmed, ledColor = PsrColors.LedRed, fontSize = 7.5.sp, height = 28.dp, modifier = Modifier.width(30.dp), testTag = "btn_reg_memory")
      }

      // Multi Pad + OTS 1..4
      Row(
        modifier = Modifier
          .background(PsrColors.ChassisPanel, RoundedCornerShape(5.dp))
          .border(1.dp, PsrColors.ChassisBorder, RoundedCornerShape(5.dp))
          .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val bank = state.multiPadBanks.getOrNull(state.activeMultiPadBankIndex)
        for (p in 1..4) {
          val playing = bank?.pads?.find { it.padIndex == p }?.isPlaying == true
          HardwareButton("P$p", { actions.triggerMultiPad(p) }, ledOn = playing, height = 28.dp, modifier = Modifier.width(25.dp), testTag = "btn_multipad_$p")
        }
        HardwareButton("SP", actions::stopAllMultiPads, showLedBar = false, height = 28.dp, modifier = Modifier.width(24.dp))
        Spacer(modifier = Modifier.width(3.dp))
        for (ots in 1..4) {
          HardwareButton("O$ots", { actions.selectOts(ots) }, ledOn = state.styleState.activeOtsSlot == ots, ledColor = PsrColors.GoldHighlight, height = 28.dp, modifier = Modifier.width(25.dp))
        }
      }
    }

    // Full 61-Key Playable Touch Keyboard + Pitch Bend & Modulation Wheels
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(keybedHeight)
        .padding(horizontal = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      PitchBendModDeck(
        pitchBendSemis = state.pitchBendSemis,
        modDepth = state.modWheelDepth,
        onPitchBendChange = actions::setPitchBend,
        onModDepthChange = actions::setModWheel,
        modifier = Modifier
          .width(72.dp)
          .fillMaxHeight(),
      )
      WorkstationKeyboard(
        pressedNotes = state.activePressedNotes,
        splitPointMidi = state.splitPointMidi,
        acmpEnabled = state.styleState.acmpEnabled,
        keySizeScale = state.keySizeScale,
        viewportOffset = state.keyViewportOffset,
        onKeySizeScaleChange = actions::setKeySizeScale,
        onViewportOffsetChange = actions::setKeyViewportOffset,
        onNoteDown = actions::onKeyDown,
        onNoteUp = actions::onKeyUp,
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight(),
      )
    }
  }
}

@Composable
private fun PortraitWorkstationConsole(
  state: WorkstationUiState,
  actions: WorkstationActions,
  totalHeight: androidx.compose.ui.unit.Dp,
) {
  var activeDeckTab by remember { mutableIntStateOf(0) }
  val deckLabels = listOf("STYLE CTRL", "PITCH & MOD", "CHORDS", "VOICES & PART", "REG & M.PAD", "KNOBS & TRANSPORT")
  val keybedHeight = (totalHeight * 0.28f).coerceIn(120.dp, 220.dp)

  Column(modifier = Modifier.fillMaxSize()) {
    TopChassisHeaderBar(state = state, actions = actions, compact = true)

    // Main LCD Display
    LcdDisplayFrame(
      state = state,
      actions = actions,
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(horizontal = 6.dp, vertical = 4.dp),
    )

    // Switchable Hardware Control Deck Tabs
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 6.dp)
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      deckLabels.forEachIndexed { idx, lbl ->
        HardwareButton(
          label = lbl,
          onClick = { activeDeckTab = idx },
          ledOn = activeDeckTab == idx,
          ledColor = PsrColors.GoldAccent,
          height = 28.dp,
          modifier = Modifier.width(106.dp),
        )
      }
    }

    // Active Control Deck Content
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 6.dp, vertical = 4.dp)
        .background(PsrColors.ChassisPanel, RoundedCornerShape(6.dp))
        .border(1.dp, PsrColors.ChassisBorder, RoundedCornerShape(6.dp))
        .padding(6.dp),
    ) {
      when (activeDeckTab) {
        0 -> {
          val st = state.styleState
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
              HardwareButton("ACMP", actions::toggleAcmp, ledOn = st.acmpEnabled, ledColor = PsrColors.LedCyan, modifier = Modifier.weight(1f))
              HardwareButton("SYNC ST", actions::toggleSyncStart, ledOn = st.syncStartEnabled, ledColor = PsrColors.LedAmber, modifier = Modifier.weight(1f))
              HardwareButton("KIT: ${state.activeStyleDrumKit.name.take(9)}", { actions.navigateTo(LcdPage.STYLE) }, ledOn = !state.activeStyleDrumKit.isFactory, ledColor = PsrColors.GoldHighlight, fontSize = 7.sp, modifier = Modifier.weight(1.25f), testTag = "btn_portrait_kit_shortcut")
              HardwareButton(if (st.isPlaying) "STOP" else "START", actions::toggleStyleStartStop, ledOn = st.isPlaying, isPrimaryAccent = true, modifier = Modifier.weight(1.1f), testTag = "btn_style_start_stop")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
              HardwareButton("INTRO 1", { actions.selectStyleSection(StyleSection.INTRO_1) }, ledOn = st.currentSection == StyleSection.INTRO_1, fontSize = 7.5.sp, modifier = Modifier.weight(1f))
              HardwareButton("MAIN A", { actions.selectStyleSection(StyleSection.MAIN_A) }, ledOn = st.currentSection == StyleSection.MAIN_A, fontSize = 7.5.sp, modifier = Modifier.weight(1f))
              HardwareButton("MAIN B", { actions.selectStyleSection(StyleSection.MAIN_B) }, ledOn = st.currentSection == StyleSection.MAIN_B, fontSize = 7.5.sp, modifier = Modifier.weight(1f))
              HardwareButton("MAIN C", { actions.selectStyleSection(StyleSection.MAIN_C) }, ledOn = st.currentSection == StyleSection.MAIN_C, fontSize = 7.5.sp, modifier = Modifier.weight(1f))
              HardwareButton("MAIN D", { actions.selectStyleSection(StyleSection.MAIN_D) }, ledOn = st.currentSection == StyleSection.MAIN_D, fontSize = 7.5.sp, modifier = Modifier.weight(1f))
              HardwareButton("FILL", actions::triggerFillIn, ledOn = st.currentSection.isFill, ledColor = PsrColors.LedAmber, fontSize = 7.5.sp, modifier = Modifier.weight(0.9f))
              HardwareButton("END 1", { actions.selectStyleSection(StyleSection.ENDING_1) }, ledOn = st.currentSection == StyleSection.ENDING_1, fontSize = 7.5.sp, modifier = Modifier.weight(1f))
            }
          }
        }
        1 -> {
          Row(
            modifier = Modifier.fillMaxWidth().height(74.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            PitchBendModDeck(
              pitchBendSemis = state.pitchBendSemis,
              modDepth = state.modWheelDepth,
              onPitchBendChange = actions::setPitchBend,
              onModDepthChange = actions::setModWheel,
              modifier = Modifier.width(84.dp).fillMaxHeight(),
            )
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
                HardwareButton("-2", { actions.setPitchBend(-2f) }, ledOn = state.pitchBendSemis <= -1.5f, ledColor = PsrColors.LedCyan, fontSize = 8.sp, height = 28.dp, modifier = Modifier.weight(1f))
                HardwareButton("-1", { actions.setPitchBend(-1f) }, ledOn = state.pitchBendSemis in -1.49f..-0.5f, ledColor = PsrColors.LedCyan, fontSize = 8.sp, height = 28.dp, modifier = Modifier.weight(1f))
                HardwareButton("0", { actions.setPitchBend(0f) }, ledOn = kotlin.math.abs(state.pitchBendSemis) < 0.05f, ledColor = PsrColors.LedGreen, fontSize = 8.sp, height = 28.dp, modifier = Modifier.weight(1f))
                HardwareButton("+1", { actions.setPitchBend(1f) }, ledOn = state.pitchBendSemis in 0.5f..1.49f, ledColor = PsrColors.LedCyan, fontSize = 8.sp, height = 28.dp, modifier = Modifier.weight(1f))
                HardwareButton("+2", { actions.setPitchBend(2f) }, ledOn = state.pitchBendSemis >= 1.5f, ledColor = PsrColors.LedCyan, fontSize = 8.sp, height = 28.dp, modifier = Modifier.weight(1f))
              }
              Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
                HardwareButton("MOD 0", { actions.setModWheel(0f) }, ledOn = state.modWheelDepth < 0.05f, ledColor = PsrColors.GoldAccent, fontSize = 7.5.sp, height = 28.dp, modifier = Modifier.weight(1f))
                HardwareButton("25%", { actions.setModWheel(0.25f) }, ledOn = state.modWheelDepth in 0.15f..0.35f, ledColor = PsrColors.GoldAccent, fontSize = 7.5.sp, height = 28.dp, modifier = Modifier.weight(1f))
                HardwareButton("50%", { actions.setModWheel(0.50f) }, ledOn = state.modWheelDepth in 0.4f..0.65f, ledColor = PsrColors.GoldAccent, fontSize = 7.5.sp, height = 28.dp, modifier = Modifier.weight(1f))
                HardwareButton("100%", { actions.setModWheel(1.0f) }, ledOn = state.modWheelDepth >= 0.85f, ledColor = PsrColors.GoldAccent, fontSize = 7.5.sp, height = 28.dp, modifier = Modifier.weight(1f))
              }
            }
          }
        }
        2 -> {
          val quickChords = listOf(
            "C" to DetectedChord(0, ChordQuality.MAJOR),
            "Dm" to DetectedChord(2, ChordQuality.MINOR),
            "Em" to DetectedChord(4, ChordQuality.MINOR),
            "F" to DetectedChord(5, ChordQuality.MAJOR),
            "G" to DetectedChord(7, ChordQuality.MAJOR),
            "Am" to DetectedChord(9, ChordQuality.MINOR),
            "Bdim" to DetectedChord(11, ChordQuality.DIM),
            "C7" to DetectedChord(0, ChordQuality.SEVENTH),
          )
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
              quickChords.forEach { (label, chord) ->
                HardwareButton(
                  label = label,
                  onClick = { actions.selectChordPreset(chord) },
                  ledOn = state.detectedChord.displaySymbol == chord.displaySymbol,
                  ledColor = PsrColors.LedCyan,
                  fontSize = 8.sp,
                  modifier = Modifier.weight(1f),
                  testTag = "btn_chord_${label.lowercase()}",
                )
              }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
              HardwareButton(
                label = if (state.styleState.acmpEnabled) "SPLIT ON" else "SPLIT OFF",
                onClick = actions::toggleAcmp,
                ledOn = state.styleState.acmpEnabled,
                ledColor = PsrColors.LedGreen,
                modifier = Modifier.weight(1.3f),
                testTag = "btn_split_toggle",
              )
              HardwareButton("TRANS -", { actions.setTranspose(state.transpose - 1) }, showLedBar = false, modifier = Modifier.weight(1f))
              HardwareButton("TRANS +", { actions.setTranspose(state.transpose + 1) }, showLedBar = false, modifier = Modifier.weight(1f))
              HardwareButton("ALL STOP", actions::panicAllNotesOff, ledColor = PsrColors.LedRed, showLedBar = false, modifier = Modifier.weight(1.1f), testTag = "btn_all_stop")
            }
          }
        }
        3 -> {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
              HardwareButton("VOICE ◀", { actions.cycleVoice(-1) }, showLedBar = false, fontSize = 8.sp, modifier = Modifier.weight(1f), testTag = "btn_voice_prev")
              HardwareButton("VOICE ▶", { actions.cycleVoice(1) }, showLedBar = false, fontSize = 8.sp, modifier = Modifier.weight(1f), testTag = "btn_voice_next")
              HardwareButton("LEFT", { actions.toggleVoiceSlotEnabled(ActiveVoiceSlot.LEFT) }, ledOn = state.voiceState.leftEnabled, modifier = Modifier.weight(0.9f))
              HardwareButton("RIGHT 1", { actions.toggleVoiceSlotEnabled(ActiveVoiceSlot.RIGHT_1) }, ledOn = state.voiceState.right1Enabled, modifier = Modifier.weight(1f))
              HardwareButton("RIGHT 2", { actions.toggleVoiceSlotEnabled(ActiveVoiceSlot.RIGHT_2) }, ledOn = state.voiceState.right2Enabled, modifier = Modifier.weight(1f))
              HardwareButton("SUSTAIN", actions::toggleSustain, ledOn = state.voiceState.sustainEnabled, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
              HardwareButton("PIANO", { actions.selectVoiceCategory(VoiceCategory.PIANO); actions.navigateTo(LcdPage.VOICE) }, modifier = Modifier.weight(1f))
              HardwareButton("ETHIOPIAN", { actions.selectVoiceCategory(VoiceCategory.ETHIOPIAN); actions.navigateTo(LcdPage.VOICE) }, modifier = Modifier.weight(1f))
              HardwareButton("BRASS", { actions.selectVoiceCategory(VoiceCategory.BRASS); actions.navigateTo(LcdPage.VOICE) }, modifier = Modifier.weight(1f))
              HardwareButton("STRINGS", { actions.selectVoiceCategory(VoiceCategory.STRINGS); actions.navigateTo(LcdPage.VOICE) }, modifier = Modifier.weight(1f))
            }
          }
        }
        4 -> {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
              for (slot in 1..8) {
                HardwareButton("$slot", { actions.pressRegistrationSlot(slot) }, ledOn = state.activeRegSlot == slot, modifier = Modifier.weight(1f))
              }
              HardwareButton("MEM", actions::toggleRegMemoryArm, ledOn = state.regMemoryArmed, ledColor = PsrColors.LedRed, modifier = Modifier.weight(1.2f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
              for (p in 1..4) {
                HardwareButton("PAD $p", { actions.triggerMultiPad(p) }, modifier = Modifier.weight(1f))
              }
              HardwareButton("STOP PAD", actions::stopAllMultiPads, modifier = Modifier.weight(1f))
            }
          }
        }
        else -> {
          Row(horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            RotaryKnob("MASTER", state.audioStats.masterVolume, actions::setMasterVolume, size = 38.dp)
            RotaryKnob("CUTOFF", state.liveKnobCutoff, actions::setLiveKnobCutoff, size = 38.dp, accentColor = PsrColors.LedCyan)
            RotaryKnob("RESON", state.liveKnobResonance, actions::setLiveKnobResonance, size = 38.dp, accentColor = PsrColors.LedCyan)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                HardwareButton("BPM-", { actions.setTempo(state.styleState.tempoBpm - 2) }, showLedBar = false, modifier = Modifier.width(44.dp))
                HardwareButton("BPM+", { actions.setTempo(state.styleState.tempoBpm + 2) }, showLedBar = false, modifier = Modifier.width(44.dp))
                HardwareButton("TR-", { actions.setTranspose(state.transpose - 1) }, showLedBar = false, modifier = Modifier.width(40.dp))
                HardwareButton("TR+", { actions.setTranspose(state.transpose + 1) }, showLedBar = false, modifier = Modifier.width(40.dp))
              }
              Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                HardwareButton("TAP", actions::tapTempo, isPrimaryAccent = true, showLedBar = false, modifier = Modifier.width(64.dp))
                HardwareButton("ALL STOP", actions::panicAllNotesOff, ledColor = PsrColors.LedRed, showLedBar = false, modifier = Modifier.width(104.dp))
              }
            }
          }
        }
      }
    }

    // Playable 61-Key Keyboard
    WorkstationKeyboard(
      pressedNotes = state.activePressedNotes,
      splitPointMidi = state.splitPointMidi,
      acmpEnabled = state.styleState.acmpEnabled,
      keySizeScale = if (state.keySizeScale == 1.0f) 2.2f else state.keySizeScale,
      viewportOffset = state.keyViewportOffset,
      onKeySizeScaleChange = actions::setKeySizeScale,
      onViewportOffsetChange = actions::setKeyViewportOffset,
      onNoteDown = actions::onKeyDown,
      onNoteUp = actions::onKeyUp,
      modifier = Modifier
        .fillMaxWidth()
        .height(keybedHeight),
    )
  }
}

@Composable
private fun HelpOverlayDialog(onDismiss: () -> Unit) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xBB000000))
      .clickable(onClick = onDismiss),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth(0.86f)
        .clip(RoundedCornerShape(10.dp))
        .background(PsrColors.ChassisPanel)
        .border(1.5.dp, PsrColors.GoldAccent, RoundedCornerShape(10.dp))
        .clickable(enabled = false) {}
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("FARIS YAMAHA PSR-S975 — QUICK GUIDE", fontFamily = DisplayFont, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PsrColors.GoldHighlight)
        Icon(Icons.Default.Close, contentDescription = "Close Help", tint = Color.White, modifier = Modifier.size(20.dp).clickable(onClick = onDismiss))
      }
      Text("• Keyboard: Multi-touch 61 keys. Tap lower on a key for louder velocity. Use KEY SIZE to zoom on phones.", fontFamily = BodyFont, fontSize = 11.sp, color = PsrColors.SilverAccent)
      Text("• Styles & Chords: Turn ACMP ON, play chords below Split Point (F#3), and press START or enable SYNC START. Switch MAIN A-D with AUTO FILL.", fontFamily = BodyFont, fontSize = 11.sp, color = PsrColors.SilverAccent)
      Text("• Voices & Ethiopian Pack: Tap R1/R2/LEFT or category buttons to layer Grand Piano, Sax, Strings, Masinko, Krar, Washint, and Kebero.", fontFamily = BodyFont, fontSize = 11.sp, color = PsrColors.SilverAccent)
      Text("• Expansion & Custom Drum Kits: Tap DRUM KITS or open Expansion to import WAV/MP3/OGG/SF2 drum kits, edit MIDI note maps, import .STY styles, and bind any custom kit live in Arranger/Style.", fontFamily = BodyFont, fontSize = 11.sp, color = PsrColors.SilverAccent)
      Text("• Registration Memory: Press MEM (lights red) then tap 1–8 to save your entire workstation setup; tap 1–8 anytime to recall.", fontFamily = BodyFont, fontSize = 11.sp, color = PsrColors.SilverAccent)
    }
  }
}
