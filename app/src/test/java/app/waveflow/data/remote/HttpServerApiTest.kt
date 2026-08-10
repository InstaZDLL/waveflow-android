package app.waveflow.data.remote

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Le client HTTP face à un serveur de test.
 *
 * Les corps de réponse et d'erreur sont ceux relevés sur `waveflow-server`
 * 2.0.0-beta.0 — une réponse inventée ne prouverait pas grand-chose.
 */
class HttpServerApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ServerApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = HttpServerApi()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun url(): String = server.url("/").toString().trimEnd('/')

    /** L'exception levée par [bloc], ou un échec de test s'il n'en lève aucune. */
    private suspend fun echecDe(bloc: suspend () -> Unit): Throwable =
        runCatching { bloc() }.exceptionOrNull()
            ?: throw AssertionError("aucune exception levée")

    @Test
    fun `la connexion envoie les identifiants et lit les jetons`() = runTest {
        server.enqueue(MockResponse().setBody(AUTH_BODY))

        val tokens = api.login(url(), "admin", "secret", "Pixel de test")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v2/auth/login", request.path)
        assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))

        val body = request.body.readUtf8()
        assertTrue(body, body.contains("\"username\":\"admin\""))
        // Le serveur attend `device_name`, pas le nom Kotlin.
        assertTrue(body, body.contains("\"device_name\":\"Pixel de test\""))

        assertEquals("wfa_acces", tokens.accessToken)
        assertEquals("wfr_rafraichir", tokens.refreshToken)
        assertEquals("admin", tokens.username)
        assertEquals("da5adeb0-2eb7-4904-94fd-97c1e5534be2", tokens.deviceId)
        assertEquals(900L, tokens.expiresInSeconds)
    }

    @Test
    fun `un 401 devient un refus d'identifiants et reprend le message`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"code":"unauthorized","message":"Authentication failed"}"""),
        )

        val error = echecDe { api.login(url(), "admin", "faux", "Pixel") }

        assertTrue(error.toString(), error is ServerException.Unauthorized)
        assertEquals("Authentication failed", error.message)
    }

    @Test
    fun `un 422 devient un rejet, pas un refus d'identifiants`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(422)
                .setBody("""{"code":"validation_error","message":"The request is invalid"}"""),
        )

        val error = echecDe { api.login(url(), "admin", "secret", "") }

        assertTrue(error.toString(), error is ServerException.Rejected)
    }

    @Test
    fun `une erreur en texte brut ne fait pas echouer la lecture du message`() = runTest {
        // Relevé sur le vrai serveur : un corps mal formé lui fait renvoyer du
        // texte, pas le `{code, message}` habituel.
        server.enqueue(
            MockResponse()
                .setResponseCode(422)
                .setBody("Failed to deserialize the JSON body: missing field `password`"),
        )

        val error = echecDe { api.login(url(), "admin", "secret", "Pixel") }

        assertTrue(error.toString(), error is ServerException.Rejected)
        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("missing field"))
    }

    @Test
    fun `une panne serveur est signalee comme inattendue, pas comme un rejet`() = runTest {
        // Rien à ressaisir : c'est le serveur qui va mal, l'appel est à refaire.
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"code":"unavailable","message":"Database unavailable"}"""),
        )

        val error = echecDe { api.login(url(), "admin", "secret", "Pixel") }

        assertTrue(error.toString(), error is ServerException.Unexpected)
        assertEquals("Database unavailable", error.message)
    }

    @Test
    fun `un serveur injoignable ne remonte pas comme un refus`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val error = echecDe { api.login(url(), "admin", "secret", "Pixel") }

        assertTrue(error.toString(), error is ServerException.Unreachable)
    }

    @Test
    fun `une reponse illisible est signalee comme inattendue`() = runTest {
        server.enqueue(MockResponse().setBody("""{"pas":"ce qu'on attend"}"""))

        val error = echecDe { api.login(url(), "admin", "secret", "Pixel") }

        assertTrue(error.toString(), error is ServerException.Unexpected)
    }

    @Test
    fun `les champs inconnus d'une reponse ne cassent rien`() = runTest {
        // Le serveur en ajoutera : une version installée ne doit pas tomber
        // parce qu'une réponse s'est enrichie.
        server.enqueue(MockResponse().setBody(AUTH_BODY_WITH_EXTRAS))

        val tokens = api.login(url(), "admin", "secret", "Pixel")

        assertEquals("wfa_acces", tokens.accessToken)
    }

    @Test
    fun `le rafraichissement envoie le jeton et lit le nouveau`() = runTest {
        server.enqueue(MockResponse().setBody(AUTH_BODY))

        val tokens = api.refresh(url(), "wfr_ancien")

        val request = server.takeRequest()
        assertEquals("/api/v2/auth/refresh", request.path)
        assertTrue(request.body.readUtf8().contains("\"refresh_token\":\"wfr_ancien\""))
        assertEquals("wfr_rafraichir", tokens.refreshToken)
    }

    @Test
    fun `la deconnexion presente le jeton d'acces`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        api.logout(url(), "wfa_acces")

        val request = server.takeRequest()
        assertEquals("/api/v2/auth/logout", request.path)
        assertEquals("Bearer wfa_acces", request.getHeader("Authorization"))
    }

    @Test
    fun `une adresse sans schema est jointe en HTTPS`() = runTest {
        // Le serveur de test parle en clair : joint en HTTPS, il ne répond pas.
        // C'est précisément ce qui prouve le schéma retenu — une adresse restée
        // relative n'aurait même pas été tentée.
        val hostPort = server.hostName + ":" + server.port

        val error = echecDe { api.login(hostPort, "admin", "secret", "Pixel") }

        assertTrue(error.toString(), error is ServerException.Unreachable)
    }

    @Test
    fun `une adresse vide est refusee avant tout appel`() = runTest {
        val error = echecDe { api.login("   ", "admin", "secret", "Pixel") }

        assertTrue(error.toString(), error is ServerException.Rejected)
    }

    @Test
    fun `un chemin deja present dans l'adresse est conserve`() = runTest {
        server.enqueue(MockResponse().setBody(AUTH_BODY))

        api.login("${url()}/musique", "admin", "secret", "Pixel")

        assertEquals("/musique/api/v2/auth/login", server.takeRequest().path)
    }

    private companion object {
        val AUTH_BODY = """
            {
              "access_token": "wfa_acces",
              "refresh_token": "wfr_rafraichir",
              "token_type": "Bearer",
              "expires_in": 900,
              "user": {
                "id": "10cafbdc-282f-4783-98c1-7a4139c269d6",
                "username": "admin",
                "role": "admin"
              },
              "device_id": "da5adeb0-2eb7-4904-94fd-97c1e5534be2"
            }
        """.trimIndent()

        val AUTH_BODY_WITH_EXTRAS = AUTH_BODY.replace(
            "\"token_type\": \"Bearer\",",
            "\"token_type\": \"Bearer\", \"capacites\": [\"sync\"], \"serveur\": {\"version\": \"3\"},",
        )
    }
}
