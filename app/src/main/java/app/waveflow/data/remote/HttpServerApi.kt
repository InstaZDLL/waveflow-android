package app.waveflow.data.remote

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Client HTTP du serveur WaveFlow.
 *
 * OkHttp arrivait déjà par Coil ; le réutiliser évite un second pool de
 * connexions. Le [json] est tolérant aux champs inconnus : le serveur en
 * ajoutera, et une réponse enrichie ne doit pas casser une version installée.
 */
class HttpServerApi(
    private val client: OkHttpClient = defaultClient(),
) : ServerApi {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun login(
        serverUrl: String,
        username: String,
        password: String,
        deviceName: String,
    ): AuthTokens = post(
        serverUrl = serverUrl,
        path = AUTH_LOGIN,
        body = json.encodeToString(
            LoginRequest(username = username, password = password, deviceName = deviceName),
        ),
    ).toTokens()

    override suspend fun refresh(serverUrl: String, refreshToken: String): AuthTokens = post(
        serverUrl = serverUrl,
        path = AUTH_REFRESH,
        body = json.encodeToString(RefreshRequest(refreshToken = refreshToken)),
    ).toTokens()

    override suspend fun logout(serverUrl: String, accessToken: String) {
        post(serverUrl = serverUrl, path = AUTH_LOGOUT, body = "{}", accessToken = accessToken)
    }

    private suspend fun post(
        serverUrl: String,
        path: String,
        body: String,
        accessToken: String? = null,
    ): String {
        val url = serverUrl.toApiUrl(path)

        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .apply { accessToken?.let { header("Authorization", "Bearer $it") } }
            .build()

        // La lecture du corps est bloquante et lit sur le réseau : elle doit
        // rester sous le dispatcher IO, au même titre que l'appel lui-même.
        return withContext(Dispatchers.IO) {
            client.newCall(request).await().use {
                if (it.isSuccessful) it.body?.string().orEmpty() else throw it.toException()
            }
        }
    }

    /**
     * Construit l'URL d'un point d'API à partir de ce qu'a saisi l'utilisateur.
     *
     * L'adresse est reprise telle quelle, sauf le schéma : `192.168.1.10:4533`
     * seul n'est pas une URL pour OkHttp alors que c'est ce qu'on tape. Un
     * chemin déjà présent est conservé — le serveur peut vivre derrière un
     * proxy qui le préfixe.
     */
    private fun String.toApiUrl(path: String): HttpUrl {
        val trimmed = trim().trimEnd('/')
        if (trimmed.isEmpty()) throw ServerException.Rejected("Adresse du serveur vide.")

        val absolute = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val base = absolute.toHttpUrlOrNull()
            ?: throw ServerException.Rejected("Adresse du serveur invalide : $this")

        return base.newBuilder().addPathSegments(path).build()
    }

    private fun Response.toException(): ServerException {
        // Le serveur répond `{code, message}` sur ses erreurs métier, mais un
        // corps mal formé lui fait renvoyer du texte brut : lire le message
        // sans supposer du JSON.
        val raw = body?.string().orEmpty()
        val message = runCatching { json.decodeFromString<ErrorBody>(raw).message }
            .getOrNull()
            ?: raw.ifBlank { "Erreur $code" }

        return when (code) {
            401, 403 -> ServerException.Unauthorized(message)
            in 400..499 -> ServerException.Rejected(message)
            else -> ServerException.Unexpected(message)
        }
    }

    private fun String.toTokens(): AuthTokens = try {
        json.decodeFromString<AuthResponse>(this).let {
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
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val AUTH_LOGIN = "api/v2/auth/login"
        const val AUTH_REFRESH = "api/v2/auth/refresh"
        const val AUTH_LOGOUT = "api/v2/auth/logout"

        /**
         * Les délais par défaut d'OkHttp portent sur chaque étape prise à part ;
         * aucun ne borne l'appel entier. Un serveur qui répond au compte-gouttes
         * laisserait donc l'écran sur « Connexion… » indéfiniment.
         */
        val CALL_TIMEOUT = 30.seconds.toJavaDuration()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .callTimeout(CALL_TIMEOUT)
            .build()
    }
}

/**
 * Fait d'un appel OkHttp une suspension annulable.
 *
 * L'annulation de la coroutine annule l'appel : sans ça, un écran quitté
 * laisserait la requête vivre jusqu'à son délai d'expiration.
 */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            // Une annulation entre l'arrivée de la réponse et sa remise laisse
            // le corps ouvert, donc la connexion retenue : c'est à cette
            // variante de `resume` de le refermer.
            continuation.resume(response) { _, delivered, _ ->
                runCatching { delivered.close() }
            }
        }

        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeIfActive(
                ServerException.Unreachable(e.message ?: "Serveur injoignable.", e),
            )
        }
    })
    continuation.invokeOnCancellation { cancel() }
}

/**
 * Un appel annulé rapporte quand même son échec ; le reprendre alors ferait
 * lever `IllegalStateException` à la place de l'annulation attendue.
 */
private fun CancellableContinuation<Response>.resumeIfActive(error: Throwable) {
    if (isActive) resumeWithException(error)
}
