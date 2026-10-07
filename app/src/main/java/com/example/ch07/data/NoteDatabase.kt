package com.example.ch07.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// 1 -> 2: kolom tags (T4.3). '[]' = daftar tag kosong, supaya catatan lama
// tetap terbaca.
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'")
    }
}

// TODO [T3.2] Buat Migration berikutnya untuk kolom `updatedAt`
//   (INTEGER NOT NULL DEFAULT 0), lalu isi catatan lama dengan
//   UPDATE notes SET updatedAt = createdAt  agar tidak tampil "puluhan tahun lalu".

@Database(
    entities = [NoteEntity::class],
    version = 2,                 // TODO [T3.2] naikkan lagi setiap skema berubah
    exportSchema = true          // JSON skema -> app/schemas/ (lihat build.gradle.kts)
)
@TypeConverters(Converters::class)
abstract class NoteDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao

    companion object {
        // @Volatile: INSTANCE selalu dibaca dari memori utama, bukan cache thread
        @Volatile
        private var INSTANCE: NoteDatabase? = null

        // synchronized: mencegah dua thread membuat instance bersamaan
        fun getInstance(context: Context): NoteDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NoteDatabase::class.java,
                    "note_database"
                )
                    // fallbackToDestructiveMigration MENGHAPUS data pengguna saat
                    // versi naik, jadi pakai migrasi eksplisit
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
