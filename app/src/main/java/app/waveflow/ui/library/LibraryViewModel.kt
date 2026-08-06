package app.waveflow.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.waveflow.WaveFlowApp
import app.waveflow.data.MusicRepository
import app.waveflow.data.PlaylistRepository
import app.waveflow.model.Album
import app.waveflow.model.Artist
import app.waveflow.model.Song
import app.waveflow.model.toAlbums
import app.waveflow.model.toArtists
import app.waveflow.playback.PlaybackController
import app.waveflow.ui.player.PlayerUiState
import app.waveflow.ui.playlists.PlaylistsUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Orchestrateur de la bibliothèque et du lecteur.
 *
 * Il ne connaît ni le MediaStore ni Media3 : il assemble le flux de morceaux
 * du [MusicRepository] et l'état du [PlaybackController] en deux états d'écran
 * — [uiState] pour la liste, [playerState] pour le lecteur — et relaie les
 * intentions de l'utilisateur.
 */
class LibraryViewModel(
    private val musicRepository: MusicRepository,
    private val playlistRepository: PlaylistRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {

    /** Partie de l'état qui appartient à la bibliothèque (le reste vient de la lecture). */
    private data class LibraryData(
        val isLoading: Boolean = true,
        val songs: List<Song> = emptyList(),
        val albums: List<Album> = emptyList(),
        val artists: List<Artist> = emptyList(),
        val errorMessage: String? = null,
    )

    private val library = MutableStateFlow(LibraryData())

    val uiState: StateFlow<LibraryUiState> =
        combine(library, playbackController.state) { data, playback ->
            LibraryUiState(
                isLoading = data.isLoading,
                songs = data.songs,
                albums = data.albums,
                artists = data.artists,
                errorMessage = data.errorMessage,
                nowPlayingId = playback.currentSongId,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = LibraryUiState(),
        )

    val playerState: StateFlow<PlayerUiState> =
        combine(library, playbackController.state) { data, playback ->
            PlayerUiState(
                song = playback.currentSongId?.let { id -> data.songs.firstOrNull { it.id == id } },
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

    val playlistsState: StateFlow<PlaylistsUiState> =
        combine(
            library,
            playlistRepository.observePlaylists(),
            playlistRepository.observeEntries(),
        ) { data, playlists, entries ->
            val songsById = data.songs.associateBy { it.id }
            PlaylistsUiState(
                isLoading = data.isLoading,
                playlists = playlists,
                songsByPlaylist = entries
                    .groupBy { it.playlistId }
                    .mapValues { (_, entriesOfPlaylist) ->
                        entriesOfPlaylist
                            .sortedBy { it.position }
                            .mapNotNull { songsById[it.songId] }
                    },
            )
        }.catch { error ->
            // Sans ça, une base illisible ferait échouer le flux en silence et
            // l'onglet resterait bloqué sur son indicateur de chargement.
            emit(
                PlaylistsUiState(
                    isLoading = false,
                    errorMessage = error.message ?: "Impossible de lire les playlists.",
                ),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = PlaylistsUiState(),
        )

    /**
     * Échecs d'écriture, à afficher une fois puis à oublier.
     *
     * Un événement plutôt qu'un état : une suppression ratée ne doit pas
     * remplacer durablement la liste par un message d'erreur.
     */
    private val _playlistErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val playlistErrors: SharedFlow<String> = _playlistErrors.asSharedFlow()

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
                    // Regroupements calculés ici, une fois par chargement.
                    library.update {
                        it.copy(
                            isLoading = false,
                            songs = songs,
                            albums = songs.toAlbums(),
                            artists = songs.toArtists(),
                            errorMessage = null,
                        )
                    }
                }
        }
    }

    /**
     * Démarre [song] avec [queue] comme file d'attente : la bibliothèque
     * entière depuis l'onglet Titres, l'album ou l'artiste depuis leur écran.
     */
    fun playFrom(queue: List<Song>, song: Song) {
        val startIndex = queue.indexOfFirst { it.id == song.id }
        if (startIndex < 0) return
        playbackController.play(queue, startIndex)
    }

    fun playShuffled(queue: List<Song>) = playbackController.playShuffled(queue)

    fun togglePlayPause() = playbackController.playPause()

    fun skipNext() = playbackController.skipNext()

    fun skipPrevious() = playbackController.skipPrevious()

    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)

    fun toggleShuffle() = playbackController.toggleShuffle()

    fun cycleRepeatMode() = playbackController.cycleRepeatMode()

    /**
     * Exécute une écriture de playlist en confinant son échec.
     *
     * Une exception qui remonterait de `viewModelScope.launch` n'est rattrapée
     * par personne et fait tomber l'application : les erreurs Room doivent
     * devenir un message, pas un crash.
     */
    private fun playlistWrite(failureMessage: String, write: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                write()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // Le message SQLite ne dit rien à l'utilisateur : on remonte
                // l'action qui a échoué.
                _playlistErrors.emit(failureMessage)
            }
        }
    }

    fun createPlaylist(name: String) {
        val trimmed = name.trim().ifBlank { return }
        playlistWrite("Impossible de créer la playlist.") {
            playlistRepository.create(trimmed)
        }
    }

    /** Crée une playlist et y place [song] dans la foulée. */
    fun createPlaylistWith(name: String, song: Song) {
        val trimmed = name.trim().ifBlank { return }
        playlistWrite("Impossible de créer la playlist.") {
            playlistRepository.createWithSong(trimmed, song.id)
        }
    }

    fun renamePlaylist(playlistId: Long, name: String) {
        val trimmed = name.trim().ifBlank { return }
        playlistWrite("Impossible de renommer la playlist.") {
            playlistRepository.rename(playlistId, trimmed)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        playlistWrite("Impossible de supprimer la playlist.") {
            playlistRepository.delete(playlistId)
        }
    }

    fun addSongToPlaylist(playlistId: Long, song: Song) {
        playlistWrite("Impossible d'ajouter le morceau à la playlist.") {
            playlistRepository.addSong(playlistId, song.id)
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, song: Song) {
        playlistWrite("Impossible de retirer le morceau de la playlist.") {
            playlistRepository.removeSong(playlistId, song.id)
        }
    }

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
                    playlistRepository = app.container.playlistRepository,
                    playbackController = app.container.createPlaybackController(),
                )
            }
        }
    }
}
