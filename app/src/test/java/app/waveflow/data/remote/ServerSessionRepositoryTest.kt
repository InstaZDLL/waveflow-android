package app.waveflow.data.remote

import app.waveflow.model.ServerSession
import app.waveflow.testing.FakeServerApi
import app.waveflow.testing.FakeSessionStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * La vie d'une session serveur.
 *
 * L'horloge est injectée : l'échéance d'un jeton d'accès est de quinze minutes,
 * et aucun test n'a vocation à les attendre.
 *
 * Robolectric parce que le dépôt journalise ses fermetures de session par
 * `android.util.Log`, qui lève sur une JVM nue.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ServerSessionRepositoryTest {

    private var maintenant = 1_000_000L

    private fun repository(
        api: FakeServerApi = FakeServerApi(),
        store: FakeSessionStore = FakeSessionStore(),
    ) = ServerSessionRepository(
        api = api,
        store = store,
        deviceName = "Pixel de test",
        now = { maintenant },
    )

    /**
     * Session déjà persistée avant le test.
     *
     * Ses jetons portent un suffixe distinct de ceux qu'émet [FakeServerApi] :
     * sans ça, un renouvellement rendrait la même valeur que le jeton stocké et
     * aucune assertion ne pourrait distinguer « renouvelé » de « inchangé ».
     */
    private fun connectedSession(
        accessToken: String = "wfa_stocke",
        expiresAtMs: Long = maintenant + 900_000L,
    ) = ServerSession.Connected(
        serverUrl = "https://musique.test",
        username = "admin",
        accessToken = accessToken,
        refreshToken = "wfr_stocke",
        deviceId = "appareil-1",
        accessExpiresAtMs = expiresAtMs,
    )

    @Test
    fun `la connexion expose la session et la persiste`() = runTest {
        val store = FakeSessionStore()
        val repository = repository(store = store)

        repository.connect("https://musique.test", "admin", "secret")

        val session = repository.session.value as ServerSession.Connected
        assertEquals("https://musique.test", session.serverUrl)
        assertEquals("admin", session.username)
        assertEquals("appareil-1", session.deviceId)
        // `expires_in` est relatif ; l'échéance est absolue.
        assertEquals(maintenant + 900_000L, session.accessExpiresAtMs)
        assertEquals(session, store.written)
    }

    @Test
    fun `la connexion transmet le nom d'appareil`() = runTest {
        val api = FakeServerApi()

        repository(api = api).connect("https://musique.test", "admin", "secret")

        assertEquals("Pixel de test", api.lastDeviceName)
    }

    @Test
    fun `une connexion refusee laisse la session precedente intacte`() = runTest {
        val store = FakeSessionStore(stored = connectedSession())
        val api = FakeServerApi(loginFailure = ServerException.Unauthorized("refusé"))
        val repository = repository(api = api, store = store)
        repository.restore()

        val error = runCatching {
            repository.connect("https://autre.test", "admin", "faux")
        }.exceptionOrNull()

        assertTrue(error is ServerException.Unauthorized)
        assertEquals(connectedSession(), repository.session.value)
    }

    @Test
    fun `la session persistee est relue au demarrage`() = runTest {
        val repository = repository(store = FakeSessionStore(stored = connectedSession()))

        repository.restore()

        assertEquals(connectedSession(), repository.session.value)
    }

    @Test
    fun `un jeton encore valide est rendu sans appeler le serveur`() = runTest {
        val api = FakeServerApi()
        val repository = repository(api = api, store = FakeSessionStore(stored = connectedSession()))
        repository.restore()

        assertEquals("wfa_stocke", repository.validAccessToken())
        assertEquals(0, api.refreshCalls)
    }

    @Test
    fun `un jeton proche de l'echeance est renouvele avant d'etre rendu`() = runTest {
        val api = FakeServerApi()
        val repository = repository(
            api = api,
            // Encore valide une demi-minute : moins que la marge de sécurité.
            store = FakeSessionStore(stored = connectedSession(expiresAtMs = maintenant + 30_000L)),
        )
        repository.restore()

        assertEquals("wfa_1", repository.validAccessToken())
        assertEquals(1, api.refreshCalls)
    }

    @Test
    fun `le renouvellement enregistre le nouveau jeton de rafraichissement`() = runTest {
        // Le serveur invalide l'ancien dès qu'il en émet un nouveau : conserver
        // le précédent condamnerait le renouvellement suivant.
        val store = FakeSessionStore(stored = connectedSession(expiresAtMs = maintenant))
        val repository = repository(store = store)
        repository.restore()

        repository.validAccessToken()

        val session = repository.session.value as ServerSession.Connected
        assertEquals("wfr_1", session.refreshToken)
        assertEquals(session, store.written)
    }

    @Test
    fun `le renouvellement suivant repart du jeton renouvele`() = runTest {
        // C'est tout l'enjeu de la rotation : réutiliser l'ancien vaudrait 401.
        val api = FakeServerApi()
        val repository = repository(
            api = api,
            store = FakeSessionStore(stored = connectedSession(expiresAtMs = maintenant)),
        )
        repository.restore()

        repository.validAccessToken()
        assertEquals("wfr_stocke", api.lastRefreshToken)

        maintenant += 900_000L
        repository.validAccessToken()
        assertEquals("wfr_1", api.lastRefreshToken)
    }

    @Test
    fun `deux demandes concurrentes ne declenchent qu'un renouvellement`() = runTest {
        // Le serveur invalide l'ancien jeton dès qu'il en émet un nouveau :
        // deux renouvellements partis du même jeton en perdraient un. Le portail
        // maintient le premier appel en vol le temps que le second se présente,
        // sans quoi ils ne se croiseraient jamais.
        val portail = CompletableDeferred<Unit>()
        val api = FakeServerApi(refreshGate = portail)
        val repository = repository(
            api = api,
            store = FakeSessionStore(stored = connectedSession(expiresAtMs = maintenant)),
        )
        repository.restore()

        val premier = async { repository.validAccessToken() }
        val second = async { repository.validAccessToken() }
        runCurrent()

        portail.complete(Unit)

        assertEquals("wfa_1", premier.await())
        // Le second trouve un jeton frais et n'a plus rien à renouveler.
        assertEquals("wfa_1", second.await())
        assertEquals(1, api.refreshCalls)
    }

    @Test
    fun `un renouvellement refuse ferme la session`() = runTest {
        val api = FakeServerApi(refreshFailure = ServerException.Unauthorized("périmé"))
        val store = FakeSessionStore(stored = connectedSession(expiresAtMs = maintenant))
        val repository = repository(api = api, store = store)
        repository.restore()

        assertNull(repository.validAccessToken())
        assertEquals(ServerSession.Disconnected, repository.session.value)
        assertTrue("la session doit aussi être effacée du disque", store.cleared)
    }

    @Test
    fun `un serveur injoignable au renouvellement conserve la session`() = runTest {
        // Contrairement à un refus : il n'y a rien à ressaisir, seulement à
        // réessayer plus tard.
        val api = FakeServerApi(refreshFailure = ServerException.Unreachable("coupure"))
        val repository = repository(
            api = api,
            store = FakeSessionStore(stored = connectedSession(expiresAtMs = maintenant)),
        )
        repository.restore()

        val error = runCatching { repository.validAccessToken() }.exceptionOrNull()

        assertTrue(error is ServerException.Unreachable)
        assertTrue(repository.session.value is ServerSession.Connected)
    }

    @Test
    fun `sans session il n'y a pas de jeton`() = runTest {
        assertNull(repository().validAccessToken())
    }

    @Test
    fun `la deconnexion revoque cote serveur puis oublie la session`() = runTest {
        val api = FakeServerApi()
        val store = FakeSessionStore(stored = connectedSession())
        val repository = repository(api = api, store = store)
        repository.restore()

        repository.disconnect()

        assertEquals("wfa_stocke", api.revokedAccessToken)
        assertEquals(ServerSession.Disconnected, repository.session.value)
        assertTrue(store.cleared)
    }

    @Test
    fun `une revocation impossible n'empeche pas la deconnexion`() = runTest {
        // Se déconnecter hors ligne doit marcher : l'utilisateur l'a demandé.
        val api = FakeServerApi(logoutFailure = ServerException.Unreachable("hors ligne"))
        val store = FakeSessionStore(stored = connectedSession())
        val repository = repository(api = api, store = store)
        repository.restore()

        repository.disconnect()

        assertEquals(ServerSession.Disconnected, repository.session.value)
        assertTrue(store.cleared)
    }
}
