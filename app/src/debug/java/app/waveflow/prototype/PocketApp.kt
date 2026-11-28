package app.waveflow.prototype

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.waveflow.model.Library
import app.waveflow.model.Song
import app.waveflow.ui.home.HomeUiState
import app.waveflow.ui.player.LyricsUiState
import app.waveflow.ui.player.PlayerUiState
import app.waveflow.ui.playlists.AddToPlaylistSheet
import app.waveflow.ui.playlists.PlaylistsUiState

/**
 * La coquille du prototype : l'onglet ouvert et sa pile de pages, la
 * navigation, la lecture, et Now Playing qui monte par-dessus. Trois
 * compositions, choisies par la largeur de la fenêtre ([WindowClass]) :
 *
 * - **compacte** — le dock au pied : le mini-lecteur et la barre de
 *   navigation d'un seul tenant ;
 * - **moyenne** — le rail à gauche, le mini-lecteur au pied de la page ;
 * - **étendue** — le rail, la page, et le panneau de lecture à droite, avec la
 *   suite de la file (sauf dans une fenêtre trop basse, qui garde le dock).
 *
 * La page ne passe jamais sous le dock : elle s'arrête au-dessus de lui.
 *
 * Toucher un onglet ramène à sa racine. La pile tient en chaînes
 * (`album:12`, voir [Page]) pour survivre à la recréation de l'activité.
 * La recherche n'est pas l'un des quatre écrans du prototype : son onglet le dit.
 */
