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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.faris.psrs975.style.StyleCategory
import com.faris.psrs975.style.StyleChannelType
import com.faris.psrs975.style.StyleSection
import com.faris.psrs975.voice.ActiveVoiceSlot
import com.faris.psrs975.voice.VoiceCategory
import kotlin.math.roundToInt

@Composable
fun MainLcdPage(state: WorkstationUiState, actions: WorkstationActions) {
  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isCompactVertical = maxHeight < 185.dp
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
      Row(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        // Left LCD Column: Song, Style, Chord/Status
        Column(
          modifier = Modifier.weight(1f).fillMaxHeight(),
          verticalArrangement = Arrangement.spacedBy(if (isCompactVertical) 3.dp else 4.dp),
        ) {
          // SONG CARD
          LcdSlotCard(
            header = "SONG",
            title = state.midiState.currentSong?.title ?: "No Song",
            subtitle = "BAR ${state.midiState.currentBar.toString().padStart(3, '0')} | BPM ${state.midiState.tempoBpm}",
            badge = if (state.midiState.isPlaying) "PLAY" else "STOP",
            badgeColor = if (state.midiState.isPlaying) PsrColors.LedGreen else PsrColors.LcdTextMuted,
            icon = Icons.Default.MusicNote,
            compact = isCompactVertical,
            onClick = { actions.navigateTo(LcdPage.SONG) },
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("lcd_song_card"),
          )

          // STYLE CARD
          val st = state.styleState
          LcdSlotCard(
            header = "STYLE (${st.currentStyle?.category?.displayName ?: "Pop"})",
            title = st.currentStyle?.name ?: "ConcertGrand",
            subtitle = "${st.currentSection.label} | BAR ${st.currentBar}:${st.currentBeat} | ${st.tempoBpm} BPM",
            badge = if (st.isPlaying) "RUN" else "RDY",
            badgeColor = if (st.isPlaying) PsrColors.LedGreen else PsrColors.LcdHighlightBlue,
            icon = Icons.Default.LibraryMusic,
            compact = isCompactVertical,
            onClick = { actions.navigateTo(LcdPage.STYLE) },
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("lcd_style_card"),
          )

          // QUICK BAR: Chord, Drum Kit, MultiPad
          Row(
            modifier = if (isCompactVertical) Modifier.fillMaxWidth().height(24.dp) else Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            LcdMiniTile(
              label = if (isCompactVertical) "CHORD" else "CHORD",
              value = state.detectedChord.displaySymbol,
              compact = isCompactVertical,
              onClick = { actions.navigateTo(LcdPage.UTILITY) },
              modifier = if (isCompactVertical) Modifier.weight(0.75f).fillMaxHeight() else Modifier.weight(0.75f),
            )
            LcdMiniTile(
              label = "DRUM KIT",
              value = state.activeStyleDrumKit.name,
              compact = isCompactVertical,
              onClick = { actions.navigateTo(LcdPage.DRUM_KIT) },
              modifier = (if (isCompactVertical) Modifier.weight(1.25f).fillMaxHeight() else Modifier.weight(1.25f)).testTag("lcd_drum_kit_tile"),
            )
            LcdMiniTile(
              label = if (isCompactVertical) "PAD" else "M.PAD",
              value = state.multiPadBanks.getOrNull(state.activeMultiPadBankIndex)?.bankName?.take(8) ?: "Bank 1",
              compact = isCompactVertical,
              onClick = { actions.navigateTo(LcdPage.MULTI_PAD) },
              modifier = if (isCompactVertical) Modifier.weight(0.9f).fillMaxHeight() else Modifier.weight(0.9f),
            )
          }
        }

        // Right LCD Column: Voice R1, Voice R2, Voice Left, Mixer/FX Shortcut
        Column(
          modifier = Modifier.weight(1f).fillMaxHeight(),
          verticalArrangement = Arrangement.spacedBy(if (isCompactVertical) 3.dp else 4.dp),
        ) {
          val vs = state.voiceState
          LcdSlotCard(
            header = "RIGHT 1 (${if (vs.right1Enabled && vs.right1Voice != null) "ON" else if (vs.right1Voice == null) "EMPTY" else "OFF"})",
            title = vs.right1Voice?.name ?: "Empty (No Voice)",
            subtitle = vs.right1Voice?.category?.displayName ?: "Tap to assign voice",
            badge = vs.right1Voice?.badge ?: "EMPTY",
            badgeColor = if (vs.right1Enabled && vs.right1Voice != null) PsrColors.LcdHighlightBlue else PsrColors.LcdTextMuted,
            isHighlighted = vs.activePartSelection == ActiveVoiceSlot.RIGHT_1,
            icon = Icons.Default.Piano,
            compact = isCompactVertical,
            onClick = {
              actions.setActiveVoiceSlot(ActiveVoiceSlot.RIGHT_1)
              actions.navigateTo(LcdPage.VOICE)
            },
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("lcd_r1_card"),
          )
          LcdSlotCard(
            header = "RIGHT 2 (${if (vs.right2Enabled && vs.right2Voice != null) "ON" else if (vs.right2Voice == null) "EMPTY" else "OFF"})",
            title = vs.right2Voice?.name ?: "Empty (No Voice)",
            subtitle = vs.right2Voice?.category?.displayName ?: "Tap to assign voice",
            badge = vs.right2Voice?.badge ?: "EMPTY",
            badgeColor = if (vs.right2Enabled && vs.right2Voice != null) PsrColors.LedGreen else PsrColors.LcdTextMuted,
            isHighlighted = vs.activePartSelection == ActiveVoiceSlot.RIGHT_2,
            icon = Icons.Default.Piano,
            compact = isCompactVertical,
            onClick = {
              actions.setActiveVoiceSlot(ActiveVoiceSlot.RIGHT_2)
              actions.navigateTo(LcdPage.VOICE)
            },
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("lcd_r2_card"),
          )
          LcdSlotCard(
            header = "LEFT (${if (vs.leftEnabled && vs.leftVoice != null) "ON" else if (vs.leftVoice == null) "EMPTY" else "OFF"})",
            title = vs.leftVoice?.name ?: "Empty (No Voice)",
            subtitle = "Split ${midiNoteName(state.splitPointMidi)} | Reg B${state.activeRegBank + 1}",
            badge = vs.leftVoice?.badge ?: "EMPTY",
            badgeColor = if (vs.leftEnabled && vs.leftVoice != null) PsrColors.LedGreen else PsrColors.LcdTextMuted,
            isHighlighted = vs.activePartSelection == ActiveVoiceSlot.LEFT,
            icon = Icons.Default.Piano,
            compact = isCompactVertical,
            onClick = {
              actions.setActiveVoiceSlot(ActiveVoiceSlot.LEFT)
              actions.navigateTo(LcdPage.VOICE)
            },
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("lcd_left_card"),
          )

          Row(
            modifier = if (isCompactVertical) Modifier.fillMaxWidth().height(24.dp) else Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Row(
              modifier = Modifier
                .weight(1f)
                .then(if (isCompactVertical) Modifier.fillMaxHeight() else Modifier)
                .clip(RoundedCornerShape(4.dp))
                .background(if (state.activeKeyboardSetId != null) Color(0xFFE6EFFB) else PsrColors.LcdCardBg)
                .border(1.dp, PsrColors.LcdBorder, RoundedCornerShape(4.dp))
                .clickable { actions.navigateTo(LcdPage.KEYBOARD_SET) }
                .padding(horizontal = 4.dp, vertical = if (isCompactVertical) 1.dp else 4.dp)
                .testTag("lcd_kbd_set_shortcut"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center,
            ) {
              Icon(Icons.Default.Layers, contentDescription = "Keyboard Set", tint = PsrColors.LcdHighlightBlue, modifier = Modifier.size(12.dp))
              Spacer(modifier = Modifier.width(3.dp))
              Text("KBD SET", fontFamily = DisplayFont, fontSize = 8.5.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, color = PsrColors.LcdTextDark, maxLines = 1, softWrap = false)
            }
            Row(
              modifier = Modifier
                .weight(1f)
                .then(if (isCompactVertical) Modifier.fillMaxHeight() else Modifier)
                .clip(RoundedCornerShape(4.dp))
                .background(PsrColors.LcdCardBg)
                .border(1.dp, PsrColors.LcdBorder, RoundedCornerShape(4.dp))
                .clickable { actions.navigateTo(LcdPage.MIXER) }
                .padding(horizontal = 4.dp, vertical = if (isCompactVertical) 1.dp else 4.dp)
                .testTag("lcd_mixer_shortcut"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center,
            ) {
              Icon(Icons.Default.Equalizer, contentDescription = "Mixer", tint = PsrColors.LcdHighlightBlue, modifier = Modifier.size(12.dp))
              Spacer(modifier = Modifier.width(3.dp))
              Text("MIXER", fontFamily = DisplayFont, fontSize = 8.5.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, color = PsrColors.LcdTextDark, maxLines = 1, softWrap = false)
            }
            Row(
              modifier = Modifier
                .weight(1f)
                .then(if (isCompactVertical) Modifier.fillMaxHeight() else Modifier)
                .clip(RoundedCornerShape(4.dp))
                .background(PsrColors.LcdCardBg)
                .border(1.dp, PsrColors.LcdBorder, RoundedCornerShape(4.dp))
                .clickable { actions.navigateTo(LcdPage.EFFECTS) }
                .padding(horizontal = 4.dp, vertical = if (isCompactVertical) 1.dp else 4.dp)
                .testTag("lcd_effects_shortcut"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center,
            ) {
              Icon(Icons.Default.Waves, contentDescription = "Effects", tint = PsrColors.LcdHighlightBlue, modifier = Modifier.size(12.dp))
              Spacer(modifier = Modifier.width(3.dp))
              Text("FX", fontFamily = DisplayFont, fontSize = 8.5.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, color = PsrColors.LcdTextDark, maxLines = 1, softWrap = false)
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(if (isCompactVertical) 2.dp else 4.dp))

      // Bottom Style Channel On/Off Bar (RHY1..PHR2)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color(0xFF142338), RoundedCornerShape(4.dp))
          .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        StyleChannelType.entries.forEach { ch ->
          val enabled = state.styleState.channelEnabled[ch] ?: true
          if (isCompactVertical) {
            Row(
              modifier = Modifier
                .weight(1f)
                .height(20.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (enabled) Color(0xFF1D4E2E) else Color(0xFF212B3B))
                .border(1.dp, if (enabled) PsrColors.LedGreen else Color(0xFF3E4B5E), RoundedCornerShape(3.dp))
                .clickable { actions.toggleStyleChannel(ch) }
                .padding(horizontal = 2.dp)
                .testTag("lcd_style_ch_${ch.label.lowercase()}"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center,
            ) {
              Box(
                modifier = Modifier
                  .size(4.dp)
                  .clip(CircleShape)
                  .background(if (enabled) PsrColors.LedGreen else PsrColors.SilverMuted),
              )
              Spacer(modifier = Modifier.width(2.dp))
              Text(ch.label, fontFamily = MonoFont, fontSize = 7.sp, lineHeight = 9.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
          } else {
            Column(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(3.dp))
                .background(if (enabled) Color(0xFF1D4E2E) else Color(0xFF212B3B))
                .border(1.dp, if (enabled) PsrColors.LedGreen else Color(0xFF3E4B5E), RoundedCornerShape(3.dp))
                .clickable { actions.toggleStyleChannel(ch) }
                .padding(vertical = 2.dp)
                .testTag("lcd_style_ch_${ch.label.lowercase()}"),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(ch.label, fontFamily = MonoFont, fontSize = 7.sp, lineHeight = 9.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
              Text(if (enabled) "ON" else "OFF", fontFamily = MonoFont, fontSize = 7.sp, lineHeight = 9.sp, color = if (enabled) PsrColors.LedGreen else PsrColors.SilverMuted, maxLines = 1, softWrap = false)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LcdSlotCard(
  header: String,
  title: String,
  subtitle: String,
  badge: String,
  badgeColor: Color,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  isHighlighted: Boolean = false,
  compact: Boolean = false,
) {
  Row(
    modifier = modifier
      .clip(RoundedCornerShape(5.dp))
      .background(if (isHighlighted) PsrColors.LcdCardActive else PsrColors.LcdCardBg)
      .border(1.dp, if (isHighlighted) PsrColors.LcdHighlightBlue else PsrColors.LcdBorder, RoundedCornerShape(5.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = if (compact) 5.dp else 7.dp, vertical = if (compact) 1.dp else 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (compact) {
      val compactTag = when {
        header.startsWith("SONG") -> "SNG"
        header.startsWith("STYLE") -> "STY"
        header.startsWith("RIGHT 1") -> "R1"
        header.startsWith("RIGHT 2") -> "R2"
        else -> "L"
      }
      Text(
        text = compactTag,
        fontFamily = MonoFont,
        fontSize = 7.sp,
        lineHeight = 9.sp,
        fontWeight = FontWeight.Bold,
        color = PsrColors.LcdTextMuted,
        maxLines = 1,
        softWrap = false,
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = title,
        fontFamily = DisplayFont,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Bold,
        color = PsrColors.LcdTextDark,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      Spacer(modifier = Modifier.width(3.dp))
    } else {
      Icon(icon, contentDescription = header, tint = PsrColors.LcdHighlightBlue, modifier = Modifier.size(18.dp))
      Spacer(modifier = Modifier.width(6.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(header, fontFamily = DisplayFont, fontSize = 8.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold, color = PsrColors.LcdTextMuted, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        Text(title, fontFamily = DisplayFont, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, color = PsrColors.LcdTextDark, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        Text(subtitle, fontFamily = MonoFont, fontSize = 8.sp, lineHeight = 10.sp, color = PsrColors.LcdTextMuted, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
      }
    }
    Box(
      modifier = Modifier
        .clip(RoundedCornerShape(3.dp))
        .background(badgeColor.copy(alpha = 0.18f))
        .border(1.dp, badgeColor, RoundedCornerShape(3.dp))
        .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
      Text(badge, fontFamily = MonoFont, fontSize = 8.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold, color = PsrColors.LcdTextDark, maxLines = 1, softWrap = false)
    }
  }
}

@Composable
private fun LcdMiniTile(
  label: String,
  value: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
) {
  if (compact) {
    Row(
      modifier = modifier
        .clip(RoundedCornerShape(4.dp))
        .background(PsrColors.LcdCardBg)
        .border(1.dp, PsrColors.LcdBorder, RoundedCornerShape(4.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 5.dp, vertical = 1.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(label, fontFamily = DisplayFont, fontSize = 7.sp, lineHeight = 9.sp, color = PsrColors.LcdTextMuted, maxLines = 1, softWrap = false)
      Spacer(modifier = Modifier.width(3.dp))
      Text(value, fontFamily = DisplayFont, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, color = PsrColors.LcdTextDark, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
  } else {
    Column(
      modifier = modifier
        .clip(RoundedCornerShape(4.dp))
        .background(PsrColors.LcdCardBg)
        .border(1.dp, PsrColors.LcdBorder, RoundedCornerShape(4.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
      Text(label, fontFamily = DisplayFont, fontSize = 7.sp, lineHeight = 9.sp, color = PsrColors.LcdTextMuted, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
      Text(value, fontFamily = DisplayFont, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, color = PsrColors.LcdTextDark, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
  }
}

@Composable
fun VoiceLcdPage(state: WorkstationUiState, actions: WorkstationActions) {
  var showFavoritesOnly by remember { mutableStateOf(false) }
  var showVoiceEditor by remember { mutableStateOf(false) }
  var userVoiceName by remember { mutableStateOf("") }

  val vs = state.voiceState
  val filtered = remember(vs.allVoices, state.selectedVoiceCategory, state.voiceSearchQuery, showFavoritesOnly, vs.favoriteIds) {
    vs.allVoices.filter { v ->
      val catOk = showFavoritesOnly || state.voiceSearchQuery.isNotBlank() || v.category == state.selectedVoiceCategory
      val favOk = !showFavoritesOnly || v.id in vs.favoriteIds
      val searchOk = state.voiceSearchQuery.isBlank() || v.name.contains(state.voiceSearchQuery, ignoreCase = true)
      catOk && favOk && searchOk
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isCompactVertical = maxHeight < 240.dp
    Column(modifier = Modifier.fillMaxSize()) {
      // Top bar: Target Slot (R1/R2/Left), Search, Favs, Voice Edit toggle
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .then(if (isCompactVertical) Modifier.height(24.dp) else Modifier)
          .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        ActiveVoiceSlot.entries.forEach { slot ->
          val active = vs.activePartSelection == slot
          LcdChip(
            text = slot.label,
            selected = active,
            onClick = { actions.setActiveVoiceSlot(slot) },
          )
        }
        LcdChip(
          text = if (showFavoritesOnly) "★ FAVS" else "☆ FAVS",
          selected = showFavoritesOnly,
          onClick = { showFavoritesOnly = !showFavoritesOnly },
        )
        LcdChip(
          text = if (showVoiceEditor) "LIST" else "VOICE EDIT",
          selected = showVoiceEditor,
          onClick = { showVoiceEditor = !showVoiceEditor },
        )
        val activeVoice = when (vs.activePartSelection) {
          ActiveVoiceSlot.RIGHT_1 -> vs.right1Voice
          ActiveVoiceSlot.RIGHT_2 -> vs.right2Voice
          ActiveVoiceSlot.LEFT -> vs.leftVoice
        }
        LcdChip(
          text = "PREVIEW",
          selected = false,
          testTag = "btn_voice_preview",
          onClick = { activeVoice?.let { actions.previewVoicePatch(it) } },
        )
        LcdChip(
          text = "IMPORT .SF2",
          selected = false,
          testTag = "btn_import_sf2",
          onClick = { actions.requestImportSf2File() },
        )
      }

      Spacer(modifier = Modifier.height(if (isCompactVertical) 3.dp else 4.dp))

    if (showVoiceEditor) {
      val slot = vs.activePartSelection
      val params = when (slot) {
        ActiveVoiceSlot.RIGHT_1 -> vs.right1Edit
        ActiveVoiceSlot.RIGHT_2 -> vs.right2Edit
        ActiveVoiceSlot.LEFT -> vs.leftEdit
      }
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .background(PsrColors.LcdCardBg, RoundedCornerShape(5.dp))
          .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text("EDITING ${slot.label}: ${when (slot) {
          ActiveVoiceSlot.RIGHT_1 -> vs.right1Voice?.name
          ActiveVoiceSlot.RIGHT_2 -> vs.right2Voice?.name
          ActiveVoiceSlot.LEFT -> vs.leftVoice?.name
        } ?: ""}", fontFamily = DisplayFont, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PsrColors.LcdTextDark)

        LcdSliderRow("Attack Scale", params.attackScale, 0.2f..3.0f) {
          actions.updateVoiceSlotEdit(slot, params.copy(attackScale = it))
        }
        LcdSliderRow("Release Scale", params.releaseScale, 0.2f..3.0f) {
          actions.updateVoiceSlotEdit(slot, params.copy(releaseScale = it))
        }
        LcdSliderRow("Brightness / Cutoff", params.brightnessScale, 0.3f..1.8f) {
          actions.updateVoiceSlotEdit(slot, params.copy(brightnessScale = it))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Octave Offset: ${params.octaveOffset}", fontFamily = MonoFont, fontSize = 10.sp, color = PsrColors.LcdTextDark)
          LcdChip("-1", false) { actions.updateVoiceSlotEdit(slot, params.copy(octaveOffset = (params.octaveOffset - 1).coerceIn(-2, 2))) }
          LcdChip("0", params.octaveOffset == 0) { actions.updateVoiceSlotEdit(slot, params.copy(octaveOffset = 0)) }
          LcdChip("+1", false) { actions.updateVoiceSlotEdit(slot, params.copy(octaveOffset = (params.octaveOffset + 1).coerceIn(-2, 2))) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          OutlinedTextField(
            value = userVoiceName,
            onValueChange = { userVoiceName = it },
            placeholder = { Text("Custom user voice name", fontSize = 10.sp) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = PsrColors.LcdTextDark,
              unfocusedTextColor = PsrColors.LcdTextDark,
            ),
          )
          LcdChip("SAVE USER", true) {
            actions.saveUserVoice(userVoiceName)
            userVoiceName = ""
            showVoiceEditor = false
          }
        }
      }
    } else {
      // Category Row + Search
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .then(if (isCompactVertical) Modifier.height(24.dp) else Modifier)
          .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        VoiceCategory.entries.forEach { cat ->
          LcdChip(
            text = cat.displayName,
            selected = !showFavoritesOnly && state.selectedVoiceCategory == cat,
            onClick = {
              showFavoritesOnly = false
              actions.selectVoiceCategory(cat)
            },
          )
        }
      }
      Spacer(modifier = Modifier.height(if (isCompactVertical) 3.dp else 4.dp))
      if (isCompactVertical) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(PsrColors.LcdCardBg)
            .border(1.dp, PsrColors.LcdBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Default.Search, contentDescription = "Search voices", tint = PsrColors.LcdTextMuted, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (state.voiceSearchQuery.isEmpty()) {
              Text("Search voices (Piano, Masinko, Sax...)", fontFamily = BodyFont, fontSize = 9.sp, lineHeight = 11.sp, color = PsrColors.LcdTextMuted, maxLines = 1)
            }
            BasicTextField(
              value = state.voiceSearchQuery,
              onValueChange = actions::setVoiceSearch,
              singleLine = true,
              textStyle = TextStyle(fontFamily = BodyFont, fontSize = 10.sp, color = PsrColors.LcdTextDark),
              cursorBrush = SolidColor(PsrColors.LcdHighlightBlue),
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      } else {
        OutlinedTextField(
          value = state.voiceSearchQuery,
          onValueChange = actions::setVoiceSearch,
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search voices", tint = PsrColors.LcdTextMuted, modifier = Modifier.size(16.dp)) },
          placeholder = { Text("Search voices (Piano, Masinko, Sax...)", fontSize = 10.sp, color = PsrColors.LcdTextMuted) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().height(46.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = PsrColors.LcdTextDark,
            unfocusedTextColor = PsrColors.LcdTextDark,
            focusedContainerColor = PsrColors.LcdCardBg,
            unfocusedContainerColor = PsrColors.LcdCardBg,
          ),
        )
      }
      Spacer(modifier = Modifier.height(if (isCompactVertical) 3.dp else 4.dp))
      LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth().testTag("voice_list_lazy"),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        items(filtered, key = { it.id }) { patch ->
          val activeId = when (vs.activePartSelection) {
            ActiveVoiceSlot.RIGHT_1 -> vs.right1Voice?.id
            ActiveVoiceSlot.RIGHT_2 -> vs.right2Voice?.id
            ActiveVoiceSlot.LEFT -> vs.leftVoice?.id
          }
          val isSelected = activeId == patch.id
          val isFav = patch.id in vs.favoriteIds
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .then(if (isCompactVertical) Modifier.height(28.dp) else Modifier)
              .clip(RoundedCornerShape(4.dp))
              .background(if (isSelected) PsrColors.LcdHighlightBlue else PsrColors.LcdCardBg)
              .border(1.dp, PsrColors.LcdBorder, RoundedCornerShape(4.dp))
              .clickable { actions.selectVoicePatch(patch) }
              .padding(horizontal = 8.dp, vertical = if (isCompactVertical) 2.dp else 5.dp)
              .testTag("voice_item_${patch.id}"),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (isCompactVertical) {
              Text(
                text = patch.name,
                fontFamily = DisplayFont,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else PsrColors.LcdTextDark,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = patch.sourceFormat,
                fontFamily = MonoFont,
                fontSize = 8.sp,
                lineHeight = 10.sp,
                color = if (isSelected) Color(0xFFD5E6FF) else PsrColors.LcdTextMuted,
                maxLines = 1,
                softWrap = false,
              )
              Spacer(modifier = Modifier.width(6.dp))
            } else {
              Column(modifier = Modifier.weight(1f)) {
                Text(patch.name, fontFamily = DisplayFont, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Color.White else PsrColors.LcdTextDark, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                Text("${patch.category.displayName} • ${patch.sourceFormat}", fontFamily = MonoFont, fontSize = 8.sp, lineHeight = 10.sp, color = if (isSelected) Color(0xFFD5E6FF) else PsrColors.LcdTextMuted, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
              }
            }
            Text(patch.badge, fontFamily = MonoFont, fontSize = 8.sp, lineHeight = 10.sp, color = if (isSelected) PsrColors.GoldHighlight else PsrColors.LcdHighlightBlue, maxLines = 1, softWrap = false)
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
              imageVector = Icons.Default.PlayArrow,
              contentDescription = "Preview ${patch.name}",
              tint = if (isSelected) PsrColors.GoldHighlight else PsrColors.LcdHighlightBlue,
              modifier = Modifier
                .size(18.dp)
                .clickable { actions.previewVoicePatch(patch) }
                .testTag("btn_preview_voice_${patch.id}"),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
              imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
              contentDescription = "Favorite ${patch.name}",
              tint = if (isFav) PsrColors.LedRed else if (isSelected) Color.White else PsrColors.LcdTextMuted,
              modifier = Modifier.size(16.dp).clickable { actions.toggleFavoriteVoice(patch.id) },
            )
          }
        }
      }
    }
  }
  }
}

@Composable
fun StyleLcdPage(state: WorkstationUiState, actions: WorkstationActions) {
  var showEditor by remember { mutableStateOf(false) }
  var editSection by remember { mutableStateOf(StyleSection.MAIN_A) }
  var customName by remember { mutableStateOf("My MIDI Style") }
  val st = state.styleState

  Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    // 1. IMPORT STY & CUSTOM DRUM KIT Toolbar + Categories
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      LcdChip(
        text = "IMPORT .STY",
        selected = true,
        testTag = "btn_import_sty",
      ) { actions.requestImportStyFile() }
      LcdChip(
        text = "DRUM KITS ↗",
        selected = false,
        testTag = "btn_open_drum_kit_page",
      ) { actions.navigateTo(LcdPage.DRUM_KIT) }
      LcdChip(if (showEditor) "STYLE LIST" else "STYLE EDITOR", showEditor) { showEditor = !showEditor }
      StyleCategory.entries.forEach { cat ->
        LcdChip(cat.displayName, !showEditor && state.selectedStyleCategory == cat) {
          showEditor = false
          actions.selectStyleCategory(cat)
        }
      }
    }

    // 2. ARRANGER DRUM KIT SELECTOR (Works live while style is playing)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(PsrColors.LcdCardBg, RoundedCornerShape(4.dp))
        .border(1.dp, PsrColors.LcdHighlightBlue, RoundedCornerShape(4.dp))
        .padding(horizontal = 6.dp, vertical = 4.dp)
        .horizontalScroll(rememberScrollState())
        .testTag("arranger_drum_kit_selector"),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = "DRUM KIT SELECTOR:",
        fontFamily = DisplayFont,
        fontSize = 8.5.sp,
        fontWeight = FontWeight.Bold,
        color = PsrColors.LcdTextDark,
      )
      state.drumKitState.kits.forEach { kit ->
        val selected = state.activeStyleDrumKitId == kit.id
        LcdChip(
          text = if (selected) "✓ ${kit.name}" else kit.name,
          selected = selected,
          testTag = "arranger_kit_chip_${kit.id}",
        ) { actions.assignDrumKitToStyle(kit.id) }
      }
      LcdChip("+ IMPORT KIT", false, testTag = "btn_style_import_drum_kit") {
        actions.requestImportDrumKitFiles()
      }
    }

    // 3. Inline ARRANGER TRANSPORT & SECTIONS (Start, Stop, Sync Start, Tempo, Intro/Main/Fill/Break/End)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .testTag("arranger_inline_transport"),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      LcdChip(if (st.isPlaying) "STOP" else "START", st.isPlaying, testTag = "arranger_start_stop") {
        actions.toggleStyleStartStop()
      }
      LcdChip("SYNC ST", st.syncStartEnabled, testTag = "arranger_sync_start") {
        actions.toggleSyncStart()
      }
      LcdChip("BPM-", false) { actions.setTempo(st.tempoBpm - 2) }
      Text("${st.tempoBpm}", fontFamily = MonoFont, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = PsrColors.LcdTextDark)
      LcdChip("BPM+", false) { actions.setTempo(st.tempoBpm + 2) }
      listOf(
        StyleSection.INTRO_1 to "INT",
        StyleSection.MAIN_A to "A",
        StyleSection.MAIN_B to "B",
        StyleSection.MAIN_C to "C",
        StyleSection.MAIN_D to "D",
        StyleSection.BREAK to "BRK",
        StyleSection.ENDING_1 to "END",
      ).forEach { (sec, lbl) ->
        LcdChip(lbl, st.currentSection == sec) { actions.selectStyleSection(sec) }
      }
      LcdChip("FILL", st.currentSection.isFill) { actions.triggerFillIn() }
    }

    if (showEditor) {
      val style = st.currentStyle
      val pat = style?.sections?.get(editSection)
      Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("SECTION:", fontFamily = DisplayFont, fontSize = 9.sp, color = PsrColors.LcdTextDark)
          listOf(StyleSection.MAIN_A, StyleSection.MAIN_B, StyleSection.MAIN_C, StyleSection.MAIN_D).forEach { sec ->
            LcdChip(sec.label, editSection == sec) { editSection = sec }
          }
        }
        if (pat != null) {
          val rows = listOf(
            Triple("KICK", StyleChannelType.RHYTHM_1, 36),
            Triple("SNARE", StyleChannelType.RHYTHM_1, 38),
            Triple("HI-HAT", StyleChannelType.RHYTHM_1, 42),
            Triple("BASS", StyleChannelType.BASS, 36),
            Triple("CHORD", StyleChannelType.CHORD_1, 60),
          )
          rows.forEach { (lbl, ch, note) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
              Text(lbl, fontFamily = MonoFont, fontSize = 8.sp, color = PsrColors.LcdTextDark, modifier = Modifier.width(42.dp))
              Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (step in 0 until 16) {
                  val active = pat.events.any { it.stepIndex == step && it.channel == ch && (ch != StyleChannelType.RHYTHM_1 || it.fixedMidiNote == note) }
                  val isCurrent = st.isPlaying && (st.activeStepInBar == step)
                  Box(
                    modifier = Modifier
                      .weight(1f)
                      .height(18.dp)
                      .clip(RoundedCornerShape(2.dp))
                      .background(
                        when {
                          active && isCurrent -> PsrColors.GoldAccent
                          active -> PsrColors.LcdHighlightBlue
                          isCurrent -> Color(0xFF9FB8D8)
                          step % 4 == 0 -> Color(0xFFCAD8EC)
                          else -> PsrColors.LcdCardBg
                        },
                      )
                      .border(0.5.dp, PsrColors.LcdBorder, RoundedCornerShape(2.dp))
                      .clickable { actions.toggleStylePatternStep(editSection, step, ch, note) },
                  )
                }
              }
            }
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          OutlinedTextField(
            value = customName,
            onValueChange = { customName = it },
            singleLine = true,
            modifier = Modifier.weight(1f),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = PsrColors.LcdTextDark, unfocusedTextColor = PsrColors.LcdTextDark),
          )
          LcdChip("IMPORT MIDI AS STYLE", true) { actions.importCurrentMidiAsStyle(customName) }
        }
      }
    } else {
      val styles = st.allStyles.filter { it.category == state.selectedStyleCategory || it.isUserStyle }
      LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items(styles, key = { it.id }) { style ->
          val active = st.currentStyle?.id == style.id
          val boundKitName = state.styleDrumKitBindings[style.id]?.let { kitId ->
            state.drumKitState.kits.find { it.id == kitId }?.name
          }
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(4.dp))
              .background(if (active) PsrColors.LcdHighlightBlue else PsrColors.LcdCardBg)
              .border(1.dp, PsrColors.LcdBorder, RoundedCornerShape(4.dp))
              .clickable { actions.selectStyle(style) }
              .padding(horizontal = 8.dp, vertical = 6.dp)
              .testTag("style_item_${style.id}"),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(style.name, fontFamily = DisplayFont, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (active) Color.White else PsrColors.LcdTextDark)
              val kitTag = if (boundKitName != null) " • Kit: $boundKitName" else ""
              Text(
                "${style.category.displayName} • ${style.defaultBpm} BPM • ${style.timeSignatureNumerator}/${style.timeSignatureDenominator} • ${if (style.isUserStyle) "User/.STY" else "Factory"}$kitTag",
                fontFamily = MonoFont,
                fontSize = 8.sp,
                color = if (active) Color(0xFFD5E6FF) else PsrColors.LcdTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Text(if (active) "SELECTED" else "LOAD", fontFamily = MonoFont, fontSize = 9.sp, color = if (active) PsrColors.GoldHighlight else PsrColors.LcdHighlightBlue)
          }
        }
      }
    }
  }
}

@Composable
fun SongLcdPage(state: WorkstationUiState, actions: WorkstationActions) {
  val ms = state.midiState
  Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
    // Transport + Seek
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(PsrColors.LcdCardBg, RoundedCornerShape(5.dp))
        .border(1.dp, PsrColors.LcdBorder, RoundedCornerShape(5.dp))
        .padding(6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector = if (ms.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
        contentDescription = "Play/Pause Song",
        tint = PsrColors.LcdHighlightBlue,
        modifier = Modifier.size(24.dp).clickable { actions.toggleSongPlayPause() }.testTag("song_play_pause"),
      )
      Icon(
        imageVector = Icons.Default.Stop,
        contentDescription = "Stop Song",
        tint = PsrColors.LcdTextDark,
        modifier = Modifier.size(22.dp).clickable { actions.stopSong() }.testTag("song_stop"),
      )
      Icon(
        imageVector = Icons.Default.Repeat,
        contentDescription = "Loop Song",
        tint = if (ms.loopEnabled) PsrColors.LedGreen else PsrColors.LcdTextMuted,
        modifier = Modifier.size(20.dp).clickable { actions.toggleSongLoop() },
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "${ms.currentSong?.title ?: "None"}  (Bar ${ms.currentBar}:${ms.currentBeat})",
          fontFamily = DisplayFont,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = PsrColors.LcdTextDark,
        )
        Slider(
          value = (ms.currentTick.toFloat() / ms.totalTicks.coerceAtLeast(1L)).coerceIn(0f, 1f),
          onValueChange = actions::seekSong,
          modifier = Modifier.height(16.dp),
          colors = SliderDefaults.colors(thumbColor = PsrColors.LcdHighlightBlue, activeTrackColor = PsrColors.LcdHighlightBlue),
        )
      }
      LcdChip(if (ms.isRecording) "STOP MIDI REC" else "REC MIDI", ms.isRecording) { actions.toggleMidiRecording() }
      LcdChip("EXPORT .MID", false) { actions.exportActiveSongMidi() }
    }

    Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      // Song List
      LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items(state.availableSongs, key = { it.id }) { song ->
          val active = song.id == ms.currentSong?.id
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(4.dp))
              .background(if (active) PsrColors.LcdHighlightBlue else PsrColors.LcdCardBg)
              .border(1.dp, PsrColors.LcdBorder, RoundedCornerShape(4.dp))
              .clickable { actions.selectMidiSong(song) }
              .padding(6.dp),
          ) {
            Text(song.title, fontFamily = DisplayFont, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (active) Color.White else PsrColors.LcdTextDark, modifier = Modifier.weight(1f))
            Text("${song.initialBpm} BPM", fontFamily = MonoFont, fontSize = 8.sp, color = if (active) PsrColors.GoldHighlight else PsrColors.LcdTextMuted)
          }
        }
      }

      // Track Mute / Solo
      LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items(ms.tracks, key = { it.trackIndex }) { trk ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(PsrColors.LcdCardBg, RoundedCornerShape(4.dp))
              .border(1.dp, PsrColors.LcdBorder, RoundedCornerShape(4.dp))
              .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(trk.name, fontFamily = DisplayFont, fontSize = 9.sp, color = PsrColors.LcdTextDark, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            LcdChip("M", trk.muted) { actions.toggleSongTrackMute(trk.trackIndex) }
            Spacer(modifier = Modifier.width(3.dp))
            LcdChip("S", trk.soloed) { actions.toggleSongTrackSolo(trk.trackIndex) }
          }
        }
      }
    }
  }
}

@Composable
fun LcdChip(
  text: String,
  selected: Boolean,
  testTag: String? = null,
  onClick: () -> Unit,
) {
  Box(
    modifier = Modifier
      .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
      .clip(RoundedCornerShape(4.dp))
      .background(if (selected) PsrColors.LcdHighlightBlue else PsrColors.LcdCardBg)
      .border(1.dp, if (selected) PsrColors.LcdTopBar else PsrColors.LcdBorder, RoundedCornerShape(4.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 7.dp, vertical = 3.dp),
  ) {
    Text(
      text = text,
      fontFamily = DisplayFont,
      fontSize = 9.sp,
      lineHeight = 11.sp,
      fontWeight = FontWeight.Bold,
      color = if (selected) Color.White else PsrColors.LcdTextDark,
      maxLines = 1,
      softWrap = false,
    )
  }
}

@Composable
fun LcdSliderRow(
  label: String,
  value: Float,
  range: ClosedFloatingPointRange<Float> = 0f..1f,
  onValueChange: (Float) -> Unit,
) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
    Text(label, fontFamily = DisplayFont, fontSize = 9.sp, color = PsrColors.LcdTextDark, modifier = Modifier.width(108.dp))
    Slider(
      value = value.coerceIn(range.start, range.endInclusive),
      valueRange = range,
      onValueChange = onValueChange,
      modifier = Modifier.weight(1f).height(20.dp),
      colors = SliderDefaults.colors(thumbColor = PsrColors.LcdHighlightBlue, activeTrackColor = PsrColors.LcdHighlightBlue),
    )
    Text(
      text = ((value * 100).roundToInt() / 100f).toString(),
      fontFamily = MonoFont,
      fontSize = 9.sp,
      color = PsrColors.LcdTextMuted,
      modifier = Modifier.width(38.dp),
    )
  }
}
