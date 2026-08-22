package app.waveflow.data.remote

import app.waveflow.model.ServerSession
import app.waveflow.testing.FakeServerApi
import app.waveflow.testing.FakeSessionStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * La signature des requêtes d'images.
 *
 * Coil ne connaît rien de la session : tout se joue dans cet intercepteur, et
 * une erreur y enverrait un jeton d'accès à un hôte tiers.
 */
@RunWith(RobolectricTestRunner::class)
class ServerImageAuthInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun url(): String = server.url("/").toString().trimEnd('/')

    private suspend fun sessions(
        serverUrl: String = url(),
        api: FakeServerApi = FakeServerApi(),
        connected: Boolean = true,
    ): ServerSessionRepository {
        val stored = if (connected) {
            ServerSession.Connected(
                serverUrl = serverUrl,
                username = "admin",
                accessToken = "wfa_stocke",
                refreshToken = "wfr_stocke",
                deviceId = "appareil-1",
                accessExpiresAtMs = Long.MAX_VALUE,
            )
        } else {
            ServerSession.Disconnected
        }

        return ServerSessionRepository(
            api = api,
            store = FakeSessionStore(stored = stored),
            deviceName = "Pixel de test",
            now = { 0L },
        ).also { it.restore() }
    }

    private fun clientWith(sessions: ServerSessionRepository) = OkHttpClient.Builder()
        .addInterceptor(ServerImageAuthInterceptor(sessions))
        .build()

    private fun fetch(client: OkHttpClient, target: String) {
        client.newCall(Request.Builder().url(target).build()).execute().close()
    }

    @Test
    fun `une pochette du serveur connecte porte le jeton`() = runTest {
        server.enqueue(MockResponse().setBody("image"))

        fetch(clientWith(sessions()), "${url()}/api/v2/artwork/1daf991a")

        assertEquals("Bearer wfa_stocke", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `sans session rien n'est signe`() = runTest {
        server.enqueue(MockResponse().setBody("image"))

        fetch(clientWith(sessions(connected = false)), "${url()}/api/v2/artwork/1daf991a")

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `un hote tiers ne recoit pas le jeton`() = runTest {
        // Une jaquette servie ailleurs — une pochette locale distante, un cache
        // d'images — ne doit pas se voir confier le jeton du serveur.
        val autre = MockWebServer()
        autre.start()
        autre.enqueue(MockResponse().setBody("image"))

        try {
            val sessions = sessions(serverUrl = url())
            fetch(clientWith(sessions), autre.url("/pochette.jpg").toString())

            assertNull(autre.takeRequest().getHeader("Authorization"))
        } finally {
            autre.shutdown()
        }
    }

    @Test
    fun `le meme hote en clair ne recoit pas le jeton d'un serveur en HTTPS`() = runTest {
        // Le port ne suffit pas à distinguer les deux quand il est explicite,
        // ce qui est le cas courant d'un serveur auto-hébergé : sans comparer
        // le schéma, le jeton partirait en clair vers le même hôte.
        server.enqueue(MockResponse().setBody("image"))
        val enHttps = "https://${server.hostName}:${server.port}"

        fetch(clientWith(sessions(serverUrl = enHttps)), "${url()}/api/v2/artwork/abc123")

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `un jeton refuse est renouvele et la requete rejouee`() = runTest {
        // Révocation depuis un autre appareil : l'horloge locale croit le jeton
        // encore valide, seul le serveur sait que non.
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setBody("image"))

        val api = FakeServerApi()
        fetch(clientWith(sessions(api = api)), "${url()}/api/v2/artwork/1daf991a")

        assertEquals("Bearer wfa_stocke", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer wfa_1", server.takeRequest().getHeader("Authorization"))
        assertEquals(1, api.refreshCalls)
    }

    @Test
    fun `un jeton obtenu apres un changement de serveur ne part pas a l'ancien`() = runTest {
        // La fenêtre : l'intercepteur attend la réponse de l'ancien serveur —
        // il ne tient alors aucun verrou — et l'utilisateur se reconnecte
        // ailleurs pendant ce temps. Le jeton qu'il obtiendra ensuite est celui
        // du nouveau serveur ; le rejeu l'enverrait à l'ancien, qui n'a rien à
        // en connaître.
        //
        // La bascule est déclenchée depuis la réponse elle-même, ce qui la
        // place exactement dans la fenêtre plutôt que d'espérer l'y croiser.
        val autre = MockWebServer()
        autre.start()

        try {
            val sessions = sessions()
            var bascule = false
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (!bascule) {
                        bascule = true
                        val ailleurs = autre.url("/").toString().trimEnd('/')
                        runBlocking { sessions.connect(ailleurs, "autre", "secret") }
                    }
                    return MockResponse().setResponseCode(401)
                }
            }

            fetch(clientWith(sessions), "${url()}/api/v2/artwork/1daf991a")

            assertEquals("Bearer wfa_stocke", server.takeRequest().getHeader("Authorization"))
            assertNull(
                "le jeton du nouveau serveur ne doit pas partir à l'ancien",
                server.takeRequest().getHeader("Authorization"),
            )
            assertEquals("le nouveau serveur n'a rien demandé", 0, autre.requestCount)
        } finally {
            autre.shutdown()
        }
    }

    @Test
    fun `un second refus n'est pas rejoue indefiniment`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))

        val sessions = sessions()
        val client = clientWith(sessions)

        val code = client.newCall(
            Request.Builder().url("${url()}/api/v2/artwork/1daf991a").build(),
        ).execute().use { it.code }

        assertEquals(401, code)
        assertEquals("deux tentatives, pas plus", 2, server.requestCount)
    }

    @Test
    fun `la session reste utilisable apres une pochette signee`() = runTest {
        // L'intercepteur bloque sur le mutex de session : s'il le gardait, tout
        // appel d'API suivant se figerait.
        server.enqueue(MockResponse().setBody("image"))
        val sessions = sessions()

        fetch(clientWith(sessions), "${url()}/api/v2/artwork/1daf991a")

        assertEquals("wfa_stocke", runBlocking { sessions.authorize()?.accessToken })
    }
}
