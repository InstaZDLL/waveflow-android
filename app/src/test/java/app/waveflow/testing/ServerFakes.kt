package app.waveflow.testing

import app.waveflow.data.remote.AuthTokens
import app.waveflow.data.remote.CatalogApi
import app.waveflow.data.remote.ServerApi
import app.waveflow.data.remote.ServerException
import app.waveflow.data.remote.SessionStore
import app.waveflow.model.RemoteAlbum
import app.waveflow.model.RemoteAlbumDetail
import app.waveflow.model.RemoteArtist
import app.waveflow.model.RemoteArtistDetail
import app.waveflow.model.ServerSession
import kotlinx.coroutines.CompletableDeferred

/**
 * Serveur simulé.
 *
 * Les jetons sont numérotés : `wfa_1` à la connexion, puis `wfa_2`, `wfa_3`…
 * à chaque renouvellement. Un test peut ainsi affirmer *lequel* est rendu, ce
 * qu'une valeur constante ne permettrait pas.
 */
class FakeServerApi(
    /** Modifiable : un test peut faire échouer une connexion puis l'accepter. */
    var loginFailure: Throwable? = null,
    private val refreshFailure: Throwable? = null,
    private val logoutFailure: Throwable? = null,
    /**
     * Si non nul, `refresh` attend ce signal avant de rendre la main.
     *
     * Sans lui, chaque renouvellement s'achève avant que le suivant ne parte :
     * deux ne sont jamais en vol ensemble, et la sérialisation ne peut pas être
     * mise à l'épreuve.
     */
    private val refreshGate: CompletableDeferred<Unit>? = null,
) : ServerApi {

    var refreshCalls = 0
        private set
    var lastDeviceName: String? = null
        private set
    var lastRefreshToken: String? = null
        private set
    var revokedAccessToken: String? = null
        private set

    private var generation = 0
    private var username = "admin"

    override suspend fun login(
        serverUrl: String,
        username: String,
        password: String,
        deviceName: String,
    ): AuthTokens {
        lastDeviceName = deviceName
        loginFailure?.let { throw it }
        this.username = username
        return nextTokens(username)
    }

    override suspend fun refresh(serverUrl: String, refreshToken: String): AuthTokens {
        refreshCalls++
        lastRefreshToken = refreshToken
        refreshGate?.await()
        refreshFailure?.let { throw it }
        // Le compte reste celui de la connexion : le serveur ne le change pas
        // au renouvellement.
        return nextTokens(username)
    }

    override suspend fun logout(serverUrl: String, accessToken: String) {
        revokedAccessToken = accessToken
        logoutFailure?.let { throw it }
    }

    private fun nextTokens(username: String): AuthTokens {
        generation++
        return AuthTokens(
            accessToken = "wfa_$generation",
            refreshToken = "wfr_$generation",
            username = username,
            deviceId = "appareil-1",
            expiresInSeconds = 900L,
        )
    }
}

/**
 * Catalogue simulé.
 *
 * Retient ce qu'on lui a passé, et sait refuser un nombre donné d'appels avant
 * d'accepter — ce qu'il faut pour éprouver le rejeu après renouvellement.
 */
class FakeCatalogApi(
    private val failuresBeforeSuccess: Int = 0,
    private val failure: Throwable? = null,
    private val albums: List<RemoteAlbum> = emptyList(),
    private val artists: List<RemoteArtist> = emptyList(),
) : CatalogApi {

    var calls = 0
        private set
    var lastServerUrl: String? = null
        private set
    var lastAccessToken: String? = null
        private set
    var lastPage: Pair<Int, Int>? = null
        private set

    override suspend fun albums(
        serverUrl: String,
        accessToken: String,
        offset: Int,
        limit: Int,
    ): List<RemoteAlbum> {
        record(serverUrl, accessToken, offset to limit)
        return albums
    }

    override suspend fun artists(
        serverUrl: String,
        accessToken: String,
        offset: Int,
        limit: Int,
    ): List<RemoteArtist> {
        record(serverUrl, accessToken, offset to limit)
        return artists
    }

    override suspend fun album(
        serverUrl: String,
        accessToken: String,
        albumId: String,
    ): RemoteAlbumDetail {
        record(serverUrl, accessToken, null)
        return RemoteAlbumDetail(
            album = RemoteAlbum(albumId, "Album", null, null, null),
            songs = emptyList(),
        )
    }

    override suspend fun artist(
        serverUrl: String,
        accessToken: String,
        artistId: String,
    ): RemoteArtistDetail {
        record(serverUrl, accessToken, null)
        return RemoteArtistDetail(
            artist = RemoteArtist(artistId, "Artiste", null),
            albums = emptyList(),
        )
    }

    private fun record(serverUrl: String, accessToken: String, page: Pair<Int, Int>?) {
        calls++
        lastServerUrl = serverUrl
        lastAccessToken = accessToken
        page?.let { lastPage = it }

        failure?.let { throw it }
        if (calls <= failuresBeforeSuccess) {
            throw ServerException.Unauthorized("jeton refusé")
        }
    }
}

