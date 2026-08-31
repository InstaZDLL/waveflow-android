package app.waveflow.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.waveflow.model.Album
import app.waveflow.model.Song
import app.waveflow.ui.components.Artwork
import app.waveflow.ui.components.MediaRow

/**
 * La première page : ce que l'appareil sait déjà de vos écoutes.
 *
 * Rien n'y est demandé au réseau — l'écoute passée vient de la base locale, les
 * ajouts récents du MediaStore. L'écran s'affiche donc hors ligne comme
 * connecté, ce qui est la moindre des choses pour la page d'ouverture.
 *
 * Les sections vides disparaissent au lieu de s'afficher creuses : une
 * bibliothèque neuve n'a pas d'écoutes à montrer, et un cadre vide se lit comme
 * une panne.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onSongClick: (Song) -> Unit,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    if (state.isEmpty) {
        EmptyHome(modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.resume?.let { piste ->
            item {
                SectionTitle("Reprendre")
                ResumeCard(song = piste, onClick = { onSongClick(piste) })
            }
        }

        if (state.recentlyAdded.isNotEmpty()) {
            item {
                SectionTitle("Ajouts récents")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.recentlyAdded, key = { it.id }) { album ->
                        AlbumTile(album = album, onClick = { onAlbumClick(album) })
                    }
                }
            }
        }

        if (state.recentlyPlayed.isNotEmpty()) {
            item { SectionTitle("Écouté récemment") }
            items(state.recentlyPlayed, key = { it.id }) { piste ->
                MediaRow(
                    artworkUri = piste.artworkUri,
                    title = piste.title,
                    subtitle = piste.displayArtist,
                    onClick = { onSongClick(piste) },
                )
            }
        }
    }
}

@Composable
private fun EmptyHome(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Rien à reprendre pour l'instant",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Écoutez quelques morceaux : ils réapparaîtront ici.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

/** La dernière piste écoutée, donnée plus grande que les autres. */
@Composable
private fun ResumeCard(song: Song, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Artwork(
                artworkUri = song.artworkUri,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(64.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song.displayArtist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun AlbumTile(album: Album, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(TUILE)
            .clickable(onClick = onClick),
    ) {
        Artwork(
            artworkUri = album.artworkUri,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(TUILE),
        )
        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = album.displayArtist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val TUILE = 132.dp
