package com.example.ch07.ui.list

import com.example.ch07.data.NoteEntity
import com.example.ch07.testutil.FakeNoteRepository
import com.example.ch07.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// Tes perilaku DASAR (sudah hijau sejak awal). Pakai argumen bernama saja,
// supaya tetap valid setelah kamu menambah kolom baru di NoteEntity.
// Saat mengerjakan Tugas 2 dan 4, tambahkan tes serupa untuk undo hapus dan
// filter tag (contohnya ada di praktikum/ch07).
@OptIn(ExperimentalCoroutinesApi::class)
class NoteListViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private fun note(id: Int, title: String, content: String = "", pinned: Boolean = false) =
        NoteEntity(id = id, title = title, content = content, createdAt = id.toLong(), isPinned = pinned)

    // uiState memakai stateIn(WhileSubscribed): harus ada yang mengumpulkan
    private fun TestScope.start(vm: NoteListViewModel) {
        backgroundScope.launch { vm.uiState.collect { } }
        runCurrent()
    }

    @Test fun `catatan yang disematkan tampil paling atas`() = runTest {
        val vm = NoteListViewModel(FakeNoteRepository(listOf(note(1, "A"), note(2, "B", pinned = true))))
        start(vm)

        assertEquals(listOf("B", "A"), vm.uiState.value.notes.map { it.title })
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test fun `pencarian ditunda 300 ms lalu memfilter judul atau isi`() = runTest {
        val vm = NoteListViewModel(FakeNoteRepository(listOf(
            note(1, "Belanja", "susu"), note(2, "Kuliah", "bab Room"), note(3, "Ide", "aplikasi")
        )))
        start(vm)

        vm.onQueryChange("room")
        runCurrent()
        assertEquals("teks langsung tampil di TextField", "room", vm.uiState.value.query)
        advanceTimeBy(299); runCurrent()
        assertEquals("belum difilter sebelum 300 ms", 3, vm.uiState.value.notes.size)

        advanceTimeBy(2); runCurrent()
        assertEquals(listOf("Kuliah"), vm.uiState.value.notes.map { it.title })
        assertTrue(vm.uiState.value.isFiltering)

        vm.onQueryChange("")           // kosong = tanpa jeda, semua tampil lagi
        runCurrent()
        assertEquals(3, vm.uiState.value.notes.size)
    }

    @Test fun `hapus menghilangkan catatan dari daftar`() = runTest {
        val target = note(1, "A")
        val repo = FakeNoteRepository(listOf(target, note(2, "B")))
        val vm = NoteListViewModel(repo)
        start(vm)

        vm.deleteNote(target); runCurrent()

        assertEquals(listOf("B"), vm.uiState.value.notes.map { it.title })
    }

    @Test fun `togglePin menyematkan lalu melepas`() = runTest {
        val n = note(1, "A")
        val repo = FakeNoteRepository(listOf(n))
        val vm = NoteListViewModel(repo)
        start(vm)

        vm.togglePin(n); runCurrent()
        assertTrue(repo.current.single().isPinned)

        vm.togglePin(repo.current.single()); runCurrent()
        assertFalse(repo.current.single().isPinned)
    }

    @Test fun `hapus menyimpan catatan yang baru dihapus untuk undo`() = runTest {
        val target = note(1, "A")
        val vm = NoteListViewModel(FakeNoteRepository(listOf(target)))
        start(vm)

        vm.deleteNote(target); runCurrent()

        assertEquals(target, vm.uiState.value.recentlyDeleted)
    }

    @Test fun `undo delete mengembalikan catatan yang sama`() = runTest {
        val target = note(1, "A", "isi", pinned = true)
        val repo = FakeNoteRepository(listOf(target, note(2, "B")))
        val vm = NoteListViewModel(repo)
        start(vm)

        vm.deleteNote(target); runCurrent()
        vm.undoDelete(); runCurrent()

        assertNull(vm.uiState.value.recentlyDeleted)
        assertEquals("id, pin, dan isi harus kembali persis", target, repo.current.single { it.id == 1 })
    }

    @Test fun `snackbar diabaikan - catatan tetap terhapus`() = runTest {
        val target = note(1, "A")
        val repo = FakeNoteRepository(listOf(target))
        val vm = NoteListViewModel(repo)
        start(vm)

        vm.deleteNote(target); runCurrent()
        vm.onUndoDismissed(); runCurrent()

        assertNull(vm.uiState.value.recentlyDeleted)
        assertTrue(repo.current.isEmpty())
    }

    private fun tagged(id: Int, title: String, tags: List<String>) =
        NoteEntity(id = id, title = title, content = "", createdAt = id.toLong(), tags = tags)

    @Test fun `filter tag memilih lalu melepas lagi`() = runTest {
        val repo = FakeNoteRepository(listOf(
            tagged(1, "A", listOf("kuliah")), tagged(2, "B", listOf("ide")), tagged(3, "C", emptyList())
        ))
        val vm = NoteListViewModel(repo)
        start(vm)

        assertEquals(listOf("ide", "kuliah"), vm.uiState.value.availableTags)
        assertFalse(vm.uiState.value.isFiltering)

        vm.onTagSelected("kuliah"); runCurrent()
        assertEquals(listOf("A"), vm.uiState.value.notes.map { it.title })
        assertTrue(vm.uiState.value.isFiltering)

        vm.onTagSelected("kuliah"); runCurrent()   // ketuk tag aktif = lepas filter
        assertNull(vm.uiState.value.selectedTag)
        assertEquals(3, vm.uiState.value.notes.size)
    }

    @Test fun `filter tag bekerja bersama pencarian`() = runTest {
        val repo = FakeNoteRepository(listOf(
            tagged(1, "Bab Room", listOf("kuliah")), tagged(2, "Kelas", listOf("kuliah")),
            tagged(3, "Ide", listOf("ide"))
        ))
        val vm = NoteListViewModel(repo)
        start(vm)

        vm.onTagSelected("kuliah")
        vm.onQueryChange("room")
        advanceTimeBy(310); runCurrent()

        assertEquals(listOf("Bab Room"), vm.uiState.value.notes.map { it.title })
    }

    @Test fun `tag yang tidak dipakai lagi otomatis dilepas`() = runTest {
        val a = tagged(1, "A", listOf("kuliah"))
        val repo = FakeNoteRepository(listOf(a))
        val vm = NoteListViewModel(repo)
        start(vm)

        vm.onTagSelected("kuliah"); runCurrent()
        vm.deleteNote(a); runCurrent()

        assertTrue(vm.uiState.value.availableTags.isEmpty())
        assertNull(vm.uiState.value.selectedTag)
        assertFalse(vm.uiState.value.isFiltering)
    }

    @Test fun `menyematkan tidak mengubah updatedAt`() = runTest {
        val n = NoteEntity(id = 1, title = "A", content = "", createdAt = 1_000L, updatedAt = 2_000L)
        val repo = FakeNoteRepository(listOf(n))
        val vm = NoteListViewModel(repo)
        start(vm)

        vm.togglePin(n); runCurrent()
        assertTrue(repo.current.single().isPinned)
        assertEquals(2_000L, repo.current.single().updatedAt)
    }
}
