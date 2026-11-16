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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.faris.psrs975.voice.ActiveVoiceSlot

@Composable
fun KeyboardSetLcdPage(
  state: WorkstationUiState,
  actions: WorkstationActions,
) {
  val activeSet = state.activeKeyboardSet
  val vs = state.voiceState
  var showRenameBar by remember { mutableStateOf(false) }
  var renameDraft by remember(activeSet?.id) { mutableStateOf(activeSet?.name ?: "Empty Set") }

  val isLiveEmpty = vs.right1Voice == null && vs.right2Voice == null && vs.leftVoice == null

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .testTag("keyboard_set_lcd_page"),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    // 1. Toolbar: + EMPTY KBD SET, + SAVE LIVE, CLEAR LIVE EMPTY, plus Active Set actions
    item(key = "kbd_set_toolbar") {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        LcdChip(
          text = "+ EMPTY KBD SET",
          selected = true,
          testTag = "btn_new_empty_keyboard_set",
          onClick = {
            actions.createEmptyKeyboardSet(name = null, activateImmediately = true)
          },
        )
        LcdChip(
          text = "+ SAVE LIVE SET",
          selected = false,
          testTag = "btn_save_current_keyboard_set",
          onClick = { actions.saveCurrentAsKeyboardSet(name = null) },
        )
        LcdChip(
          text = "CLEAR EMPTY",
          selected = isLiveEmpty,
          testTag = "btn_clear_keyboard_set_empty",
          onClick = { actions.clearLiveKeyboardToEmpty() },
        )
        if (activeSet != null) {
          LcdChip(
            text = "UPDATE SET",
            selected = false,
            testTag = "btn_update_keyboard_set",
            onClick = { actions.updateKeyboardSetFromLive(activeSet.id) },
          )
          LcdChip(
            text = "RENAME",
            selected = showRenameBar,
            testTag = "btn_rename_keyboard_set",
            onClick = {
              renameDraft = activeSet.name
              showRenameBar = !showRenameBar
            },
          )
          LcdChip(
            text = "DUPLICATE",
            selected = false,
            testTag = "btn_duplicate_keyboard_set",
            onClick = { actions.duplicateKeyboardSet(activeSet.id) },
          )
          LcdChip(
            text = "DELETE",
            selected = false,
            testTag = "btn_delete_keyboard_set",
            onClick = { actions.deleteKeyboardSet(activeSet.id) },
          )
        }
      }
    }

    // Optional inline rename bar for selected Keyboard Set
    if (showRenameBar && activeSet != null) {
      item(key = "kbd_set_rename_bar") {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(PsrColors.LcdCardBg, RoundedCornerShape(4.dp))
            .border(1.dp, PsrColors.LcdBorder, RoundedCornerShape(4.dp))
            .padding(4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          OutlinedTextField(
            value = renameDraft,
            onValueChange = { renameDraft = it },
            singleLine = true,
            placeholder = { Text("Keyboard Set name...", fontSize = 9.sp) },
            modifier = Modifier.weight(1f).testTag("kbd_set_rename_input"),
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = PsrColors.LcdTextDark,
              unfocusedTextColor = PsrColors.LcdTextDark,
            ),
          )
          LcdChip("SAVE", true, testTag = "btn_confirm_kbd_set_rename") {
            actions.renameKeyboardSet(activeSet.id, renameDraft)
            showRenameBar = false
          }
        }
      }
    }

    // 2. Live Keyboard Layers Card (Right 1, Right 2, Left) + Performance Flags
    item(key = "kbd_set_live_layers") {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(PsrColors.LcdCardBg, RoundedCornerShape(5.dp))
          .border(1.dp, if (isLiveEmpty) PsrColors.GoldAccent else PsrColors.LcdBorder, RoundedCornerShape(5.dp))
          .padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
              Icons.Default.Layers,
              contentDescription = "Active Keyboard Set",
              tint = PsrColors.LcdHighlightBlue,
              modifier = Modifier.size(13.dp),
            )
            Text(
              text = activeSet?.name ?: if (isLiveEmpty) "Live: Empty Keyboard Set" else "Live Keyboard Setup",
              fontFamily = DisplayFont,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = PsrColors.LcdTextDark,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(3.dp))
              .background(if (isLiveEmpty) Color(0xFFFFF3CD) else PsrColors.LcdHighlightBlue.copy(alpha = 0.14f))
              .border(1.dp, if (isLiveEmpty) PsrColors.GoldAccent else PsrColors.LcdHighlightBlue, RoundedCornerShape(3.dp))
              .padding(horizontal = 5.dp, vertical = 1.dp),
          ) {
            Text(
              text = if (isLiveEmpty) "EMPTY" else "${listOf(vs.right1Voice, vs.right2Voice, vs.leftVoice).count { it != null }}/3 VOICES",
              fontFamily = MonoFont,
              fontSize = 8.sp,
              fontWeight = FontWeight.Bold,
              color = PsrColors.LcdTextDark,
            )
          }
        }

        KeyboardSetSlotRow(
          slotLabel = "R1",
          voiceName = vs.right1Voice?.name,
          enabled = vs.right1Enabled,
          onToggle = { actions.toggleVoiceSlotEnabled(ActiveVoiceSlot.RIGHT_1) },
          onPickVoice = {
            actions.setActiveVoiceSlot(ActiveVoiceSlot.RIGHT_1)
            actions.navigateTo(LcdPage.VOICE)
          },
          onClearSlot = { actions.clearKeyboardSetSlot(activeSet?.id, ActiveVoiceSlot.RIGHT_1) },
          clearTag = "btn_clear_kbd_slot_r1",
        )
        KeyboardSetSlotRow(
          slotLabel = "R2",
          voiceName = vs.right2Voice?.name,
          enabled = vs.right2Enabled,
          onToggle = { actions.toggleVoiceSlotEnabled(ActiveVoiceSlot.RIGHT_2) },
          onPickVoice = {
            actions.setActiveVoiceSlot(ActiveVoiceSlot.RIGHT_2)
            actions.navigateTo(LcdPage.VOICE)
          },
          onClearSlot = { actions.clearKeyboardSetSlot(activeSet?.id, ActiveVoiceSlot.RIGHT_2) },
          clearTag = "btn_clear_kbd_slot_r2",
        )
        KeyboardSetSlotRow(
          slotLabel = "LEFT",
          voiceName = vs.leftVoice?.name,
          enabled = vs.leftEnabled,
          onToggle = { actions.toggleVoiceSlotEnabled(ActiveVoiceSlot.LEFT) },
          onPickVoice = {
            actions.setActiveVoiceSlot(ActiveVoiceSlot.LEFT)
            actions.navigateTo(LcdPage.VOICE)
          },
          onClearSlot = { actions.clearKeyboardSetSlot(activeSet?.id, ActiveVoiceSlot.LEFT) },
          clearTag = "btn_clear_kbd_slot_left",
        )

        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "SPLIT ${midiNoteName(state.splitPointMidi)} • TR ${if (state.transpose >= 0) "+${state.transpose}" else state.transpose}",
            fontFamily = MonoFont,
            fontSize = 8.sp,
            color = PsrColors.LcdTextMuted,
          )
          LcdChip("HARM", vs.harmonyEnabled) { actions.toggleHarmony() }
          LcdChip("SUST", vs.sustainEnabled) { actions.toggleSustain() }
          LcdChip("DSP", vs.dspEnabled) { actions.toggleDsp() }
        }
      }
    }

    // 3. Saved Keyboard Sets Library Header & Items
    item(key = "kbd_set_library_header") {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("keyboard_set_library_list")
          .padding(top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "SAVED KEYBOARD SETS (${state.keyboardSets.size})",
          fontFamily = DisplayFont,
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          color = PsrColors.LcdTextDark,
        )
        Text(
          text = "Tap a set to recall or start empty",
          fontFamily = MonoFont,
          fontSize = 8.sp,
          color = PsrColors.LcdTextMuted,
        )
      }
    }

    if (state.keyboardSets.isEmpty()) {
      item(key = "kbd_set_empty_state") {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(PsrColors.LcdCardBg.copy(alpha = 0.78f), RoundedCornerShape(5.dp))
            .border(1.dp, PsrColors.LcdBorder, RoundedCornerShape(5.dp))
            .padding(10.dp)
            .testTag("keyboard_set_empty_state"),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Icon(
            Icons.Default.AddCircleOutline,
            contentDescription = "Empty Keyboard Set",
            tint = PsrColors.LcdHighlightBlue,
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.height(3.dp))
          Text(
            text = "NO SAVED KEYBOARD SETS YET",
            fontFamily = DisplayFont,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = PsrColors.LcdTextDark,
          )
          Text(
            text = "Tap + EMPTY KBD SET to create a blank Keyboard Set with unassigned R1/R2/Left slots, or + SAVE LIVE SET to store your active sounds.",
            fontFamily = BodyFont,
            fontSize = 8.5.sp,
            color = PsrColors.LcdTextMuted,
          )
        }
      }
    } else {
      items(state.keyboardSets, key = { it.id }) { setEntity ->
        val isSelected = setEntity.id == state.activeKeyboardSetId
        val isSetEmpty = setEntity.isEmpty ||
          (setEntity.right1VoiceId == null && setEntity.right2VoiceId == null && setEntity.leftVoiceId == null)
        val r1Name = setEntity.right1VoiceId?.let { id -> vs.allVoices.find { it.id == id }?.name ?: id } ?: "Empty"
        val r2Name = setEntity.right2VoiceId?.let { id -> vs.allVoices.find { it.id == id }?.name ?: id } ?: "Empty"
        val leftName = setEntity.leftVoiceId?.let { id -> vs.allVoices.find { it.id == id }?.name ?: id } ?: "Empty"

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) PsrColors.LcdHighlightBlue else PsrColors.LcdCardBg)
            .border(1.dp, if (isSelected) PsrColors.LcdTopBar else PsrColors.LcdBorder, RoundedCornerShape(4.dp))
            .clickable { actions.selectAndApplyKeyboardSet(setEntity.id) }
            .padding(horizontal = 6.dp, vertical = 5.dp)
            .testTag("keyboard_set_item_${setEntity.id}"),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(3.dp))
              .background(
                when {
                  isSelected -> Color.White.copy(alpha = 0.2f)
                  isSetEmpty -> PsrColors.GoldAccent.copy(alpha = 0.25f)
                  else -> PsrColors.LcdHighlightBlue.copy(alpha = 0.14f)
                },
              )
              .border(1.dp, if (isSetEmpty) PsrColors.GoldAccent else PsrColors.LcdHighlightBlue, RoundedCornerShape(3.dp))
              .padding(horizontal = 4.dp, vertical = 1.dp),
          ) {
            Text(
              text = if (isSetEmpty) "EMPTY" else "SET",
              fontFamily = MonoFont,
              fontSize = 7.5.sp,
              fontWeight = FontWeight.Bold,
              color = if (isSelected) Color.White else PsrColors.LcdTextDark,
            )
          }
          Spacer(modifier = Modifier.width(6.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = setEntity.name,
              fontFamily = DisplayFont,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = if (isSelected) Color.White else PsrColors.LcdTextDark,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "R1: $r1Name | R2: $r2Name | L: $leftName",
              fontFamily = MonoFont,
              fontSize = 8.sp,
              color = if (isSelected) Color(0xFFDDEEFF) else PsrColors.LcdTextMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Icon(
            Icons.Default.ClearAll,
            contentDescription = "Clear Keyboard Set to Empty",
            tint = if (isSelected) PsrColors.GoldHighlight else PsrColors.LcdHighlightBlue,
            modifier = Modifier
              .size(18.dp)
              .clickable {
                actions.selectAndApplyKeyboardSet(setEntity.id)
                actions.clearLiveKeyboardToEmpty()
              },
          )
          Spacer(modifier = Modifier.width(6.dp))
          Icon(
            Icons.Default.ContentCopy,
            contentDescription = "Duplicate Keyboard Set",
            tint = if (isSelected) Color.White else PsrColors.LcdHighlightBlue,
            modifier = Modifier
              .size(16.dp)
              .clickable { actions.duplicateKeyboardSet(setEntity.id) },
          )
          Spacer(modifier = Modifier.width(6.dp))
          Icon(
            Icons.Default.Delete,
            contentDescription = "Delete Keyboard Set",
            tint = if (isSelected) Color(0xFFFFB4B4) else PsrColors.LedRed,
            modifier = Modifier
              .size(16.dp)
              .clickable { actions.deleteKeyboardSet(setEntity.id) },
          )
        }
      }
    }
  }
}

