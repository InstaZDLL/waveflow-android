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
import app.waveflow.model.ServerSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Parcours du catalogue distant : albums et artistes, page par page.
 *
 * Les pages ne sont pas conservées à la déconnexion : elles appartiennent à un
 * compte, et l'écran suivant pourrait être celui d'un autre.
 */
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

    /** Une seule page en vol par liste : deux requêtes doubleraient le contenu. */
    private var albumsJob: Job? = null
    private var artistsJob: Job? = null
    private var detailJob: Job? = null

    init {
        session
            .map { it is ServerSession.Connected }
            .distinctUntilChanged()
            .onEach { connected -> if (connected) loadFirstPages() else clear() }
            .launchIn(viewModelScope)
    }

    /** Charge la page suivante d'albums, si elle a lieu d'être. */
    fun loadMoreAlbums() {
        val current = _albums.value
        if (albumsJob?.isActive == true || current.endReached) return

        albumsJob = viewModelScope.launch {
            _albums.value = current.copy(isLoading = true, errorMessage = null)
            try {
                val page = catalogRepository.albums(offset = current.items.size)
                _albums.value = PagedList(
                    items = current.items + page,
                    isLoading = false,
                    // Le serveur ne dit pas combien il en reste : une page plus
                    // courte que demandée est le seul signal de fin.
                    endReached = page.size < CATALOG_PAGE_SIZE,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _albums.value = current.copy(isLoading = false, errorMessage = error.toMessage())
            }
        }
    }

    fun loadMoreArtists() {
        val current = _artists.value
        if (artistsJob?.isActive == true || current.endReached) return

        artistsJob = viewModelScope.launch {
            _artists.value = current.copy(isLoading = true, errorMessage = null)
            try {
                val page = catalogRepository.artists(offset = current.items.size)
                _artists.value = PagedList(
                    items = current.items + page,
                    isLoading = false,
                    endReached = page.size < CATALOG_PAGE_SIZE,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _artists.value = current.copy(isLoading = false, errorMessage = error.toMessage())
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
        detailJob?.cancel()
        _albumDetail.value = AlbumDetailState(isLoading = true)
        detailJob = viewModelScope.launch {
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
        detailJob?.cancel()
        _artistDetail.value = ArtistDetailState(isLoading = true)
        detailJob = viewModelScope.launch {
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
        detailJob?.cancel()
        _albums.value = PagedList()
        _artists.value = PagedList()
        _albumDetail.value = AlbumDetailState()
        _artistDetail.value = ArtistDetailState()
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
