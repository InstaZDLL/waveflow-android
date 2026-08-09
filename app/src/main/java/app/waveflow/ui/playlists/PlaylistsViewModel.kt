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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    /** Sérialise les réordonnancements ; voir [reorder]. */
    private val reorderMutex = Mutex()

    /**
     * Rang du dernier réordonnancement demandé, par playlist.
     *
     * Lu et écrit depuis `viewModelScope`, donc toujours sur le thread
     * principal : une map ordinaire suffit.
     */
    private val reorderGenerations = mutableMapOf<Long, Int>()

    /**
     * Exécute une écriture en confinant son échec.
     *
     * Une exception qui remonterait de `viewModelScope.launch` n'est rattrapée
     * par personne et fait tomber l'application : les erreurs Room doivent
     * devenir un message, pas un crash.
     */
    private fun write(
        failureMessage: String,
        onFailure: () -> Unit = {},
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                onFailure()
                // La cause reste dans le journal : sans elle, un échec ne
                // laisserait aucune trace exploitable.
                Log.w(TAG, failureMessage, error)
                // Le message SQLite, lui, ne dit rien à l'utilisateur : on ne
                // lui remonte que l'action qui a échoué.
                _errors.emit(failureMessage)
            }
        }
    }

    fun create(name: String) {
        val trimmed = name.trim().ifBlank { return }
        write("Impossible de créer la playlist.") { playlistRepository.create(trimmed) }
    }

    /** Crée une playlist et y place [song] dans la foulée. */
    fun createWith(name: String, song: Song) {
        val trimmed = name.trim().ifBlank { return }
        write("Impossible de créer la playlist.") {
            playlistRepository.createWithSong(trimmed, song.id)
        }
    }

    fun rename(playlistId: Long, name: String) {
        val trimmed = name.trim().ifBlank { return }
        write("Impossible de renommer la playlist.") {
            playlistRepository.rename(playlistId, trimmed)
        }
    }

    fun delete(playlistId: Long) {
        write("Impossible de supprimer la playlist.") { playlistRepository.delete(playlistId) }
    }

    fun addSong(playlistId: Long, song: Song) {
        write("Impossible d'ajouter le morceau à la playlist.") {
            playlistRepository.addSong(playlistId, song.id)
        }
    }

    fun removeSong(playlistId: Long, song: Song) {
        write("Impossible de retirer le morceau de la playlist.") {
            playlistRepository.removeSong(playlistId, song.id)
        }
    }

    /**
     * Enregistre l'ordre obtenu par glisser-déposer.
     *
     * L'écran a déjà réordonné sa liste pour suivre le doigt. En cas d'échec,
     * rien n'a changé en base : le flux Room n'émettra donc pas, et c'est
     * [onFailure] qui doit ramener l'écran à l'ordre stocké. Sans lui, la
     * liste resterait durablement en désaccord avec la base.
     */
    fun reorder(playlistId: Long, songs: List<Song>, onFailure: () -> Unit = {}) {
        val generation = (reorderGenerations[playlistId] ?: 0) + 1
        reorderGenerations[playlistId] = generation

        write(
            failureMessage = "Impossible de réordonner la playlist.",
            // Un échec périmé ne doit pas défaire un ordre plus récent :
            // l'écran a déjà affiché le suivant, le restaurer le ramènerait en
            // arrière sans raison visible.
            onFailure = { if (reorderGenerations[playlistId] == generation) onFailure() },
        ) {
            // Deux glissers rapprochés partent dans deux coroutines : sans ce
            // verrou, c'est l'ordre d'arrivée en base qui déciderait, pas
            // l'ordre des gestes.
            reorderMutex.withLock {
                playlistRepository.reorder(playlistId, songs.map { it.id })
            }
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
