package app.waveflow.data.remote

import app.waveflow.model.ServerSession
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Porte le jeton de session sur les requêtes d'images du serveur.
 *
 * `/api/v2/artwork/` exige le même Bearer que le reste de l'API native, et
 * Coil ne connaît rien de la session : cet intercepteur l'ajoute pour lui.
 *
 * Seules les requêtes vers l'hôte du serveur connecté sont signées. Une
 * pochette locale — un `content://` — ne passe pas par OkHttp, mais une
 * jaquette venue d'ailleurs pourrait ; lui joindre le jeton reviendrait à le
 * confier à un tiers.
 *
 * L'appel est bloquant : les intercepteurs OkHttp le sont, et s'exécutent sur
 * ses propres fils.
 */
class ServerImageAuthInterceptor(
    private val sessionRepository: ServerSessionRepository,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val session = sessionRepository.session.value as? ServerSession.Connected
            ?: return chain.proceed(request)

        if (!request.url.isSameHostAs(session.serverUrl)) return chain.proceed(request)

        val token = runCatching { runBlocking { sessionRepository.validAccessToken() } }
            .getOrNull()
            ?: return chain.proceed(request)

        val signed = chain.proceed(request.withBearer(token))
        if (signed.code != HTTP_UNAUTHORIZED) return signed

        // Jeton révoqué ailleurs : l'horloge locale le croyait bon. Comme pour
        // le catalogue, on le périme et on rejoue une fois — un second refus
        // veut dire que la session est fermée, et la pochette manquera.
        signed.close()
        val renewed = runCatching {
            runBlocking {
                sessionRepository.expireAccessToken()
                sessionRepository.validAccessToken()
            }
        }.getOrNull() ?: return chain.proceed(request)

        return chain.proceed(request.withBearer(renewed))
    }

    private fun okhttp3.Request.withBearer(token: String) =
        newBuilder().header("Authorization", "Bearer $token").build()

    /**
     * Compare l'hôte et le port à ceux du serveur connecté.
     *
     * L'adresse saisie par l'utilisateur passe par la même normalisation que
     * les appels d'API : sans schéma, elle est jointe en HTTPS.
     */
    private fun okhttp3.HttpUrl.isSameHostAs(serverUrl: String): Boolean {
        val server = runCatching { ServerHttp.parseBase(serverUrl) }.getOrNull() ?: return false
        return host == server.host && port == server.port
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
