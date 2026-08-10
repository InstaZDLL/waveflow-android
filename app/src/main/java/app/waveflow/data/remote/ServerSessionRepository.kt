package app.waveflow.data.remote

import android.util.Log
import app.waveflow.model.ServerSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * La session serveur, et les seuls chemins qui la font changer.
 *
 * Porté au niveau application : les jetons ne survivraient pas à la recréation
 * d'un ViewModel, et plusieurs appelants devront s'en servir dès que le
 * catalogue distant arrivera.
 *
 * @param now horloge injectée — l'échéance du jeton se calcule ici, et un test
 *   doit pouvoir la franchir sans attendre.
 */
class ServerSessionRepository(
    private val api: ServerApi,
    private val store: SessionStore,
    private val deviceName: String,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val _session = MutableStateFlow<ServerSession>(ServerSession.Disconnected)
    val session: StateFlow<ServerSession> = _session.asStateFlow()

    /**
     * Sérialise tout ce qui touche aux jetons.
     *
     * Le serveur invalide le jeton de rafraîchissement dès qu'il en émet un
     * nouveau. Deux renouvellements concurrents partiraient du même jeton :
     * le second serait refusé, et la session tomberait alors qu'elle était
     * valide. Un seul à la fois, donc.
     */
    private val mutex = Mutex()

    /** Relit la session persistée. À appeler au démarrage. */
    suspend fun restore() = mutex.withLock {
        _session.value = store.read()
    }

    /**
     * Ouvre une session.
     *
     * @throws ServerException si le serveur refuse ou reste injoignable ; la
     *   session en cours, s'il y en avait une, n'est pas touchée.
     */
    suspend fun connect(serverUrl: String, username: String, password: String) = mutex.withLock {
        val tokens = api.login(serverUrl, username, password, deviceName)
        persist(tokens.toSession(serverUrl))
    }

    /**
     * Ferme la session.
     *
     * La révocation côté serveur est tentée mais non exigée : l'utilisateur a
     * demandé à se déconnecter, un serveur injoignable ne doit pas l'en
     * empêcher. Le jeton reste alors valide jusqu'à son échéance, ce qui est le
     * prix d'une déconnexion hors ligne.
     */
    suspend fun disconnect() = mutex.withLock {
        val current = _session.value
        if (current is ServerSession.Connected) {
            try {
                api.logout(current.serverUrl, current.accessToken)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: ServerException) {
                Log.i(TAG, "Révocation impossible, session oubliée localement", error)
            }
        }
        persist(ServerSession.Disconnected)
    }

    /**
     * Un jeton d'accès utilisable, renouvelé si son échéance approche.
     *
     * Renvoie `null` quand il n'y a pas de session, ou quand le renouvellement
     * a été refusé — auquel cas la session est effacée et l'utilisateur devra
     * ressaisir son mot de passe.
     *
     * @throws ServerException.Unreachable si le serveur ne répond pas ; la
     *   session est conservée, l'appelant réessaiera.
     */
    suspend fun validAccessToken(): String? = mutex.withLock {
        val current = _session.value as? ServerSession.Connected ?: return@withLock null
        if (now() < current.accessExpiresAtMs - EXPIRY_MARGIN_MS) return@withLock current.accessToken

        try {
            val tokens = api.refresh(current.serverUrl, current.refreshToken)
            persist(tokens.toSession(current.serverUrl))
            tokens.accessToken
        } catch (refused: ServerException.Unauthorized) {
            // Le jeton de rafraîchissement est mort : révoqué ailleurs, ou
            // périmé. Rien à réessayer, il faut une nouvelle connexion.
            Log.i(TAG, "Rafraîchissement refusé, session fermée", refused)
            persist(ServerSession.Disconnected)
            null
        }
    }

    /** À n'appeler que sous [mutex]. */
    private suspend fun persist(session: ServerSession) {
        // Le disque d'abord : l'état en mémoire ne doit jamais annoncer une
        // session que le prochain démarrage ne retrouverait pas.
        store.write(session)
        _session.value = session
    }

    private fun AuthTokens.toSession(serverUrl: String) = ServerSession.Connected(
        serverUrl = serverUrl,
        username = username,
        accessToken = accessToken,
        refreshToken = refreshToken,
        deviceId = deviceId,
        accessExpiresAtMs = now() + expiresInSeconds * 1_000,
    )

    private companion object {
        const val TAG = "ServerSession"

        /**
         * De quoi couvrir l'aller-retour d'une requête lancée juste avant
         * l'échéance — sans cette marge, un jeton valide à l'envoi peut être
         * périmé à l'arrivée.
         */
        const val EXPIRY_MARGIN_MS = 60_000L
    }
}
