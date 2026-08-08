package app.waveflow.ui.playlists

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.waveflow.WaveFlowApp
import app.waveflow.data.LibraryStore
import app.waveflow.data.PlaylistRepository
import app.waveflow.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Playlists locales : lecture de leur contenu et écritures.
 *
 * Les playlists ne stockent que des identifiants ; leur résolution en [Song]
 * se fait ici, contre la bibliothèque partagée.
 */
class PlaylistsViewModel(
    libraryStore: LibraryStore,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    val state: StateFlow<PlaylistsUiState> =
        combine(
            libraryStore.library,
            playlistRepository.observePlaylists(),
            playlistRepository.observeEntries(),
        ) { library, playlists, entries ->
            PlaylistsUiState(
                isLoading = library.isLoading,
                playlists = playlists,
                songsByPlaylist = entries
                    .groupBy { it.playlistId }
                    .mapValues { (_, entriesOfPlaylist) ->
                        entriesOfPlaylist
                            .sortedBy { it.position }
                            .mapNotNull { library.songsById[it.songId] }
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
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    /**
     * Exécute une opération d’écriture et transforme ses échecs en événements d’erreur.
     *
     * Les annulations sont propagées, tandis que les autres exceptions sont journalisées
     * et signalées avec le message fourni.
     *
     * @param failureMessage Message à émettre en cas d’échec.
     * @param block Opération d’écriture à exécuter.
     */
    private fun write(failureMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // La cause reste dans le journal : sans elle, un échec ne
                // laisserait aucune trace exploitable.
                Log.w(TAG, failureMessage, error)
                // Le message SQLite, lui, ne dit rien à l'utilisateur : on ne
                // lui remonte que l'action qui a échoué.
                _errors.emit(failureMessage)
            }
        }
    }

    /**
     * Creates a playlist with the provided name.
     *
     * Blank or whitespace-only names are ignored.
     *
     * @param name The name of the playlist to create.
     */
    fun create(name: String) {
        val trimmed = name.trim().ifBlank { return }
        write("Impossible de créer la playlist.") { playlistRepository.create(trimmed) }
    }

    /**
     * Creates a playlist and adds the specified song to it.
     *
     * Blank names are ignored.
     *
     * @param name The playlist name.
     * @param song The song to add to the playlist.
     */
    fun createWith(name: String, song: Song) {
        val trimmed = name.trim().ifBlank { return }
        write("Impossible de créer la playlist.") {
            playlistRepository.createWithSong(trimmed, song.id)
        }
    }

    /**
     * Renames a playlist using the provided name after removing surrounding whitespace.
     *
     * @param playlistId The identifier of the playlist to rename.
     * @param name The new playlist name.
     */
    fun rename(playlistId: Long, name: String) {
        val trimmed = name.trim().ifBlank { return }
        write("Impossible de renommer la playlist.") {
            playlistRepository.rename(playlistId, trimmed)
        }
    }

    /**
     * Deletes a playlist.
     *
     * @param playlistId The identifier of the playlist to delete.
     */
    fun delete(playlistId: Long) {
        write("Impossible de supprimer la playlist.") { playlistRepository.delete(playlistId) }
    }

    /**
     * Adds a song to a playlist.
     *
     * @param playlistId The identifier of the playlist.
     * @param song The song to add.
     */
    fun addSong(playlistId: Long, song: Song) {
        write("Impossible d'ajouter le morceau à la playlist.") {
            playlistRepository.addSong(playlistId, song.id)
        }
    }

    /**
     * Removes a song from a playlist.
     *
     * @param playlistId The identifier of the playlist.
     * @param song The song to remove.
     */
    fun removeSong(playlistId: Long, song: Song) {
        write("Impossible de retirer le morceau de la playlist.") {
            playlistRepository.removeSong(playlistId, song.id)
        }
    }

    companion object {
        private const val TAG = "PlaylistsViewModel"
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WaveFlowApp
                PlaylistsViewModel(
                    libraryStore = app.container.libraryStore,
                    playlistRepository = app.container.playlistRepository,
                )
            }
        }
    }
}
