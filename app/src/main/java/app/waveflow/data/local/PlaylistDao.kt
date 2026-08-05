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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntry(entry: PlaylistSongEntity)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun deleteEntry(playlistId: Long, songId: Long)

    /** Ajout en fin de liste : lecture de la position et insertion dans la même transaction. */
    @Transaction
    suspend fun addSong(playlistId: Long, songId: Long, updatedAt: Long) {
        insertEntry(PlaylistSongEntity(playlistId, songId, nextPosition(playlistId)))
        touchPlaylist(playlistId, updatedAt)
    }

    @Transaction
    suspend fun removeSong(playlistId: Long, songId: Long, updatedAt: Long) {
        deleteEntry(playlistId, songId)
        touchPlaylist(playlistId, updatedAt)
    }
}
