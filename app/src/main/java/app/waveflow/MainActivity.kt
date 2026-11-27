package app.waveflow

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import app.waveflow.model.AppPreferences
import app.waveflow.model.Song
import app.waveflow.model.ThemeChoice
import app.waveflow.playback.voiceRequestOf
import app.waveflow.ui.browse.AlbumDetailScreen
import app.waveflow.ui.browse.AlbumsScreen
import app.waveflow.ui.browse.ArtistDetailScreen
import app.waveflow.ui.browse.ArtistsScreen
import app.waveflow.ui.cache.CacheViewModel
import app.waveflow.ui.home.HomeScreen
import app.waveflow.ui.home.HomeViewModel
import app.waveflow.ui.library.LibraryScreen
import app.waveflow.ui.library.LibraryViewModel
import app.waveflow.ui.navigation.LibraryTab
import app.waveflow.ui.navigation.LibraryTabs
import app.waveflow.ui.navigation.PAGE_HEADER_ROUTES
import app.waveflow.ui.navigation.Routes
import app.waveflow.ui.navigation.WaveFlowBottomBar
import app.waveflow.ui.navigation.openTabAsState
import app.waveflow.ui.navigation.rememberTabNavController
import app.waveflow.ui.navigation.selectLibraryTab
import app.waveflow.ui.navigation.selectTab
import app.waveflow.ui.navigation.topBarTitle
import app.waveflow.ui.permission.AudioPermissionGate
import app.waveflow.ui.permission.hasAudioPermission
import app.waveflow.ui.player.MiniPlayerHost
import app.waveflow.ui.player.NowPlayingScreen
import app.waveflow.ui.player.PlayerViewModel
import app.waveflow.ui.playlists.AddToPlaylistSheet
import app.waveflow.ui.playlists.PlaylistDetailScreen
import app.waveflow.ui.playlists.PlaylistMenu
import app.waveflow.ui.playlists.PlaylistsScreen
import app.waveflow.ui.playlists.PlaylistsViewModel
import app.waveflow.ui.quality.StreamQualityViewModel
import app.waveflow.ui.search.SearchField
import app.waveflow.ui.search.SearchScreen
import app.waveflow.ui.search.SearchViewModel
import app.waveflow.ui.server.ServerAccountScreen
import app.waveflow.ui.server.ServerSignInScreen
import app.waveflow.ui.server.ServerViewModel
import app.waveflow.ui.server.catalog.CatalogViewModel
import app.waveflow.ui.server.catalog.RemoteAlbumDetailScreen
import app.waveflow.ui.server.catalog.RemoteArtistDetailScreen
import app.waveflow.ui.server.catalog.ServerCatalogScreen
import app.waveflow.ui.settings.SettingsScreen
import app.waveflow.ui.settings.SettingsViewModel
import app.waveflow.ui.theme.WaveFlowTheme

class MainActivity : ComponentActivity() {

