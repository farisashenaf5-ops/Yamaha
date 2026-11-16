package com.faris.psrs975.storage

import kotlinx.coroutines.flow.Flow

class WorkstationRepository(
  private val dao: WorkstationDao,
  private val preferencesStore: SessionPreferencesStore,
) {
  val favorites: Flow<List<FavoriteVoiceEntity>> = dao.observeFavorites()
  val expansionPacks: Flow<List<ExpansionPackEntity>> = dao.observeExpansionPacks()
  val userVoices: Flow<List<UserVoiceEntity>> = dao.observeUserVoices()
  val customDrumKits: Flow<List<CustomDrumKitEntity>> = dao.observeCustomDrumKits()
  val keyboardSets: Flow<List<KeyboardSetEntity>> = dao.observeKeyboardSets()
  val sessionFlow: Flow<WorkstationSessionSnapshot> = preferencesStore.sessionFlow

  fun observeRegistrations(bankIndex: Int): Flow<List<RegistrationMemoryEntity>> =
    dao.observeRegistrationsByBank(bankIndex)

  suspend fun getAllRegistrations(): List<RegistrationMemoryEntity> = dao.getAllRegistrations()

  suspend fun saveRegistration(entity: RegistrationMemoryEntity) = dao.upsertRegistration(entity)

  suspend fun deleteRegistration(bankIndex: Int, slotIndex: Int) =
    dao.deleteRegistration(bankIndex, slotIndex)

  suspend fun setFavorite(voiceId: String, isFav: Boolean) {
    if (isFav) dao.insertFavorite(FavoriteVoiceEntity(voiceId)) else dao.deleteFavorite(voiceId)
  }

  suspend fun findExpansionByHash(sha256: String): ExpansionPackEntity? = dao.findExpansionByHash(sha256)

  suspend fun insertExpansionPack(entity: ExpansionPackEntity) = dao.insertExpansionPack(entity)

  suspend fun updateExpansionGroup(id: String, group: String) = dao.updateExpansionGroup(id, group)

  suspend fun deleteExpansionPack(id: String) = dao.deleteExpansionPack(id)

  suspend fun insertUserVoice(entity: UserVoiceEntity) = dao.insertUserVoice(entity)

  suspend fun deleteUserVoice(id: String) = dao.deleteUserVoice(id)

  suspend fun getAllCustomDrumKits(): List<CustomDrumKitEntity> = dao.getAllCustomDrumKits()

  suspend fun upsertCustomDrumKit(entity: CustomDrumKitEntity) = dao.upsertCustomDrumKit(entity)

  suspend fun deleteCustomDrumKit(id: String) = dao.deleteCustomDrumKit(id)

  suspend fun getAllKeyboardSets(): List<KeyboardSetEntity> = dao.getAllKeyboardSets()

  suspend fun upsertKeyboardSet(entity: KeyboardSetEntity) = dao.upsertKeyboardSet(entity)

  suspend fun deleteKeyboardSet(id: String) = dao.deleteKeyboardSet(id)

  suspend fun loadSession(): WorkstationSessionSnapshot = preferencesStore.loadSnapshot()

  suspend fun saveSession(snapshot: WorkstationSessionSnapshot) = preferencesStore.saveSnapshot(snapshot)
}