@Composable
fun PocketApp(
    library: Library,
    home: HomeUiState,
    playlists: PlaylistsUiState,
    player: PlayerUiState,
    lyrics: LyricsUiState,
    actions: NowPlayingActions,
    verbs: PocketVerbs,
    messages: SnackbarHostState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    settingsPage: @Composable () -> Unit = {},
) {
    val (largeurFenetre, hauteurFenetre) = rememberWindowSize()
    val fenetre = windowClassOf(largeurFenetre)
    val panneau = showsPlaybackPanel(largeurFenetre, hauteurFenetre) && player.track != null
    var onglet by rememberSaveable { mutableStateOf(PocketTab.Accueil) }
    var categorie by rememberSaveable { mutableStateOf(LibraryCategory.Albums) }
    var pile by rememberSaveable { mutableStateOf(listOf<String>()) }
    var aAjouter by rememberSaveable { mutableStateOf<Long?>(null) }
    val colors = Pocket.colors
    val etats = rememberSaveableStateHolder()

    // Une page qu'on quitte par Retour oublie son état (son défilement) :
    // rouverte plus tard, elle repart en tête. Les onglets gardent le leur.
    fun depiler(jusqua: Int) {
        pile.drop(jusqua).forEach(etats::removeState)
        pile = pile.take(jusqua)
    }

    fun choisir(tab: PocketTab) {
        onglet = tab
        depiler(0)
    }

    fun ouvrir(page: Page) {
        pile = pile.pushedOnce(page.route)
    }
    val menu: (Song) -> List<RowAction> = { song ->
        listOf(
            RowAction("Ajouter à une playlist") { aAjouter = song.id },
            RowAction("Voir l'album") { ouvrir(Page.Album(song.albumId)) },
            RowAction("Voir l'artiste") { ouvrir(Page.Artiste(song.artistId)) },
        )
    }

    // Le lecteur déployé n'est visible qu'avec un morceau : Retour, la Snackbar
    // et l'animation suivent ce qui est à l'écran, pas l'intention.
    val lecteurVisible = expanded && player.track != null
    CollapseWhenQueueEmpties(hasTrack = player.track != null, onCollapse = { onExpandedChange(false) })
    BackHandlers(
        playerShown = lecteurVisible,
        pageOpen = pile.isNotEmpty(),
        onCollapse = { onExpandedChange(false) },
        onPop = { depiler(pile.size - 1) },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas),
    ) {
        Row(Modifier.fillMaxSize()) {
            if (fenetre != WindowClass.Compact) {
                PocketRail(
                    selected = onglet,
                    onSelect = ::choisir,
                    onSettings = { ouvrir(Page.Reglages) },
                    modifier = Modifier.background(colors.raised),
                )
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.weight(1f).windowInsetsPadding(pageInsets(fenetre))) {
                    val route = pile.lastOrNull()
                    val page = route?.let(Page::of)
                    // Chaque destination garde son état — son défilement — quand une
                    // page passe par-dessus : revenir d'un album ne remonte pas
                    // l'accueil en tête.
                    etats.SaveableStateProvider(route ?: onglet.name) {
                        Destination(
                            page = page,
                            onglet = onglet,
                            categorie = categorie,
                            library = library,
                            home = home,
                            playlists = playlists,
                            player = player,
                            verbs = verbs,
                            onTogglePlayPause = actions.onTogglePlayPause,
                            menu = menu,
                            onOpen = ::ouvrir,
                            onBack = { depiler(pile.size - 1) },
                            onCategory = { categorie = it },
                            onOpenCategory = {
                                categorie = it
                                onglet = PocketTab.Bibliotheque
                            },
                            showSettings = fenetre == WindowClass.Compact,
                            playbackShown = panneau,
                            settingsPage = settingsPage,
                            onExpandPlayer = { onExpandedChange(true) },
                        )
                    }
                    if (!lecteurVisible) SnackbarHost(messages, Modifier.align(Alignment.BottomCenter))
                }
                Dock(
                    // Le panneau, à droite, porte déjà la lecture.
                    player = player.takeUnless { panneau },
                    actions = actions,
                    onExpand = { onExpandedChange(true) },
                    // Le rail porte la navigation hors d'une fenêtre compacte.
                    tab = onglet.takeIf { fenetre == WindowClass.Compact },
                    onSelectTab = ::choisir,
                )
            }
            if (panneau) {
                PocketPlaybackPanel(
                    state = player,
                    actions = actions,
                    onExpand = { onExpandedChange(true) },
                    modifier = Modifier.width(PANEL_WIDTH),
                )
            }
        }

        AnimatedVisibility(
            visible = lecteurVisible,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            PocketNowPlaying(state = player, lyrics = lyrics, actions = actions)
        }

        // Le lecteur déployé recouvre la page : la Snackbar passe au-dessus de
        // lui, au-dessus des barres du système.
        if (lecteurVisible) {
            SnackbarHost(
                messages,
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            )
        }

        aAjouter?.let { library.songsById[it] }?.let { song ->
            AddToPlaylistSheet(
                song = song,
                playlists = playlists.playlists,
                songCountOf = { playlists.songs(it.id).size },
                onAddTo = {
                    verbs.onAddToPlaylist(it.id, song)
                    aAjouter = null
                },
                onCreateAndAdd = { nom ->
                    verbs.onCreatePlaylistWith(nom, song)
                    aAjouter = null
                },
                onDismiss = { aAjouter = null },
            )
        }
    }
}

/** La page ouverte : une collection empilée, ou la racine de l'onglet. */
@Composable
private fun Destination(
    page: Page?,
    onglet: PocketTab,
    categorie: LibraryCategory,
    library: Library,
    home: HomeUiState,
    playlists: PlaylistsUiState,
    player: PlayerUiState,
    verbs: PocketVerbs,
    onTogglePlayPause: () -> Unit,
    menu: (Song) -> List<RowAction>,
    onOpen: (Page) -> Unit,
    onBack: () -> Unit,
    onCategory: (LibraryCategory) -> Unit,
    onOpenCategory: (LibraryCategory) -> Unit,
    showSettings: Boolean,
    playbackShown: Boolean,
    settingsPage: @Composable () -> Unit,
    onExpandPlayer: () -> Unit,
) {
    when {
        page == Page.Reglages -> SettingsPage(onBack = onBack, content = settingsPage)
        page != null -> PageDeCollection(page, library, playlists, player, verbs, menu, onBack = onBack)
        onglet == PocketTab.Accueil -> PocketHome(
            state = home,
            library = library,
            player = player,
            playlistCount = playlists.playlists.size.takeUnless { playlists.isLoading },
            playbackShown = playbackShown,
            actions = HomeActions(
                onPlay = verbs.onPlay,
                onShuffle = verbs.onShuffle,
                onTogglePlayPause = onTogglePlayPause,
                onOpenAlbum = { onOpen(Page.Album(it)) },
                onOpenCategory = onOpenCategory,
                onAnotherSuggestion = verbs.onAnotherSuggestion,
                onExpandPlayer = onExpandPlayer,
                onSettings = if (showSettings) ({ onOpen(Page.Reglages) }) else null,
            ),
        )
        onglet == PocketTab.Bibliotheque -> PocketLibrary(
            library = library,
            playlists = playlists,
            player = player,
            category = categorie,
            onCategory = onCategory,
            actions = LibraryActions(
                onPlay = verbs.onPlay,
                onOpenAlbum = { onOpen(Page.Album(it)) },
                onOpenArtist = { onOpen(Page.Artiste(it)) },
                onOpenPlaylist = { onOpen(Page.Playlist(it)) },
                rowMenu = menu,
            ),
        )
        else -> AVenir(onglet)
    }
}

