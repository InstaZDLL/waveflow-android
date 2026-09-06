package app.waveflow.ui.player

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.waveflow.data.PreferencesStore
import app.waveflow.model.AppPreferences
import app.waveflow.model.PlaybackSpeed
import app.waveflow.playback.PlaybackFailure
import app.waveflow.playback.PlaybackState
import app.waveflow.playback.PlayingTrack
import app.waveflow.playback.SleepTimer
import app.waveflow.playback.TrackSource
import app.waveflow.testing.FakePlaybackController
import app.waveflow.testing.FakePreferencesStore
import app.waveflow.testing.MainDispatcherRule
import app.waveflow.testing.remoteSong
import app.waveflow.testing.song
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    private val remoteSongs = listOf(remoteSong("a"), remoteSong("b"), remoteSong("c"))
    private val controller = FakePlaybackController()
    private val preferences = FakePreferencesStore()

    /**
     * Le ViewModel sous test, muni d'une minuterie qui suit l'horloge virtuelle.
     *
     * Elle est construite par test : sa portée est celle de `runTest`, et une
     * minuterie qui survivrait d'un test à l'autre porterait son échéance avec
     * elle.
     */
    private fun TestScope.playerViewModel(
        preferencesStore: PreferencesStore = preferences,
    ) = PlayerViewModel(
        playbackController = controller,
        sleepTimer = SleepTimer(backgroundScope) { testScheduler.currentTime },
        preferencesStore = preferencesStore,
    )

    @Test
    fun `jouer un morceau met la file demandee et non toute la bibliotheque`() = runTest {
        val viewModel = playerViewModel()

        val albumQueue = songs.take(2)
        viewModel.playFrom(albumQueue, songs[1])

        assertEquals(listOf(albumQueue to 1), controller.playCalls)
    }

    @Test
    fun `jouer un morceau absent de la file ne declenche rien`() = runTest {
        val viewModel = playerViewModel()

        viewModel.playFrom(songs.take(2), song(id = 99L))

        assertTrue(controller.playCalls.isEmpty())
    }

    @Test
    fun `playFirst sur une file vide ne declenche rien`() = runTest {
        val viewModel = playerViewModel()

        viewModel.playFirst(emptyList())

        assertTrue(controller.playCalls.isEmpty())
    }

    @Test
    fun `jouer un morceau distant passe par la file distante`() = runTest {
        // Chemin distinct : les deux catalogues ne partagent ni type ni
        // identifiant, et la file distante remplace la locale.
        val viewModel = playerViewModel()

        viewModel.playRemoteFrom(remoteSongs, remoteSongs[2])

        assertEquals(listOf(remoteSongs to 2), controller.playRemoteCalls)
        assertTrue("la file locale ne doit pas être touchée", controller.playCalls.isEmpty())
    }

    @Test
    fun `jouer un morceau distant absent de la file ne declenche rien`() = runTest {
        val viewModel = playerViewModel()

        viewModel.playRemoteFrom(remoteSongs, remoteSong("inconnu"))

        assertTrue(controller.playRemoteCalls.isEmpty())
    }

    @Test
    fun `l'aleatoire distant passe par la file distante`() = runTest {
        val viewModel = playerViewModel()

        viewModel.playRemoteShuffled(remoteSongs)

        assertEquals(listOf(remoteSongs), controller.playRemoteShuffledCalls)
        assertTrue("la file locale ne doit pas être touchée", controller.playShuffledCalls.isEmpty())
        // Ni lecture ordonnée en plus : elle poserait la file une seconde fois
        // et l'emporterait, rendant le bouton Aléatoire sans effet.
        assertTrue("aucune lecture ordonnée distante", controller.playRemoteCalls.isEmpty())
    }

    @Test
    fun `l'etat reprend la piste telle que le lecteur la decrit`() = runTest {
        // Plus de résolution dans la bibliothèque : une piste du serveur n'y
        // figure pas, et la chercher ne rendrait rien à afficher.
        val viewModel = playerViewModel()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }
        controller.emit(
            PlaybackState(
                isConnected = true,
                current = PlayingTrack(
                    mediaId = "remote:a",
                    title = "Résonance",
                    artist = "Bruit de Fond",
                    album = "Écho",
                    artworkUri = null,
                    localSongId = null,
                    source = TrackSource.Remote,
                ),
                isPlaying = true,
                durationMs = 60_000L,
            ),
        )

        val state = viewModel.state.value
        assertEquals("Résonance", state.track?.title)
        assertEquals(TrackSource.Remote, state.track?.source)
        // Rien à souligner dans les listes locales pour une piste distante.
        assertNull(state.track?.localSongId)
        assertEquals(true, state.isPlaying)

        job.cancel()
    }

    @Test
    fun `une lecture qui echoue se dit`() = runTest {
        // Le défaut d'origine : le lecteur s'arrêtait sur une erreur sans que
        // rien ne l'annonce, la piste restant affichée comme si elle allait
        // démarrer.
        val viewModel = playerViewModel()
        val messages = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.errors.collect { messages += it }
        }

        controller.emit(PlaybackState(failure = PlaybackFailure.Unreachable))

        assertEquals(listOf("Serveur injoignable : lecture impossible."), messages)
        job.cancel()
    }

    @Test
    fun `une piste illisible ne fait pas accuser le serveur`() = runTest {
        val viewModel = playerViewModel()
        val messages = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.errors.collect { messages += it }
        }

        controller.emit(PlaybackState(failure = PlaybackFailure.Unplayable))

        assertEquals(listOf("Ce morceau n'a pas pu être lu."), messages)
        job.cancel()
    }

    @Test
    fun `une panne qui dure ne se repete pas`() = runTest {
        // L'état est republié à chaque tic de position : sans quoi le message
        // reviendrait deux fois par seconde tant que la panne dure.
        val viewModel = playerViewModel()
        val messages = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.errors.collect { messages += it }
        }

        controller.emit(PlaybackState(failure = PlaybackFailure.Unreachable))
        controller.emit(PlaybackState(failure = PlaybackFailure.Unreachable, positionMs = 500L))
        controller.emit(PlaybackState(failure = PlaybackFailure.Unreachable, positionMs = 1_000L))

        assertEquals(1, messages.size)
        job.cancel()
    }

    @Test
    fun `un second echec apres reprise se dit de nouveau`() = runTest {
        // Media3 oublie son erreur quand on le prépare à nouveau : la panne
        // repasse par `null`, et le second échec doit se voir comme le premier.
        val viewModel = playerViewModel()
        val messages = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.errors.collect { messages += it }
        }

        controller.emit(PlaybackState(failure = PlaybackFailure.Unreachable))
        controller.emit(PlaybackState(failure = null, isPlaying = true))
        controller.emit(PlaybackState(failure = PlaybackFailure.Unreachable))

        assertEquals(2, messages.size)
        job.cancel()
    }

    @Test
    fun `une panne deja annoncee ne se redit pas a un ecran recree`() = runTest {
        // La rotation détruit l'activité et relance la collecte. La panne, elle,
        // reste dans l'état du lecteur jusqu'à la prochaine préparation : un
        // flux redérivé par abonné repartirait de cette valeur courante et
        // redirait l'erreur, à chaque rotation.
        val viewModel = playerViewModel()
        val avant = mutableListOf<String>()
        val premier = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.errors.collect { avant += it }
        }

        controller.emit(PlaybackState(failure = PlaybackFailure.Unreachable))
        premier.cancel()

        val apres = mutableListOf<String>()
        val second = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.errors.collect { apres += it }
        }
        advanceUntilIdle()

        assertEquals("l'erreur est dite une fois au premier écran", 1, avant.size)
        assertTrue("l'écran recréé ne la redit pas", apres.isEmpty())
        second.cancel()
    }

    @Test
    fun `un lecteur qui va bien ne dit rien`() = runTest {
        val viewModel = playerViewModel()
        val messages = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.errors.collect { messages += it }
        }

        controller.emit(PlaybackState(isConnected = true, isPlaying = true))

        assertTrue(messages.isEmpty())
        job.cancel()
    }

    @Test
    fun `le controleur est libere avec le ViewModel`() = runTest {
        // On passe par un vrai ViewModelStore pour déclencher onCleared comme
        // le ferait la destruction de l'écran.
        val viewModelStore = ViewModelStore()
        val provider = ViewModelProvider(
            viewModelStore,
            viewModelFactory { initializer { playerViewModel() } },
        )
        provider[PlayerViewModel::class.java]
        advanceUntilIdle()

        viewModelStore.clear()

        assertTrue("une liaison vivante empêcherait le service de s'arrêter", controller.released)
    }

    @Test
    fun `la minuterie armee apparait dans l'etat du lecteur`() = runTest {
        // L'écran allume son icône à partir de là : sans cette remontée, la
        // minuterie tournerait sans que rien ne le dise.
        val viewModel = playerViewModel()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.startSleepTimer(30 * 60_000L)
        runCurrent()

        assertTrue(viewModel.state.value.sleepTimerActive)
        assertEquals(30 * 60_000L, viewModel.sleepTimerRemainingMs())

        job.cancel()
    }

    @Test
    fun `annuler la minuterie l'efface de l'etat`() = runTest {
        val viewModel = playerViewModel()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.startSleepTimer(30 * 60_000L)
        runCurrent()
        viewModel.cancelSleepTimer()
        runCurrent()

        assertFalse(viewModel.state.value.sleepTimerActive)
        assertNull(viewModel.sleepTimerRemainingMs())

        job.cancel()
    }

    @Test
    fun `la vitesse enregistree habille l'etat des le depart`() = runTest {
        // Sans cette remontée, l'écran s'ouvrirait sur « ×1 » pendant que le
        // service joue déjà à ×1,5 : le bouton mentirait sur ce qu'on entend.
        val viewModel = playerViewModel(
            FakePreferencesStore(AppPreferences(playbackSpeed = 1.5f)),
        )
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        advanceUntilIdle()

        assertEquals(1.5f, viewModel.state.value.playbackSpeed, 0f)

        job.cancel()
    }

    @Test
    fun `changer la vitesse se voit alors que le lecteur n'emet plus rien`() = runTest {
        // Le piège du lecteur : `PlaybackState` ne se reconstruit qu'aux tics de
        // position, donc plus du tout en pause — et c'est justement en pause
        // qu'on règle sa vitesse. Le contrôleur reste ici muet de bout en bout :
        // si l'état tenait la vitesse de lui, rien ne bougerait.
        val viewModel = playerViewModel()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        advanceUntilIdle()

        assertEquals(PlaybackSpeed.NORMALE, viewModel.state.value.playbackSpeed, 0f)

        viewModel.setPlaybackSpeed(1.5f)
        advanceUntilIdle()

        assertEquals(1.5f, viewModel.state.value.playbackSpeed, 0f)

        job.cancel()
    }

    @Test
    fun `la vitesse choisie passe par les preferences et non par le lecteur`() = runTest {
        // C'est ce qui la fait tenir d'un lancement à l'autre, et s'appliquer
        // aux lectures démarrées sans qu'aucun écran soit ouvert : le service
        // observe la préférence, il n'attend pas d'ordre de l'interface.
        val viewModel = playerViewModel()

        viewModel.setPlaybackSpeed(1.75f)
        advanceUntilIdle()

        assertEquals(1.75f, preferences.playbackSpeed, 0f)
    }
}
