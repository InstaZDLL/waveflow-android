package app.waveflow.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Porte le jeton de session sur les requêtes d'images du serveur.
 *
 * `/api/v2/artwork/` exige le même Bearer que le reste de l'API native, et
 * Coil ne connaît rien de la session : cet intercepteur l'ajoute pour lui.
 *
 * Seules les requêtes vers l'origine du serveur connecté sont signées. Une
 * pochette locale — un `content://` — ne passe pas par OkHttp, mais une
 * jaquette venue d'ailleurs pourrait ; lui joindre le jeton reviendrait à le
 * confier à un tiers.
 *
 * L'origine est comparée à celle que [ServerSessionRepository.authorize] rend
 * **avec** le jeton, et non à une session lue auparavant : entre les deux
 * lectures, l'utilisateur peut s'être reconnecté ailleurs, et le jeton du
 * nouveau serveur partirait à l'ancien.
 *
 * Conséquence assumée : une image venue d'ailleurs passe elle aussi par le
 * verrou de session, et peut déclencher un renouvellement qui n'attendait plus
 * qu'un appel. Le prix est modeste — le renouvellement était dû — et la
 * garantie ne tient qu'à ce prix : filtrer avant de demander le jeton, c'est
 * filtrer sur une session qui n'est peut-être plus celle du jeton obtenu.
 *
 * L'appel est bloquant : les intercepteurs OkHttp le sont, et s'exécutent sur
 * ses propres fils.
 */
class ServerImageAuthInterceptor(
    private val sessionRepository: ServerSessionRepository,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenFor(request) ?: return chain.proceed(request)

        val signed = chain.proceed(request.withBearer(token))
        if (signed.code != HTTP_UNAUTHORIZED) return signed

        // Jeton révoqué ailleurs : l'horloge locale le croyait bon. Comme pour
        // le catalogue, on le périme et on rejoue une fois — un second refus
        // veut dire que la session est fermée, et la pochette manquera.
        signed.close()
        val renewed = renewedTokenFor(request, refused = token) ?: return chain.proceed(request)

        return chain.proceed(request.withBearer(renewed))
    }

    /** Périme le jeton refusé, puis en redemande un — la session en émettra un neuf. */
    private fun renewedTokenFor(request: okhttp3.Request, refused: String): String? {
        // Si la péremption échoue, redemander rendrait le même jeton que le
        // serveur vient de refuser : autant renoncer que rejouer pour rien.
        obtain { sessionRepository.expireAccessToken(refused) } ?: return null
        return tokenFor(request)
    }

    /**
     * Le jeton à joindre à [request], ou `null` s'il n'y a rien à signer.
     *
     * La session est prise d'un bloc — adresse et jeton — puis confrontée à
     * l'URL demandée : le jeton n'est joint que s'il appartient bien au serveur
     * auquel la requête s'adresse.
     */
    private fun tokenFor(request: okhttp3.Request): String? {
        val authorized = obtain { sessionRepository.authorize() } ?: return null
        return authorized.accessToken.takeIf { request.url.isSameOriginAs(authorized.serverUrl) }
    }

    /**
     * Exécute [call] en bloquant, sans laisser remonter d'échec.
     *
     * Un serveur injoignable ou un refus ne doivent pas faire échouer le
     * chargement de l'image : la requête partira sans signature, et la pochette
     * manquera au pire.
     */
    private fun <T> obtain(call: suspend () -> T): T? =
        runCatching { runBlocking { call() } }.getOrNull()

    private fun okhttp3.Request.withBearer(token: String) =
        newBuilder().header("Authorization", "Bearer $token").build()

    /**
     * Compare l'origine — schéma, hôte et port — à celle du serveur connecté.
     *
     * Le schéma compte autant que le reste : un serveur joint en HTTPS et une
     * adresse en `http://` vers le même hôte et le même port enverraient le
     * jeton en clair. Les ports par défaut suffisent à les distinguer quand
     * ils sont implicites, pas quand le port est explicite — ce qui est le cas
     * courant d'un serveur auto-hébergé.
     *
     * L'adresse saisie par l'utilisateur passe par la même normalisation que
     * les appels d'API : sans schéma, elle est jointe en HTTPS.
     */
    private fun okhttp3.HttpUrl.isSameOriginAs(serverUrl: String): Boolean {
        val server = runCatching { ServerHttp.parseBase(serverUrl) }.getOrNull() ?: return false
        return scheme == server.scheme && host == server.host && port == server.port
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
