package app.waveflow.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.waveflow.WaveFlowApp
import app.waveflow.data.LibraryStore
import app.waveflow.data.PlayHistoryRepository
import app.waveflow.model.Album
import app.waveflow.model.Song
import app.waveflow.playback.localSongIdOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Ce que l'accueil a à montrer.
 *
 * @property resume la dernière piste écoutée, s'il en reste une trace et
 *   qu'elle est toujours sur l'appareil.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val resume: Song? = null,
    val recentlyPlayed: List<Song> = emptyList(),
    val recentlyAdded: List<Album> = emptyList(),
) {
    /** Rien à proposer : ni écoute passée, ni musique sur l'appareil. */
    val isEmpty: Boolean
        get() = !isLoading && resume == null && recentlyPlayed.isEmpty() && recentlyAdded.isEmpty()
}

/**
 * L'accueil, composé de ce que l'appareil sait déjà.
 *
 * Rien n'est demandé au réseau : l'écoute passée vient de la base locale, les
 * ajouts récents du MediaStore. L'écran s'affiche donc hors ligne comme
 * connecté.
 */
class HomeViewModel(
    libraryStore: LibraryStore,
    history: PlayHistoryRepository,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        libraryStore.library,
        history.observeRecent(HISTORIQUE),
    ) { library, ecoutes ->
        // Les pistes du serveur sont bien notées dans l'historique, mais
        // l'accueil ne sait pas encore les rendre : il faudrait les redemander
        // au catalogue, et l'écran ne parle qu'à l'appareil. Elles sont donc
        // ignorées ici plutôt qu'affichées à moitié.
        val ecoutees = ecoutes.mapNotNull { entree ->
            localSongIdOf(entree.mediaId)?.let { library.songsById[it] }
        }

        HomeUiState(
            isLoading = library.isLoading,
            resume = ecoutees.firstOrNull(),
            // La reprise occupe déjà la première : la répéter juste en dessous
            // ferait doublon.
            recentlyPlayed = ecoutees.drop(1).take(RECENTES),
            recentlyAdded = library.albums
                .sortedByDescending { it.addedAtMs }
                .take(AJOUTS),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(ARRET_MS), HomeUiState())

    companion object {
        private const val HISTORIQUE = 21
        private const val RECENTES = 20
        private const val AJOUTS = 12
        private const val ARRET_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as WaveFlowApp
                HomeViewModel(
                    libraryStore = app.container.libraryStore,
                    history = app.container.playHistoryRepository,
                )
            }
        }
    }
}
