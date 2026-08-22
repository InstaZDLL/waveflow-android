package app.waveflow.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.waveflow.model.Album
import app.waveflow.model.Artist
import app.waveflow.model.SearchResults
import app.waveflow.model.Song
import app.waveflow.ui.albumCountLabel
import app.waveflow.ui.components.CenteredMessage
import app.waveflow.ui.components.MediaRow
import app.waveflow.ui.components.SongRow
import app.waveflow.ui.trackCountLabel

/**
 * Résultats de recherche, groupés par type.
 *
 * Les trois sections partagent une seule liste : un album qui n'apparaît
 * qu'après trente titres reste atteignable en continuant de faire défiler,
 * plutôt que caché derrière un onglet de plus.
 */
@Composable
fun SearchScreen(
    query: String,
    results: SearchResults,
    nowPlayingId: Long?,
    onSongClick: (Song) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    onSongLongClick: (Song) -> Unit = {},
    /**
     * Proposé quand la recherche locale ne trouve rien et qu'un serveur est
     * connecté. Un bouton explicite plutôt que des résultats distants qui
     * s'ajouteraient d'eux-mêmes : les deux sources restent séparées, et
     * l'utilisateur sait toujours où il cherche.
     */
    onSearchOnServer: (() -> Unit)? = null,
) {
    if (query.isBlank() || results.isEmpty) {
        Box(modifier = modifier.fillMaxSize()) {
            CenteredMessage(
                message = if (query.isBlank()) {
                    "Cherchez un titre, un album ou un artiste."
                } else {
                    "Aucun résultat pour « ${query.trim()} » sur cet appareil."
                },
                modifier = Modifier.align(Alignment.Center),
                action = onSearchOnServer
                    ?.takeIf { query.isNotBlank() }
                    ?.let { chercher ->
                        { Button(onClick = chercher) { Text("Chercher sur le serveur") } }
                    },
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = bottomPadding),
        modifier = modifier.fillMaxSize(),
    ) {
        section(title = "Titres", items = results.songs, keyPrefix = "song", id = Song::id) { song ->
            SongRow(
                song = song,
                isCurrent = song.id == nowPlayingId,
                onClick = { onSongClick(song) },
                onLongClick = { onSongLongClick(song) },
            )
        }

        section(title = "Albums", items = results.albums, keyPrefix = "album", id = Album::id) { album ->
            MediaRow(
                artworkUri = album.artworkUri,
                title = album.title,
                subtitle = album.displayArtist,
                onClick = { onAlbumClick(album) },
            )
        }

        section(title = "Artistes", items = results.artists, keyPrefix = "artist", id = Artist::id) { artist ->
            MediaRow(
                artworkUri = artist.artworkUri,
                title = artist.name,
                subtitle = "${albumCountLabel(artist.albumCount)} · ${trackCountLabel(artist.trackCount)}",
                onClick = { onArtistClick(artist) },
                artworkShape = CircleShape,
            )
        }
    }
}

/**
 * Une section titrée, omise si elle n'a rien à montrer.
 *
 * [keyPrefix] sépare les espaces d'identifiants : un album et un morceau
 * peuvent porter le même identifiant MediaStore, et deux clés identiques dans
 * une même `LazyColumn` sont une erreur d'exécution.
 */
private fun <T> LazyListScope.section(
    title: String,
    items: List<T>,
    keyPrefix: String,
    id: (T) -> Long,
    row: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return

    item(key = "$keyPrefix-header") { SectionHeader(title) }
    items(items, key = { "$keyPrefix-${id(it)}" }) { row(it) }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
