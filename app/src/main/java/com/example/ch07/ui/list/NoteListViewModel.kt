package com.example.ch07.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ch07.data.NoteEntity
import com.example.ch07.data.NoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NoteListUiState(
    val isLoading: Boolean = false,
    val notes: List<NoteEntity> = emptyList(),
    val query: String = "",
    val recentlyDeleted: NoteEntity? = null,
    // TODO [T4.6] Tambahkan `availableTags: List<String> = emptyList()` dan
    //   `selectedTag: String? = null` untuk filter chip.
    val errorMessage: String? = null
) {
    // Turunan: sedang mencari/memfilter atau tidak (menentukan pesan kosong).
    // TODO [T4.6] Setelah ada selectedTag, sertakan juga `|| selectedTag != null`.
    val isFiltering: Boolean
        get() = query.isNotBlank()
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class NoteListViewModel(
    private val repository: NoteRepository
) : ViewModel() {

    // Teks yang sedang diketik (langsung tampil di TextField)
    private val queryText = MutableStateFlow("")
    private val loadError = MutableStateFlow<String?>(null)
    // Catatan yang baru dihapus, ditahan dulu supaya bisa dikembalikan (undo)
    private val recentlyDeleted = MutableStateFlow<NoteEntity?>(null)
    // TODO [T4.6] Tambahkan `selectedTag = MutableStateFlow<String?>(null)`.

    // Query ke database ditunda 300 ms setelah pengguna berhenti mengetik
    // (debounce), dan hanya query TERBARU yang diamati (flatMapLatest).
    // Query kosong tidak ditunda: daftar penuh tampil segera.
    private val searchResults: Flow<List<NoteEntity>> = queryText
        .debounce { query -> if (query.isBlank()) 0L else 300L }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isBlank()) repository.getAllNotes()
            else repository.searchNotes(query)
        }
        .catch { e ->
            loadError.value = e.message ?: "Gagal memuat catatan"
            emit(emptyList())
        }

    // UiState DITURUNKAN dari beberapa sumber (pola Chapter 6).
    // TODO [T4.6] Tambahkan selectedTag dan `repository.getAllNotes()` untuk daftar
    //   tag ke combine ini. combine menerima sampai 5 flow bertipe; gabungkan dua
    //   flow dulu jika perlu.
    val uiState: StateFlow<NoteListUiState> = combine(
        searchResults, queryText, loadError, recentlyDeleted
    ) { results, query, error, deleted ->
        NoteListUiState(
            notes = results,
            query = query,
            recentlyDeleted = deleted,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NoteListUiState(isLoading = true)
    )

    fun onQueryChange(newQuery: String) {
        queryText.value = newQuery
    }

    // Catatan disimpan dulu ke recentlyDeleted sebelum Snackbar ditampilkan
    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.deleteNote(note)
            recentlyDeleted.value = note
        }
    }

    // Kembalikan catatan yang SAMA: id sama -> REPLACE di DAO, jadi id, pin,
    // dan tag ikut kembali seperti semula.
    fun undoDelete() {
        val note = recentlyDeleted.value ?: return
        recentlyDeleted.value = null
        viewModelScope.launch { repository.insertNote(note) }
    }

    // Snackbar hilang sendiri tanpa dibatalkan: biarkan catatan tetap terhapus
    fun onUndoDismissed() {
        recentlyDeleted.value = null
    }

    // TODO [T4.6] Tambahkan `onTagSelected(tag: String?)`: ketuk tag yang sedang aktif
    //   melepas filter; null = "Semua". Tag yang sudah tidak dipakai catatan mana
    //   pun harus otomatis dilepas.

    // Menyematkan tidak mengubah updatedAt (bukan perubahan isi catatan)
    fun togglePin(note: NoteEntity) {
        viewModelScope.launch {
            repository.updateNote(note.copy(isPinned = !note.isPinned))
        }
    }
}
