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
    val availableTags: List<String> = emptyList(),
    val selectedTag: String? = null,
    val errorMessage: String? = null
) {
    // Turunan: sedang mencari/memfilter atau tidak (menentukan pesan kosong)
    val isFiltering: Boolean
        get() = query.isNotBlank() || selectedTag != null
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
    // Tag yang sedang dipilih sebagai filter; null = "Semua"
    private val selectedTag = MutableStateFlow<String?>(null)

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

    // UiState DITURUNKAN dari beberapa sumber (pola Chapter 6). combine menerima
    // sampai 5 flow bertipe, jadi dibagi dua kelompok: state pencarian/undo dan
    // daftar lengkap untuk menghitung tag yang tersedia.
    val uiState: StateFlow<NoteListUiState> = combine(
        combine(
            searchResults, queryText, loadError, recentlyDeleted, selectedTag
        ) { results, query, error, deleted, tag ->
            NoteListUiState(
                notes = results,
                query = query,
                recentlyDeleted = deleted,
                selectedTag = tag,
                errorMessage = error
            )
        },
        repository.getAllNotes().catch { emit(emptyList()) }
    ) { partial, allNotes ->
        val tags = allNotes.flatMap { it.tags }.distinct().sorted()
        // Tag yang tidak dipakai catatan mana pun otomatis dilepas
        val activeTag = partial.selectedTag?.takeIf { it in tags }
        partial.copy(
            notes = if (activeTag == null) partial.notes
            else partial.notes.filter { activeTag in it.tags },
            availableTags = tags,
            selectedTag = activeTag
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

    // Ketuk chip: tag yang sedang aktif melepas filter, selain itu ganti filter
    fun onTagSelected(tag: String?) {
        selectedTag.value =
            if (tag == null || tag == uiState.value.selectedTag) null else tag
    }

    // Menyematkan tidak mengubah updatedAt (bukan perubahan isi catatan)
    fun togglePin(note: NoteEntity) {
        viewModelScope.launch {
            repository.updateNote(note.copy(isPinned = !note.isPinned))
        }
    }
}
