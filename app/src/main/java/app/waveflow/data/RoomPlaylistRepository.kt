package app.waveflow.data

import app.waveflow.data.local.PlaylistDao
import app.waveflow.data.local.PlaylistEntity
import app.waveflow.model.Playlist
import app.waveflow.model.PlaylistEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Implémentation Room de [PlaylistRepository].
 *
 * Les entités Room ne sortent pas de cette couche : l'UI ne manipule que les
 * types de `model/`, ce qui laisse la liberté de changer de stockage (ou d'y
 * greffer une source distante) sans toucher aux écrans.
 *
 * L'horloge est injectée pour que les tests n'aient pas à composer avec
 * `System.currentTimeMillis()`.
 */
class RoomPlaylistRepository(
    private val dao: PlaylistDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) : PlaylistRepository {

    override fun observePlaylists(): Flow<List<Playlist>> =
        dao.observePlaylists()
            .map { entities -> entities.map { Playlist(id = it.id, name = it.name) } }
            .flowOn(ioDispatcher)

    override fun observeEntries(): Flow<List<PlaylistEntry>> =
        dao.observeEntries()
            .map { entities ->
                entities.map { PlaylistEntry(it.playlistId, it.songId, it.position) }
            }
            .flowOn(ioDispatcher)

    override suspend fun create(name: String): Long = withContext(ioDispatcher) {
        val timestamp = now()
        dao.insertPlaylist(
            PlaylistEntity(name = name, createdAt = timestamp, updatedAt = timestamp),
        )
    }

    override suspend fun createWithSong(name: String, songId: Long): Long = withContext(ioDispatcher) {
        val timestamp = now()
        dao.createWithSong(
            playlist = PlaylistEntity(name = name, createdAt = timestamp, updatedAt = timestamp),
            songId = songId,
        )
    }

    override suspend fun rename(playlistId: Long, name: String) = withContext(ioDispatcher) {
        dao.renamePlaylist(playlistId, name, now())
    }

    override suspend fun delete(playlistId: Long) = withContext(ioDispatcher) {
        dao.deletePlaylist(playlistId)
    }

    override suspend fun addSong(playlistId: Long, songId: Long) = withContext(ioDispatcher) {
        dao.addSong(playlistId, songId, now())
    }

    override suspend fun removeSong(playlistId: Long, songId: Long) = withContext(ioDispatcher) {
        dao.removeSong(playlistId, songId, now())
    }
}
