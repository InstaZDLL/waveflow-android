package app.waveflow.data.remote

import app.waveflow.model.RemoteAlbum
import app.waveflow.model.RemoteAlbumDetail
import app.waveflow.model.RemoteArtist
import app.waveflow.model.RemoteArtistDetail
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
    ).decode<List<AlbumResponse>>().map { it.toModel() }

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
    ).decode<List<ArtistResponse>>().map { it.toModel() }

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
        RemoteAlbumDetail(
            album = response.album.toModel(),
            // Le serveur ne garantit pas l'ordre des morceaux d'un album ;
            // le numéro de piste, lui, est ce que l'utilisateur attend.
            songs = response.songs.map { it.toModel() }.sortedWith(BY_TRACK_THEN_TITLE),
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
        RemoteArtistDetail(
            artist = response.artist.toModel(),
            albums = response.albums.map { it.toModel() },
        )
    }

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

        /** Sans numéro de piste, on retombe sur le titre plutôt que sur rien. */
        val BY_TRACK_THEN_TITLE = compareBy<app.waveflow.model.RemoteSong>(
            { it.trackNumber ?: Int.MAX_VALUE },
            { it.title },
        )
    }
}
