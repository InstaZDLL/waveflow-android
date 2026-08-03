package app.waveflow.ui.library

import app.waveflow.model.Album
import app.waveflow.model.Artist
import app.waveflow.model.Song

/**
 * État partagé par les trois onglets de navigation et leurs écrans de détail.
 *
 * Albums et artistes sont dérivés de [songs] au moment du chargement, pas à
 * chaque émission de l'état de lecture : le regroupement ne doit pas être
 * refait à chaque tic de position.
 *
 * Tout ce qui concerne la lecture elle-même vit dans `PlayerUiState` ; ici on
 * ne garde que [nowPlayingId], nécessaire pour mettre en évidence la ligne en
 * cours.
 */
data class LibraryUiState(
    val isLoading: Boolean = true,
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val errorMessage: String? = null,
    val nowPlayingId: Long? = null,
) {
    /** Bibliothèque vide alors que le chargement s'est bien terminé. */
    val isEmpty: Boolean
        get() = !isLoading && errorMessage == null && songs.isEmpty()

    fun album(albumId: Long): Album? = albums.firstOrNull { it.id == albumId }

    fun artist(artistId: Long): Artist? = artists.firstOrNull { it.id == artistId }

    /** Morceaux d'un album, dans l'ordre de la bibliothèque. */
    fun songsOfAlbum(albumId: Long): List<Song> = songs.filter { it.albumId == albumId }

    fun songsOfArtist(artistId: Long): List<Song> = songs.filter { it.artistId == artistId }
}
