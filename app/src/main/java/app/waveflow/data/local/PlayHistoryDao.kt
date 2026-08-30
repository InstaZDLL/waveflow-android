package app.waveflow.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayHistoryDao {

    /**
     * Note une écoute, ou l'ajoute à celles qu'on avait déjà de cette piste.
     *
     * En deux instructions sous transaction, et non par un `ON CONFLICT … DO
     * UPDATE` : cette syntaxe demande SQLite 3.24, que l'appareil n'embarque
     * qu'à partir d'Android 10. En deçà — et l'application descend jusqu'à
     * Android 8 — elle lève une erreur de syntaxe à la première écoute. Les
     * tests ne l'auraient jamais vu : Robolectric prête le SQLite de la
     * machine, bien plus récent que celui d'un téléphone.
     *
     * L'ordre compte. L'insertion pose la ligne à zéro écoute si elle manque et
     * ne touche à rien sinon ; la mise à jour qui suit compte l'écoute dans les
     * deux cas. La transaction rend le couple indivisible, ce qui importe : le
     * service enregistre depuis sa propre portée pendant que l'application peut
     * en enregistrer d'autres.
     */
    @Transaction
    suspend fun record(mediaId: String, playedAtMs: Long) {
        insertIfAbsent(mediaId, playedAtMs)
        countPlay(mediaId, playedAtMs)
    }

    @Query(
        """
        INSERT OR IGNORE INTO play_history (media_id, last_played_at, play_count)
        VALUES (:mediaId, :playedAtMs, 0)
        """,
    )
    suspend fun insertIfAbsent(mediaId: String, playedAtMs: Long)

    @Query(
        """
        UPDATE play_history
        SET last_played_at = :playedAtMs, play_count = play_count + 1
        WHERE media_id = :mediaId
        """,
    )
    suspend fun countPlay(mediaId: String, playedAtMs: Long)

    /** Les dernières pistes écoutées, la plus récente d'abord. */
    @Query("SELECT * FROM play_history ORDER BY last_played_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PlayHistoryEntity>>

    /** Les plus écoutées ; à égalité de compte, la plus récente d'abord. */
    @Query("SELECT * FROM play_history ORDER BY play_count DESC, last_played_at DESC LIMIT :limit")
    fun observeMostPlayed(limit: Int): Flow<List<PlayHistoryEntity>>
}
