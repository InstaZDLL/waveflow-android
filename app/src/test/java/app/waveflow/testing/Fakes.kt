package app.waveflow.testing

import android.net.Uri
import app.waveflow.data.MusicRepository
import app.waveflow.data.PlaylistRepository
import app.waveflow.data.PreferencesStore
import app.waveflow.model.AppPreferences
import app.waveflow.model.PlaybackSpeed
import app.waveflow.model.Playlist
import app.waveflow.model.PlaylistEntry
import app.waveflow.model.RemoteSong
import app.waveflow.model.Song
import app.waveflow.model.ThemeChoice
import app.waveflow.playback.PlaybackController
import app.waveflow.playback.PlaybackState
import app.waveflow.playback.PlayingTrack
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update

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
    addedAtMs: Long = 0L,
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
    addedAtMs = addedAtMs,
)

/** Fabrique de morceaux distants pour les tests. */
fun remoteSong(
    id: String,
    title: String = "Titre $id",
    artist: String? = "Artiste $id",
    album: String? = "Album $id",
    albumId: String? = "album-$id",
    trackNumber: Int? = 1,
    durationMs: Long = 60_000L,
    artworkUri: Uri? = null,
): RemoteSong = RemoteSong(
    id = id,
    title = title,
    album = album,
    albumId = albumId,
    artist = artist,
    trackNumber = trackNumber,
    durationMs = durationMs,
    artworkUri = artworkUri,
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
    /**
     * Si non nul, `reorder` attend ce signal avant de rendre la main.
     *
     * Sans lui, un dispatcher non confiné exécute chaque écriture avant que la
     * suivante ne parte : deux réordonnancements ne sont jamais en vol
     * ensemble, et une course ne peut pas être reproduite.
     */
    private val reorderGate: CompletableDeferred<Unit>? = null,
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
        reorderGate?.await()
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
    val playRemoteCalls = mutableListOf<Pair<List<RemoteSong>, Int>>()
    val playShuffledCalls = mutableListOf<List<Song>>()
    val playRemoteShuffledCalls = mutableListOf<List<RemoteSong>>()

    /** Les gestes sur la file, dans l'ordre où l'écran les a demandés. */
    val queueCalls = mutableListOf<String>()

    override fun playQueueItem(index: Int) {
        queueCalls += "play:$index"
    }

    override fun moveQueueItem(from: Int, to: Int) {
        queueCalls += "move:$from->$to"
    }

    override fun removeQueueItem(index: Int) {
        queueCalls += "remove:$index"
    }

    /** Pose une file, comme si le lecteur l'avait acceptée. */
    fun setQueue(queue: List<PlayingTrack>, index: Int) {
        _state.value = _state.value.copy(queue = queue, queueIndex = index)
    }

    override fun connect() {
        connectCount++
        _state.value = _state.value.copy(isConnected = true)
    }

    override fun play(songs: List<Song>, startIndex: Int) {
        playCalls += songs to startIndex
    }

    override fun playRemote(songs: List<RemoteSong>, startIndex: Int) {
        playRemoteCalls += songs to startIndex
    }

    override fun playShuffled(songs: List<Song>) {
        playShuffledCalls += songs
    }

    override fun playRemoteShuffled(songs: List<RemoteSong>) {
        playRemoteShuffledCalls += songs
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

/**
 * Les préférences, en mémoire.
 *
 * Un `MutableStateFlow` et non le vrai magasin : ce que ces tests éprouvent est
 * ce que fait l'application d'une préférence qui change, pas la fidélité du
 * DataStore — celle-ci se joue dans `PreferencesStoreTest`, sur un vrai fichier.
 *
 * Il **borne comme le vrai**. Un faux plus permissif que l'original rendrait
 * verts des tests qui décriraient une application qui n'existe pas.
 */
class FakePreferencesStore(initial: AppPreferences = AppPreferences()) : PreferencesStore {

    // Borné dès la construction, et pas seulement à l'écriture : le vrai
    // magasin borne ce qu'il relit, et un faux plus permissif rendrait vert un
    // test décrivant une application qui n'existe pas.
    private val flux = MutableStateFlow(
        initial.copy(playbackSpeed = PlaybackSpeed.borner(initial.playbackSpeed)),
    )

    override val preferences: Flow<AppPreferences> = flux

    override suspend fun setTheme(choice: ThemeChoice) {
        flux.update { it.copy(theme = choice) }
    }

    override suspend fun setPlaybackSpeed(speed: Float) {
        flux.update { it.copy(playbackSpeed = PlaybackSpeed.borner(speed)) }
    }

    val theme: ThemeChoice get() = flux.value.theme
    val playbackSpeed: Float get() = flux.value.playbackSpeed
}
