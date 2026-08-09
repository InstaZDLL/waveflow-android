package app.waveflow.testing

import android.net.Uri
import app.waveflow.data.MusicRepository
import app.waveflow.data.PlaylistRepository
import app.waveflow.model.Playlist
import app.waveflow.model.PlaylistEntry
import app.waveflow.model.Song
import app.waveflow.playback.PlaybackController
import app.waveflow.playback.PlaybackState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Fabrique de morceaux pour les tests.
 *
 * `Uri.parse` impose Robolectric : toute classe de test qui appelle [song]
 * doit tourner sous `RobolectricTestRunner`.
 */
fun song(
    id: Long,
    title: String = "Titre $id",
    artist: String? = "Artiste $id",
    artistId: Long = id,
    album: String? = "Album $id",
    albumId: Long = id,
    durationMs: Long = 60_000L,
): Song = Song(
    id = id,
    uri = Uri.parse("content://media/external/audio/media/$id"),
    title = title,
    artist = artist,
    artistId = artistId,
    album = album,
    albumId = albumId,
    durationMs = durationMs,
    artworkUri = null,
)

class FakeMusicRepository(
    private val songs: Flow<List<Song>> = flowOf(emptyList()),
) : MusicRepository {
    override fun observeSongs(): Flow<List<Song>> = songs
}

/** Enregistre les appels d'écriture pour qu'un test puisse les vérifier. */
class FakePlaylistRepository(
    private val playlists: Flow<List<Playlist>> = flowOf(emptyList()),
    private val entries: Flow<List<PlaylistEntry>> = flowOf(emptyList()),
    /** Si non nul, toute écriture échoue avec cette exception. */
    private val writeFailure: Throwable? = null,
) : PlaylistRepository {

    val createCalls = mutableListOf<String>()
    val createWithSongCalls = mutableListOf<Pair<String, Long>>()
    val addSongCalls = mutableListOf<Pair<Long, Long>>()
    val removeSongCalls = mutableListOf<Pair<Long, Long>>()
    val reorderCalls = mutableListOf<Pair<Long, List<Long>>>()

    override fun observePlaylists(): Flow<List<Playlist>> = playlists

    override fun observeEntries(): Flow<List<PlaylistEntry>> = entries

    private fun failIfConfigured() {
        writeFailure?.let { throw it }
    }

    override suspend fun create(name: String): Long {
        failIfConfigured()
        createCalls += name
        return createCalls.size.toLong()
    }

    override suspend fun createWithSong(name: String, songId: Long): Long {
        failIfConfigured()
        createWithSongCalls += name to songId
        return createWithSongCalls.size.toLong()
    }

    override suspend fun rename(playlistId: Long, name: String) = failIfConfigured()

    override suspend fun delete(playlistId: Long) = failIfConfigured()

    override suspend fun addSong(playlistId: Long, songId: Long) {
        failIfConfigured()
        addSongCalls += playlistId to songId
    }

    override suspend fun removeSong(playlistId: Long, songId: Long) {
        failIfConfigured()
        removeSongCalls += playlistId to songId
    }

    override suspend fun reorder(playlistId: Long, orderedSongIds: List<Long>) {
        failIfConfigured()
        reorderCalls += playlistId to orderedSongIds
    }
}

class FakePlaybackController : PlaybackController {

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    var connectCount = 0
        private set
    var released = false
        private set
    val playCalls = mutableListOf<Pair<List<Song>, Int>>()
    val playShuffledCalls = mutableListOf<List<Song>>()

    override fun connect() {
        connectCount++
        _state.value = _state.value.copy(isConnected = true)
    }

    override fun play(songs: List<Song>, startIndex: Int) {
        playCalls += songs to startIndex
    }

    override fun playShuffled(songs: List<Song>) {
        playShuffledCalls += songs
    }

    override fun playPause() = Unit

    override fun skipNext() = Unit

    override fun skipPrevious() = Unit

    override fun seekTo(positionMs: Long) = Unit

    override fun toggleShuffle() = Unit

    override fun cycleRepeatMode() = Unit

    override fun release() {
        released = true
    }

    /** Simule une notification du lecteur. */
    fun emit(state: PlaybackState) {
        _state.value = state
    }
}
