package app.waveflow.data.remote

import app.waveflow.model.RemoteAlbum
import app.waveflow.model.RemoteAlbumDetail
import app.waveflow.model.RemoteArtist
import app.waveflow.model.RemoteArtistDetail
import app.waveflow.model.ServerSession

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

    /**
     * URL de diffusion d'une piste, valable une heure côté serveur.
     *
     * Demandée au moment de lire, et non à la constitution de la file : une
     * longue file dépasserait l'échéance avant d'atteindre ses derniers
     * morceaux.
     */
    suspend fun streamUrl(trackId: String): String =
        authorized { url, token -> api.streamTicket(url, token, trackId) }

    /**
     * Exécute [call] avec un jeton valide, en réessayant une fois sur refus.
     *
     * [ServerSessionRepository.validAccessToken] renouvelle déjà avant
     * l'échéance, mais un jeton peut être révoqué depuis un autre appareil : il
     * est alors valide selon l'horloge et refusé par le serveur. Le second essai
     * repart d'un jeton fraîchement obtenu ; s'il échoue à son tour, c'est que
     * la session est bel et bien fermée.
     */
    private suspend fun <T> authorized(call: suspend (String, String) -> T): T {
        val first = token() ?: throw ServerException.Unauthorized(SESSION_CLOSED)

        return try {
            call(first.first, first.second)
        } catch (refused: ServerException.Unauthorized) {
            val renewed = renewedToken() ?: throw refused
            call(renewed.first, renewed.second)
        }
    }

    /** Adresse et jeton courants, ou `null` sans session. */
    private suspend fun token(): Pair<String, String>? {
        val accessToken = sessionRepository.validAccessToken() ?: return null
        val url = (sessionRepository.session.value as? ServerSession.Connected)?.serverUrl
            ?: return null
        return url to accessToken
    }

    /**
     * Force un renouvellement en périmant le jeton courant.
     *
     * Sans ça, le second essai réutiliserait celui que le serveur vient de
     * refuser : l'échéance locale le croit encore bon.
     */
    private suspend fun renewedToken(): Pair<String, String>? {
        sessionRepository.expireAccessToken()
        return token()
    }

    private companion object {
        const val SESSION_CLOSED = "Aucune session serveur."
    }
}
