package app.waveflow.testing

import app.waveflow.data.remote.AuthTokens
import app.waveflow.data.remote.ServerApi
import app.waveflow.data.remote.SessionStore
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
