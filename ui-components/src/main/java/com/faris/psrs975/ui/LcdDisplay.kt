package com.faris.psrs975.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LcdDisplayFrame(
  state: WorkstationUiState,
  actions: WorkstationActions,
  modifier: Modifier = Modifier,
) {
  val lcdBrush = Brush.verticalGradient(
    listOf(PsrColors.LcdSurfaceTop, PsrColors.LcdSurfaceBottom),
  )

  Column(
    modifier = modifier
      .clip(RoundedCornerShape(8.dp))
      .background(if (state.powerOn) lcdBrush else Brush.verticalGradient(listOf(Color(0xFF0C1016), Color(0xFF07090C))))
      .border(2.dp, Color(0xFF2D3546), RoundedCornerShape(8.dp))
      .testTag("main_lcd_display"),
  ) {
    if (!state.powerOn) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
          text = "FARIS PSR-S975 — STANDBY (PRESS POWER)",
          fontFamily = DisplayFont,
          fontSize = 12.sp,
          color = PsrColors.SilverMuted,
        )
      }
      return@Column
    }

    // Top LCD Dark Navy Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(26.dp)
        .background(PsrColors.LcdTopBar)
        .padding(horizontal = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (state.currentLcdPage != LcdPage.MAIN) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(Color(0xFF1D3557))
              .clickable { actions.navigateTo(LcdPage.MAIN) }
              .padding(horizontal = 6.dp, vertical = 2.dp)
              .testTag("lcd_back_main"),
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Main", tint = Color.White, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text("MAIN", fontFamily = DisplayFont, fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
          }
          Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
          text = if (state.showMenuOverlay) "WORKSTATION MENU" else state.currentLcdPage.title,
          fontFamily = DisplayFont,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = PsrColors.GoldHighlight,
        )
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "TR:${if (state.transpose >= 0) "+${state.transpose}" else "${state.transpose}"}",
          fontFamily = MonoFont,
          fontSize = 9.sp,
          color = Color(0xFFBBD3F2),
        )
        Text(
          text = "OCT:${if (state.upperOctave >= 0) "+${state.upperOctave}" else "${state.upperOctave}"}",
          fontFamily = MonoFont,
          fontSize = 9.sp,
          color = Color(0xFFBBD3F2),
        )
        Text(
          text = "BPM:${state.styleState.tempoBpm}",
          fontFamily = MonoFont,
          fontSize = 9.sp,
          color = PsrColors.LedCyan,
          fontWeight = FontWeight.Bold,
        )
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF1E3F6B))
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .testTag("lcd_chord_readout"),
        ) {
          Text(
            text = state.detectedChord.displaySymbol,
            fontFamily = DisplayFont,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
          )
        }
      }
    }

    // Pending STY import Drum Kit selector banner
    state.pendingStyDrumSelectionStyleId?.let { pendingStyleId ->
      val pendingStyle = state.styleState.allStyles.find { it.id == pendingStyleId }
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color(0xFF142D4C))
          .padding(horizontal = 6.dp, vertical = 3.dp)
          .testTag("pending_sty_drum_prompt"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = "STY '${pendingStyle?.name ?: "Imported"}' DRUM KIT:",
          fontFamily = DisplayFont,
          fontSize = 8.5.sp,
          fontWeight = FontWeight.Bold,
          color = PsrColors.GoldHighlight,
          maxLines = 1,
        )
        Row(
          modifier = Modifier.weight(1f),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          state.drumKitState.kits.take(3).forEach { kit ->
            LcdChip(
              text = kit.name.take(14),
              selected = state.activeStyleDrumKitId == kit.id,
              testTag = "prompt_sty_kit_${kit.id}",
              onClick = { actions.assignDrumKitToStyle(kit.id, pendingStyleId) },
            )
          }
        }
        Icon(
          Icons.Default.Close,
          contentDescription = "Dismiss Drum Kit Prompt",
          tint = Color.White,
          modifier = Modifier.size(14.dp).clickable { actions.dismissPendingStyDrumPrompt() },
        )
      }
    }

    // Active Page Content or Menu Grid
    Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(6.dp)) {
      if (state.showMenuOverlay) {
        LcdMenuGrid(
          currentPage = state.currentLcdPage,
          onSelect = { page ->
            actions.navigateTo(page)
          },
          onClose = { actions.toggleMenuOverlay() },
        )
      } else {
        when (state.currentLcdPage) {
          LcdPage.MAIN -> MainLcdPage(state, actions)
          LcdPage.VOICE -> VoiceLcdPage(state, actions)
          LcdPage.KEYBOARD_SET -> KeyboardSetLcdPage(state, actions)
          LcdPage.STYLE -> StyleLcdPage(state, actions)
          LcdPage.DRUM_KIT -> DrumKitLcdPage(state, actions)
          LcdPage.SONG -> SongLcdPage(state, actions)
          LcdPage.MIXER -> MixerLcdPage(state, actions)
          LcdPage.EFFECTS -> EffectsLcdPage(state, actions)
          LcdPage.MULTI_PAD -> MultiPadLcdPage(state, actions)
          LcdPage.REGISTRATION -> RegistrationLcdPage(state, actions)
          LcdPage.MIDI -> MidiLcdPage(state, actions)
          LcdPage.RECORDING -> RecordingLcdPage(state, actions)
          LcdPage.EXPANSION_MANAGER -> ExpansionLcdPage(state, actions)
          LcdPage.UTILITY -> UtilityLcdPage(state, actions)
          LcdPage.SETTINGS -> SettingsLcdPage(state, actions)
        }
      }
    }
  }
}

