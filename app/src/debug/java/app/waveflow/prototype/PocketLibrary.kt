package app.waveflow.prototype

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.waveflow.model.Album
import app.waveflow.model.Artist
import app.waveflow.model.Library
import app.waveflow.model.Song
import app.waveflow.ui.albumCountLabel
import app.waveflow.ui.components.Artwork
import app.waveflow.ui.player.PlayerUiState
import app.waveflow.ui.playlists.PlaylistsUiState
import app.waveflow.ui.trackCountLabel

/**
 * La bibliothèque de Pocket : un vrai navigateur de musique
 * (`docs/design/sonic-editorial.md`).
 *
 * Un titre d'écran, puis le sélecteur de catégorie — des pastilles qui
 * défilent, celle qui est ouverte pleine d'encre —, la rangée du compte et du
 * tri, puis la catégorie : les
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
    // L'ordre choisi par catégorie : changer de catégorie ne l'oublie pas.
    var tris by rememberSaveable { mutableStateOf(mapOf<LibraryCategory, SortOrder>()) }
    val ordre = tris[category] ?: sortOrdersFor(category).firstOrNull() ?: SortOrder.Titre
    // Trié une fois par bibliothèque et par ordre, pas à chaque tic du lecteur.
    val vue = remember(library, category, ordre) { library.sorted(category, ordre) }
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
                Text(
                    "Bibliothèque",
                    style = MaterialTheme.typography.displaySmall,
                    color = Pocket.colors.ink,
                    modifier = Modifier
                        .padding(start = if (tuiles) 0.dp else gouttiere, top = 20.dp)
                        .semantics { heading() },
                )
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
                    modifier = (if (tuiles) Modifier.bleed(gouttiere) else Modifier).padding(vertical = 4.dp),
                )
            }
            item(key = "tri", span = { GridItemSpan(maxLineSpan) }) {
                SortRow(
                    count = countLabel(category, library, playlists),
                    orders = sortOrdersFor(category),
                    selected = ordre,
                    onSelect = { tris = tris + (category to it) },
                    modifier = Modifier.padding(horizontal = if (tuiles) 0.dp else gouttiere),
                )
            }
            if (category == LibraryCategory.Playlists) {
                // Les playlists vivent dans la base de l'application, pas dans
                // le MediaStore : elles se montrent même sans morceau local.
                playlists(playlists, gouttiere, actions)
            } else {
                // Les titres sont la seule catégorie qui lit le lecteur (la
                // ligne qui joue) : ils arrivent tout faits.
                category(category, vue, gouttiere, actions) { tracks(vue, player, gouttiere, actions) }
            }
        }
    }
}

/** Une catégorie des morceaux locaux, ou ce qui l'empêche d'avoir du contenu. */
private fun LazyGridScope.category(
    category: LibraryCategory,
    vue: SortedLibrary,
    gouttiere: Dp,
    actions: LibraryActions,
    trackRows: LazyGridScope.() -> Unit,
) {
    val library = vue.library
    val erreur = library.errorMessage
    when {
        library.isLoading -> message("Lecture de la bibliothèque…", "Un instant : l'appareil compte ses morceaux.")
        erreur != null -> message("La bibliothèque n'a pas pu être lue.", erreur)
        library.isEmpty -> message(
            "Aucun morceau sur l'appareil.",
            "Copiez de la musique dans le dossier Musique du téléphone : WaveFlow la trouvera.",
        )
        category == LibraryCategory.Albums -> albums(vue.albums, actions)
        category == LibraryCategory.Artistes -> artists(vue.artists, gouttiere, actions)
        else -> trackRows()
    }
}

/**
 * La bibliothèque vue dans l'ordre [order] pour [category] : seule la liste de
 * la catégorie est triée, le reste est celui de la bibliothèque.
 */
private fun Library.sorted(category: LibraryCategory, order: SortOrder): SortedLibrary = SortedLibrary(
    library = this,
    albums = if (category == LibraryCategory.Albums) albums.sortedFor(order) else albums,
    artists = if (category == LibraryCategory.Artistes) artists.sortedFor(order) else artists,
    songs = if (category == LibraryCategory.Titres) songs.sortedFor(order) else songs,
)

