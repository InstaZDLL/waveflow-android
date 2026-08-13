package app.waveflow.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.waveflow.WaveFlowApp
import app.waveflow.model.RemoteSong
import app.waveflow.model.Song
import app.waveflow.playback.PlaybackController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Tout ce qui touche à la lecture : état du lecteur et commandes.
 *
 * Il possède le [PlaybackController] et le libère avec lui-même — une liaison
 * vivante empêcherait le service de s'arrêter.
 */
class PlayerViewModel(
    private val playbackController: PlaybackController,
) : ViewModel() {

    // Plus de croisement avec la bibliothèque : le lecteur décrit lui-même sa
    // piste, ce qui vaut aussi pour celles du serveur, absentes du MediaStore.
    // Cette projection est réévaluée à chaque tic de position — la garder sans
    // recherche est ce qui la rend gratuite.
    val state: StateFlow<PlayerUiState> =
        playbackController.state.map { playback ->
            PlayerUiState(
                track = playback.current,
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

    fun connect() = playbackController.connect()

    /**
     * Démarre [song] avec [queue] comme file d'attente : la bibliothèque
     * entière depuis l'onglet Titres, l'album, l'artiste ou la playlist depuis
     * leur écran.
     */
    fun playFrom(queue: List<Song>, song: Song) {
        val startIndex = queue.indexOfFirst { it.id == song.id }
        if (startIndex < 0) return
        playbackController.play(queue, startIndex)
    }

    /** Démarre [queue] par son premier morceau. Sans effet si elle est vide. */
    fun playFirst(queue: List<Song>) {
        queue.firstOrNull()?.let { playFrom(queue, it) }
    }

    fun playShuffled(queue: List<Song>) = playbackController.playShuffled(queue)

    /**
     * Démarre [song] depuis une file de morceaux du serveur.
     *
     * Chemin distinct de [playFrom] : les deux catalogues ne partagent ni type
     * ni identifiant, et la file remplace l'autre plutôt que de s'y mêler.
     */
    fun playRemoteFrom(queue: List<RemoteSong>, song: RemoteSong) {
        val startIndex = queue.indexOfFirst { it.id == song.id }
        if (startIndex < 0) return
        playbackController.playRemote(queue, startIndex)
    }

    /** Démarre [queue] distante par son premier morceau. */
    fun playRemoteFirst(queue: List<RemoteSong>) {
        queue.firstOrNull()?.let { playRemoteFrom(queue, it) }
    }

    fun playRemoteShuffled(queue: List<RemoteSong>) =
        playbackController.playRemoteShuffled(queue)

    fun togglePlayPause() = playbackController.playPause()

    fun skipNext() = playbackController.skipNext()

    fun skipPrevious() = playbackController.skipPrevious()

    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)

    fun toggleShuffle() = playbackController.toggleShuffle()

    fun cycleRepeatMode() = playbackController.cycleRepeatMode()

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
                    playbackController = app.container.createPlaybackController(),
                )
            }
        }
    }
}
