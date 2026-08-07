package app.waveflow.ui.player

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.waveflow.data.LibraryStore
import app.waveflow.playback.PlaybackState
import app.waveflow.testing.FakeMusicRepository
import app.waveflow.testing.FakePlaybackController
import app.waveflow.testing.MainDispatcherRule
import app.waveflow.testing.song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class PlayerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val songs = listOf(song(id = 1L), song(id = 2L), song(id = 3L))
    private val controller = FakePlaybackController()

    private fun CoroutineScope.loadedStore(): LibraryStore =
        LibraryStore(FakeMusicRepository(flowOf(songs)), this).also { it.load() }

    @Test
    fun `jouer un morceau met la file demandee et non toute la bibliotheque`() = runTest {
        val viewModel = PlayerViewModel(backgroundScope.loadedStore(), controller)
        advanceUntilIdle()

        val albumQueue = songs.take(2)
        viewModel.playFrom(albumQueue, songs[1])

        assertEquals(listOf(albumQueue to 1), controller.playCalls)
    }

    @Test
    fun `jouer un morceau absent de la file ne declenche rien`() = runTest {
        val viewModel = PlayerViewModel(backgroundScope.loadedStore(), controller)
        advanceUntilIdle()

        viewModel.playFrom(songs.take(2), song(id = 99L))

        assertTrue(controller.playCalls.isEmpty())
    }

    @Test
    fun `playFirst sur une file vide ne declenche rien`() = runTest {
        val viewModel = PlayerViewModel(backgroundScope.loadedStore(), controller)
        advanceUntilIdle()

        viewModel.playFirst(emptyList())

        assertTrue(controller.playCalls.isEmpty())
    }

    @Test
    fun `l'etat resout le morceau courant depuis la bibliotheque`() = runTest {
        val store = backgroundScope.loadedStore()
        val viewModel = PlayerViewModel(store, controller)
        advanceUntilIdle()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }
        controller.emit(
            PlaybackState(isConnected = true, currentSongId = 2L, isPlaying = true, durationMs = 60_000L),
        )

        val state = viewModel.state.value
        assertEquals(2L, state.song?.id)
        assertEquals(true, state.isPlaying)

        job.cancel()
    }

    @Test
    fun `le controleur est libere avec le ViewModel`() = runTest {
        val store = backgroundScope.loadedStore()
        // On passe par un vrai ViewModelStore pour déclencher onCleared comme
        // le ferait la destruction de l'écran.
        val viewModelStore = ViewModelStore()
        val provider = ViewModelProvider(
            viewModelStore,
            viewModelFactory { initializer { PlayerViewModel(store, controller) } },
        )
        provider[PlayerViewModel::class.java]
        advanceUntilIdle()

        viewModelStore.clear()

        assertTrue("une liaison vivante empêcherait le service de s'arrêter", controller.released)
    }
}
