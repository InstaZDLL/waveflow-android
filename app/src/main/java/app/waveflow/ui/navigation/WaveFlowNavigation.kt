package app.waveflow.ui.navigation

import android.net.Uri
import android.os.Bundle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Routes de navigation. Les détails portent leur identifiant dans le chemin. */
object Routes {
    const val HOME = "accueil"
    const val SONGS = "songs"
    const val ALBUMS = "albums"
    const val ARTISTS = "artists"
    const val PLAYLISTS = "playlists"
    const val SERVER = "server"

    /**
     * Les réglages, hors de la barre du bas.
     *
     * Ils s'ouvrent depuis l'en-tête, d'où qu'on vienne : ce n'est pas une
     * section de la bibliothèque mais ce qui la gouverne.
     */
    const val SETTINGS = "reglages"

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

    /**
     * Le compte, sous les réglages et non sous le serveur.
     *
     * Il s'ouvre depuis les réglages et il y appartient : connexion, jeton,
     * cache. Le laisser sous `server/` le faisait réclamer par l'onglet Serveur
     * de la bibliothèque — l'écran Compte affichait alors les sous-onglets et
     * se disait dans la bibliothèque, où il n'est pas.
     */
    const val SERVER_ACCOUNT = "$SETTINGS/serveur"

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
 *
 * L'absence de la clé rend `null` elle aussi. `getLong` y répondrait encore
 * `0`, et rien ne distingue ce zéro-là d'un autre : autant ne pas rouvrir la
 * porte que le filtre vient de fermer.
 */
fun Bundle?.longArgOf(currentRoute: String?, route: String, key: String): Long? =
    this?.takeIf { currentRoute == route && it.containsKey(key) }?.getLong(key)

/**
 * Les deux destinations de la barre du bas.
 *
 * Deux et non cinq. Les quatre vues de la bibliothèque — titres, albums,
 * artistes, playlists — ne sont pas des sections différentes mais des manières
 * de regarder la même chose : elles passent en sous-onglets. Et le serveur
 * cesse d'être une destination pour devenir une source parmi d'autres, à
 * l'intérieur de la bibliothèque.
 *
 * La recherche et les réglages n'y figurent pas non plus : ils s'ouvrent depuis
 * l'en-tête, d'où qu'on vienne.
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Home(Routes.HOME, "Accueil", Icons.Filled.Home),
    Library(Routes.SONGS, "Bibliothèque", Icons.AutoMirrored.Filled.LibraryBooks),
    ;

    /**
     * Vrai aussi pour les écrans de détail de la section : ouvrir un album
     * garde la bibliothèque sélectionnée.
     */
    fun owns(route: String?): Boolean = when (this) {
        Home -> route == Routes.HOME
        Library -> LibraryTab.entries.any { it.owns(route) }
    }
}

/**
 * Les vues de la bibliothèque, et la source qui n'en est pas une.
 *
 * `Serveur` figure dans la même rangée faute de mieux : son catalogue se
 * demande au réseau, page par page, quand les quatre autres lisent une
 * bibliothèque déjà en mémoire. Les réunir sous un vrai filtre de source
 * suppose de réconcilier ces deux façons de charger — c'est un travail à part,
 * et le catalogue devait rester atteignable d'ici là.
 */
enum class LibraryTab(val route: String, val label: String) {
    Songs(Routes.SONGS, "Titres"),
    Albums(Routes.ALBUMS, "Albums"),
    Artists(Routes.ARTISTS, "Artistes"),
    Playlists(Routes.PLAYLISTS, "Playlists"),
    Server(Routes.SERVER, "Serveur"),
    ;

    fun owns(route: String?): Boolean =
        route == this.route || route?.startsWith("${this.route}/") == true
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

/**
 * Les sous-onglets de la bibliothèque.
 *
 * Défilants plutôt que répartis : à cinq entrées, un partage égal donnerait des
 * libellés tronqués sur un téléphone étroit ou à grande police.
 *
 * `Secondary` parce qu'ils le sont : la barre du bas dit où l'on est, ceux-ci
 * seulement comment on regarde ce qu'on y trouve.
 */
@Composable
fun LibraryTabs(
    currentRoute: String?,
    onSelect: (LibraryTab) -> Unit,
) {
    val selectionne = LibraryTab.entries.indexOfFirst { it.owns(currentRoute) }
    if (selectionne < 0) return

    SecondaryScrollableTabRow(
        selectedTabIndex = selectionne,
        edgePadding = 8.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        divider = {},
    ) {
        LibraryTab.entries.forEach { onglet ->
            Tab(
                selected = onglet.owns(currentRoute),
                onClick = { onSelect(onglet) },
                text = { Text(onglet.label) },
            )
        }
    }
}
