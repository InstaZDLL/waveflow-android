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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import app.waveflow.model.Library
import app.waveflow.model.Song
import app.waveflow.ui.browse.AlbumDetailScreen
import app.waveflow.ui.browse.AlbumsScreen
import app.waveflow.ui.browse.ArtistDetailScreen
import app.waveflow.ui.browse.ArtistsScreen
import app.waveflow.ui.library.LibraryScreen
import app.waveflow.ui.library.LibraryViewModel
import app.waveflow.ui.navigation.Routes
import app.waveflow.ui.navigation.TopLevelDestination
import app.waveflow.ui.navigation.WaveFlowBottomBar
import app.waveflow.ui.permission.AudioPermissionGate
import app.waveflow.ui.player.MiniPlayer
import app.waveflow.ui.player.NowPlayingScreen
import app.waveflow.ui.player.PlayerViewModel
import app.waveflow.ui.playlists.AddToPlaylistSheet
import app.waveflow.ui.playlists.PlaylistDetailScreen
import app.waveflow.ui.playlists.PlaylistMenu
import app.waveflow.ui.playlists.PlaylistsScreen
import app.waveflow.ui.playlists.PlaylistsViewModel
import app.waveflow.ui.search.SearchField
import app.waveflow.ui.search.SearchScreen
import app.waveflow.ui.search.SearchViewModel
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

