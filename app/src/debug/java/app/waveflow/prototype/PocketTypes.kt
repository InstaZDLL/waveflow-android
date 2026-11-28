package app.waveflow.prototype

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.graphics.vector.ImageVector
import app.waveflow.model.Song

/*
 * Les types du prototype : ce que chaque écran sait faire (ses actions), ses
 * destinations et ses catégories. Réunis ici pour que chaque fichier d'écran
 * ne porte que ses composables.
 */

/** Ce que la coquille relaie au lecteur et aux playlists de production. */
data class PocketVerbs(
    val onPlay: (queue: List<Song>, song: Song) -> Unit,
    val onShuffle: (List<Song>) -> Unit,
    val onAnotherSuggestion: () -> Unit,
    val onAddToPlaylist: (playlistId: Long, song: Song) -> Unit,
    val onCreatePlaylistWith: (name: String, song: Song) -> Unit,
)

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

/** Tout ce que le lecteur de Pocket sait faire : les commandes du `PlayerViewModel` de production. */
data class NowPlayingActions(
    val onCollapse: () -> Unit,
    val onTogglePlayPause: () -> Unit,
    val onSkipNext: () -> Unit,
    val onSkipPrevious: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onToggleShuffle: () -> Unit,
    val onCycleRepeat: () -> Unit,
    val onPlayQueueItem: (Int) -> Unit,
    val onMoveQueueItem: (Int, Int) -> Unit,
    val onRemoveQueueItem: (Int) -> Unit,
    val onStartSleepTimer: (Long) -> Unit,
    val onCancelSleepTimer: () -> Unit,
    val onSleepTimerRemainingMs: () -> Long?,
    val onSetPlaybackSpeed: (Float) -> Unit,
    val onMarkAbLoop: () -> Unit,
    val onToggleLyrics: () -> Unit,
    val onRetryLyrics: () -> Unit,
    val onLyricsOnScreen: (Boolean) -> Unit,
)

/** Ce que l'accueil sait faire : tout passe par le lecteur et la bibliothèque réels. */
data class HomeActions(
    val onPlay: (queue: List<Song>, song: Song) -> Unit,
    val onShuffle: (List<Song>) -> Unit,
    val onTogglePlayPause: () -> Unit,
    val onOpenAlbum: (Long) -> Unit,
    val onOpenCategory: (LibraryCategory) -> Unit,
    val onAnotherSuggestion: () -> Unit,
)

/** Les catégories de la bibliothèque, dans l'ordre du sélecteur. */
enum class LibraryCategory(val label: String, val icon: ImageVector) {
    Albums("Albums", Icons.Rounded.Album),
    Artistes("Artistes", Icons.Rounded.Person),
    Titres("Titres", Icons.Rounded.LibraryMusic),
    Playlists("Playlists", Icons.AutoMirrored.Rounded.QueueMusic),
}

/** Ce que la bibliothèque sait faire. */
data class LibraryActions(
    val onPlay: (queue: List<Song>, song: Song) -> Unit,
    val onOpenAlbum: (Long) -> Unit,
    val onOpenArtist: (Long) -> Unit,
    val onOpenPlaylist: (Long) -> Unit,
    val rowMenu: (Song) -> List<RowAction>,
)

/** Ce qu'est la page : elle se dessine un peu autrement pour chacun. */
enum class CollectionKind(val label: String) {
    Album("Album"),
    Artiste("Artiste"),
    Playlist("Playlist"),
}

/** Ce que la page d'une collection sait faire. */
data class CollectionActions(
    val onBack: () -> Unit,
    val onPlay: (queue: List<Song>, song: Song) -> Unit,
    val onShuffle: (List<Song>) -> Unit,
    val rowMenu: (Song) -> List<RowAction>,
)

/** Les trois destinations, gardées de l'application actuelle. */
enum class PocketTab(val label: String, val icon: ImageVector, val iconSelected: ImageVector) {
    Accueil("Accueil", Icons.Outlined.Home, Icons.Rounded.Home),
    Bibliotheque("Bibliothèque", Icons.Outlined.LibraryMusic, Icons.Rounded.LibraryMusic),
    Recherche("Recherche", Icons.Outlined.Search, Icons.Rounded.Search),
}

/** Une action du menu d'une ligne : seulement ce que l'application sait faire. */
data class RowAction(val label: String, val onClick: () -> Unit)
