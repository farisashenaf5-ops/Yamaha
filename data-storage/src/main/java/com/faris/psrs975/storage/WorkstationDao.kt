package com.faris.psrs975.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkstationDao {
  @Query("SELECT * FROM favorite_voices ORDER BY addedAtMs DESC")
  fun observeFavorites(): Flow<List<FavoriteVoiceEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertFavorite(entity: FavoriteVoiceEntity)

  @Query("DELETE FROM favorite_voices WHERE voiceId = :voiceId")
  suspend fun deleteFavorite(voiceId: String)

  @Query("SELECT * FROM registration_memories WHERE bankIndex = :bankIndex ORDER BY slotIndex ASC")
  fun observeRegistrationsByBank(bankIndex: Int): Flow<List<RegistrationMemoryEntity>>

  @Query("SELECT * FROM registration_memories ORDER BY bankIndex ASC, slotIndex ASC")
  suspend fun getAllRegistrations(): List<RegistrationMemoryEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertRegistration(entity: RegistrationMemoryEntity)

  @Query("DELETE FROM registration_memories WHERE bankIndex = :bankIndex AND slotIndex = :slotIndex")
  suspend fun deleteRegistration(bankIndex: Int, slotIndex: Int)

  @Query("SELECT * FROM expansion_packs ORDER BY importedAtMs DESC")
  fun observeExpansionPacks(): Flow<List<ExpansionPackEntity>>

  @Query("SELECT * FROM expansion_packs WHERE sha256 = :sha256 LIMIT 1")
  suspend fun findExpansionByHash(sha256: String): ExpansionPackEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertExpansionPack(entity: ExpansionPackEntity)

  @Query("UPDATE expansion_packs SET libraryGroup = :group WHERE id = :id")
  suspend fun updateExpansionGroup(id: String, group: String)

  @Query("DELETE FROM expansion_packs WHERE id = :id")
  suspend fun deleteExpansionPack(id: String)

  @Query("SELECT * FROM user_voices ORDER BY createdAtMs DESC")
  fun observeUserVoices(): Flow<List<UserVoiceEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertUserVoice(entity: UserVoiceEntity)

  @Query("DELETE FROM user_voices WHERE id = :id")
  suspend fun deleteUserVoice(id: String)

  @Query("SELECT * FROM custom_drum_kits ORDER BY updatedAtMs DESC")
  fun observeCustomDrumKits(): Flow<List<CustomDrumKitEntity>>

  @Query("SELECT * FROM custom_drum_kits ORDER BY updatedAtMs DESC")
  suspend fun getAllCustomDrumKits(): List<CustomDrumKitEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertCustomDrumKit(entity: CustomDrumKitEntity)

  @Query("DELETE FROM custom_drum_kits WHERE id = :id")
  suspend fun deleteCustomDrumKit(id: String)

  @Query("SELECT * FROM keyboard_sets ORDER BY updatedAtMs DESC")
  fun observeKeyboardSets(): Flow<List<KeyboardSetEntity>>

  @Query("SELECT * FROM keyboard_sets ORDER BY updatedAtMs DESC")
  suspend fun getAllKeyboardSets(): List<KeyboardSetEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertKeyboardSet(entity: KeyboardSetEntity)

  @Query("DELETE FROM keyboard_sets WHERE id = :id")
  suspend fun deleteKeyboardSet(id: String)
}
