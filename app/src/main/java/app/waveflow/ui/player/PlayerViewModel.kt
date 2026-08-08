package app.waveflow.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.waveflow.WaveFlowApp
import app.waveflow.data.LibraryStore
import app.waveflow.model.Song
import app.waveflow.playback.PlaybackController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Tout ce qui touche à la lecture : état du lecteur et commandes.
 *
 * Il possède le [PlaybackController] et le libère avec lui-même — une liaison
 * vivante empêcherait le service de s'arrêter.
 */
class PlayerViewModel(
    libraryStore: LibraryStore,
    private val playbackController: PlaybackController,
) : ViewModel() {

    val state: StateFlow<PlayerUiState> =
        combine(libraryStore.library, playbackController.state) { library, playback ->
            PlayerUiState(
                // Index plutôt que parcours : cette combinaison est réévaluée à
                // chaque tic de position.
                song = playback.currentSongId?.let { library.songsById[it] },
                isPlaying = playback.isPlaying,
                positionMs = playback.positionMs,
                durationMs = playback.durationMs,
                shuffleEnabled = playback.shuffleEnabled,
                repeatMode = playback.repeatMode,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = PlayerUiState(),
        )

    /**
 * Connects to the playback controller.
 */
fun connect() = playbackController.connect()

    /**
     * Démarre la lecture de [song] dans [queue].
     *
     * Si [song] n'est pas présente dans la file, aucune lecture ne démarre.
     *
     * @param queue La file d'attente à utiliser.
     * @param song Le morceau à lire.
     */
    fun playFrom(queue: List<Song>, song: Song) {
        val startIndex = queue.indexOfFirst { it.id == song.id }
        if (startIndex < 0) return
        playbackController.play(queue, startIndex)
    }

    /** Starts playback with the first song in [queue]. Has no effect when the queue is empty. */
    fun playFirst(queue: List<Song>) {
        queue.firstOrNull()?.let { playFrom(queue, it) }
    }

    /**
 * Starts playback with the songs in shuffled order.
 *
 * @param queue The songs available for playback.
 */
fun playShuffled(queue: List<Song>) = playbackController.playShuffled(queue)

    /**
 * Toggles playback between playing and paused states.
 */
fun togglePlayPause() = playbackController.playPause()

    /**
 * Advances playback to the next item in the queue.
 */
fun skipNext() = playbackController.skipNext()

    /**
 * Skips to the previous song in the playback queue.
 */
fun skipPrevious() = playbackController.skipPrevious()

    /**
 * Seeks playback to the specified position.
 *
 * @param positionMs The target playback position in milliseconds.
 */
fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)

    /**
 * Toggles shuffle playback.
 */
fun toggleShuffle() = playbackController.toggleShuffle()

    /**
 * Advances to the next repeat mode.
 */
fun cycleRepeatMode() = playbackController.cycleRepeatMode()

    /**
     * Releases playback resources when the view model is cleared while allowing background playback to continue.
     */
    override fun onCleared() {
        // Le service, lui, survit et continue la lecture en arrière-plan.
        playbackController.release()
        super.onCleared()
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WaveFlowApp
                PlayerViewModel(
                    libraryStore = app.container.libraryStore,
                    playbackController = app.container.createPlaybackController(),
                )
            }
        }
    }
}
