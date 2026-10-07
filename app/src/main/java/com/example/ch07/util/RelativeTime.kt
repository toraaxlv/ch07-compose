package com.example.ch07.util

// formatRelativeTime(now, then) mengubah selisih waktu menjadi teks:
//   < 1 menit  -> "baru saja"        < 1 hari   -> "N jam lalu"
//   < 1 jam    -> "N menit lalu"     < 30 hari  -> "N hari lalu"
//   < 365 hari -> "N bulan lalu" (1 bulan = 30 hari)   selebihnya -> "N tahun lalu"
// Selisih negatif (then di masa depan) dianggap 0.
fun formatRelativeTime(now: Long, then: Long): String {
    val diff = (now - then).coerceAtLeast(0L)
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        diff < minute -> "baru saja"
        diff < hour -> "${diff / minute} menit lalu"
        diff < day -> "${diff / hour} jam lalu"
        diff < 30 * day -> "${diff / day} hari lalu"
        diff < 365 * day -> "${diff / (30 * day)} bulan lalu"
        else -> "${diff / (365 * day)} tahun lalu"
    }
}
