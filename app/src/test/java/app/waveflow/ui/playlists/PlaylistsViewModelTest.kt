package app.waveflow.ui.playlists

import app.waveflow.data.LibraryStore
import app.waveflow.model.Playlist
import app.waveflow.model.PlaylistEntry
import app.waveflow.model.Song
import app.waveflow.testing.FakeMusicRepository
import app.waveflow.testing.FakePlaylistRepository
import app.waveflow.testing.MainDispatcherRule
import app.waveflow.testing.song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaylistsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val songs = listOf(song(id = 1L), song(id = 2L), song(id = 3L))

    private fun CoroutineScope.storeWith(loaded: List<Song>): LibraryStore =
        LibraryStore(FakeMusicRepository(flowOf(loaded)), this).also { it.load() }

    @Test
    fun `une erreur des playlists arrete le chargement et remonte le message`() = runTest {
        val repository = FakePlaylistRepository(
            playlists = flow { throw IllegalStateException("base illisible") },
        )
        val viewModel = PlaylistsViewModel(backgroundScope.storeWith(songs), repository)
        advanceUntilIdle()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        val state = viewModel.state.value
        assertEquals(false, state.isLoading)
        assertEquals("base illisible", state.errorMessage)

        job.cancel()
    }

    @Test
    fun `creer une playlist depuis un morceau passe par l'appel atomique`() = runTest {
        val repository = FakePlaylistRepository()
        val viewModel = PlaylistsViewModel(backgroundScope.storeWith(songs), repository)

        viewModel.createWith(" Ma playlist ", song(id = 7L))
        advanceUntilIdle()

        assertEquals(listOf("Ma playlist" to 7L), repository.createWithSongCalls)
        assertTrue(
            "la création ne doit pas se faire en deux temps",
            repository.createCalls.isEmpty() && repository.addSongCalls.isEmpty(),
        )
    }

    @Test
    fun `un nom vide ne cree pas de playlist`() = runTest {
        val repository = FakePlaylistRepository()
        val viewModel = PlaylistsViewModel(backgroundScope.storeWith(songs), repository)

        viewModel.create("   ")
        viewModel.createWith("  ", song(id = 1L))
        advanceUntilIdle()

        assertTrue(repository.createCalls.isEmpty())
        assertTrue(repository.createWithSongCalls.isEmpty())
    }

    @Test
    fun `le contenu d'une playlist suit les positions et ignore les morceaux disparus`() = runTest {
        val repository = FakePlaylistRepository(
            playlists = flowOf(listOf(Playlist(id = 1L, name = "Ma playlist"))),
            entries = flowOf(
                listOf(
                    PlaylistEntry(playlistId = 1L, songId = 3L, position = 0),
                    PlaylistEntry(playlistId = 1L, songId = 99L, position = 1),
                    PlaylistEntry(playlistId = 1L, songId = 1L, position = 2),
                ),
            ),
        )
        val viewModel = PlaylistsViewModel(backgroundScope.storeWith(songs), repository)
        advanceUntilIdle()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        assertEquals(listOf(3L, 1L), viewModel.state.value.songs(playlistId = 1L).map { it.id })

        job.cancel()
    }

    @Test
    fun `reordonner transmet l'ordre affiche en identifiants`() = runTest {
        val repository = FakePlaylistRepository()
        val viewModel = PlaylistsViewModel(backgroundScope.storeWith(songs), repository)

        viewModel.reorder(playlistId = 7L, songs = listOf(songs[2], songs[0], songs[1]))
        advanceUntilIdle()

        assertEquals(listOf(7L to listOf(3L, 1L, 2L)), repository.reorderCalls)
    }

    @Test
    fun `un reordonnancement qui echoue devient un message`() = runTest {
        val repository = FakePlaylistRepository(
            writeFailure = IllegalStateException("disque plein"),
        )
        val viewModel = PlaylistsViewModel(backgroundScope.storeWith(songs), repository)

        val errors = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.errors.collect { errors += it }
        }

        viewModel.reorder(playlistId = 7L, songs = songs.reversed())
        advanceUntilIdle()

        assertEquals(listOf("Impossible de réordonner la playlist."), errors)

        job.cancel()
    }

    /**
     * Sans le try/catch, l'exception remonterait de `viewModelScope.launch`
     * jusqu'au scheduler du test, qui ferait échouer celui-ci — exactement ce
     * qui ferait crasher l'application en production.
     */
    @Test
    fun `une ecriture qui echoue devient un message et non un crash`() = runTest {
        val repository = FakePlaylistRepository(
            writeFailure = IllegalStateException("disque plein"),
        )
        val viewModel = PlaylistsViewModel(backgroundScope.storeWith(songs), repository)

        val errors = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.errors.collect { errors += it }
        }

        viewModel.create("Ma playlist")
        viewModel.removeSong(playlistId = 1L, song = song(id = 1L))
        advanceUntilIdle()

        assertEquals(
            listOf(
                "Impossible de créer la playlist.",
                "Impossible de retirer le morceau de la playlist.",
            ),
            errors,
        )

        job.cancel()
    }
}
