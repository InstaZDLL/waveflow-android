package app.waveflow.prototype

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.waveflow.model.Library
import app.waveflow.model.Song
import app.waveflow.ui.albumCountLabel
import app.waveflow.ui.components.Artwork
import app.waveflow.ui.player.PlayerUiState
import app.waveflow.ui.playlists.PlaylistsUiState
import app.waveflow.ui.trackCountLabel

/** Ce que la bibliothèque sait faire. */
data class LibraryActions(
    val onPlay: (queue: List<Song>, song: Song) -> Unit,
    val onOpenAlbum: (Long) -> Unit,
    val onOpenArtist: (Long) -> Unit,
    val onOpenPlaylist: (Long) -> Unit,
    val rowMenu: (Song) -> List<RowAction>,
)

/**
 * La bibliothèque de Pocket : un vrai navigateur de musique
 * (`docs/design/sonic-editorial.md`).
 *
 * Un titre d'écran, puis le sélecteur de catégorie — des pastilles qui
 * défilent, celle qui est ouverte pleine d'encre —, puis la catégorie : les
 * albums en grille (deux colonnes sur un téléphone, plus sur une tablette), les
 * artistes, les titres et les playlists en lignes (sur deux colonnes dès
 * qu'une ligne a la place). Une seule surface qui défile, des clés stables :
 * elle tient des milliers de titres.
 *
 * Le catalogue du serveur, sous-onglet de l'application, n'y est pas : le
 * prototype ne lit que l'appareil (voir le document).
 */
@Composable
fun PocketLibrary(
    library: Library,
    playlists: PlaylistsUiState,
    player: PlayerUiState,
    category: LibraryCategory,
    onCategory: (LibraryCategory) -> Unit,
    actions: LibraryActions,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val gouttiere = if (maxWidth < NARROW_WIDTH) 20.dp else 24.dp
        val colonnes = if (category == LibraryCategory.Albums) {
            GridCells.Adaptive(ALBUM_MIN_WIDTH)
        } else {
            GridCells.Adaptive(ROW_MIN_WIDTH)
        }
        val tuiles = category == LibraryCategory.Albums
        LazyVerticalGrid(
            columns = colonnes,
            contentPadding = PaddingValues(
                start = if (tuiles) gouttiere else 0.dp,
                end = if (tuiles) gouttiere else 0.dp,
                bottom = 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(if (tuiles) TILE_SPACING else 0.dp),
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            item(key = "titre", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(start = if (tuiles) 0.dp else gouttiere, top = 24.dp)) {
                    Text(
                        "Bibliothèque",
                        style = MaterialTheme.typography.displaySmall,
                        color = Pocket.colors.ink,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (library.songs.isNotEmpty()) {
                        Text(
                            "${trackCountLabel(library.songs.size)} · ${albumCountLabel(library.albums.size)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Pocket.colors.ink3,
                        )
                    }
                }
            }
            item(key = "categories", span = { GridItemSpan(maxLineSpan) }) {
                CategorySelector(
                    entries = LibraryCategory.entries,
                    selected = category,
                    label = { it.label },
                    onSelect = onCategory,
                    gutter = gouttiere,
                    // En grille, la gouttière est déjà celle de la grille : les
                    // pastilles débordent jusqu'au bord pour défiler sous le
                    // doigt, comme dans les autres catégories.
                    modifier = (if (tuiles) Modifier.bleed(gouttiere) else Modifier).padding(vertical = 12.dp),
                )
            }
            when {
                // Les playlists vivent dans la base de l'application, pas dans
                // le MediaStore : elles se montrent même sans morceau local.
                category == LibraryCategory.Playlists -> playlists(playlists, gouttiere, actions)
                library.isLoading -> message(
                    "Lecture de la bibliothèque…",
                    "Un instant : l'appareil compte ses morceaux.",
                )
                library.errorMessage != null -> message("La bibliothèque n'a pas pu être lue.", library.errorMessage)
                library.isEmpty -> message(
                    "Aucun morceau sur l'appareil.",
                    "Copiez de la musique dans le dossier Musique du téléphone : WaveFlow la trouvera.",
                )
                else -> when (category) {
                    LibraryCategory.Albums -> albums(library, actions)
                    LibraryCategory.Artistes -> artists(library, gouttiere, actions)
                    LibraryCategory.Titres -> tracks(library, player, gouttiere, actions)
                    LibraryCategory.Playlists -> Unit
                }
            }
        }
    }
}

