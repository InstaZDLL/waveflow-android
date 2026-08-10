package app.waveflow.data.remote

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString

/** Authentification du serveur WaveFlow, par-dessus [ServerHttp]. */
class HttpServerApi(
    private val http: ServerHttp = ServerHttp(),
) : ServerApi {

    override suspend fun login(
        serverUrl: String,
        username: String,
        password: String,
        deviceName: String,
    ): AuthTokens = http.post(
        serverUrl = serverUrl,
        path = AUTH_LOGIN,
        body = http.json.encodeToString(
            LoginRequest(username = username, password = password, deviceName = deviceName),
        ),
    ).toTokens()

    override suspend fun refresh(serverUrl: String, refreshToken: String): AuthTokens = http.post(
        serverUrl = serverUrl,
        path = AUTH_REFRESH,
        body = http.json.encodeToString(RefreshRequest(refreshToken = refreshToken)),
    ).toTokens()

    override suspend fun logout(serverUrl: String, accessToken: String) {
        http.post(serverUrl = serverUrl, path = AUTH_LOGOUT, body = "{}", accessToken = accessToken)
    }

    private fun String.toTokens(): AuthTokens = try {
        http.json.decodeFromString<AuthResponse>(this).let {
            AuthTokens(
                accessToken = it.accessToken,
                refreshToken = it.refreshToken,
                username = it.user.username,
                deviceId = it.deviceId,
                expiresInSeconds = it.expiresIn,
            )
        }
    } catch (error: SerializationException) {
        throw ServerException.Unexpected("Réponse illisible du serveur : ${error.message}", error)
    }

    private companion object {
        const val AUTH_LOGIN = "api/v2/auth/login"
        const val AUTH_REFRESH = "api/v2/auth/refresh"
        const val AUTH_LOGOUT = "api/v2/auth/logout"
    }
}
