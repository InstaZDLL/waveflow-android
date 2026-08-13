package app.waveflow.data.remote

import app.waveflow.model.RemoteAlbum
import app.waveflow.model.RemoteArtist
import app.waveflow.model.RemoteSong
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ce qui circule sur le fil, et qui ne sort pas de ce paquet.
 *
 * Les noms sont ceux du serveur ; le reste de l'app manipule [AuthTokens] et
 * [app.waveflow.model.ServerSession], que l'API distante ne contraint pas.
 */
@Serializable
internal data class LoginRequest(
    val username: String,
    val password: String,
    @SerialName("device_name") val deviceName: String,
)

@Serializable
internal data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
internal data class AuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("device_id") val deviceId: String,
    val user: UserResponse,
)

@Serializable
internal data class UserResponse(
    val id: String,
    val username: String,
    val role: String,
)

/** Corps d'erreur du serveur : `{"code": "...", "message": "..."}`. */
@Serializable
internal data class ErrorBody(
    val code: String,
    val message: String,
)

// --- Catalogue ------------------------------------------------------------
//
// Le serveur aplatit ses détails : `/albums/{id}` renvoie les champs de l'album
// au premier niveau, avec `songs` à côté, et non un objet `album` imbriqué.
// D'où la répétition des champs dans les réponses de détail.

@Serializable
internal data class AlbumResponse(
    val id: String,
    val title: String,
    val artist: String? = null,
    @SerialName("artist_id") val artistId: String? = null,
    val year: Int? = null,
    @SerialName("artwork_hash") val artworkHash: String? = null,
) {
    fun toModel(artwork: ArtworkUrls) = RemoteAlbum(
        id = id,
        title = title,
        artist = artist,
        artistId = artistId,
        year = year,
        artworkUri = artwork.forHash(artworkHash),
    )
}

@Serializable
internal data class ArtistResponse(
    val id: String,
    val name: String,
    @SerialName("album_count") val albumCount: Int? = null,
    @SerialName("artwork_hash") val artworkHash: String? = null,
) {
    fun toModel(artwork: ArtworkUrls) = RemoteArtist(
        id = id,
        name = name,
        albumCount = albumCount,
        artworkUri = artwork.forHash(artworkHash),
    )
}

@Serializable
internal data class SongResponse(
    val id: String,
    val title: String,
    val album: String? = null,
    @SerialName("album_id") val albumId: String? = null,
    val artist: String? = null,
    val track: Int? = null,
    @SerialName("duration_ms") val durationMs: Long,
    @SerialName("artwork_hash") val artworkHash: String? = null,
) {
    fun toModel(artwork: ArtworkUrls) = RemoteSong(
        id = id,
        title = title,
        album = album,
        albumId = albumId,
        artist = artist,
        trackNumber = track,
        durationMs = durationMs,
        artworkUri = artwork.forHash(artworkHash),
    )
}

@Serializable
internal data class AlbumDetailResponse(
    val id: String,
    val title: String,
    val artist: String? = null,
    @SerialName("artist_id") val artistId: String? = null,
    val year: Int? = null,
    @SerialName("artwork_hash") val artworkHash: String? = null,
    val songs: List<SongResponse> = emptyList(),
) {
    val album: AlbumResponse
        get() = AlbumResponse(id, title, artist, artistId, year, artworkHash)
}

/** `/api/v2/search` : les trois types dans une seule réponse. */
@Serializable
internal data class SearchResponse(
    val artists: List<ArtistResponse> = emptyList(),
    val albums: List<AlbumResponse> = emptyList(),
    val songs: List<SongResponse> = emptyList(),
)

/** `{"url": "/api/v2/stream/<ticket>", "expires_at": <ms>}` — l'URL est relative. */
@Serializable
internal data class StreamTicketResponse(
    val url: String,
    @SerialName("expires_at") val expiresAt: Long,
)

@Serializable
internal data class ArtistDetailResponse(
    val id: String,
    val name: String,
    @SerialName("album_count") val albumCount: Int? = null,
    @SerialName("artwork_hash") val artworkHash: String? = null,
    val albums: List<AlbumResponse> = emptyList(),
) {
    val artist: ArtistResponse
        get() = ArtistResponse(id, name, albumCount, artworkHash)
}