/**
 * Ce que la page évite des bords du système. Le rail tient le bord de début
 * hors d'une fenêtre compacte : la page n'évite alors que ce que le système
 * pose de l'autre côté (encoche, boutons en paysage).
 */
@Composable
private fun pageInsets(fenetre: WindowClass): WindowInsets =
    WindowInsets.navigationBars.union(WindowInsets.displayCutout).only(
        if (fenetre == WindowClass.Compact) WindowInsetsSides.Horizontal else WindowInsetsSides.End,
    )

/**
 * Le dock : le mini-lecteur, et sous lui, d'un seul tenant, la barre de
 * navigation du téléphone — un fond, aucune marge en double. Sans onglet
 * ([tab] nul : le rail porte la navigation), il s'écarte lui-même de la barre
 * du système. Sans morceau, il ne garde que la navigation ; sans morceau ni
 * onglet (ou [player] nul : le panneau porte la lecture), il ne garde que
 * l'écart de la barre du système, pour que la fin de la page n'y passe pas
 * dessous.
 */
@Composable
private fun Dock(
    player: PlayerUiState?,
    actions: NowPlayingActions,
    onExpand: () -> Unit,
    tab: PocketTab?,
    onSelectTab: (PocketTab) -> Unit,
) {
    if (player?.track == null && tab == null) {
        Spacer(Modifier.fillMaxWidth().navigationBarsPadding())
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Pocket.colors.raised)
            // Le fond court d'un bord à l'autre ; le contenu évite une barre du
            // système posée sur le côté (ou une encoche). Avec le rail, qui tient
            // déjà le bord de début, le dock n'évite que la fin et le bas ; sur
            // un téléphone, la barre de navigation garde le bas pour elle.
            .windowInsetsPadding(
                WindowInsets.navigationBars.union(WindowInsets.displayCutout).only(
                    if (tab == null) WindowInsetsSides.Bottom + WindowInsetsSides.End else WindowInsetsSides.Horizontal,
                ),
            ),
    ) {
        if (player != null) {
            PocketMiniPlayer(
                state = player,
                onExpand = onExpand,
                onTogglePlayPause = actions.onTogglePlayPause,
                onSkipNext = actions.onSkipNext,
                onSkipPrevious = actions.onSkipPrevious,
            )
        }
        if (tab != null) PocketNavBar(selected = tab, onSelect = onSelectTab)
    }
}

/**
 * Les réglages de l'application — ceux de la production, sous les jetons de
 * Pocket : le thème, le son, le serveur. Une page empilée, comme un album.
 */
@Composable
private fun SettingsPage(onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour", tint = Pocket.colors.ink)
            }
        }
        Text(
            "Réglages",
            style = MaterialTheme.typography.displaySmall,
            color = Pocket.colors.ink,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp).semantics { heading() },
        )
        Box(Modifier.weight(1f)) { content() }
    }
}

/**
 * Retour replie d'abord le lecteur déployé ; lecteur replié, il dépile la page
 * ouverte. Sur la racine d'un onglet, il revient au système.
 */