private data class MenuEntry(val page: LcdPage, val label: String, val icon: ImageVector)

@Composable
private fun LcdMenuGrid(
  currentPage: LcdPage,
  onSelect: (LcdPage) -> Unit,
  onClose: () -> Unit,
) {
  val entries = listOf(
    MenuEntry(LcdPage.MAIN, "Main", Icons.Default.Home),
    MenuEntry(LcdPage.VOICE, "Voice", Icons.Default.Piano),
    MenuEntry(LcdPage.KEYBOARD_SET, "Kbd Set", Icons.Default.Layers),
    MenuEntry(LcdPage.STYLE, "Arranger", Icons.Default.LibraryMusic),
    MenuEntry(LcdPage.DRUM_KIT, "Drum Kit", Icons.Default.GraphicEq),
    MenuEntry(LcdPage.SONG, "Song", Icons.Default.MusicNote),
    MenuEntry(LcdPage.MIXER, "Mixer", Icons.Default.Equalizer),
    MenuEntry(LcdPage.EFFECTS, "Effects", Icons.Default.Waves),
    MenuEntry(LcdPage.MULTI_PAD, "Multi Pad", Icons.Default.GridView),
    MenuEntry(LcdPage.REGISTRATION, "Regist", Icons.Default.Memory),
    MenuEntry(LcdPage.MIDI, "MIDI", Icons.Default.Usb),
    MenuEntry(LcdPage.RECORDING, "Audio Rec", Icons.Default.FiberManualRecord),
    MenuEntry(LcdPage.EXPANSION_MANAGER, "Expansion", Icons.Default.Extension),
    MenuEntry(LcdPage.UTILITY, "Utility", Icons.Default.Tune),
    MenuEntry(LcdPage.SETTINGS, "Settings", Icons.Default.Settings),
  )

  Column(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        "SELECT WORKSTATION PAGE",
        fontFamily = DisplayFont,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = PsrColors.LcdTextDark,
      )
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(4.dp))
          .background(PsrColors.LcdTopBar)
          .clickable { onClose() }
          .padding(horizontal = 8.dp, vertical = 3.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Close, contentDescription = "Close Menu", tint = Color.White, modifier = Modifier.size(12.dp))
          Spacer(modifier = Modifier.width(3.dp))
          Text("CLOSE", fontFamily = DisplayFont, fontSize = 9.sp, color = Color.White)
        }
      }
    }
    LazyVerticalGrid(
      columns = GridCells.Adaptive(minSize = 68.dp),
      contentPadding = PaddingValues(2.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      modifier = Modifier.fillMaxSize().testTag("lcd_menu_grid"),
    ) {
      items(entries) { item ->
        val active = item.page == currentPage
        Column(
          modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(if (active) PsrColors.LcdHighlightBlue else PsrColors.LcdCardBg)
            .border(1.dp, PsrColors.LcdBorder, RoundedCornerShape(5.dp))
            .clickable { onSelect(item.page) }
            .padding(vertical = 4.dp, horizontal = 3.dp)
            .testTag("menu_item_${item.page.name.lowercase()}"),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Icon(
            item.icon,
            contentDescription = item.label,
            tint = if (active) Color.White else PsrColors.LcdHighlightBlue,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = item.label,
            fontFamily = DisplayFont,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) Color.White else PsrColors.LcdTextDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}
