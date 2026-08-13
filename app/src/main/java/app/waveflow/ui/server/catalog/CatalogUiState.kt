package app.waveflow.ui.server.catalog

import app.waveflow.model.RemoteAlbumDetail
import app.waveflow.model.RemoteArtistDetail
import app.waveflow.model.RemoteSearchResults

/**
 * Une liste paginée en cours de chargement.
 *
 * Le serveur renvoie un tableau nu, sans total ni curseur : la fin se déduit
 * d'une page plus courte que demandée. [endReached] porte cette déduction pour
 * que l'écran cesse de redemander.
 */
data class PagedList<T>(
    val items: List<T> = emptyList(),
    val isLoading: Boolean = false,
    val endReached: Boolean = false,
    val errorMessage: String? = null,
) {
    /** Vrai quand rien n'a encore été chargé et qu'il n'y a rien à montrer. */
    val isInitialLoad: Boolean get() = isLoading && items.isEmpty()

    val isEmpty: Boolean get() = !isLoading && errorMessage == null && items.isEmpty()

    /** Une erreur survenue après coup ne doit pas effacer ce qui est déjà là. */
    val hasContent: Boolean get() = items.isNotEmpty()
}

/** Un détail chargé à la demande : album ou artiste. */
data class DetailState<T>(
    val value: T? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Recherche sur le serveur.
 *
 * Distincte de la recherche locale, qui filtre en mémoire : celle-ci part sur
 * le réseau, peut échouer, et met du temps. L'écran doit pouvoir le montrer.
 */
data class RemoteSearchState(
    val query: String = "",
    val results: RemoteSearchResults = RemoteSearchResults(),
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
) {
    val isActive: Boolean get() = query.isNotBlank()

    /** Vrai quand la recherche a abouti sans rien trouver. */
    val foundNothing: Boolean
        get() = isActive && !isSearching && errorMessage == null && results.isEmpty
}

typealias AlbumDetailState = DetailState<RemoteAlbumDetail>

typealias ArtistDetailState = DetailState<RemoteArtistDetail>
