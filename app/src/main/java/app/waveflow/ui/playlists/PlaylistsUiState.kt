package app.waveflow.ui.playlists

import app.waveflow.model.Playlist
import app.waveflow.model.Song

/**
 * État des playlists et de leur contenu déjà résolu.
 *
 * Les playlists ne stockent que des identifiants ; la résolution en [Song] se
 * fait ici, contre la bibliothèque chargée. Un morceau dont le fichier a
 * disparu est simplement absent de la liste, sans trou ni ligne fantôme.
 */
data class PlaylistsUiState(
    val isLoading: Boolean = true,
    val playlists: List<Playlist> = emptyList(),
    val songsByPlaylist: Map<Long, List<Song>> = emptyMap(),
) {
    val isEmpty: Boolean
        get() = !isLoading && playlists.isEmpty()

    fun playlist(playlistId: Long): Playlist? = playlists.firstOrNull { it.id == playlistId }

    fun songs(playlistId: Long): List<Song> = songsByPlaylist[playlistId].orEmpty()
}
