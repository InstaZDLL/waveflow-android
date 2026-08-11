package app.waveflow.data.remote

import app.waveflow.model.RemoteAlbum
import app.waveflow.model.RemoteAlbumDetail
import app.waveflow.model.RemoteArtist
import app.waveflow.model.RemoteArtistDetail

/**
 * Lecture du catalogue d'un serveur WaveFlow.
 *
 * Chaque appel porte son jeton d'accès plutôt que d'aller le chercher : c'est
 * à [CatalogRepository] de décider quand le renouveler, et ce découpage rend
 * l'API testable sans session.
 */
interface CatalogApi {

    /** `GET /api/v2/albums`, trié par le serveur. */
    suspend fun albums(
        serverUrl: String,
        accessToken: String,
        offset: Int,
        limit: Int,
    ): List<RemoteAlbum>

    /** `GET /api/v2/artists`. */
    suspend fun artists(
        serverUrl: String,
        accessToken: String,
        offset: Int,
        limit: Int,
    ): List<RemoteArtist>

    /** `GET /api/v2/albums/{id}` : l'album et ses morceaux d'un seul coup. */
    suspend fun album(serverUrl: String, accessToken: String, albumId: String): RemoteAlbumDetail

    /** `GET /api/v2/artists/{id}` : l'artiste et ses albums. */
    suspend fun artist(serverUrl: String, accessToken: String, artistId: String): RemoteArtistDetail

    /**
     * `POST /api/v2/tracks/{id}/stream-ticket`.
     *
     * Rend une URL de diffusion **absolue**, qui ne demande aucun en-tête
     * d'autorisation — le serveur la rend relative, elle est résolue ici contre
     * [serverUrl]. C'est ce qui permet de la confier telle quelle à ExoPlayer,
     * y compris pour les requêtes de plage d'un déplacement dans le morceau.
     */
    suspend fun streamTicket(serverUrl: String, accessToken: String, trackId: String): String
}

/**
 * Nombre d'éléments demandés par page.
 *
 * Le serveur refuse au-delà de 500 ; on reste bien en deçà, une page devant
 * arriver assez vite pour que le défilement ne marque pas d'arrêt.
 */
const val CATALOG_PAGE_SIZE = 50
