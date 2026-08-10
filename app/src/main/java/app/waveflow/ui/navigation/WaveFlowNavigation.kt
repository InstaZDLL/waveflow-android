package app.waveflow.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Cloud
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
    const val SERVER = "server"

    const val ARG_ALBUM_ID = "albumId"
    const val ARG_ARTIST_ID = "artistId"
    const val ARG_PLAYLIST_ID = "playlistId"

    const val ALBUM_DETAIL = "$ALBUMS/{$ARG_ALBUM_ID}"
    const val ARTIST_DETAIL = "$ARTISTS/{$ARG_ARTIST_ID}"
    const val PLAYLIST_DETAIL = "$PLAYLISTS/{$ARG_PLAYLIST_ID}"

    /** Détails distants, sous la section Serveur : leurs clés sont des UUID. */
    private const val SERVER_ALBUMS = "$SERVER/albums"
    private const val SERVER_ARTISTS = "$SERVER/artists"

    const val SERVER_ALBUM_DETAIL = "$SERVER_ALBUMS/{$ARG_ALBUM_ID}"
    const val SERVER_ARTIST_DETAIL = "$SERVER_ARTISTS/{$ARG_ARTIST_ID}"
    const val SERVER_ACCOUNT = "$SERVER/compte"

    fun albumDetail(albumId: Long): String = "$ALBUMS/$albumId"

    fun artistDetail(artistId: Long): String = "$ARTISTS/$artistId"

    fun playlistDetail(playlistId: Long): String = "$PLAYLISTS/$playlistId"

    fun serverAlbumDetail(albumId: String): String = "$SERVER_ALBUMS/$albumId"

    fun serverArtistDetail(artistId: String): String = "$SERVER_ARTISTS/$artistId"
}

/**
 * Les sections atteignables depuis la barre du bas.
 *
 * Serveur est en dernier et à part : les quatre premières décrivent la
 * bibliothèque de l'appareil, celle-ci une source distante.
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Songs(Routes.SONGS, "Titres", Icons.Filled.MusicNote),
    Albums(Routes.ALBUMS, "Albums", Icons.Filled.Album),
    Artists(Routes.ARTISTS, "Artistes", Icons.Filled.Person),
    Playlists(Routes.PLAYLISTS, "Playlists", Icons.AutoMirrored.Filled.QueueMusic),
    Server(Routes.SERVER, "Serveur", Icons.Filled.Cloud),
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
