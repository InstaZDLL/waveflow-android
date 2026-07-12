package app.waveflow.ui.library

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.waveflow.WaveFlowApp
import app.waveflow.data.Song
import app.waveflow.playback.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** État observable de l'écran bibliothèque. */
data class LibraryUiState(
    val isLoading: Boolean = true,
    val songs: List<Song> = emptyList(),
    val nowPlayingId: Long? = null,
    val isPlaying: Boolean = false,
)

/**
 * ViewModel de la bibliothèque : charge les morceaux via [MusicRepository] et
 * pilote la lecture à travers un [MediaController] connecté au
 * [PlaybackService]. L'UI observe [uiState] et n'appelle que [playSong] /
 * [togglePlayPause].
 */
class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as WaveFlowApp).container.musicRepository

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _uiState.value = _uiState.value.copy(nowPlayingId = mediaItem?.mediaId?.toLongOrNull())
        }
    }

    /** Connecte l'UI au service de lecture. Appelé quand la permission est accordée. */
    fun connect() {
        if (controllerFuture != null) return
        val context = getApplication<Application>()
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener({
            controller = future.get().also { it.addListener(playerListener) }
        }, ContextCompat.getMainExecutor(context))
    }

    fun loadSongs() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val songs = repository.getAllSongs()
            _uiState.value = _uiState.value.copy(isLoading = false, songs = songs)
        }
    }

    /** Charge toute la bibliothèque comme file d'attente et démarre à [song]. */
    fun playSong(song: Song) {
        val ctrl = controller ?: return
        val songs = _uiState.value.songs
        val startIndex = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)

        ctrl.setMediaItems(songs.map { it.toMediaItem() }, startIndex, 0L)
        ctrl.prepare()
        ctrl.play()
    }

    fun togglePlayPause() {
        val ctrl = controller ?: return
        if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
    }

    override fun onCleared() {
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        controllerFuture = null
        super.onCleared()
    }
}

private fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri)
                .build(),
        )
        .build()
