package app.waveflow.ui.library

import app.waveflow.model.Playlist
import app.waveflow.model.PlaylistEntry
import app.waveflow.playback.PlaybackController
import app.waveflow.testing.FakeMusicRepository
import app.waveflow.testing.FakePlaybackController
import app.waveflow.testing.FakePlaylistRepository
import app.waveflow.testing.MainDispatcherRule
import app.waveflow.testing.song
import app.waveflow.ui.playlists.PlaylistsUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val playbackController = FakePlaybackController()

    private fun viewModel(
        songs: Flow<List<app.waveflow.model.Song>> = flowOf(emptyList()),
        playlistRepository: FakePlaylistRepository = FakePlaylistRepository(),
        controller: PlaybackController = playbackController,
    ) = LibraryViewModel(
        musicRepository = FakeMusicRepository(songs),
        playlistRepository = playlistRepository,
        playbackController = controller,
    )

    @Test
    fun `une erreur des playlists arrete le chargement et remonte le message`() = runTest {
        val repository = FakePlaylistRepository(
            playlists = flow { throw IllegalStateException("base illisible") },
        )
        val viewModel = viewModel(playlistRepository = repository)

        val states = mutableListOf<PlaylistsUiState>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.playlistsState.collect { states += it }
        }

        val last = states.last()
        assertEquals(false, last.isLoading)
        assertEquals("base illisible", last.errorMessage)

        job.cancel()
    }

    @Test
    fun `creer une playlist depuis un morceau passe par l'appel atomique`() = runTest {
        val repository = FakePlaylistRepository()
        val viewModel = viewModel(playlistRepository = repository)

        viewModel.createPlaylistWith(" Ma playlist ", song(id = 7L))

        assertEquals(listOf("Ma playlist" to 7L), repository.createWithSongCalls)
        assertTrue(
            "la création ne doit pas se faire en deux temps",
            repository.createCalls.isEmpty() && repository.addSongCalls.isEmpty(),
        )
    }

    @Test
    fun `un nom vide ne cree pas de playlist`() = runTest {
        val repository = FakePlaylistRepository()
        val viewModel = viewModel(playlistRepository = repository)

        viewModel.createPlaylist("   ")
        viewModel.createPlaylistWith("  ", song(id = 1L))

        assertTrue(repository.createCalls.isEmpty())
        assertTrue(repository.createWithSongCalls.isEmpty())
    }

    @Test
    fun `le contenu d'une playlist suit les positions et ignore les morceaux disparus`() = runTest {
        val songs = listOf(song(id = 1L), song(id = 2L), song(id = 3L))
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
        val viewModel = viewModel(songs = flowOf(songs), playlistRepository = repository)

        val states = mutableListOf<PlaylistsUiState>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.playlistsState.collect { states += it }
        }
        viewModel.onAudioAccessGranted()

        val resolved = states.last().songs(playlistId = 1L)
        assertEquals(listOf(3L, 1L), resolved.map { it.id })

        job.cancel()
    }

    /**
     * Sans le try/catch, l'exception remonterait de `viewModelScope.launch`
     * jusqu'au scheduler du test, qui ferait échouer celui-ci — exactement ce
     * qui ferait crasher l'application en production.
     */
    @Test
    fun `une ecriture de playlist qui echoue devient un message et non un crash`() = runTest {
        val repository = FakePlaylistRepository(
            writeFailure = IllegalStateException("disque plein"),
        )
        val viewModel = viewModel(playlistRepository = repository)

        val errors = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.playlistErrors.collect { errors += it }
        }

        viewModel.createPlaylist("Ma playlist")
        viewModel.removeSongFromPlaylist(playlistId = 1L, song = song(id = 1L))

        assertEquals(
            listOf(
                "Impossible de créer la playlist.",
                "Impossible de retirer le morceau de la playlist.",
            ),
            errors,
        )

        job.cancel()
    }

    @Test
    fun `jouer un morceau met la file demandee et non toute la bibliotheque`() = runTest {
        val songs = listOf(song(id = 1L), song(id = 2L), song(id = 3L))
        val viewModel = viewModel(songs = flowOf(songs))
        viewModel.onAudioAccessGranted()

        val albumQueue = songs.take(2)
        viewModel.playFrom(albumQueue, songs[1])

        assertEquals(listOf(albumQueue to 1), playbackController.playCalls)
    }
}
