package app.waveflow.data.remote

import app.waveflow.model.RemoteAlbum
import app.waveflow.model.RemoteAlbumDetail
import app.waveflow.model.RemoteArtist
import app.waveflow.model.RemoteArtistDetail
import app.waveflow.model.RemoteSearchResults
import app.waveflow.model.StreamRendering

/**
 * Le catalogue distant, muni d'une session.
 *
 * Fait le lien entre [CatalogApi], qui ne connaît que des jetons, et
 * [ServerSessionRepository], qui les détient.
 */
class CatalogRepository(
    private val api: CatalogApi,
    private val sessionRepository: ServerSessionRepository,
) {

    suspend fun albums(offset: Int, limit: Int = CATALOG_PAGE_SIZE): List<RemoteAlbum> =
        authorized { url, token -> api.albums(url, token, offset, limit) }

    suspend fun artists(offset: Int, limit: Int = CATALOG_PAGE_SIZE): List<RemoteArtist> =
        authorized { url, token -> api.artists(url, token, offset, limit) }

    suspend fun album(albumId: String): RemoteAlbumDetail =
        authorized { url, token -> api.album(url, token, albumId) }

    suspend fun artist(artistId: String): RemoteArtistDetail =
        authorized { url, token -> api.artist(url, token, artistId) }

    suspend fun search(query: String, limit: Int = CATALOG_PAGE_SIZE): RemoteSearchResults =
        authorized { url, token -> api.search(url, token, query, offset = 0, limit = limit) }

    /**
     * URL de diffusion d'une piste, valable une heure côté serveur.
     *
     * Demandée au moment de lire, et non à la constitution de la file : une
     * longue file dépasserait l'échéance avant d'atteindre ses derniers
     * morceaux.
     */
    suspend fun streamUrl(trackId: String, rendering: StreamRendering): String =
        authorized { url, token -> api.streamTicket(url, token, trackId, rendering) }

    /** Voir [CatalogApi.transcodingAvailable]. */
    suspend fun transcodingAvailable(): Boolean =
        authorized { url, token -> api.transcodingAvailable(url, token) }

    /**
     * Exécute [call] avec un jeton valide, en réessayant une fois sur refus.
     *
     * [ServerSessionRepository.authorize] renouvelle déjà avant l'échéance,
     * mais un jeton peut être révoqué depuis un autre appareil : il est alors
     * valide selon l'horloge et refusé par le serveur. Le second essai repart
     * d'un jeton fraîchement obtenu ; s'il échoue à son tour, c'est que la
     * session est bel et bien fermée.
     *
     * L'adresse et le jeton viennent du même appel, donc de la même session :
     * les demander séparément permettrait d'adresser à un serveur le jeton d'un
     * autre, si l'utilisateur se reconnecte ailleurs entre les deux.
     */
    private suspend fun <T> authorized(call: suspend (String, String) -> T): T {
        val first = sessionRepository.authorize()
            ?: throw ServerException.Unauthorized(SESSION_CLOSED)

        return try {
            call(first.serverUrl, first.accessToken)
        } catch (refused: ServerException.Unauthorized) {
            // Périmer d'abord : sans ça, le second essai réutiliserait le jeton
            // que le serveur vient de refuser, l'échéance locale le croyant bon.
            sessionRepository.expireAccessToken(first.accessToken)
            // Un renouvellement n'est utilisable que sur le serveur du premier
            // essai : l'appel s'est déroulé sans verrou, et la session a pu
            // basculer ailleurs entre-temps. Les identifiants n'ont de sens que
            // pour celui qui les a émis — rejouer ailleurs rendrait une erreur,
            // ou pire une ressource étrangère portant le même identifiant.
            val renewed = sessionRepository.authorize()
                ?.takeIf { it.serverUrl == first.serverUrl }
                ?: throw refused
            call(renewed.serverUrl, renewed.accessToken)
        }
    }

    private companion object {
        const val SESSION_CLOSED = "Aucune session serveur."
    }
}
