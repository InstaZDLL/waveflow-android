package app.waveflow.prototype

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.waveflow.BuildConfig
import app.waveflow.MainActivity
import app.waveflow.WaveFlowApp
import app.waveflow.data.LibraryStore
import app.waveflow.model.ThemeChoice
import app.waveflow.ui.home.HomeViewModel
import app.waveflow.ui.permission.AudioPermissionGate
import app.waveflow.ui.permission.hasAudioPermission
import app.waveflow.ui.player.PlayerViewModel
import app.waveflow.ui.playlists.PlaylistsViewModel
import app.waveflow.ui.server.ServerViewModel
import app.waveflow.ui.settings.SettingsScreen
import app.waveflow.ui.settings.SettingsViewModel
import app.waveflow.ui.theme.WaveFlowTheme
import kotlinx.coroutines.flow.merge

/**
 * L'entrée du prototype **WaveFlow Pocket** — variante `debug` seulement, avec
 * sa propre icône de lanceur (`app/src/debug/AndroidManifest.xml`). Une build
 * `release` n'en contient rien.
 *
 * Il lit les vraies données et pilote le vrai lecteur : les ViewModels de
 * production — le lecteur (sa fabrique, donc son `MediaController`, sa
 * minuterie, sa boucle A-B, ses paroles), l'accueil (la reprise et la
 * suggestion du jour) et les playlists — et le `LibraryStore`
 * d'`AppContainer`. Rien de Media3, du MediaStore ni du service n'est réécrit.
 *
 * Le thème suit celui choisi dans les réglages de l'application.
 */
class PocketActivity : ComponentActivity() {

    private val playerViewModel: PlayerViewModel by viewModels { PlayerViewModel.Factory }

    // Les ViewModels de production de l'accueil (reprise, suggestion du jour)
    // et des playlists : le prototype n'a pas de données à lui.
    private val homeViewModel: HomeViewModel by viewModels { HomeViewModel.Factory }
    private val playlistsViewModel: PlaylistsViewModel by viewModels { PlaylistsViewModel.Factory }

