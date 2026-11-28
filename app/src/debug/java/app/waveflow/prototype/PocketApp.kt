package app.waveflow.prototype

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.waveflow.model.Library
import app.waveflow.model.Song
import app.waveflow.ui.home.HomeUiState
import app.waveflow.ui.player.LyricsUiState
import app.waveflow.ui.player.PlayerUiState
import app.waveflow.ui.playlists.AddToPlaylistSheet
import app.waveflow.ui.playlists.PlaylistsUiState

/** Ce que la coquille relaie au lecteur et aux playlists de production. */
data class PocketVerbs(
    val onPlay: (queue: List<Song>, song: Song) -> Unit,
    val onShuffle: (List<Song>) -> Unit,
    val onAnotherSuggestion: () -> Unit,
    val onAddToPlaylist: (playlistId: Long, song: Song) -> Unit,
    val onCreatePlaylistWith: (name: String, song: Song) -> Unit,
)

/**
 * La coquille du prototype : l'onglet ouvert et sa pile de pages, le
 * mini-lecteur posé sur la barre de navigation, et Now Playing qui monte
 * par-dessus.
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
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    var onglet by rememberSaveable { mutableStateOf(PocketTab.Accueil) }
    var categorie by rememberSaveable { mutableStateOf(LibraryCategory.Albums) }
    var pile by rememberSaveable { mutableStateOf(listOf<String>()) }
    var aAjouter by rememberSaveable { mutableStateOf<Long?>(null) }
    val colors = Pocket.colors

    fun ouvrir(page: Page) {
        pile = pile + page.route
    }
    val menu: (Song) -> List<RowAction> = { song ->
        listOf(
            RowAction("Ajouter à une playlist") { aAjouter = song.id },
            RowAction("Voir l'album") { ouvrir(Page.Album(song.albumId)) },
            RowAction("Voir l'artiste") { ouvrir(Page.Artiste(song.artistId)) },
        )
    }

    BackHandler(enabled = expanded) { onExpandedChange(false) }
    BackHandler(enabled = !expanded && pile.isNotEmpty()) { pile = pile.dropLast(1) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                val page = pile.lastOrNull()?.let(Page::of)
                when {
                    page != null -> PageDeCollection(page, library, playlists, player, verbs, menu, onBack = {
                        pile =
                            pile.dropLast(1)
                    })
                    onglet == PocketTab.Accueil -> PocketHome(
                        state = home,
                        library = library,
                        player = player,
                        actions = HomeActions(
                            onPlay = verbs.onPlay,
                            onShuffle = verbs.onShuffle,
                            onTogglePlayPause = actions.onTogglePlayPause,
                            onOpenAlbum = { ouvrir(Page.Album(it)) },
                            onOpenCategory = {
                                categorie = it
                                onglet = PocketTab.Bibliotheque
                            },
                            onAnotherSuggestion = verbs.onAnotherSuggestion,
                        ),
                    )
                    onglet == PocketTab.Bibliotheque -> PocketLibrary(
                        library = library,
                        playlists = playlists,
                        player = player,
                        category = categorie,
                        onCategory = { categorie = it },
                        actions = LibraryActions(
                            onPlay = verbs.onPlay,
                            onOpenAlbum = { ouvrir(Page.Album(it)) },
                            onOpenArtist = { ouvrir(Page.Artiste(it)) },
                            onOpenPlaylist = { ouvrir(Page.Playlist(it)) },
                            rowMenu = menu,
                        ),
                    )
                    else -> AVenir(onglet)
                }
            }
            PocketMiniPlayer(
                state = player,
                onExpand = { onExpandedChange(true) },
                onTogglePlayPause = actions.onTogglePlayPause,
                onSkipNext = actions.onSkipNext,
                onSkipPrevious = actions.onSkipPrevious,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            PocketNavBar(
                selected = onglet,
                onSelect = {
                    onglet = it
                    pile = emptyList()
                },
            )
        }

        AnimatedVisibility(
            visible = expanded && player.track != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            PocketNowPlaying(state = player, lyrics = lyrics, actions = actions)
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

/** Une page empilée au-dessus d'un onglet, codée en chaîne pour l'état sauvegardé. */
sealed interface Page {
    val route: String

    data class Album(val id: Long) : Page {
        override val route get() = "album:$id"
    }

    data class Artiste(val id: Long) : Page {
        override val route get() = "artiste:$id"
    }

    data class Playlist(val id: Long) : Page {
        override val route get() = "playlist:$id"
    }

    companion object {
        /** La page de [route], ou `null` si la chaîne n'en décrit aucune. */
        fun of(route: String): Page? {
            val (sorte, valeur) = route.split(':', limit = 2).takeIf { it.size == 2 } ?: return null
            val id = valeur.toLongOrNull() ?: return null
            return when (sorte) {
                "album" -> Album(id)
                "artiste" -> Artiste(id)
                "playlist" -> Playlist(id)
                else -> null
            }
        }
    }
}

@Composable
private fun PageDeCollection(
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
        is Page.Album -> {
            val album = library.album(page.id)
            PocketCollection(
                kind = CollectionKind.Album,
                title = album?.title ?: "Album introuvable",
                subtitle = album?.displayArtist,
                artworkUri = album?.artworkUri,
                songs = if (album != null) library.songsOfAlbum(album.id) else emptyList(),
                player = player,
                actions = actions,
            )
        }
        is Page.Artiste -> {
            val artiste = library.artist(page.id)
            PocketCollection(
                kind = CollectionKind.Artiste,
                title = artiste?.name ?: "Artiste introuvable",
                subtitle = null,
                artworkUri = artiste?.artworkUri,
                songs = if (artiste != null) library.songsOfArtist(artiste.id) else emptyList(),
                player = player,
                actions = actions,
            )
        }
        is Page.Playlist -> {
            val playlist = playlists.playlist(page.id)
            val titres = playlists.songs(page.id)
            PocketCollection(
                kind = CollectionKind.Playlist,
                title = playlist?.name ?: "Playlist introuvable",
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