@Composable
private fun KeyboardSetSlotRow(
  slotLabel: String,
  voiceName: String?,
  enabled: Boolean,
  onToggle: () -> Unit,
  onPickVoice: () -> Unit,
  onClearSlot: () -> Unit,
  clearTag: String,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(Color.White.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
      .border(1.dp, PsrColors.LcdBorder.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
      .padding(horizontal = 5.dp, vertical = 3.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      text = slotLabel,
      fontFamily = DisplayFont,
      fontSize = 9.sp,
      fontWeight = FontWeight.Bold,
      color = PsrColors.LcdTopBar,
      modifier = Modifier.width(32.dp),
    )
    Text(
      text = voiceName ?: "Empty (No Voice)",
      fontFamily = BodyFont,
      fontSize = 9.5.sp,
      fontWeight = if (voiceName != null) FontWeight.SemiBold else FontWeight.Normal,
      color = if (voiceName != null) PsrColors.LcdTextDark else PsrColors.LcdTextMuted,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    LcdChip(if (enabled && voiceName != null) "ON" else "OFF", enabled && voiceName != null) {
      onToggle()
    }
    LcdChip(if (voiceName == null) "ASSIGN" else "CHANGE", false) {
      onPickVoice()
    }
    if (voiceName != null) {
      LcdChip("EMPTY", false, testTag = clearTag) {
        onClearSlot()
      }
    }
  }
}
