package app.waveflow.ui.navigation

import android.net.Uri
import android.os.Bundle
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

    /**
     * Les identifiants distants sont encodés, contrairement aux locaux qui sont
     * des entiers : un `/` dans un identifiant scinderait la route, qui ne
     * correspondrait alors à aucune destination. La navigation les décode
     * d'elle-même à la lecture de l'argument.
     */
    fun serverAlbumDetail(albumId: String): String = "$SERVER_ALBUMS/${Uri.encode(albumId)}"

    fun serverArtistDetail(artistId: String): String = "$SERVER_ARTISTS/${Uri.encode(artistId)}"
}

/**
 * L'identifiant entier porté par [key], à condition que la destination courante
 * soit bien [route].
 *
 * Deux routes partagent la même clé d'argument sans partager son type :
 * [Routes.ALBUM_DETAIL] porte un identifiant MediaStore, déclaré `LongType`, et
 * [Routes.SERVER_ALBUM_DETAIL] un UUID, déclaré `StringType`. Lire le second
 * comme le premier ne lève rien : `Bundle.getLong` attrape la
 * `ClassCastException` et rend `0`, que rien ne distingue ensuite d'un
 * identifiant véritable.
 *
 * Le filtre par route est donc ce qui sépare les deux, et il doit précéder la
 * lecture plutôt que la suivre : c'est l'accès lui-même qui coûte.
 */
fun Bundle?.longArgOf(currentRoute: String?, route: String, key: String): Long? =
    this?.takeIf { currentRoute == route }?.getLong(key)

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
