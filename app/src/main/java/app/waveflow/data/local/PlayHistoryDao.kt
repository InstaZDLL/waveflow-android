package app.waveflow.data.local

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayHistoryDao {

    /**
     * Note une écoute, ou l'ajoute à celles qu'on avait déjà de cette piste.
     *
     * En une seule instruction : lire puis réécrire laisserait passer les
     * écoutes concurrentes — le service enregistre depuis sa propre portée
     * pendant que l'application peut en enregistrer d'autres.
     */
    @Query(
        """
        INSERT INTO play_history (media_id, last_played_at, play_count)
        VALUES (:mediaId, :playedAtMs, 1)
        ON CONFLICT(media_id) DO UPDATE SET
            last_played_at = :playedAtMs,
            play_count = play_count + 1
        """,
    )
    suspend fun record(mediaId: String, playedAtMs: Long)

    /** Les dernières pistes écoutées, la plus récente d'abord. */
    @Query("SELECT * FROM play_history ORDER BY last_played_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PlayHistoryEntity>>

    /** Les plus écoutées ; à égalité de compte, la plus récente d'abord. */
    @Query("SELECT * FROM play_history ORDER BY play_count DESC, last_played_at DESC LIMIT :limit")
    fun observeMostPlayed(limit: Int): Flow<List<PlayHistoryEntity>>
}
