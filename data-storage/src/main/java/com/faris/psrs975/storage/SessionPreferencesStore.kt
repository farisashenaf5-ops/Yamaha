package com.faris.psrs975.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.workstationDataStore: DataStore<Preferences> by preferencesDataStore(name = "psr_s975_session")

data class WorkstationSessionSnapshot(
  val masterVolume: Float = 0.85f,
  val balance: Float = 0.0f,
  val sampleRate: Int = 44100,
  val keySizeScale: Float = 1.0f,
  val octaveShift: Int = 0,
  val transpose: Int = 0,
  val splitPointMidi: Int = 54,
  val chordFingeringMode: String = "FINGERED",
  val right1VoiceId: String = "piano_grand",
  val right2VoiceId: String = "syn_pad",
  val leftVoiceId: String = "str_ensemble",
  val right1Enabled: Boolean = true,
  val right2Enabled: Boolean = false,
  val leftEnabled: Boolean = true,
  val styleId: String = "style_concert_grand_pop",
  val tempoBpm: Int = 120,
  val acmpEnabled: Boolean = true,
  val autoFillEnabled: Boolean = true,
  val activeRegBank: Int = 0,
  val activeDrumKitId: String = "factory_standard_drum_kit",
  val styleDrumKitBindingsJson: String = "{}",
  val activeKeyboardSetId: String = "",
)

class SessionPreferencesStore(private val context: Context) {
  private object Keys {
    val MASTER_VOL = floatPreferencesKey("master_vol")
    val BALANCE = floatPreferencesKey("balance")
    val SAMPLE_RATE = intPreferencesKey("sample_rate")
    val KEY_SIZE_SCALE = floatPreferencesKey("key_size_scale")
    val OCTAVE_SHIFT = intPreferencesKey("octave_shift")
    val TRANSPOSE = intPreferencesKey("transpose")
    val SPLIT_POINT = intPreferencesKey("split_point")
    val FINGERING_MODE = stringPreferencesKey("fingering_mode")
    val R1_ID = stringPreferencesKey("r1_id")
    val R2_ID = stringPreferencesKey("r2_id")
    val LEFT_ID = stringPreferencesKey("left_id")
    val R1_ON = booleanPreferencesKey("r1_on")
    val R2_ON = booleanPreferencesKey("r2_on")
    val LEFT_ON = booleanPreferencesKey("left_on")
    val STYLE_ID = stringPreferencesKey("style_id")
    val TEMPO_BPM = intPreferencesKey("tempo_bpm")
    val ACMP_ON = booleanPreferencesKey("acmp_on")
    val AUTO_FILL_ON = booleanPreferencesKey("auto_fill_on")
    val REG_BANK = intPreferencesKey("reg_bank")
    val ACTIVE_DRUM_KIT_ID = stringPreferencesKey("active_drum_kit_id")
    val STYLE_DRUM_KIT_BINDINGS = stringPreferencesKey("style_drum_kit_bindings")
    val ACTIVE_KEYBOARD_SET_ID = stringPreferencesKey("active_keyboard_set_id")
  }

  val sessionFlow: Flow<WorkstationSessionSnapshot> = context.workstationDataStore.data.map { p ->
    WorkstationSessionSnapshot(
      masterVolume = p[Keys.MASTER_VOL] ?: 0.85f,
      balance = p[Keys.BALANCE] ?: 0f,
      sampleRate = p[Keys.SAMPLE_RATE] ?: 44100,
      keySizeScale = p[Keys.KEY_SIZE_SCALE] ?: 1.0f,
      octaveShift = p[Keys.OCTAVE_SHIFT] ?: 0,
      transpose = p[Keys.TRANSPOSE] ?: 0,
      splitPointMidi = p[Keys.SPLIT_POINT] ?: 54,
      chordFingeringMode = p[Keys.FINGERING_MODE] ?: "FINGERED",
      right1VoiceId = p[Keys.R1_ID] ?: "piano_grand",
      right2VoiceId = p[Keys.R2_ID] ?: "syn_pad",
      leftVoiceId = p[Keys.LEFT_ID] ?: "str_ensemble",
      right1Enabled = p[Keys.R1_ON] ?: true,
      right2Enabled = p[Keys.R2_ON] ?: false,
      leftEnabled = p[Keys.LEFT_ON] ?: true,
      styleId = p[Keys.STYLE_ID] ?: "style_concert_grand_pop",
      tempoBpm = p[Keys.TEMPO_BPM] ?: 120,
      acmpEnabled = p[Keys.ACMP_ON] ?: true,
      autoFillEnabled = p[Keys.AUTO_FILL_ON] ?: true,
      activeRegBank = p[Keys.REG_BANK] ?: 0,
      activeDrumKitId = p[Keys.ACTIVE_DRUM_KIT_ID] ?: "factory_standard_drum_kit",
      styleDrumKitBindingsJson = p[Keys.STYLE_DRUM_KIT_BINDINGS] ?: "{}",
      activeKeyboardSetId = p[Keys.ACTIVE_KEYBOARD_SET_ID] ?: "",
    )
  }

  suspend fun loadSnapshot(): WorkstationSessionSnapshot = sessionFlow.first()

  suspend fun saveSnapshot(snap: WorkstationSessionSnapshot) {
    context.workstationDataStore.edit { p ->
      p[Keys.MASTER_VOL] = snap.masterVolume
      p[Keys.BALANCE] = snap.balance
      p[Keys.SAMPLE_RATE] = snap.sampleRate
      p[Keys.KEY_SIZE_SCALE] = snap.keySizeScale
      p[Keys.OCTAVE_SHIFT] = snap.octaveShift
      p[Keys.TRANSPOSE] = snap.transpose
      p[Keys.SPLIT_POINT] = snap.splitPointMidi
      p[Keys.FINGERING_MODE] = snap.chordFingeringMode
      p[Keys.R1_ID] = snap.right1VoiceId
      p[Keys.R2_ID] = snap.right2VoiceId
      p[Keys.LEFT_ID] = snap.leftVoiceId
      p[Keys.R1_ON] = snap.right1Enabled
      p[Keys.R2_ON] = snap.right2Enabled
      p[Keys.LEFT_ON] = snap.leftEnabled
      p[Keys.STYLE_ID] = snap.styleId
      p[Keys.TEMPO_BPM] = snap.tempoBpm
      p[Keys.ACMP_ON] = snap.acmpEnabled
      p[Keys.AUTO_FILL_ON] = snap.autoFillEnabled
      p[Keys.REG_BANK] = snap.activeRegBank
      p[Keys.ACTIVE_DRUM_KIT_ID] = snap.activeDrumKitId
      p[Keys.STYLE_DRUM_KIT_BINDINGS] = snap.styleDrumKitBindingsJson
      p[Keys.ACTIVE_KEYBOARD_SET_ID] = snap.activeKeyboardSetId
    }
  }
}
