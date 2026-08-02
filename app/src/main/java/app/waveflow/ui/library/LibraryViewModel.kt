package app.waveflow.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.waveflow.WaveFlowApp
import app.waveflow.data.MusicRepository
import app.waveflow.model.Song
import app.waveflow.playback.PlaybackController
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Orchestrateur de l'écran bibliothèque.
 *
 * Il ne connaît ni le MediaStore ni Media3 : il assemble le flux de morceaux
 * du [MusicRepository] et l'état du [PlaybackController] en un unique
 * [LibraryUiState], et relaie les intentions de l'utilisateur.
 */
class LibraryViewModel(
    private val musicRepository: MusicRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {

    /** Partie de l'état qui appartient à la bibliothèque (le reste vient de la lecture). */
    private data class LibraryData(
        val isLoading: Boolean = true,
        val songs: List<Song> = emptyList(),
        val errorMessage: String? = null,
    )

    private val library = MutableStateFlow(LibraryData())

    val uiState: StateFlow<LibraryUiState> =
        combine(library, playbackController.state) { data, playback ->
            LibraryUiState(
                isLoading = data.isLoading,
                songs = data.songs,
                errorMessage = data.errorMessage,
                nowPlayingId = playback.currentSongId,
                isPlaying = playback.isPlaying,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = LibraryUiState(),
        )

    private var libraryJob: Job? = null

    /**
     * Appelé une fois la permission audio accordée : c'est seulement à ce
     * moment que le MediaStore est lisible et que la lecture a un sens.
     */
    fun onAudioAccessGranted() {
        playbackController.connect()
        if (libraryJob == null) observeLibrary()
    }

    /** Relance le chargement après une erreur. */
    fun retry() = observeLibrary()

    private fun observeLibrary() {
        libraryJob?.cancel()
        libraryJob = viewModelScope.launch {
            musicRepository.observeSongs()
                .onStart { library.update { it.copy(isLoading = true, errorMessage = null) } }
                .catch { error ->
                    library.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Impossible de lire la bibliothèque.",
                        )
                    }
                }
                .collect { songs ->
                    library.update {
                        it.copy(isLoading = false, songs = songs, errorMessage = null)
                    }
                }
        }
    }

    /** Charge toute la bibliothèque comme file d'attente et démarre à [song]. */
    fun playSong(song: Song) {
        val songs = library.value.songs
        val startIndex = songs.indexOfFirst { it.id == song.id }
        if (startIndex < 0) return
        playbackController.play(songs, startIndex)
    }

    fun togglePlayPause() = playbackController.playPause()

    fun skipNext() = playbackController.skipNext()

    fun skipPrevious() = playbackController.skipPrevious()

    override fun onCleared() {
        // Le contrôleur est détenu par ce ViewModel : on le libère avec lui.
        // Le service, lui, survit et continue la lecture en arrière-plan.
        playbackController.release()
        super.onCleared()
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WaveFlowApp
                LibraryViewModel(
                    musicRepository = app.container.musicRepository,
                    playbackController = app.container.createPlaybackController(),
                )
            }
        }
    }
}