/** Routes affichant une flèche de retour plutôt que le titre de section. */
private val DETAIL_ROUTES = setOf(
    Routes.ALBUM_DETAIL,
    Routes.ARTIST_DETAIL,
    Routes.PLAYLIST_DETAIL,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WaveFlowRoot() {
    val libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
    val playerViewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory)
    val playlistsViewModel: PlaylistsViewModel = viewModel(factory = PlaylistsViewModel.Factory)
    val searchViewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory)

    val library by libraryViewModel.library.collectAsStateWithLifecycle()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val playlistsState by playlistsViewModel.state.collectAsStateWithLifecycle()
    val searchQuery by searchViewModel.query.collectAsStateWithLifecycle()
    val searchResults by searchViewModel.results.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    var playerExpanded by rememberSaveable { mutableStateOf(false) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var songToAdd by remember { mutableStateOf<Song?>(null) }

    // Quitter la recherche n'a pas à laisser la requête derrière : la rouvrir
    // doit repartir d'un champ vide.
    fun closeSearch() {
        searchActive = false
        searchViewModel.clear()
    }

    val nowPlayingId = playerState.song?.id
    val hasTrack = playerState.song != null

    // Les écritures de playlist qui échouent se signalent une fois, sans
    // remplacer le contenu de l'écran.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        playlistsViewModel.errors.collect { snackbarHostState.showSnackbar(it) }
    }

    // Si la file se vide, il n'y a plus rien à afficher en plein écran.
    LaunchedEffect(hasTrack) {
        if (!hasTrack) playerExpanded = false
    }

    // Le lecteur plein écran recouvre la recherche : tant qu'il est ouvert,
    // c'est lui que le retour referme.
    BackHandler(enabled = searchActive && !playerExpanded) { closeSearch() }

    BackHandler(enabled = playerExpanded) { playerExpanded = false }

    val listBottomPadding = if (hasTrack) MiniPlayerSpace else 0.dp
    val isDetailRoute = currentRoute in DETAIL_ROUTES
    val openPlaylist = backStackEntry
        ?.takeIf { currentRoute == Routes.PLAYLIST_DETAIL }
        ?.arguments
        ?.getLong(Routes.ARG_PLAYLIST_ID)
        ?.let { playlistsState.playlist(it) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        if (searchActive) {
                            SearchField(
                                query = searchQuery,
                                onQueryChange = searchViewModel::onQueryChange,
                            )
                        } else {
                            Text(
                                currentScreenTitle(
                                    currentRoute = currentRoute,
                                    albumId = backStackEntry?.arguments?.getLong(Routes.ARG_ALBUM_ID),
                                    artistId = backStackEntry?.arguments?.getLong(Routes.ARG_ARTIST_ID),
                                    library = library,
                                    playlistName = openPlaylist?.name,
                                ),
                            )
                        }
                    },
                    navigationIcon = {
                        when {
                            searchActive -> IconButton(onClick = { closeSearch() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Fermer la recherche",
                                )
                            }

                            isDetailRoute -> IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Retour",
                                )
                            }
                        }
                    },
                    actions = {
                        if (searchActive) {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = searchViewModel::clear) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Effacer la recherche",
                                    )
                                }
                            }
                        } else {
                            // Rien à chercher tant que la bibliothèque n'a rien
                            // rendu — permission refusée, scan en cours,
                            // appareil sans musique.
                            if (library.songs.isNotEmpty()) {
                                IconButton(onClick = { searchActive = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = "Rechercher",
                                    )
                                }
                            }

                            openPlaylist?.let { playlist ->
                                PlaylistMenu(
                                    playlist = playlist,
                                    onRename = { playlistsViewModel.rename(playlist.id, it) },
                                    onDelete = {
                                        playlistsViewModel.delete(playlist.id)
                                        navController.popBackStack()
                                    },
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
                LaunchedEffect(Unit) {
                    libraryViewModel.onAudioAccessGranted()
                    playerViewModel.connect()
                }

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
                                library = library,
                                nowPlayingId = nowPlayingId,
                                onSongClick = { playerViewModel.playFrom(library.songs, it) },
                                onRetry = libraryViewModel::retry,
                                bottomPadding = listBottomPadding,
                                onSongLongClick = { songToAdd = it },
                            )
                        }

                        composable(Routes.ALBUMS) {
                            AlbumsScreen(
                                library = library,
                                onAlbumClick = { navController.navigate(Routes.albumDetail(it.id)) },
                                onRetry = libraryViewModel::retry,
                                bottomPadding = listBottomPadding,
                            )
                        }

                        composable(Routes.ARTISTS) {
                            ArtistsScreen(
                                library = library,
                                onArtistClick = { navController.navigate(Routes.artistDetail(it.id)) },
                                onRetry = libraryViewModel::retry,
                                bottomPadding = listBottomPadding,
                            )
                        }

                        composable(
                            route = Routes.ALBUM_DETAIL,
                            arguments = listOf(navArgument(Routes.ARG_ALBUM_ID) { type = NavType.LongType }),
                        ) { entry ->
                            val albumId = entry.arguments?.getLong(Routes.ARG_ALBUM_ID) ?: return@composable
                            // Le suivi de position recompose la racine toutes les
                            // 500 ms : sans mémorisation, on refiltrerait toute la
                            // bibliothèque à chaque fois.
                            val songs = remember(library, albumId) { library.songsOfAlbum(albumId) }

                            AlbumDetailScreen(
                                album = library.album(albumId),
                                songs = songs,
                                nowPlayingId = nowPlayingId,
                                onSongClick = { playerViewModel.playFrom(songs, it) },
                                onPlay = { playerViewModel.playFirst(songs) },
                                onShuffle = { playerViewModel.playShuffled(songs) },
                                bottomPadding = listBottomPadding,
                                onSongLongClick = { songToAdd = it },
                            )
                        }

                        composable(
                            route = Routes.ARTIST_DETAIL,
                            arguments = listOf(navArgument(Routes.ARG_ARTIST_ID) { type = NavType.LongType }),
                        ) { entry ->
                            val artistId = entry.arguments?.getLong(Routes.ARG_ARTIST_ID) ?: return@composable
                            val songs = remember(library, artistId) { library.songsOfArtist(artistId) }

                            ArtistDetailScreen(
                                artist = library.artist(artistId),
                                songs = songs,
                                nowPlayingId = nowPlayingId,
                                onSongClick = { playerViewModel.playFrom(songs, it) },
                                onPlay = { playerViewModel.playFirst(songs) },
                                onShuffle = { playerViewModel.playShuffled(songs) },
                                bottomPadding = listBottomPadding,
                                onSongLongClick = { songToAdd = it },
                            )
                        }

                        composable(Routes.PLAYLISTS) {
                            PlaylistsScreen(
                                state = playlistsState,
                                onPlaylistClick = { navController.navigate(Routes.playlistDetail(it.id)) },
                                onCreatePlaylist = playlistsViewModel::create,
                                bottomPadding = listBottomPadding,
                            )
                        }

                        composable(
                            route = Routes.PLAYLIST_DETAIL,
                            arguments = listOf(navArgument(Routes.ARG_PLAYLIST_ID) { type = NavType.LongType }),
                        ) { entry ->
                            val playlistId = entry.arguments?.getLong(Routes.ARG_PLAYLIST_ID) ?: return@composable
                            val songs = playlistsState.songs(playlistId)

                            PlaylistDetailScreen(
                                playlist = playlistsState.playlist(playlistId),
                                songs = songs,
                                nowPlayingId = nowPlayingId,
                                onSongClick = { playerViewModel.playFrom(songs, it) },
                                onRemoveSong = { playlistsViewModel.removeSong(playlistId, it) },
                                onReorder = { order, onFailure ->
                                    playlistsViewModel.reorder(playlistId, order, onFailure)
                                },
                                onPlay = { playerViewModel.playFirst(songs) },
                                onShuffle = { playerViewModel.playShuffled(songs) },
                                bottomPadding = listBottomPadding,
                            )
                        }
                    }

                    // Posée par-dessus le NavHost plutôt qu'à sa place : la
                    // pile de navigation reste intacte, et fermer la recherche
                    // rend l'écran exactement tel qu'il était.
                    if (searchActive) {
                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            SearchScreen(
                                query = searchQuery,
                                results = searchResults,
                                nowPlayingId = nowPlayingId,
                                // La file de lecture est la liste affichée :
                                // enchaîner sur les résultats suivants est le
                                // comportement attendu.
                                onSongClick = { playerViewModel.playFrom(searchResults.songs, it) },
                                onAlbumClick = {
                                    closeSearch()
                                    navController.navigate(Routes.albumDetail(it.id))
                                },
                                onArtistClick = {
                                    closeSearch()
                                    navController.navigate(Routes.artistDetail(it.id))
                                },
                                bottomPadding = listBottomPadding,
                                onSongLongClick = { songToAdd = it },
                            )
                        }
                    }

                    // Inutile de le composer sous le lecteur plein écran, qui
                    // le recouvre entièrement.
                    if (!playerExpanded) {
                        MiniPlayer(
                            state = playerState,
                            onExpand = { playerExpanded = true },
                            onTogglePlayPause = playerViewModel::togglePlayPause,
                            onSkipNext = playerViewModel::skipNext,
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
                onTogglePlayPause = playerViewModel::togglePlayPause,
                onSkipNext = playerViewModel::skipNext,
                onSkipPrevious = playerViewModel::skipPrevious,
                onSeek = playerViewModel::seekTo,
                onToggleShuffle = playerViewModel::toggleShuffle,
                onCycleRepeat = playerViewModel::cycleRepeatMode,
            )
        }
    }

    songToAdd?.let { song ->
        AddToPlaylistSheet(
            song = song,
            playlists = playlistsState.playlists,
            songCountOf = { playlistsState.songs(it.id).size },
            onAddTo = { playlistsViewModel.addSong(it.id, song) },
            onCreateAndAdd = { name -> playlistsViewModel.createWith(name, song) },
            onDismiss = { songToAdd = null },
        )
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

/**
 * Titre de la barre du haut. Les écrans de détail se rabattent sur un libellé
 * générique tant que l'entité n'est pas résolue : la bibliothèque peut être
 * encore en cours de scan, ou le fichier avoir disparu depuis.
 */
private fun currentScreenTitle(
    currentRoute: String?,
    albumId: Long?,
    artistId: Long?,
    library: Library,
    playlistName: String?,
): String = when (currentRoute) {
    Routes.ALBUMS -> "Albums"
    Routes.ARTISTS -> "Artistes"
    Routes.PLAYLISTS -> "Playlists"
    Routes.ALBUM_DETAIL -> albumId?.let { library.album(it)?.title } ?: "Album"
    Routes.ARTIST_DETAIL -> artistId?.let { library.artist(it)?.name } ?: "Artiste"
    Routes.PLAYLIST_DETAIL -> playlistName ?: "Playlist"
    else -> "WaveFlow"
}
