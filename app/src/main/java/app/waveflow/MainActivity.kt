package app.waveflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.waveflow.model.Song
import app.waveflow.ui.browse.AlbumDetailScreen
import app.waveflow.ui.browse.AlbumsScreen
import app.waveflow.ui.browse.ArtistDetailScreen
import app.waveflow.ui.browse.ArtistsScreen
import app.waveflow.ui.library.LibraryScreen
import app.waveflow.ui.library.LibraryUiState
import app.waveflow.ui.library.LibraryViewModel
import app.waveflow.ui.navigation.Routes
import app.waveflow.ui.navigation.TopLevelDestination
import app.waveflow.ui.navigation.WaveFlowBottomBar
import app.waveflow.ui.permission.AudioPermissionGate
import app.waveflow.ui.player.MiniPlayer
import app.waveflow.ui.player.NowPlayingScreen
import app.waveflow.ui.theme.WaveFlowTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WaveFlowTheme {
                WaveFlowRoot()
            }
        }
    }
}

/** Hauteur réservée sous les listes pour que le mini-player ne masque rien. */
private val MiniPlayerSpace = 76.dp

private const val PLAYER_TRANSITION_MS = 300

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WaveFlowRoot() {
    val viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
    val libraryState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    var playerExpanded by rememberSaveable { mutableStateOf(false) }
    val hasTrack = playerState.song != null

    // Si la file se vide, il n'y a plus rien à afficher en plein écran.
    LaunchedEffect(hasTrack) {
        if (!hasTrack) playerExpanded = false
    }

    BackHandler(enabled = playerExpanded) { playerExpanded = false }

    val listBottomPadding = if (hasTrack) MiniPlayerSpace else 0.dp
    val isDetailRoute = currentRoute == Routes.ALBUM_DETAIL || currentRoute == Routes.ARTIST_DETAIL

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            currentScreenTitle(
                                currentRoute = currentRoute,
                                albumId = backStackEntry?.arguments?.getLong(Routes.ARG_ALBUM_ID),
                                artistId = backStackEntry?.arguments?.getLong(Routes.ARG_ARTIST_ID),
                                state = libraryState,
                            ),
                        )
                    },
                    navigationIcon = {
                        if (isDetailRoute) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Retour",
                                )
                            }
                        }
                    },
                )
            },
            bottomBar = {
                WaveFlowBottomBar(
                    currentRoute = currentRoute,
                    onSelect = { navController.switchTab(it) },
                )
            },
        ) { innerPadding ->
            AudioPermissionGate(modifier = Modifier.padding(innerPadding)) {
                // Ne démarre le scan et la connexion au service qu'une fois la
                // permission acquise.
                LaunchedEffect(Unit) { viewModel.onAudioAccessGranted() }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = Routes.SONGS,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        composable(Routes.SONGS) {
                            LibraryScreen(
                                state = libraryState,
                                onSongClick = { song -> viewModel.playFrom(libraryState.songs, song) },
                                onRetry = viewModel::retry,
                                bottomPadding = listBottomPadding,
                            )
                        }

                        composable(Routes.ALBUMS) {
                            AlbumsScreen(
                                state = libraryState,
                                onAlbumClick = { navController.navigate(Routes.albumDetail(it.id)) },
                                onRetry = viewModel::retry,
                                bottomPadding = listBottomPadding,
                            )
                        }

                        composable(Routes.ARTISTS) {
                            ArtistsScreen(
                                state = libraryState,
                                onArtistClick = { navController.navigate(Routes.artistDetail(it.id)) },
                                onRetry = viewModel::retry,
                                bottomPadding = listBottomPadding,
                            )
                        }

                        composable(
                            route = Routes.ALBUM_DETAIL,
                            arguments = listOf(navArgument(Routes.ARG_ALBUM_ID) { type = NavType.LongType }),
                        ) { entry ->
                            val albumId = entry.arguments?.getLong(Routes.ARG_ALBUM_ID) ?: return@composable
                            val songs = libraryState.songsOfAlbum(albumId)

                            AlbumDetailScreen(
                                album = libraryState.album(albumId),
                                songs = songs,
                                nowPlayingId = libraryState.nowPlayingId,
                                onSongClick = { song -> viewModel.playFrom(songs, song) },
                                onPlay = { viewModel.playFirst(songs) },
                                onShuffle = { viewModel.playShuffled(songs) },
                                bottomPadding = listBottomPadding,
                            )
                        }

                        composable(
                            route = Routes.ARTIST_DETAIL,
                            arguments = listOf(navArgument(Routes.ARG_ARTIST_ID) { type = NavType.LongType }),
                        ) { entry ->
                            val artistId = entry.arguments?.getLong(Routes.ARG_ARTIST_ID) ?: return@composable
                            val songs = libraryState.songsOfArtist(artistId)

                            ArtistDetailScreen(
                                artist = libraryState.artist(artistId),
                                songs = songs,
                                nowPlayingId = libraryState.nowPlayingId,
                                onSongClick = { song -> viewModel.playFrom(songs, song) },
                                onPlay = { viewModel.playFirst(songs) },
                                onShuffle = { viewModel.playShuffled(songs) },
                                bottomPadding = listBottomPadding,
                            )
                        }
                    }

                    // Inutile de le composer sous le lecteur plein écran, qui
                    // le recouvre entièrement.
                    if (!playerExpanded) {
                        MiniPlayer(
                            state = playerState,
                            onExpand = { playerExpanded = true },
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onSkipNext = viewModel::skipNext,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }

        // Le lecteur plein écran est posé par-dessus le Scaffold (et donc
        // par-dessus les barres), comme une feuille qui remonte.
        AnimatedVisibility(
            visible = playerExpanded,
            enter = slideInVertically(animationSpec = tween(PLAYER_TRANSITION_MS)) { it },
            exit = slideOutVertically(animationSpec = tween(PLAYER_TRANSITION_MS)) { it },
            modifier = Modifier.fillMaxSize(),
        ) {
            NowPlayingScreen(
                state = playerState,
                onCollapse = { playerExpanded = false },
                onTogglePlayPause = viewModel::togglePlayPause,
                onSkipNext = viewModel::skipNext,
                onSkipPrevious = viewModel::skipPrevious,
                onSeek = viewModel::seekTo,
                onToggleShuffle = viewModel::toggleShuffle,
                onCycleRepeat = viewModel::cycleRepeatMode,
            )
        }
    }
}

/**
 * Bascule d'onglet : une seule entrée par section dans la pile, et l'état de
 * défilement de chaque onglet est conservé.
 */
private fun NavHostController.switchTab(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun currentScreenTitle(
    currentRoute: String?,
    albumId: Long?,
    artistId: Long?,
    state: LibraryUiState,
): String = when (currentRoute) {
    Routes.ALBUMS -> "Albums"
    Routes.ARTISTS -> "Artistes"
    Routes.ALBUM_DETAIL -> albumId?.let { state.album(it)?.title } ?: "Album"
    Routes.ARTIST_DETAIL -> artistId?.let { state.artist(it)?.name } ?: "Artiste"
    else -> "WaveFlow"
}

/** Évite d'exposer un `first()` nullable à chaque écran de détail. */
private fun LibraryViewModel.playFirst(songs: List<Song>) {
    songs.firstOrNull()?.let { playFrom(songs, it) }
}