    // Les réglages de production : le thème, le son, le serveur.
    private val settingsViewModel: SettingsViewModel by viewModels { SettingsViewModel.Factory }
    private val serverViewModel: ServerViewModel by viewModels { ServerViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Sans voile sous les boutons du système : le lecteur peint sa nappe
        // jusqu'au bord, et la barre de navigation porte déjà son propre fond.
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        // Le système voile encore la barre à trois boutons pour son contraste :
        // les boutons y restent lisibles sur nos fonds, qui sont pleins.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        val libraryStore = (application as WaveFlowApp).container.libraryStore
        setContent {
            // Le thème choisi dans les réglages, comme l'application : les
            // réglages sont ceux de la production, et un choix qui ne
            // changerait rien serait un réglage mort.
            val preferences by settingsViewModel.preferences.collectAsStateWithLifecycle()
            val sombre = when (preferences.theme) {
                ThemeChoice.System -> isSystemInDarkTheme()
                ThemeChoice.Light -> false
                ThemeChoice.Dark -> true
            }
            // `enableEdgeToEdge` règle les icônes des barres du système sur le
            // thème du système au lancement ; un thème choisi à l'inverse les
            // laisserait illisibles sur la page.
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !sombre
                    isAppearanceLightNavigationBars = !sombre
                }
            }
            WaveFlowTheme(theme = preferences.theme) {
                PocketTheme(dark = sombre) {
                    AudioPermissionGate { PocketContent(libraryStore) }
                }
            }
        }
    }

    /** Les commandes du lecteur, relayées au `PlayerViewModel` de production. */
    private fun playerActions(vm: PlayerViewModel) = NowPlayingActions(
        onCollapse = {},
        onTogglePlayPause = vm::togglePlayPause,
        onSkipNext = vm::skipNext,
        onSkipPrevious = vm::skipPrevious,
        onSeek = vm::seekTo,
        onToggleShuffle = vm::toggleShuffle,
        onCycleRepeat = vm::cycleRepeatMode,
        onPlayQueueItem = vm::playQueueItem,
        onMoveQueueItem = vm::moveQueueItem,
        onRemoveQueueItem = vm::removeQueueItem,
        onStartSleepTimer = vm::startSleepTimer,
        onCancelSleepTimer = vm::cancelSleepTimer,
        onSleepTimerRemainingMs = vm::sleepTimerRemainingMs,
        onSetPlaybackSpeed = vm::setPlaybackSpeed,
        onMarkAbLoop = vm::markAbLoop,
        onToggleLyrics = vm::toggleLyrics,
        onRetryLyrics = vm::retryLyrics,
        onLyricsOnScreen = vm::lyricsOnScreen,
    )

    /** Le prototype une fois la permission audio accordée : les flux de production, puis la coquille. */
    @Composable
    private fun PocketContent(libraryStore: LibraryStore) {
        val vm = playerViewModel
        val actions = remember(vm) { playerActions(vm) }
        LaunchedEffect(Unit) {
            libraryStore.load()
            vm.connect()
        }
        val library by libraryStore.library.collectAsStateWithLifecycle()
        val player by vm.state.collectAsStateWithLifecycle()
        val lyrics by vm.lyrics.collectAsStateWithLifecycle()
        val home by homeViewModel.state.collectAsStateWithLifecycle()
        val playlists by playlistsViewModel.state.collectAsStateWithLifecycle()
        // Gardé par un ViewModel, et non par l'état sauvegardé :
        // il survit à une rotation, pas à la mort du processus.
        // Restauré « déployé » sans file (le service meurt avec
        // le processus), le lecteur se serait redéployé seul au
        // premier morceau lancé.
        val coquille: PocketShellViewModel = viewModel()
        val expanded by coquille.expanded.collectAsStateWithLifecycle()
        // Une panne de lecture, un repli de qualité, une playlist
        // qui n'a pas pu s'écrire : dits une fois, tant que
        // l'écran est visible.
        val messages = remember { SnackbarHostState() }
        val cycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(cycle) {
            // L'affichage lui-même s'arrête avec l'écran : un
            // second message en attente ne s'affiche pas en
            // arrière-plan pour y expirer.
            cycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                merge(vm.errors, playlistsViewModel.errors).collect { messages.showSnackbar(it) }
            }
        }
        PocketApp(
            library = library,
            home = home,
            playlists = playlists,
            player = player,
            lyrics = lyrics,
            actions = actions.copy(onCollapse = { coquille.expanded.value = false }),
            verbs = PocketVerbs(
                onPlay = vm::playFrom,
                onShuffle = vm::playShuffled,
                onAnotherSuggestion = homeViewModel::anotherSuggestion,
                onAddToPlaylist = playlistsViewModel::addSong,
                onCreatePlaylistWith = playlistsViewModel::createWith,
            ),
            messages = messages,
            expanded = expanded,
            onExpandedChange = { coquille.expanded.value = it },
            settingsPage = { Reglages() },
        )
    }

    /**
     * L'écran des réglages de production. La ligne du serveur ouvre
     * l'application elle-même : la connexion et le compte n'ont pas d'écran
     * dans le prototype.
     */
    @Composable
    private fun Reglages() {
        val preferences by settingsViewModel.preferences.collectAsStateWithLifecycle()
        val serveur by serverViewModel.state.collectAsStateWithLifecycle()
        SettingsScreen(
            preferences = preferences,
            isServerConnected = serveur.isConnected,
            serverSummary = serveur.connected?.serverUrl,
            appVersion = BuildConfig.VERSION_NAME,
            onThemeChange = settingsViewModel::setTheme,
            onSonChange = settingsViewModel::regler,
            onOpenServer = { startActivity(Intent(this, MainActivity::class.java)) },
        )
    }

    override fun onResume() {
        super.onResume()
        if (hasAudioPermission()) playerViewModel.connect()
    }
}

/**
 * Ce que la coquille du prototype garde d'une rotation à l'autre, et pas
 * au-delà de la vie du processus : le lecteur est-il déployé.
 */
class PocketShellViewModel : androidx.lifecycle.ViewModel() {
    val expanded = kotlinx.coroutines.flow.MutableStateFlow(false)
}
