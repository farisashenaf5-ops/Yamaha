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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.faris.psrs975.samples.GmDrumNoteNames

@Composable
fun DrumKitLcdPage(state: WorkstationUiState, actions: WorkstationActions) {
  val lib = state.drumKitState
  val selectedKit = lib.selectedKit
  val activeArrangerKitId = state.activeStyleDrumKitId

  var showRenameBar by remember { mutableStateOf(false) }
  var renameDraft by remember(selectedKit.id) { mutableStateOf(selectedKit.name) }
  var showNewKitBar by remember { mutableStateOf(false) }
  var newKitDraft by remember { mutableStateOf("My Custom Kit") }
  var addCustomNote by remember { mutableIntStateOf(39) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .testTag("custom_drum_kit_section"),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    // 1. Header Action Toolbar: IMPORT DRUM KIT (WAV/MP3/OGG/SF2), NEW KIT, SAVE, RENAME, DELETE, USE FOR STY
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      LcdChip(
        text = "IMPORT DRUM KIT",
        selected = true,
        testTag = "btn_import_drum_kit",
        onClick = { actions.requestImportDrumKitFiles() },
      )
      LcdChip(
        text = "+ NEW KIT",
        selected = showNewKitBar,
        testTag = "btn_new_drum_kit",
        onClick = { showNewKitBar = !showNewKitBar },
      )
      if (!selectedKit.isFactory) {
        LcdChip(
          text = "SAVE KIT",
          selected = false,
          testTag = "btn_save_drum_kit",
          onClick = { actions.saveCustomDrumKit(selectedKit.id) },
        )
        LcdChip(
          text = "RENAME",
          selected = showRenameBar,
          testTag = "btn_rename_drum_kit",
          onClick = {
            renameDraft = selectedKit.name
            showRenameBar = !showRenameBar
          },
        )
        LcdChip(
          text = "DELETE",
          selected = false,
          testTag = "btn_delete_drum_kit",
          onClick = { actions.deleteCustomDrumKit(selectedKit.id) },
        )
      }
      LcdChip(
        text = if (activeArrangerKitId == selectedKit.id) "★ ACTIVE IN STY" else "USE FOR STY",
        selected = activeArrangerKitId == selectedKit.id,
        testTag = "btn_use_kit_for_style",
        onClick = { actions.assignDrumKitToStyle(selectedKit.id) },
      )
    }

    if (lib.isImporting) {
      LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }

    lib.errorMessage?.let { err ->
      Text(err, fontFamily = BodyFont, fontSize = 8.5.sp, color = PsrColors.LedRed, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }

    // Optional inline New Kit / Rename Kit bar
    if (showNewKitBar) {
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
          value = newKitDraft,
          onValueChange = { newKitDraft = it },
          singleLine = true,
          placeholder = { Text("New kit name", fontSize = 9.sp) },
          modifier = Modifier.weight(1f).testTag("input_new_drum_kit_name"),
          colors = OutlinedTextFieldDefaults.colors(focusedTextColor = PsrColors.LcdTextDark, unfocusedTextColor = PsrColors.LcdTextDark),
        )
        LcdChip("CREATE", true, testTag = "btn_confirm_new_drum_kit") {
          actions.createCustomDrumKit(newKitDraft)
          showNewKitBar = false
        }
      }
    }

    if (showRenameBar && !selectedKit.isFactory) {
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
          modifier = Modifier.weight(1f).testTag("input_rename_drum_kit"),
          colors = OutlinedTextFieldDefaults.colors(focusedTextColor = PsrColors.LcdTextDark, unfocusedTextColor = PsrColors.LcdTextDark),
        )
        LcdChip("APPLY", true, testTag = "btn_confirm_rename_drum_kit") {
          actions.renameCustomDrumKit(selectedKit.id, renameDraft)
          showRenameBar = false
        }
      }
    }

    // 2. Scrollable Kit Library, Drum Pad Preview Strip & Editable MIDI Mapping Table
    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .testTag("drum_mapping_list"),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      item(key = "kit_library_strip") {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag("drum_kit_library_list"),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("KIT LIBRARY:", fontFamily = DisplayFont, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = PsrColors.LcdTextDark)
          lib.kits.forEach { kit ->
            val isEditing = kit.id == selectedKit.id
            val isPlayingInStyle = kit.id == activeArrangerKitId
            val customSamplesCount = kit.mappings.count { it.filePath != null }
            val badgeText = when {
              kit.isFactory -> "GM"
              else -> "${customSamplesCount}S"
            }
            val chipLabel = if (isPlayingInStyle) "● ${kit.name} [$badgeText]" else "${kit.name} [$badgeText]"
            LcdChip(
              text = chipLabel,
              selected = isEditing,
              testTag = "drum_kit_library_item_${kit.id}",
              onClick = { actions.selectDrumKitInEditor(kit.id) },
            )
          }
        }
      }

      item(key = "drum_pad_preview_strip") {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag("drum_pad_preview_grid"),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          selectedKit.mappings.forEach { mapping ->
            val hasCustomFile = mapping.filePath != null
            Column(
              modifier = Modifier
                .width(66.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (hasCustomFile) Color(0xFF173D6E) else PsrColors.LcdCardBg)
                .border(1.dp, if (hasCustomFile) PsrColors.GoldAccent else PsrColors.LcdBorder, RoundedCornerShape(5.dp))
                .clickable { actions.previewDrumPad(selectedKit.id, mapping.midiNote) }
                .padding(horizontal = 5.dp, vertical = 3.dp)
                .testTag("drum_pad_${mapping.slotCategory.name.lowercase()}_${mapping.midiNote}"),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                text = mapping.slotCategory.shortLabel,
                fontFamily = DisplayFont,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (hasCustomFile) PsrColors.GoldHighlight else PsrColors.LcdTextDark,
                maxLines = 1,
              )
              Text(
                text = "MIDI ${mapping.midiNote}",
                fontFamily = MonoFont,
                fontSize = 7.5.sp,
                color = if (hasCustomFile) Color.White else PsrColors.LcdTextMuted,
                maxLines = 1,
              )
              Text(
                text = mapping.sourceFormat,
                fontFamily = MonoFont,
                fontSize = 7.sp,
                color = if (hasCustomFile) PsrColors.LedCyan else PsrColors.LcdTextMuted,
                maxLines = 1,
              )
            }
          }
        }
      }

      items(selectedKit.mappings, key = { it.id }) { mapping ->
        val hasCustom = mapping.filePath != null
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(PsrColors.LcdCardBg, RoundedCornerShape(4.dp))
            .border(1.dp, if (hasCustom) PsrColors.LcdHighlightBlue else PsrColors.LcdBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          // Test Sound Button
          Box(
            modifier = Modifier
              .size(26.dp)
              .clip(RoundedCornerShape(4.dp))
              .background(PsrColors.LcdHighlightBlue)
              .clickable { actions.previewDrumPad(selectedKit.id, mapping.midiNote) }
              .testTag("btn_preview_mapping_${mapping.id}"),
            contentAlignment = Alignment.Center,
          ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Test ${mapping.name}", tint = Color.White, modifier = Modifier.size(16.dp))
          }

          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "${mapping.slotCategory.label}: ${mapping.name}",
              fontFamily = DisplayFont,
              fontSize = 9.5.sp,
              fontWeight = FontWeight.Bold,
              color = PsrColors.LcdTextDark,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "${mapping.sourceFormat} • MIDI ${mapping.midiNote} (${GmDrumNoteNames.labelForMidiNote(mapping.midiNote)})",
              fontFamily = MonoFont,
              fontSize = 7.5.sp,
              color = if (hasCustom) PsrColors.LcdHighlightBlue else PsrColors.LcdTextMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          // Quick GM Target Note Dropdown + Editable MIDI Note Stepper (- / +)
          var showNotePicker by remember(mapping.id) { mutableStateOf(false) }
          Box {
            LcdChip(
              text = "${mapping.midiNote}▾",
              selected = showNotePicker,
              testTag = "btn_midi_picker_${mapping.id}",
            ) { showNotePicker = true }
            DropdownMenu(
              expanded = showNotePicker,
              onDismissRequest = { showNotePicker = false },
            ) {
              GmDrumNoteNames.quickAssignTargets().forEach { (targetNote, targetLabel) ->
                DropdownMenuItem(
                  text = { Text(targetLabel, fontFamily = MonoFont, fontSize = 11.sp) },
                  onClick = {
                    showNotePicker = false
                    actions.updateDrumMappingMidiNote(selectedKit.id, mapping.id, targetNote)
                  },
                )
              }
            }
          }
          LcdChip("-", false, testTag = "btn_midi_minus_${mapping.id}") {
            actions.updateDrumMappingMidiNote(selectedKit.id, mapping.id, mapping.midiNote - 1)
          }
          LcdChip("+", false, testTag = "btn_midi_plus_${mapping.id}") {
            actions.updateDrumMappingMidiNote(selectedKit.id, mapping.id, mapping.midiNote + 1)
          }

          val gainPct = (mapping.gain * 100f).toInt()
          LcdChip(
            text = "${gainPct}%",
            selected = mapping.gain > 1.05f,
            testTag = "btn_gain_${mapping.id}",
          ) {
            val nextGain = when {
              mapping.gain < 0.85f -> 1.0f
              mapping.gain < 1.15f -> 1.25f
              mapping.gain < 1.4f -> 1.5f
              else -> 0.75f
            }
            actions.updateDrumMappingGain(selectedKit.id, mapping.id, nextGain)
          }

          // Load WAV / MP3 / OGG / SF2 into this slot
          LcdChip(
            text = "LOAD",
            selected = hasCustom,
            testTag = "btn_load_slot_${mapping.slotCategory.name.lowercase()}",
            onClick = { actions.requestImportDrumSampleForSlot(selectedKit.id, mapping.id) },
          )

          if (!selectedKit.isFactory && selectedKit.mappings.size > 1) {
            Icon(
              Icons.Default.Delete,
              contentDescription = "Remove Mapping",
              tint = PsrColors.LedRed,
              modifier = Modifier
                .size(16.dp)
                .clickable { actions.removeCustomDrumMappingSlot(selectedKit.id, mapping.id) },
            )
          }
        }
      }

      // Add extra custom MIDI drum mapping note row
      item(key = "add_custom_note_row") {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(PsrColors.LcdCardBg.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
            .border(1.dp, PsrColors.LcdBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Text("ADD NOTE:", fontFamily = DisplayFont, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = PsrColors.LcdTextDark)
          LcdChip("-", false) { addCustomNote = (addCustomNote - 1).coerceIn(0, 127) }
          Text("$addCustomNote (${GmDrumNoteNames.labelForMidiNote(addCustomNote)})", fontFamily = MonoFont, fontSize = 8.sp, color = PsrColors.LcdTextDark, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
          LcdChip("+", false) { addCustomNote = (addCustomNote + 1).coerceIn(0, 127) }
          LcdChip("+ ADD PAD", true, testTag = "btn_add_drum_pad") {
            actions.addCustomDrumMappingSlot(selectedKit.id, addCustomNote)
          }
        }
      }
    }
  }
}
