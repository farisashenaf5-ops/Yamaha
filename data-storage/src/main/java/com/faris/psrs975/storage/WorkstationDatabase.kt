package com.faris.psrs975.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities = [
    FavoriteVoiceEntity::class,
    RegistrationMemoryEntity::class,
    ExpansionPackEntity::class,
    UserVoiceEntity::class,
    CustomDrumKitEntity::class,
    KeyboardSetEntity::class,
  ],
  version = 3,
  exportSchema = false,
)
abstract class WorkstationDatabase : RoomDatabase() {
  abstract fun workstationDao(): WorkstationDao

  companion object {
    @Volatile private var instance: WorkstationDatabase? = null

    val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `custom_drum_kits` (
            `id` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            `sourceFormat` TEXT NOT NULL,
            `mappingsJson` TEXT NOT NULL,
            `createdAtMs` INTEGER NOT NULL,
            `updatedAtMs` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
          )
          """.trimIndent(),
        )
      }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `keyboard_sets` (
            `id` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            `isEmpty` INTEGER NOT NULL,
            `right1VoiceId` TEXT,
            `right2VoiceId` TEXT,
            `leftVoiceId` TEXT,
            `right1Enabled` INTEGER NOT NULL,
            `right2Enabled` INTEGER NOT NULL,
            `leftEnabled` INTEGER NOT NULL,
            `right1Octave` INTEGER NOT NULL,
            `right2Octave` INTEGER NOT NULL,
            `leftOctave` INTEGER NOT NULL,
            `transpose` INTEGER NOT NULL,
            `splitPointMidi` INTEGER NOT NULL,
            `harmonyEnabled` INTEGER NOT NULL,
            `sustainEnabled` INTEGER NOT NULL,
            `dspEnabled` INTEGER NOT NULL,
            `createdAtMs` INTEGER NOT NULL,
            `updatedAtMs` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
          )
          """.trimIndent(),
        )
      }
    }

    fun getInstance(context: Context): WorkstationDatabase =
      instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
          context.applicationContext,
          WorkstationDatabase::class.java,
          "faris_psr_s975.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
      }
  }
}
