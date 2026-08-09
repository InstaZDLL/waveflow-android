package app.waveflow.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE ASC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    /**
     * Toutes les appartenances d'un coup plutôt qu'un flux par playlist : le
     * volume est de l'ordre de quelques centaines de lignes, et l'écran de
     * détail n'a alors rien à charger quand on l'ouvre.
     */
    @Query("SELECT * FROM playlist_songs ORDER BY playlistId ASC, position ASC")
    fun observeEntries(): Flow<List<PlaylistSongEntity>>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, name: String, updatedAt: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("UPDATE playlists SET updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun touchPlaylist(playlistId: Long, updatedAt: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    /** @return l'identifiant de ligne inséré, ou -1 si le conflit a été ignoré. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntry(entry: PlaylistSongEntity): Long

    /** @return le nombre de lignes supprimées, 0 si le morceau n'y était pas. */
    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun deleteEntry(playlistId: Long, songId: Long): Int

    /**
     * Ajout en fin de liste : lecture de la position et insertion dans la même
     * transaction.
     *
     * `updatedAt` n'est touché que si quelque chose a réellement changé — c'est
     * un horodatage destiné à la résolution de conflits, un ajout en doublon ne
     * doit pas faire croire à une modification.
     */
    @Transaction
    suspend fun addSong(playlistId: Long, songId: Long, updatedAt: Long) {
        val inserted = insertEntry(PlaylistSongEntity(playlistId, songId, nextPosition(playlistId)))
        if (inserted != -1L) touchPlaylist(playlistId, updatedAt)
    }

    @Transaction
    suspend fun removeSong(playlistId: Long, songId: Long, updatedAt: Long) {
        if (deleteEntry(playlistId, songId) > 0) touchPlaylist(playlistId, updatedAt)
    }

    /** @return le nombre de lignes mises à jour, 0 si le morceau n'y était pas. */
    @Query("UPDATE playlist_songs SET position = :position WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun updatePosition(playlistId: Long, songId: Long, position: Int): Int

    /**
     * Réécrit les positions de la playlist dans l'ordre de [orderedSongIds].
     *
     * Réécriture complète plutôt qu'intercalation : `removeSong` ne compacte
     * pas les positions, elles sont donc déjà trouées, et rien ne garantit
     * qu'un rang libre existe entre deux voisins. Repartir de zéro à chaque
     * déplacement coûte quelques dizaines d'`UPDATE` sur une playlist
     * ordinaire, dans une seule transaction.
     *
     * Un identifiant absent de la playlist ne met à jour aucune ligne — un
     * morceau retiré entre le geste et son enregistrement est ignoré plutôt
     * que réinséré. Le rang n'avance qu'aux lignes réellement écrites : un
     * identifiant fantôme ne creuse pas de trou au milieu des positions.
     *
     * `updatedAt` n'est touché que si au moins une ligne a bougé, comme dans
     * [addSong] et [removeSong] — c'est un horodatage de résolution de
     * conflits, un ordre sans effet ne doit pas faire croire à une
     * modification.
     */
    @Transaction
    suspend fun reorder(playlistId: Long, orderedSongIds: List<Long>, updatedAt: Long) {
        var position = 0
        for (songId in orderedSongIds) {
            if (updatePosition(playlistId, songId, position) > 0) position++
        }
        if (position > 0) touchPlaylist(playlistId, updatedAt)
    }

    /**
     * Création d'une playlist déjà pourvue de son premier morceau.
     *
     * En une transaction : une annulation entre les deux ne peut pas laisser
     * une playlist vide derrière elle.
     */
    @Transaction
    suspend fun createWithSong(playlist: PlaylistEntity, songId: Long): Long {
        val playlistId = insertPlaylist(playlist)
        insertEntry(PlaylistSongEntity(playlistId, songId, position = 0))
        return playlistId
    }
}
