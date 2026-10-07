package com.example.ch07.util

// parseTags("Kuliah, #Ide,  kuliah ,,") -> ["kuliah", "ide"]
// Dipisah koma; spasi dan awalan # dibuang; huruf kecil; tanpa duplikat;
// elemen kosong dibuang.
fun parseTags(text: String): List<String> =
    text.split(',')
        .map { it.trim().removePrefix("#").trim().lowercase() }
        .filter { it.isNotEmpty() }
        .distinct()

// formatTags(["kuliah", "ide"]) -> "kuliah, ide" (untuk mengisi ulang form)
fun formatTags(tags: List<String>): String = tags.joinToString(", ")
