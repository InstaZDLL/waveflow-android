package app.waveflow.ui.server.catalog

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.waveflow.WaveFlowApp
import app.waveflow.data.remote.CATALOG_PAGE_SIZE
import app.waveflow.data.remote.CatalogRepository
import app.waveflow.data.remote.ServerException
import app.waveflow.model.RemoteAlbum
import app.waveflow.model.RemoteArtist
import app.waveflow.model.RemoteSearchResults
import app.waveflow.model.ServerSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Parcours du catalogue distant : albums et artistes, page par page.
 *
 * Les pages ne sont pas conservées à la déconnexion : elles appartiennent à un
 * compte, et l'écran suivant pourrait être celui d'un autre.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModel(
    private val catalogRepository: CatalogRepository,
    session: StateFlow<ServerSession>,
) : ViewModel() {

    private val _albums = MutableStateFlow(PagedList<RemoteAlbum>())
    val albums: StateFlow<PagedList<RemoteAlbum>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow(PagedList<RemoteArtist>())
    val artists: StateFlow<PagedList<RemoteArtist>> = _artists.asStateFlow()

    private val _albumDetail = MutableStateFlow(AlbumDetailState())
    val albumDetail: StateFlow<AlbumDetailState> = _albumDetail.asStateFlow()

    private val _artistDetail = MutableStateFlow(ArtistDetailState())
    val artistDetail: StateFlow<ArtistDetailState> = _artistDetail.asStateFlow()

    private val _search = MutableStateFlow(RemoteSearchState())
    val search: StateFlow<RemoteSearchState> = _search.asStateFlow()

    /** Une seule page en vol par liste : deux requêtes doubleraient le contenu. */
    private var albumsJob: Job? = null
    private var artistsJob: Job? = null

    /**
     * Un job par sorte de détail.
     *
     * Un job commun ferait annuler le chargement d'un artiste par l'ouverture
     * d'un album depuis sa page : l'écran de l'artiste, encore dans la pile,
     * resterait alors bloqué sur son indicateur au retour.
     */
    private var albumDetailJob: Job? = null
    private var artistDetailJob: Job? = null

    init {
        session
            .map { it is ServerSession.Connected }
            .distinctUntilChanged()
            .onEach { connected -> if (connected) loadFirstPages() else clear() }
            .launchIn(viewModelScope)

        // Contrairement à la recherche locale, qui filtre en mémoire à chaque
        // frappe, celle-ci part sur le réseau : sans délai de grâce, taper
        // « écho » lancerait quatre requêtes dont trois inutiles.
        _search
            .map { it.query.trim() }
            .distinctUntilChanged()
            .flatMapLatest { query -> searchFlow(query) }
            .onEach { outcome -> _search.update { outcome(it) } }
            .launchIn(viewModelScope)
    }

    fun onSearchQueryChange(query: String) {
        _search.update { it.copy(query = query) }
    }

    fun clearSearch() {
        _search.value = RemoteSearchState()
    }

    /**
     * Une recherche, sous forme de mises à jour successives de l'état.
     *
     * Renvoyer des transformations plutôt que des états complets évite d'écraser
     * la requête que l'utilisateur continue de taper pendant l'appel.
     *
     * Le délai de grâce est **dans** ce flux, et non avant `flatMapLatest` :
     * placé avant, il retarderait aussi l'arrivée de la nouvelle requête, et la
     * précédente continuerait de courir pendant ce temps — jusqu'à rendre des
     * résultats périmés sous une requête déjà changée. Ici, une frappe annule
     * immédiatement l'attente comme l'appel en cours.
     *
     * Une requête vide n'attend pas : effacer le champ doit vider l'écran tout
     * de suite.
     */
    private fun searchFlow(query: String): Flow<(RemoteSearchState) -> RemoteSearchState> = flow {
        if (query.isBlank()) {
            emit { it.copy(results = RemoteSearchResults(), isSearching = false, errorMessage = null) }
            return@flow
        }

        delay(SEARCH_DEBOUNCE_MS)
        emit { it.copy(isSearching = true, errorMessage = null) }
        try {
            val results = catalogRepository.search(query)
            emit { it.copy(results = results, isSearching = false) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            emit {
                it.copy(
                    results = RemoteSearchResults(),
                    isSearching = false,
                    errorMessage = error.toMessage(),
                )
            }
        }
    }

    /** Charge la page suivante d'albums, si elle a lieu d'être. */
    fun loadMoreAlbums() {
        albumsJob = loadNextPage(_albums, albumsJob) { catalogRepository.albums(offset = it) }
    }

    fun loadMoreArtists() {
        artistsJob = loadNextPage(_artists, artistsJob) { catalogRepository.artists(offset = it) }
    }

    /**
     * Ajoute une page à [state], ou ne fait rien s'il n'y a pas lieu.
     *
     * @param inFlight la page en cours pour cette liste, le cas échéant. Le
     *   défilement redemande sans attendre : sans cette garde, la même page
     *   serait chargée deux fois et affichée en double.
     * @return le job à retenir — celui qui vient de partir, ou [inFlight].
     */
    private fun <T> loadNextPage(
        state: MutableStateFlow<PagedList<T>>,
        inFlight: Job?,
        fetch: suspend (offset: Int) -> List<T>,
    ): Job? {
        val current = state.value
        if (inFlight?.isActive == true || current.endReached) return inFlight

        return viewModelScope.launch {
            state.value = current.copy(isLoading = true, errorMessage = null)
            try {
                val page = fetch(current.items.size)
                state.value = PagedList(
                    items = current.items + page,
                    isLoading = false,
                    // Le serveur ne dit pas combien il en reste : une page plus
                    // courte que demandée est le seul signal de fin.
                    endReached = page.size < CATALOG_PAGE_SIZE,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                state.value = current.copy(isLoading = false, errorMessage = error.toMessage())
            }
        }
    }

    /** Repart de zéro : après une erreur, ou sur demande explicite. */
    fun retryAlbums() {
        albumsJob?.cancel()
        _albums.value = PagedList()
        loadMoreAlbums()
    }

    fun retryArtists() {
        artistsJob?.cancel()
        _artists.value = PagedList()
        loadMoreArtists()
    }

    fun openAlbum(albumId: String) {
        albumDetailJob?.cancel()
        _albumDetail.value = AlbumDetailState(isLoading = true)
        albumDetailJob = viewModelScope.launch {
            try {
                _albumDetail.value = AlbumDetailState(value = catalogRepository.album(albumId))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _albumDetail.value = AlbumDetailState(errorMessage = error.toMessage())
            }
        }
    }

    fun openArtist(artistId: String) {
        artistDetailJob?.cancel()
        _artistDetail.value = ArtistDetailState(isLoading = true)
        artistDetailJob = viewModelScope.launch {
            try {
                _artistDetail.value = ArtistDetailState(value = catalogRepository.artist(artistId))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _artistDetail.value = ArtistDetailState(errorMessage = error.toMessage())
            }
        }
    }

    private fun loadFirstPages() {
        loadMoreAlbums()
        loadMoreArtists()
    }

    private fun clear() {
        albumsJob?.cancel()
        artistsJob?.cancel()
        albumDetailJob?.cancel()
        artistDetailJob?.cancel()
        _albums.value = PagedList()
        _artists.value = PagedList()
        _albumDetail.value = AlbumDetailState()
        _artistDetail.value = ArtistDetailState()
        _search.value = RemoteSearchState()
    }

    private fun Exception.toMessage(): String = when (this) {
        is ServerException.Unauthorized -> "Session expirée. Reconnectez-vous."
        is ServerException.Unreachable -> "Serveur injoignable."
        is ServerException -> "Le serveur n'a pas pu répondre."
        else -> {
            // Un échec qui n'est pas de nature réseau : le journaliser, sinon
            // il ne resterait qu'un message générique sans trace.
            Log.w(TAG, "Échec inattendu du catalogue", this)
            "Une erreur inattendue est survenue."
        }
    }

    companion object {
        private const val TAG = "CatalogViewModel"

        /** De quoi laisser finir un mot avant d'interroger le serveur. */
        private const val SEARCH_DEBOUNCE_MS = 300L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as WaveFlowApp
                CatalogViewModel(
                    catalogRepository = app.container.catalogRepository,
                    session = app.container.serverSessionRepository.session,
                )
            }
        }
    }
}
