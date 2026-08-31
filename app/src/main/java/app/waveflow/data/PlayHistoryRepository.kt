package app.waveflow.data

import app.waveflow.data.local.PlayHistoryDao
import kotlinx.coroutines.flow.Flow
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
        dao.observeRecent(limit).map { rows -> rows.map { it.toEntry() } }

    override fun observeMostPlayed(limit: Int): Flow<List<PlayHistoryEntry>> =
        dao.observeMostPlayed(limit).map { rows -> rows.map { it.toEntry() } }

    private fun app.waveflow.data.local.PlayHistoryEntity.toEntry() = PlayHistoryEntry(
        mediaId = mediaId,
        lastPlayedAtMs = lastPlayedAtMs,
        playCount = playCount,
    )
}
