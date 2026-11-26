package app.waveflow.data.remote

import app.waveflow.model.Lyrics
import app.waveflow.model.RemoteAlbum
import app.waveflow.model.RemoteAlbumDetail
import app.waveflow.model.RemoteArtist
import app.waveflow.model.RemoteArtistDetail
import app.waveflow.model.RemoteSearchResults
import app.waveflow.model.StreamRendering
import kotlinx.serialization.SerializationException

/** Catalogue distant, par-dessus [ServerHttp]. */
class HttpCatalogApi(
    private val http: ServerHttp = ServerHttp(),
) : CatalogApi {

    override suspend fun albums(
        serverUrl: String,
        accessToken: String,
        offset: Int,
        limit: Int,
    ): List<RemoteAlbum> = http.get(
        serverUrl = serverUrl,
        path = ALBUMS,
        query = pageQuery(offset, limit),
        accessToken = accessToken,
    ).decode<List<AlbumResponse>>().map { it.toModel(artwork(serverUrl)) }

    override suspend fun artists(
        serverUrl: String,
        accessToken: String,
        offset: Int,
        limit: Int,
    ): List<RemoteArtist> = http.get(
        serverUrl = serverUrl,
        path = ARTISTS,
        query = pageQuery(offset, limit),
        accessToken = accessToken,
    ).decode<List<ArtistResponse>>().map { it.toModel(artwork(serverUrl)) }

    override suspend fun album(
        serverUrl: String,
        accessToken: String,
        albumId: String,
    ): RemoteAlbumDetail = http.get(
        serverUrl = serverUrl,
        path = ALBUMS,
        pathSegment = albumId,
        accessToken = accessToken,
    ).decode<AlbumDetailResponse>().let { response ->
        val artwork = artwork(serverUrl)
        RemoteAlbumDetail(
            album = response.album.toModel(artwork),
            // Le serveur ne garantit pas l'ordre des morceaux d'un album ;
            // le numéro de piste, lui, est ce que l'utilisateur attend.
            songs = response.songs.map { it.toModel(artwork) }.sortedWith(BY_TRACK_THEN_TITLE),
        )
    }

    override suspend fun artist(
        serverUrl: String,
        accessToken: String,
        artistId: String,
    ): RemoteArtistDetail = http.get(
        serverUrl = serverUrl,
        path = ARTISTS,
        pathSegment = artistId,
        accessToken = accessToken,
    ).decode<ArtistDetailResponse>().let { response ->
        val artwork = artwork(serverUrl)
        RemoteArtistDetail(
            artist = response.artist.toModel(artwork),
            albums = response.albums.map { it.toModel(artwork) },
        )
    }

    override suspend fun search(
        serverUrl: String,
        accessToken: String,
        query: String,
        offset: Int,
        limit: Int,
    ): RemoteSearchResults = http.get(
        serverUrl = serverUrl,
        path = SEARCH,
        query = pageQuery(offset, limit) + ("q" to query),
        accessToken = accessToken,
    ).decode<SearchResponse>().let { response ->
        val artwork = artwork(serverUrl)
        RemoteSearchResults(
            songs = response.songs.map { it.toModel(artwork) },
            albums = response.albums.map { it.toModel(artwork) },
            artists = response.artists.map { it.toModel(artwork) },
        )
    }

    override suspend fun streamTicket(
        serverUrl: String,
        accessToken: String,
        trackId: String,
        rendering: StreamRendering,
        offsetMs: Long,
    ): String {
        val ticket = http.post(
            serverUrl = serverUrl,
            path = "$TRACKS/$trackId/stream-ticket",
            body = "{}",
            accessToken = accessToken,
        ).decode<StreamTicketResponse>()

        return http.absoluteUrl(serverUrl, ticket.url, query = rendering.toQuery(offsetMs))
    }

    /**
     * Une piste que le serveur ne connaît pas (`404`) n'a pas de paroles : elle a
     * pu disparaître d'une bibliothèque rescannée pendant qu'elle restait dans
     * la file. Ce n'est pas une panne à réessayer. Tout autre refus — un `429`
     * d'abord — remonte : il dit « pas maintenant », pas « pas de paroles ».
     */
    override suspend fun lyrics(serverUrl: String, accessToken: String, trackId: String): Lyrics? {
        // L'identifiant entre dans le chemin, suivi de `/lyrics` : il ne doit
        // pouvoir désigner aucune autre route. Le serveur n'en émet pas d'autres,
        // et une piste qu'on ne sait pas nommer n'a pas de paroles à demander.
        if (!trackId.matches(IDENTIFIANT_DE_PISTE)) return null
        return try {
            http.get(
                serverUrl = serverUrl,
                path = "$TRACKS/$trackId/lyrics",
                accessToken = accessToken,
            ).decode<LyricsListResponse>().toModel()
        } catch (refus: ServerException.Rejected) {
            if (refus.httpCode != HTTP_NOT_FOUND) throw refus
            null
        }
    }

    /**
     * L'original ne dit rien : le serveur le sert par défaut, et refuse qu'on
     * lui donne un débit ou un décalage.
     */
    private fun StreamRendering.toQuery(offsetMs: Long): Map<String, String> =
        if (isOriginal) {
            emptyMap()
        } else {
            buildMap {
                put("format", format)
                bitrate?.let { put("bitrate", it.toString()) }
                if (offsetMs > 0L) put("offset_ms", offsetMs.toString())
            }
        }

    private fun artwork(serverUrl: String) = ArtworkUrls(serverUrl, http)

    private fun pageQuery(offset: Int, limit: Int) = mapOf(
        "offset" to offset.toString(),
        "limit" to limit.toString(),
    )

    private inline fun <reified T> String.decode(): T = try {
        http.json.decodeFromString<T>(this)
    } catch (error: SerializationException) {
        throw ServerException.Unexpected("Catalogue illisible : ${error.message}", error)
    }

    private companion object {
        const val ALBUMS = "api/v2/albums"
        const val ARTISTS = "api/v2/artists"
        const val TRACKS = "api/v2/tracks"
        const val SEARCH = "api/v2/search"
        const val HTTP_NOT_FOUND = 404

        /** Un UUID, ou tout identifiant fait de lettres, chiffres et tirets. */
        val IDENTIFIANT_DE_PISTE = Regex("[A-Za-z0-9-]+")

        /** Sans numéro de piste, on retombe sur le titre plutôt que sur rien. */
        val BY_TRACK_THEN_TITLE = compareBy<app.waveflow.model.RemoteSong>(
            { it.trackNumber ?: Int.MAX_VALUE },
            { it.title },
        )
    }
}