/** Le compte de la catégorie ouverte, pour la rangée du tri. */
private fun countLabel(category: LibraryCategory, library: Library, playlists: PlaylistsUiState): String? = when {
    category == LibraryCategory.Playlists -> playlists.playlists.size.takeUnless { playlists.isLoading }?.let {
        "$it ${if (it > 1) "playlists" else "playlist"}"
    }
    library.isLoading || library.isEmpty -> null
    category == LibraryCategory.Albums -> albumCountLabel(library.albums.size)
    category == LibraryCategory.Artistes ->
        "${library.artists.size} ${if (library.artists.size > 1) "artistes" else "artiste"}"
    else -> trackCountLabel(library.songs.size)
}

/**
 * La rangée sous le sélecteur : le compte de la catégorie, et son tri — un
 * menu des ordres que les données permettent. Sans ordre à choisir (les
 * playlists), le compte seul.
 */
@Composable
private fun SortRow(
    count: String?,
    orders: List<SortOrder>,
    selected: SortOrder,
    onSelect: (SortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    var ouvert by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth().heightIn(min = 40.dp)) {
        Text(
            count.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = Pocket.colors.ink3,
            modifier = Modifier.weight(1f),
        )
        if (orders.size > 1) {
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .clip(PocketShapes.action)
                        .clickable(role = Role.Button, onClickLabel = "Changer le tri") { ouvert = true }
                        .padding(start = 10.dp, end = 4.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = null,
                        tint = Pocket.colors.ink2,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(selected.label, style = MaterialTheme.typography.labelLarge, color = Pocket.colors.ink2)
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = Pocket.colors.ink3)
                }
                DropdownMenu(
                    expanded = ouvert,
                    onDismissRequest = { ouvert = false },
                    shape = MaterialTheme.shapes.medium,
                    containerColor = Pocket.colors.floating,
                ) {
                    orders.forEach { ordre ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    ordre.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (ordre == selected) Pocket.colors.accent else Pocket.colors.ink,
                                )
                            },
                            onClick = {
                                ouvert = false
                                onSelect(ordre)
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun LazyGridScope.message(title: String, detail: String) {
    item(key = "message", span = { GridItemSpan(maxLineSpan) }) { EmptyState(title, detail) }
}

private fun LazyGridScope.albums(albums: List<Album>, actions: LibraryActions) {
    items(albums, key = { "album-${it.id}" }) { album ->
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

private fun LazyGridScope.artists(artists: List<Artist>, gouttiere: Dp, actions: LibraryActions) {
    items(artists, key = { "artiste-${it.id}" }) { artiste ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ROW_HEIGHT)
                .clickable(onClickLabel = "Ouvrir l'artiste") { actions.onOpenArtist(artiste.id) }
                .padding(horizontal = gouttiere, vertical = 6.dp),
        ) {
            ArtistPortrait(name = artiste.name, artworkUri = artiste.artworkUri, size = 44.dp)
            Spacer(Modifier.width(14.dp))
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
                    style = MaterialTheme.typography.bodySmall,
                    color = Pocket.colors.ink2,
                )
            }
        }
    }
}

private fun LazyGridScope.tracks(vue: SortedLibrary, player: PlayerUiState, gouttiere: Dp, actions: LibraryActions) {
    // La file suit l'ordre affiché : on lance la liste qu'on voit.
    val file = vue.songs
    items(file, key = { "titre-${it.id}" }) { song ->
        TrackRow(
            title = song.title,
            subtitle = song.displayArtist,
            durationMs = song.durationMs,
            current = player.track?.localSongId == song.id,
            artworkUri = song.artworkUri,
            onClick = { actions.onPlay(file, song) },
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
                .heightIn(min = ROW_HEIGHT)
                .clickable(onClickLabel = "Ouvrir la playlist") { actions.onOpenPlaylist(playlist.id) }
                .padding(horizontal = gouttiere, vertical = 6.dp),
        ) {
            Artwork(titres.firstOrNull()?.artworkUri, shape = PocketShapes.thumbnail, modifier = Modifier.size(44.dp))
            Spacer(Modifier.width(14.dp))
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

/** La bibliothèque, la liste de la catégorie ouverte triée. */
private class SortedLibrary(
    val library: Library,
    val albums: List<Album>,
    val artists: List<Artist>,
    val songs: List<Song>,
)

/** Sous cette largeur de tuile, la grille perd une colonne : deux sur un téléphone. */
private val ALBUM_MIN_WIDTH = 150.dp

/** Une ligne a besoin de cette largeur : deux colonnes de lignes dès 680 dp. */
private val ROW_MIN_WIDTH = 340.dp