/**
 * Catalogue simulé qui pagine pour de vrai.
 *
 * Découpe [albums] et [artists] selon l'offset et la limite reçus : une fixture
 * qui rendrait toujours la même page ne prouverait rien de la pagination.
 */
class PagingCatalogApi(
    private val albums: List<RemoteAlbum> = emptyList(),
    private val artists: List<RemoteArtist> = emptyList(),
    /** Numéro d'appel à partir duquel les listes échouent, 0 pour jamais. */
    private var failFromCall: Int = 0,
    private val detailFailure: Throwable? = null,
    /**
     * Si non nul, les listes attendent ce signal avant de rendre la main.
     *
     * Sans lui, un dispatcher non confiné termine chaque page avant que la
     * suivante ne parte : aucune requête n'est jamais réellement en vol, et la
     * garde contre le doublement ne peut pas être mise à l'épreuve.
     */
    private val gate: CompletableDeferred<Unit>? = null,
) : CatalogApi {

    var albumCalls = 0
        private set
    var artistCalls = 0
        private set

    fun stopFailing() {
        failFromCall = 0
    }

    override suspend fun albums(
        serverUrl: String,
        accessToken: String,
        offset: Int,
        limit: Int,
    ): List<RemoteAlbum> {
        albumCalls++
        gate?.await()
        failIfDue(albumCalls)
        return albums.page(offset, limit)
    }

    override suspend fun artists(
        serverUrl: String,
        accessToken: String,
        offset: Int,
        limit: Int,
    ): List<RemoteArtist> {
        artistCalls++
        failIfDue(artistCalls)
        return artists.page(offset, limit)
    }

    override suspend fun album(
        serverUrl: String,
        accessToken: String,
        albumId: String,
    ): RemoteAlbumDetail {
        detailFailure?.let { throw it }
        return RemoteAlbumDetail(
            album = albums.firstOrNull { it.id == albumId }
                ?: RemoteAlbum(albumId, "Album", null, null, null),
            songs = emptyList(),
        )
    }

    override suspend fun artist(
        serverUrl: String,
        accessToken: String,
        artistId: String,
    ): RemoteArtistDetail {
        detailFailure?.let { throw it }
        return RemoteArtistDetail(
            artist = artists.firstOrNull { it.id == artistId }
                ?: RemoteArtist(artistId, "Artiste", null),
            albums = emptyList(),
        )
    }

    private fun failIfDue(call: Int) {
        if (failFromCall > 0 && call >= failFromCall) {
            throw ServerException.Unreachable("coupure")
        }
    }

    private fun <T> List<T>.page(offset: Int, limit: Int): List<T> =
        drop(offset).take(limit)
}

/** Persistance en mémoire, qui retient ce qu'on lui a demandé d'écrire. */
class FakeSessionStore(
    private var stored: ServerSession = ServerSession.Disconnected,
    /** Si non nul, toute écriture ou effacement échoue avec cette exception. */
    private val writeFailure: Throwable? = null,
) : SessionStore {

    var written: ServerSession? = null
        private set
    var cleared = false
        private set

    override suspend fun read(): ServerSession = stored

    override suspend fun write(session: ServerSession) {
        when (session) {
            is ServerSession.Disconnected -> clear()
            is ServerSession.Connected -> {
                writeFailure?.let { throw it }
                stored = session
                written = session
            }
        }
    }

    override suspend fun clear() {
        writeFailure?.let { throw it }
        stored = ServerSession.Disconnected
        cleared = true
    }
}