    /**
     * Le même que celui de l'écran : `viewModel()` le retrouve dans le magasin
     * de l'activité sous la même clé. Il ne sert ici qu'à relayer une demande
     * vocale.
     */
    private val playerViewModel: PlayerViewModel by viewModels { PlayerViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Une activité recréée (rotation, retour d'un processus tué) garde
        // l'intention qui l'a lancée : la demande vocale a déjà été relayée.
        // Rouverte depuis les récents, elle la reçoit aussi, marquée cette
        // fois : `voiceRequestOf` l'écarte.
        if (savedInstanceState == null) relayVoiceSearch(intent)
        enableEdgeToEdge()
        setContent {
            // Au-dessus du thème, et non dans un écran : le choix habille toute
            // l'application, pas la page où on le fait.
            val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
            val preferences by settingsViewModel.preferences.collectAsStateWithLifecycle()

            WaveFlowTheme(theme = preferences.theme) {
                WaveFlowRoot(
                    preferences = preferences,
                    onThemeChange = settingsViewModel::setTheme,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Une demande vocale attend la liaison au service (R10 de
        // `docs/recherche-vocale.md`). Seuls les écrans gardés par la
        // permission audio la lancent ; Réglages ou Serveur n'en sont pas. La
        // liaison suit donc la permission, quel que soit l'écran : au premier
        // plan, au retour des paramètres système, après une recréation.
        // `connect` est idempotent.
        if (hasAudioPermission()) playerViewModel.connect()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        relayVoiceSearch(intent)
    }

    /**
     * « Joue Nevermind » dit à l'Assistant du téléphone : l'intention
     * `MEDIA_PLAY_FROM_SEARCH` est transmise au service, qui la résout comme
     * celle d'Android Auto (R10 de `docs/recherche-vocale.md`).
     *
     * La demande part quand l'activité lie le service, à sa reprise
     * ([onResume]) si la permission audio est accordée. Sans elle, la demande
     * attend : la recherche ne lit que la bibliothèque de l'appareil (R1), et
     * ne trouverait rien.
     */
    private fun relayVoiceSearch(intent: Intent?) {
        voiceRequestOf(intent)?.let(playerViewModel::playFromSearch)
    }
}

/** Routes affichant une flèche de retour. */
private val DETAIL_ROUTES = PAGE_HEADER_ROUTES + setOf(
    Routes.SERVER_ACCOUNT,
    Routes.SETTINGS,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WaveFlowRoot(
    preferences: AppPreferences,
    onThemeChange: (ThemeChoice) -> Unit,
) {
    val libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
    val playerViewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory)
    val playlistsViewModel: PlaylistsViewModel = viewModel(factory = PlaylistsViewModel.Factory)
    val searchViewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory)
    val serverViewModel: ServerViewModel = viewModel(factory = ServerViewModel.Factory)
    val catalogViewModel: CatalogViewModel = viewModel(factory = CatalogViewModel.Factory)
    val cacheViewModel: CacheViewModel = viewModel(factory = CacheViewModel.Factory)
    val qualityViewModel: StreamQualityViewModel = viewModel(factory = StreamQualityViewModel.Factory)
    // La même instance que celle qui habille le thème : demandée ici, au-dessus
    // du NavHost, elle est rangée chez l'activité comme l'autre.
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)

    val library by libraryViewModel.library.collectAsStateWithLifecycle()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val playlistsState by playlistsViewModel.state.collectAsStateWithLifecycle()
    val searchQuery by searchViewModel.query.collectAsStateWithLifecycle()
    val searchResults by searchViewModel.results.collectAsStateWithLifecycle()
    val serverState by serverViewModel.state.collectAsStateWithLifecycle()
    val cacheState by cacheViewModel.state.collectAsStateWithLifecycle()
    // Les états du catalogue sont collectés dans leurs destinations, et non
    // ici : chargés à la racine, chaque page reçue recomposerait le Scaffold,
    // le NavHost et tous les écrans.

    val navController = rememberTabNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // L'onglet allumé suit la pile, pas la route : un album ouvert depuis
    // l'accueil vit dans la pile de l'accueil.
    val openTab by navController.openTabAsState()

    var playerExpanded by rememberSaveable { mutableStateOf(false) }
    var songToAdd by remember { mutableStateOf<Song?>(null) }

    // La recherche est une destination, plus un mode : c'est la route qui dit
    // si l'on cherche, et non un drapeau qu'il faudrait tenir en accord avec
    // elle.
    val searchActive = currentRoute == Routes.SEARCH

    val nowPlayingId = playerState.track?.localSongId
    val hasTrack = playerState.track != null

    // Les écritures de playlist qui échouent se signalent une fois, sans
    // remplacer le contenu de l'écran.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        playlistsViewModel.errors.collect { snackbarHostState.showSnackbar(it) }
    }

    // Une lecture qui échoue ne se voit nulle part ailleurs : le lecteur garde
    // la piste affichée, à l'arrêt, sans rien dire de plus.
    LaunchedEffect(Unit) {
        playerViewModel.errors.collect { snackbarHostState.showSnackbar(it) }
    }

    // Si la file se vide, il n'y a plus rien à afficher en plein écran.
    LaunchedEffect(hasTrack) {
        if (!hasTrack) playerExpanded = false
    }

    BackHandler(enabled = playerExpanded) { playerExpanded = false }

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
                Column {
                    TopAppBar(
                        title = {
                            if (searchActive) {
                                SearchField(
                                    query = searchQuery,
                                    onQueryChange = searchViewModel::onQueryChange,
                                )
                            } else {
                                topBarTitle(currentRoute)?.let { Text(it) }
                            }
                        },
                        navigationIcon = {
                            when {
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
                                // Les réglages s'ouvrent d'où qu'on vienne : ils
                                // gouvernent l'application, ils n'appartiennent pas
                                // à une section. Le compte du serveur s'y trouve
                                // désormais, plutôt que dans l'en-tête d'un onglet.
                                if (currentRoute != Routes.SETTINGS) {
                                    IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                                        Icon(
                                            imageVector = Icons.Filled.Settings,
                                            contentDescription = "Réglages",
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

                    // Sous la barre du haut, et seulement dans la bibliothèque :
                    // ces onglets disent comment on la regarde, pas où l'on est.
                    if (!searchActive) {
                        LibraryTabs(
                            openTab = openTab,
                            currentRoute = currentRoute,
                            onSelect = navController::selectLibraryTab,
                        )
                    }
                }
            },
            bottomBar = {
                WaveFlowBottomBar(
                    selected = openTab?.destination,
                    onSelect = navController::selectTab,
                )
            },
        ) { innerPadding ->
            /**
             * Porte de permission audio, posée autour du seul contenu qui lit
             * la bibliothèque de l'appareil.
             *
             * Elle enveloppe chaque écran concerné plutôt que le `NavHost` :
             * l'onglet Serveur n'a besoin d'aucune permission, et sortir le
             * `NavHost` de la porte le recomposerait dans un autre sous-arbre,
             * lui faisant perdre sa pile de navigation.
             *
             * Sans modificateur : les marges du `Scaffold` sont déjà posées par
             * le `Box` qui l'englobe, les redonner ici les doublerait sur le
             * message de refus.
             */
            val gated: @Composable (@Composable () -> Unit) -> Unit = { content ->
                AudioPermissionGate {
                    // Ne démarre le scan et la connexion au service qu'une fois
                    // la permission acquise. Les deux appels sont idempotents,
                    // ce qui permet de les relancer à chaque écran gardé.
                    LaunchedEffect(Unit) {
                        libraryViewModel.onAudioAccessGranted()
                        playerViewModel.connect()
                    }
                    content()
                }
            }

            MiniPlayerHost(
                state = playerState,
                // Inutile de le composer sous le lecteur plein écran, qui le
                // recouvre entièrement.
                showMiniPlayer = !playerExpanded,
                onExpand = { playerExpanded = true },
                onTogglePlayPause = playerViewModel::togglePlayPause,
                onSkipNext = playerViewModel::skipNext,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) { listBottomPadding ->
                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable(Routes.HOME) {
                        gated {
                            val homeViewModel: HomeViewModel =
                                viewModel(factory = HomeViewModel.Factory)
                            val homeState by homeViewModel.state.collectAsStateWithLifecycle()

                            HomeScreen(
                                state = homeState,
                                onSongClick = { playerViewModel.playFrom(library.songs, it) },
                                onAlbumClick = {
                                    navController.navigate(Routes.albumDetail(it.id))
                                },
                                bottomPadding = listBottomPadding,
                            )
                        }
                    }

                    composable(Routes.SEARCH) {
                        gated {
                            SearchScreen(
                                query = searchQuery,
                                results = searchResults,
                                nowPlayingId = nowPlayingId,
                                // La file de lecture est la liste affichée :
                                // enchaîner sur les résultats suivants est le
                                // comportement attendu.
                                onSongClick = {
                                    playerViewModel.playFrom(searchResults.songs, it)
                                },
                                onAlbumClick = {
                                    navController.navigate(Routes.albumDetail(it.id))
                                },
                                onArtistClick = {
                                    navController.navigate(Routes.artistDetail(it.id))
                                },
                                bottomPadding = listBottomPadding,
                                onSongLongClick = { songToAdd = it },
                                // Proposé seulement quand un serveur est
                                // connecté : sinon le bouton mènerait à un écran
                                // de connexion, que l'utilisateur n'a pas demandé.
                                onSearchOnServer = serverState.connected?.let {
                                    {
                                        catalogViewModel.onSearchQueryChange(searchQuery)
                                        navController.selectLibraryTab(LibraryTab.Server)
                                    }
                                },
                            )
                        }
                    }

                    composable(Routes.SONGS) {
                        gated {
                            LibraryScreen(
                                library = library,
                                nowPlayingId = nowPlayingId,
                                onSongClick = { playerViewModel.playFrom(library.songs, it) },
                                onRetry = libraryViewModel::retry,
                                bottomPadding = listBottomPadding,
                                onSongLongClick = { songToAdd = it },
                            )
                        }
                    }

                    composable(Routes.ALBUMS) {
                        gated {
                            AlbumsScreen(
                                library = library,
                                onAlbumClick = { navController.navigate(Routes.albumDetail(it.id)) },
                                onRetry = libraryViewModel::retry,
                                bottomPadding = listBottomPadding,
                            )
                        }
                    }

                    composable(Routes.ARTISTS) {
                        gated {
                            ArtistsScreen(
                                library = library,
                                onArtistClick = { navController.navigate(Routes.artistDetail(it.id)) },
                                onRetry = libraryViewModel::retry,
                                bottomPadding = listBottomPadding,
                            )
                        }
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

                        gated {
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
                    }

                    composable(
                        route = Routes.ARTIST_DETAIL,
                        arguments = listOf(navArgument(Routes.ARG_ARTIST_ID) { type = NavType.LongType }),
                    ) { entry ->
                        val artistId = entry.arguments?.getLong(Routes.ARG_ARTIST_ID) ?: return@composable
                        val songs = remember(library, artistId) { library.songsOfArtist(artistId) }

                        gated {
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
                    }

                    composable(Routes.SERVER) {
                        // Connecté, l'onglet montre le catalogue ; le compte
                        // devient un écran qu'on ouvre depuis la barre du haut.
                        val connected = serverState.connected
                        if (connected == null) {
                            ServerSignInScreen(
                                state = serverState,
                                onConnect = serverViewModel::connect,
                                bottomPadding = listBottomPadding,
                            )
                        } else {
                            val remoteAlbums by catalogViewModel.albums
                                .collectAsStateWithLifecycle()
                            val remoteArtists by catalogViewModel.artists
                                .collectAsStateWithLifecycle()
                            val remoteSearch by catalogViewModel.search
                                .collectAsStateWithLifecycle()

                            ServerCatalogScreen(
                                albums = remoteAlbums,
                                artists = remoteArtists,
                                search = remoteSearch,
                                onSearchQueryChange = catalogViewModel::onSearchQueryChange,
                                // Un résultat de recherche se lit seul : la file
                                // est ce morceau, faute de contexte d'album.
                                onSongClick = { playerViewModel.playRemoteFrom(listOf(it), it) },
                                onAlbumClick = {
                                    navController.navigate(Routes.serverAlbumDetail(it.id))
                                },
                                onArtistClick = {
                                    navController.navigate(Routes.serverArtistDetail(it.id))
                                },
                                onLoadMoreAlbums = catalogViewModel::loadMoreAlbums,
                                onLoadMoreArtists = catalogViewModel::loadMoreArtists,
                                onRetryAlbums = catalogViewModel::retryAlbums,
                                onRetryArtists = catalogViewModel::retryArtists,
                                bottomPadding = listBottomPadding,
                            )
                        }
                    }

                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            preferences = preferences,
                            isServerConnected = serverState.isConnected,
                            serverSummary = serverState.connected?.serverUrl,
                            appVersion = BuildConfig.VERSION_NAME,
                            onThemeChange = onThemeChange,
                            onSonChange = settingsViewModel::regler,
                            // Connecté, la ligne mène au compte ; sinon à
                            // l'écran qui permet de s'y connecter. Une seule
                            // ligne pour les deux : c'est le même sujet.
                            onOpenServer = {
                                navController.navigate(
                                    if (serverState.isConnected) {
                                        Routes.SERVER_ACCOUNT
                                    } else {
                                        Routes.SERVER
                                    },
                                )
                            },
                            bottomPadding = listBottomPadding,
                        )
                    }

                    composable(Routes.SERVER_ACCOUNT) {
                        serverState.connected?.let { session ->
                            val qualityState by qualityViewModel.state.collectAsStateWithLifecycle()

                            // La taille bouge à chaque piste lue : elle se relève
                            // à l'ouverture, pas une fois pour toutes.
                            LaunchedEffect(Unit) {
                                cacheViewModel.refresh()
                            }

                            ServerAccountScreen(
                                session = session,
                                quality = qualityState,
                                cache = cacheState,
                                onDisconnect = {
                                    serverViewModel.disconnect()
                                    navController.popBackStack()
                                },
                                onChooseQuality = qualityViewModel::choose,
                                onChooseAutomaticQuality = qualityViewModel::chooseAutomatic,
                                onChooseQualityOn = qualityViewModel::chooseOn,
                                onClearCache = cacheViewModel::clear,
                                onDismissCacheError = cacheViewModel::dismissError,
                                bottomPadding = listBottomPadding,
                            )
                        }
                    }

                    composable(
                        route = Routes.SERVER_ALBUM_DETAIL,
                        arguments = listOf(navArgument(Routes.ARG_ALBUM_ID) { type = NavType.StringType }),
                    ) { entry ->
                        val albumId = entry.arguments?.getString(Routes.ARG_ALBUM_ID)
                            ?: return@composable
                        LaunchedEffect(albumId) { catalogViewModel.openAlbum(albumId) }
                        val detail by catalogViewModel.albumDetail.collectAsStateWithLifecycle()

                        RemoteAlbumDetailScreen(
                            state = detail,
                            nowPlayingMediaId = playerState.track?.mediaId,
                            // La file de lecture est l'album affiché, comme
                            // pour un album local.
                            onSongClick = { song ->
                                detail.value?.songs?.let {
                                    playerViewModel.playRemoteFrom(it, song)
                                }
                            },
                            onPlay = {
                                detail.value?.songs?.let(playerViewModel::playRemoteFirst)
                            },
                            onShuffle = {
                                detail.value?.songs?.let(playerViewModel::playRemoteShuffled)
                            },
                            onRetry = { catalogViewModel.openAlbum(albumId) },
                            bottomPadding = listBottomPadding,
                        )
                    }

                    composable(
                        route = Routes.SERVER_ARTIST_DETAIL,
                        arguments = listOf(navArgument(Routes.ARG_ARTIST_ID) { type = NavType.StringType }),
                    ) { entry ->
                        val artistId = entry.arguments?.getString(Routes.ARG_ARTIST_ID)
                            ?: return@composable
                        LaunchedEffect(artistId) { catalogViewModel.openArtist(artistId) }
                        val detail by catalogViewModel.artistDetail.collectAsStateWithLifecycle()

                        RemoteArtistDetailScreen(
                            state = detail,
                            onAlbumClick = {
                                navController.navigate(Routes.serverAlbumDetail(it.id))
                            },
                            onRetry = { catalogViewModel.openArtist(artistId) },
                            bottomPadding = listBottomPadding,
                        )
                    }

                    composable(Routes.PLAYLISTS) {
                        gated {
                            PlaylistsScreen(
                                state = playlistsState,
                                onPlaylistClick = { navController.navigate(Routes.playlistDetail(it.id)) },
                                onCreatePlaylist = playlistsViewModel::create,
                                bottomPadding = listBottomPadding,
                            )
                        }
                    }

                    composable(
                        route = Routes.PLAYLIST_DETAIL,
                        arguments = listOf(navArgument(Routes.ARG_PLAYLIST_ID) { type = NavType.LongType }),
                    ) { entry ->
                        val playlistId = entry.arguments?.getLong(Routes.ARG_PLAYLIST_ID) ?: return@composable
                        val songs = playlistsState.songs(playlistId)

                        gated {
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
                }
            }
        }

        // Le lecteur plein écran est posé par-dessus le Scaffold (et donc
        // par-dessus les barres), comme une feuille qui remonte.
        AnimatedVisibility(
            visible = playerExpanded,
            enter = slideInVertically(animationSpec = WaveFlowTheme.motion.shape()) { it },
            exit = slideOutVertically(animationSpec = WaveFlowTheme.motion.shape()) { it },
            modifier = Modifier.fillMaxSize(),
        ) {
            // Observées le temps que le lecteur est ouvert, et cherchées
            // seulement quand elles remplacent la pochette (P6 de
            // `docs/paroles.md`).
            val lyricsState by playerViewModel.lyrics.collectAsStateWithLifecycle()
            NowPlayingScreen(
                state = playerState,
                onCollapse = { playerExpanded = false },
                onTogglePlayPause = playerViewModel::togglePlayPause,
                onSkipNext = playerViewModel::skipNext,
                onSkipPrevious = playerViewModel::skipPrevious,
                onSeek = playerViewModel::seekTo,
                onToggleShuffle = playerViewModel::toggleShuffle,
                onCycleRepeat = playerViewModel::cycleRepeatMode,
                onPlayQueueItem = playerViewModel::playQueueItem,
                onMoveQueueItem = playerViewModel::moveQueueItem,
                onRemoveQueueItem = playerViewModel::removeQueueItem,
                onStartSleepTimer = playerViewModel::startSleepTimer,
                onCancelSleepTimer = playerViewModel::cancelSleepTimer,
                onSleepTimerRemainingMs = playerViewModel::sleepTimerRemainingMs,
                onSetPlaybackSpeed = playerViewModel::setPlaybackSpeed,
                onMarkAbLoop = playerViewModel::markAbLoop,
                lyrics = lyricsState,
                onToggleLyrics = playerViewModel::toggleLyrics,
                onRetryLyrics = playerViewModel::retryLyrics,
                onLyricsOnScreen = playerViewModel::lyricsOnScreen,
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
