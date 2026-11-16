package com.faris.psrs975.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_voices")
data class FavoriteVoiceEntity(
  @PrimaryKey val voiceId: String,
  val addedAtMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "registration_memories", primaryKeys = ["bankIndex", "slotIndex"])
data class RegistrationMemoryEntity(
  val bankIndex: Int,
  val slotIndex: Int, // 1..8
  val label: String,
  val right1VoiceId: String,
  val right2VoiceId: String?,
  val leftVoiceId: String,
  val right1Enabled: Boolean,
  val right2Enabled: Boolean,
  val leftEnabled: Boolean,
  val styleId: String,
  val tempoBpm: Int,
  val transpose: Int,
  val splitPointMidi: Int,
  val mixerJson: String,
  val effectsJson: String,
  val multiPadBankIndex: Int,
  val freezeMask: Int = 0,
  val updatedAtMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "expansion_packs")
data class ExpansionPackEntity(
  @PrimaryKey val id: String,
  val name: String,
  val format: String, // SF2, SFZ, WAV, MIDI, ZIP
  val sha256: String,
  val filePath: String,
  val sizeBytes: Long,
  val libraryGroup: String,
  val presetCount: Int,
  val importedAtMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "user_voices")
data class UserVoiceEntity(
  @PrimaryKey val id: String,
  val name: String,
  val basePatchId: String,
  val attackScale: Float,
  val releaseScale: Float,
  val brightnessScale: Float,
  val octaveOffset: Int,
  val createdAtMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "custom_drum_kits")
data class CustomDrumKitEntity(
  @PrimaryKey val id: String,
  val name: String,
  val sourceFormat: String,
  val mappingsJson: String,
  val createdAtMs: Long = System.currentTimeMillis(),
  val updatedAtMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "keyboard_sets")
data class KeyboardSetEntity(
  @PrimaryKey val id: String,
  val name: String,
  val isEmpty: Boolean = true,
  val right1VoiceId: String? = null,
  val right2VoiceId: String? = null,
  val leftVoiceId: String? = null,
  val right1Enabled: Boolean = false,
  val right2Enabled: Boolean = false,
  val leftEnabled: Boolean = false,
  val right1Octave: Int = 0,
  val right2Octave: Int = 0,
  val leftOctave: Int = 0,
  val transpose: Int = 0,
  val splitPointMidi: Int = 54,
  val harmonyEnabled: Boolean = false,
  val sustainEnabled: Boolean = false,
  val dspEnabled: Boolean = false,
  val createdAtMs: Long = System.currentTimeMillis(),
  val updatedAtMs: Long = System.currentTimeMillis(),
)


