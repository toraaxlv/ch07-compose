package com.example.ch07.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Satu objek = satu baris tabel "notes".
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    // Tag sudah dinormalkan (huruf kecil, tanpa duplikat) oleh parseTags()
    val tags: List<String> = emptyList()
    // TODO [T3.1] Tambahkan kolom `updatedAt: Long = createdAt` (waktu terakhir diubah).
    //   Jangan lupa naikkan versi database dan buat Migration-nya (T3.2).
)
