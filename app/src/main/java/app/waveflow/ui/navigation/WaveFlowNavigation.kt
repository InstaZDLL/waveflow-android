package app.waveflow.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/** Routes de navigation. Les détails portent leur identifiant dans le chemin. */
object Routes {
    const val SONGS = "songs"
    const val ALBUMS = "albums"
    const val ARTISTS = "artists"
    const val PLAYLISTS = "playlists"

    const val ARG_ALBUM_ID = "albumId"
    const val ARG_ARTIST_ID = "artistId"
    const val ARG_PLAYLIST_ID = "playlistId"

    const val ALBUM_DETAIL = "$ALBUMS/{$ARG_ALBUM_ID}"
    const val ARTIST_DETAIL = "$ARTISTS/{$ARG_ARTIST_ID}"
    const val PLAYLIST_DETAIL = "$PLAYLISTS/{$ARG_PLAYLIST_ID}"

    fun albumDetail(albumId: Long): String = "$ALBUMS/$albumId"

    fun artistDetail(artistId: Long): String = "$ARTISTS/$artistId"

    fun playlistDetail(playlistId: Long): String = "$PLAYLISTS/$playlistId"
}

/** Les trois sections atteignables depuis la barre du bas. */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Songs(Routes.SONGS, "Titres", Icons.Filled.MusicNote),
    Albums(Routes.ALBUMS, "Albums", Icons.Filled.Album),
    Artists(Routes.ARTISTS, "Artistes", Icons.Filled.Person),
    Playlists(Routes.PLAYLISTS, "Playlists", Icons.AutoMirrored.Filled.QueueMusic),
    ;

    /**
     * Vrai aussi pour les écrans de détail de la section : ouvrir un album
     * garde l'onglet Albums sélectionné.
     */
    fun owns(route: String?): Boolean = route == this.route || route?.startsWith("${this.route}/") == true
}

@Composable
fun WaveFlowBottomBar(
    currentRoute: String?,
    onSelect: (TopLevelDestination) -> Unit,
) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination.owns(currentRoute),
                onClick = { onSelect(destination) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(destination.label) },
            )
        }
    }
}
