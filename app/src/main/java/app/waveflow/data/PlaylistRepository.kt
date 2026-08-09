package app.waveflow.data

import app.waveflow.model.Playlist
import app.waveflow.model.PlaylistEntry
import kotlinx.coroutines.flow.Flow

/**
 * Playlists locales.
 *
 * Séparé de [MusicRepository] parce que les deux sources n'ont rien en
 * commun : l'une lit le MediaStore, l'autre écrit dans une base dont
 * l'application est propriétaire.
 */
interface PlaylistRepository {

    fun observePlaylists(): Flow<List<Playlist>>

    /** Toutes les appartenances, triées par playlist puis par position. */
    fun observeEntries(): Flow<List<PlaylistEntry>>

    /** @return l'identifiant de la playlist créée. */
    suspend fun create(name: String): Long

    /**
     * Crée une playlist contenant déjà [songId], de façon atomique : une
     * interruption ne peut pas laisser une playlist vide.
     */
    suspend fun createWithSong(name: String, songId: Long): Long

    suspend fun rename(playlistId: Long, name: String)

    suspend fun delete(playlistId: Long)

    /** Ajoute [songId] en fin de playlist. Sans effet s'il y figure déjà. */
    suspend fun addSong(playlistId: Long, songId: Long)

    suspend fun removeSong(playlistId: Long, songId: Long)

    /**
     * Réordonne la playlist selon [orderedSongIds].
     *
     * L'ordre complet plutôt qu'un couple (morceau, destination) : c'est ce
     * que l'écran connaît après un glisser-déposer, et cela reste vrai même
     * si la playlist a changé entre-temps.
     */
    suspend fun reorder(playlistId: Long, orderedSongIds: List<Long>)
}
