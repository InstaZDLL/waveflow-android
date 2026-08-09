package app.waveflow.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.waveflow.WaveFlowApp
import app.waveflow.data.LibraryStore
import app.waveflow.model.SearchResults
import app.waveflow.model.search
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Recherche dans la bibliothèque partagée.
 *
 * Ce ViewModel n'a pas de source à lui : il ne fait que croiser une requête de
 * frappe avec le [LibraryStore]. C'est ce que l'extraction du store rend
 * possible — aucun autre écran n'a à changer pour que la recherche existe.
 */
class SearchViewModel(libraryStore: LibraryStore) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * Résultats du filtrage, recalculés quand la requête ou la bibliothèque
     * changent — et à ces seuls moments. Contrairement à l'état du lecteur,
     * ce flux ne suit pas le tic de position.
     *
     * Le filtrage reste sur le thread principal : il parcourt une liste déjà
     * en mémoire, dont les regroupements sont mémorisés par `Library`. Si des
     * bibliothèques assez grandes pour que la frappe accroche apparaissent,
     * c'est ici qu'un `flowOn(Dispatchers.Default)` viendra.
     */
    val results: StateFlow<SearchResults> =
        combine(_query, libraryStore.library) { query, library ->
            library.search(query)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = SearchResults(),
        )

    fun onQueryChange(value: String) {
        _query.value = value
    }

    /** Vide le champ sans quitter la recherche. */
    fun clear() {
        _query.value = ""
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WaveFlowApp
                SearchViewModel(app.container.libraryStore)
            }
        }
    }
}