private fun LazyGridScope.message(title: String, detail: String) {
    item(key = "message", span = { GridItemSpan(maxLineSpan) }) { EmptyState(title, detail) }
}

private fun LazyGridScope.albums(library: Library, actions: LibraryActions) {
    items(library.albums, key = { "album-${it.id}" }) { album ->
        // L'écart entre deux rangées vit dans la tuile : posé sur la grille,
        // il s'appliquait aussi au titre et au sélecteur, qui sautaient d'une
        // catégorie à l'autre.
        AlbumTile(
            title = album.title,
            subtitle = album.displayArtist,
            artworkUri = album.artworkUri,
            onClick = { actions.onOpenAlbum(album.id) },
            modifier = Modifier.padding(bottom = 16.dp),
        )
    }
}

private fun LazyGridScope.artists(library: Library, gouttiere: Dp, actions: LibraryActions) {
    items(library.artists, key = { "artiste-${it.id}" }) { artiste ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clickable(onClickLabel = "Ouvrir l'artiste") { actions.onOpenArtist(artiste.id) }
                .padding(horizontal = gouttiere, vertical = 8.dp),
        ) {
            ArtistPortrait(name = artiste.name, artworkUri = artiste.artworkUri, size = 56.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    artiste.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Pocket.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${albumCountLabel(artiste.albumCount)} · ${trackCountLabel(artiste.trackCount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Pocket.colors.ink2,
                )
            }
        }
    }
}

private fun LazyGridScope.tracks(library: Library, player: PlayerUiState, gouttiere: Dp, actions: LibraryActions) {
    items(library.songs, key = { "titre-${it.id}" }) { song ->
        TrackRow(
            title = song.title,
            subtitle = song.displayArtist,
            durationMs = song.durationMs,
            playing = player.track?.localSongId == song.id,
            artworkUri = song.artworkUri,
            onClick = { actions.onPlay(library.songs, song) },
            menu = actions.rowMenu(song),
            horizontalPadding = gouttiere,
        )
    }
}

private fun LazyGridScope.playlists(state: PlaylistsUiState, gouttiere: Dp, actions: LibraryActions) {
    state.errorMessage?.let { erreur ->
        message("Les playlists n'ont pas pu être lues.", erreur)
        return
    }
    if (!state.isLoading && state.playlists.isEmpty()) {
        message(
            "Aucune playlist.",
            "Ajoutez un titre à une playlist depuis son menu : elle se crée au passage.",
        )
        return
    }
    items(state.playlists, key = { "playlist-${it.id}" }) { playlist ->
        val titres = state.songs(playlist.id)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clickable(onClickLabel = "Ouvrir la playlist") { actions.onOpenPlaylist(playlist.id) }
                .padding(horizontal = gouttiere, vertical = 8.dp),
        ) {
            Artwork(titres.firstOrNull()?.artworkUri, shape = PocketShapes.thumbnail, modifier = Modifier.size(56.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Pocket.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    trackCountLabel(titres.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Pocket.colors.ink2,
                )
            }
        }
    }
}

/** Sous cette largeur de tuile, la grille perd une colonne : deux sur un téléphone. */
private val ALBUM_MIN_WIDTH = 150.dp

/** Une ligne a besoin de cette largeur : deux colonnes de lignes dès 680 dp. */
private val ROW_MIN_WIDTH = 340.dp
