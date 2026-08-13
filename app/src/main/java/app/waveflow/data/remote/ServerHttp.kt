package app.waveflow.data.remote

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
 * Le tuyau commun à tous les appels du serveur WaveFlow.
 *
 * Extrait de [HttpServerApi] quand le catalogue est arrivé : la construction
 * d'URL et surtout le classement des erreurs doivent rester en un seul endroit,
 * sinon deux clients répondent différemment à la même panne.
 */
class ServerHttp(
    private val client: OkHttpClient = defaultClient(),
) {

    /**
     * Tolérant aux champs inconnus : le serveur en ajoutera, et une réponse
     * enrichie ne doit pas casser une version déjà installée.
     */
    val json = Json { ignoreUnknownKeys = true }

    suspend fun post(
        serverUrl: String,
        path: String,
        body: String,
        accessToken: String? = null,
    ): String = execute(serverUrl, path, accessToken) {
        post(body.toRequestBody(JSON_MEDIA_TYPE))
    }

    // `execute` prend son `method` en dernier pour la syntaxe de lambda finale ;
    // les paramètres facultatifs qui le précèdent gardent leurs valeurs par
    // défaut pour les appels qui n'en ont pas besoin.

    /**
     * @param pathSegment ajouté tel quel après [path], et encodé. Un
     *   identifiant n'a pas à être interpolé dans le chemin : il viendrait
     *   d'une réponse serveur ou d'un argument de navigation, et un `/` qui s'y
     *   glisserait désignerait un autre point d'API.
     */
    suspend fun get(
        serverUrl: String,
        path: String,
        pathSegment: String? = null,
        query: Map<String, String> = emptyMap(),
        accessToken: String? = null,
    ): String = execute(serverUrl, path, accessToken, pathSegment, query) { get() }

    private suspend fun execute(
        serverUrl: String,
        path: String,
        accessToken: String?,
        pathSegment: String? = null,
        query: Map<String, String> = emptyMap(),
        method: Request.Builder.() -> Request.Builder,
    ): String {
        val url = serverUrl.toApiUrl(path, pathSegment, query)

        val request = Request.Builder()
            .url(url)
            .method()
            .apply { accessToken?.let { header("Authorization", "Bearer $it") } }
            .build()

        // La lecture du corps est bloquante et lit sur le réseau : elle doit
        // rester sous le dispatcher IO, au même titre que l'appel lui-même.
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).await().use {
                    if (it.isSuccessful) it.body?.string().orEmpty() else throw it.toException()
                }
            } catch (broken: IOException) {
                // Une coupure pendant la lecture du corps lève ici, et non dans
                // le rappel d'échec de l'appel : sans cette conversion, une
                // IOException nue traverserait toute la pile jusqu'à un
                // `viewModelScope` qui ne la rattrape pas.
                //
                // OkHttp signale aussi l'annulation par une IOException. La
                // reconvertir en « serveur injoignable » masquerait l'abandon
                // de l'écran, d'où la vérification préalable.
                currentCoroutineContext().ensureActive()
                throw ServerException.Unreachable(
                    broken.message ?: "Connexion interrompue.",
                    broken,
                )
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
    private fun String.toApiUrl(
        path: String,
        pathSegment: String?,
        query: Map<String, String>,
    ): HttpUrl {
        val base = parseBase(this)

        return base.newBuilder()
            .addPathSegments(path)
            .apply { pathSegment?.let { addPathSegment(it) } }
            .apply { query.forEach { (name, value) -> addQueryParameter(name, value) } }
            .build()
    }

    /**
     * Résout un chemin rendu par le serveur contre l'adresse de celui-ci.
     *
     * Le ticket de diffusion arrive sous la forme `/api/v2/stream/<ticket>`.
     * Le passer à `resolve` écraserait le chemin de base : un serveur derrière
     * un proxy qui le préfixe verrait son préfixe disparaître. Il est donc
     * traité comme n'importe quel chemin d'API, par la même construction que
     * les appels — qui, elle, conserve le préfixe.
     *
     * Seul un chemin absolu du serveur est accepté. Une URL complète ou une
     * référence réseau (`//hôte/…`) désignerait un autre hôte que celui où
     * l'utilisateur s'est authentifié.
     *
     * @param pathSegment ajouté après [path], et encodé — un identifiant ou un
     *   hachage venu d'une réponse n'a pas à être interpolé dans le chemin.
     */
    fun absoluteUrl(serverUrl: String, path: String, pathSegment: String? = null): String {
        if (!path.startsWith("/") || path.startsWith("//")) {
            throw ServerException.Unexpected("Chemin de diffusion inattendu : $path")
        }

        return serverUrl
            .toApiUrl(path = path.removePrefix("/"), pathSegment = pathSegment, query = emptyMap())
            .toString()
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

    companion object {
        /**
         * Normalise l'adresse saisie par l'utilisateur.
         *
         * Le schéma est le seul ajout : `192.168.1.10:4533` seul n'est pas une
         * URL pour OkHttp alors que c'est ce qu'on tape. Exposée pour que la
         * signature des requêtes d'images compare le même hôte que les appels.
         */
        fun parseBase(serverUrl: String): HttpUrl {
            val trimmed = serverUrl.trim().trimEnd('/')
            if (trimmed.isEmpty()) throw ServerException.Rejected("Adresse du serveur vide.")

            val absolute = if (trimmed.contains("://")) trimmed else "https://$trimmed"
            return absolute.toHttpUrlOrNull()
                ?: throw ServerException.Rejected("Adresse du serveur invalide : $serverUrl")
        }

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /**
         * Les délais par défaut d'OkHttp portent sur chaque étape prise à part ;
         * aucun ne borne l'appel entier. Un serveur qui répond au compte-gouttes
         * laisserait donc l'écran sur son indicateur indéfiniment.
         */
        private val CALL_TIMEOUT = 30.seconds.toJavaDuration()

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
