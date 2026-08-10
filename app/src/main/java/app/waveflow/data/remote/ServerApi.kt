package app.waveflow.data.remote

/**
 * Les appels d'authentification du serveur WaveFlow.
 *
 * Interface plutôt qu'implémentation directe : les tests de session doivent
 * pouvoir simuler un refus ou une panne réseau sans serveur en face.
 */
interface ServerApi {

    /** `POST /api/v2/auth/login`. */
    suspend fun login(
        serverUrl: String,
        username: String,
        password: String,
        deviceName: String,
    ): AuthTokens

    /**
     * `POST /api/v2/auth/refresh`.
     *
     * Le serveur invalide [refreshToken] dès qu'il répond : la réponse perdue
     * est une session perdue, d'où la sérialisation côté appelant.
     */
    suspend fun refresh(serverUrl: String, refreshToken: String): AuthTokens

    /**
     * `POST /api/v2/auth/logout`.
     *
     * Révoque la session côté serveur. L'échec n'est pas fatal : l'appelant
     * oublie ses jetons de toute façon.
     */
    suspend fun logout(serverUrl: String, accessToken: String)
}

/** Jetons renvoyés par une connexion ou un renouvellement. */
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val username: String,
    val deviceId: String,
    /** Durée de vie de [accessToken], en secondes, à compter de la réception. */
    val expiresInSeconds: Long,
)

/**
 * Un appel qui n'a pas abouti.
 *
 * Distingue ce sur quoi l'utilisateur peut agir de ce qui relève de la panne :
 * l'écran ne propose de ressaisir un mot de passe que pour [Unauthorized].
 */
sealed class ServerException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** Identifiants refusés, ou jeton de rafraîchissement périmé. */
    class Unauthorized(message: String) : ServerException(message)

    /** Requête rejetée : adresse mal formée, nom d'appareil vide. */
    class Rejected(message: String) : ServerException(message)

    /** Serveur injoignable, TLS invalide, coupure en cours de route. */
    class Unreachable(message: String, cause: Throwable? = null) :
        ServerException(message, cause)

    /** Panne côté serveur, ou réponse que le client ne sait pas lire. */
    class Unexpected(message: String, cause: Throwable? = null) :
        ServerException(message, cause)
}