@Composable
private fun BackHandlers(playerShown: Boolean, pageOpen: Boolean, onCollapse: () -> Unit, onPop: () -> Unit) {
    BackHandler(enabled = playerShown, onBack = onCollapse)
    BackHandler(enabled = !playerShown && pageOpen, onBack = onPop)
}

/**
 * Replie le lecteur quand la file se vide : sans quoi il se redéploierait de
 * lui-même au morceau suivant. Un lecteur qui attend encore sa première piste
 * n'est pas replié.
 */
@Composable
private fun CollapseWhenQueueEmpties(hasTrack: Boolean, onCollapse: () -> Unit) {
    var avaitUnMorceau by remember { mutableStateOf(hasTrack) }
    LaunchedEffect(hasTrack) {
        if (avaitUnMorceau && !hasTrack) onCollapse()
        avaitUnMorceau = hasTrack
    }
}

/**
 * Le titre d'une collection que la page ne trouve pas : « introuvable »
 * seulement après une lecture réussie de sa source ; vide pendant qu'elle se
 * lit, ou quand elle a échoué — la page dit alors l'échec dessous.
 */
internal fun collectionTitleFallback(missing: String, loading: Boolean, error: String?): String =
    if (loading || error != null) "" else missing

@Composable
internal fun PageDeCollection(
    page: Page,
    library: Library,
    playlists: PlaylistsUiState,
    player: PlayerUiState,
    verbs: PocketVerbs,
    menu: (Song) -> List<RowAction>,
    onBack: () -> Unit,
) {
    val actions = CollectionActions(onBack = onBack, onPlay = verbs.onPlay, onShuffle = verbs.onShuffle, rowMenu = menu)
    when (page) {
        // Les réglages ont leur propre page (voir Destination).
        Page.Reglages -> Unit
        is Page.Album -> {
            val album = library.album(page.id)
            val titres = remember(library, page.id) {
                album?.let { library.songsOfAlbum(it.id) }.orEmpty()
            }
            PocketCollection(
                kind = CollectionKind.Album,
                loading = library.isLoading,
                error = library.errorMessage,
                title =
                album?.title
                    ?: collectionTitleFallback("Album introuvable", library.isLoading, library.errorMessage),
                subtitle = album?.displayArtist,
                artworkUri = album?.artworkUri,
                songs = titres,
                player = player,
                actions = actions,
            )
        }
        is Page.Artiste -> {
            val artiste = library.artist(page.id)
            val titres = remember(library, page.id) {
                artiste?.let { library.songsOfArtist(it.id) }.orEmpty()
            }
            PocketCollection(
                kind = CollectionKind.Artiste,
                loading = library.isLoading,
                error = library.errorMessage,
                title =
                artiste?.name
                    ?: collectionTitleFallback("Artiste introuvable", library.isLoading, library.errorMessage),
                subtitle = null,
                artworkUri = artiste?.artworkUri,
                songs = titres,
                player = player,
                actions = actions,
            )
        }
        is Page.Playlist -> {
            val playlist = playlists.playlist(page.id)
            val titres = playlists.songs(page.id)
            PocketCollection(
                kind = CollectionKind.Playlist,
                loading = playlists.isLoading || library.isLoading,
                error = playlists.errorMessage ?: library.errorMessage,
                title = playlist?.name ?: collectionTitleFallback(
                    "Playlist introuvable",
                    playlists.isLoading || library.isLoading,
                    playlists.errorMessage ?: library.errorMessage,
                ),
                subtitle = null,
                artworkUri = titres.firstOrNull()?.artworkUri,
                songs = titres,
                player = player,
                actions = actions,
            )
        }
    }
}

@Composable
private fun AVenir(onglet: PocketTab) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Text(onglet.label, style = MaterialTheme.typography.titleLarge, color = Pocket.colors.ink)
        Spacer(Modifier.height(8.dp))
        Text(
            "La recherche n'est pas l'un des quatre écrans du prototype : elle reste celle de l'application.",
            style = MaterialTheme.typography.bodyMedium,
            color = Pocket.colors.ink2,
        )
    }
}
