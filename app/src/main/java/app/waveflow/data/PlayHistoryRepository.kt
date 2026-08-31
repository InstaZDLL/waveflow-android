package app.waveflow.data

import android.util.Log
import app.waveflow.data.local.PlayHistoryDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Une piste et ce que l'écoute en a retenu. */
data class PlayHistoryEntry(
    val mediaId: String,
    val lastPlayedAtMs: Long,
    val playCount: Int,
)

/**
 * L'historique d'écoute.
 *
 * Interface pour que l'écran d'accueil n'ait pas à connaître Room, et que ses
 * tests n'aient pas à ouvrir de base.
 */
interface PlayHistoryRepository {

    /** Note qu'on vient d'écouter cette piste. */
    suspend fun record(mediaId: String)

    fun observeRecent(limit: Int): Flow<List<PlayHistoryEntry>>

    fun observeMostPlayed(limit: Int): Flow<List<PlayHistoryEntry>>
}

class RoomPlayHistoryRepository(
    private val dao: PlayHistoryDao,
    private val now: () -> Long = System::currentTimeMillis,
) : PlayHistoryRepository {

    override suspend fun record(mediaId: String) = dao.record(mediaId, now())

    override fun observeRecent(limit: Int): Flow<List<PlayHistoryEntry>> =
        dao.observeRecent(limit).enEntrees()

    override fun observeMostPlayed(limit: Int): Flow<List<PlayHistoryEntry>> =
        dao.observeMostPlayed(limit).enEntrees()

    /**
     * Traduit les lignes, et ne laisse pas une lecture en échec traverser.
     *
     * Un flux Room propage l'erreur de sa requête. Combinée à un autre flux dans
     * un ViewModel, elle emporte la collecte entière : l'écran perdrait aussi
     * ce que l'historique n'a jamais fourni — les ajouts récents, qui viennent
     * de la bibliothèque. Un historique illisible doit coûter l'historique, pas
     * la page.
     */
    private fun Flow<List<app.waveflow.data.local.PlayHistoryEntity>>.enEntrees():
        Flow<List<PlayHistoryEntry>> = map { rows -> rows.map { it.toEntry() } }
        .catch { error ->
            Log.w(TAG, "Historique d'écoute illisible", error)
            emit(emptyList())
        }

    private fun app.waveflow.data.local.PlayHistoryEntity.toEntry() = PlayHistoryEntry(
        mediaId = mediaId,
        lastPlayedAtMs = lastPlayedAtMs,
        playCount = playCount,
    )

    private companion object {
        const val TAG = "PlayHistory"
    }
}
