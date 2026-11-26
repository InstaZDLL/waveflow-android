package app.waveflow.data.remote

import app.waveflow.model.Lyrics
import app.waveflow.model.SyncedLine
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Les paroles d'une piste du serveur (`docs/paroles.md`, P4), face à un serveur
 * de test qui répond comme `waveflow-server` : la forme `LyricsList` en
 * camelCase, les feuilles déjà rangées, `start` en millisecondes et omis sur
 * des paroles simples.
 */
@RunWith(RobolectricTestRunner::class)
class HttpLyricsTest {

    private lateinit var server: MockWebServer
    private val api: CatalogApi = HttpCatalogApi()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun url(): String = server.url("/").toString().trimEnd('/')

    @Test
    fun `la premiere feuille est celle qu'on montre`() = runTest {
        server.enqueue(MockResponse().setBody(DEUX_FEUILLES))

        val paroles = api.lyrics(url(), "wfa_1", "uuid-7")

        val requete = server.takeRequest()
        assertEquals("GET", requete.method)
        assertEquals("/api/v2/tracks/uuid-7/lyrics", requete.path)
        assertEquals("Bearer wfa_1", requete.getHeader("Authorization"))
        assertEquals(Lyrics.Synced(listOf(SyncedLine(1_000, "Une"), SyncedLine(4_500, "Deux"))), paroles)
    }

    @Test
    fun `une feuille simple se lit sans instants`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"trackId":"t","structuredLyrics":[{"displayArtist":null,"displayTitle":"T","lang":"xxx",
                 "synced":false,"line":[{"value":""},{"value":"Couplet"},{"value":""},{"value":"Refrain"}],
                 "source":"embedded"}]}
                """.trimIndent(),
            ),
        )

        assertEquals(Lyrics.Plain(listOf("Couplet", "", "Refrain")), api.lyrics(url(), "wfa_1", "t"))
    }

    @Test
    fun `une feuille synchronisee a qui il manque un instant se montre en paroles simples`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"trackId":"t","structuredLyrics":[{"displayTitle":"T","lang":"fra","synced":true,
                 "line":[{"start":1000,"value":"Une"},{"value":"Sans instant"}]}]}
                """.trimIndent(),
            ),
        )

        assertEquals(Lyrics.Plain(listOf("Une", "Sans instant")), api.lyrics(url(), "wfa_1", "t"))
    }

    @Test
    fun `aucune feuille, pas de paroles`() = runTest {
        server.enqueue(MockResponse().setBody("""{"trackId":"t","structuredLyrics":[]}"""))

        assertNull(api.lyrics(url(), "wfa_1", "t"))
    }

    @Test
    fun `une piste inconnue du serveur n'a pas de paroles`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(404).setBody("""{"code":"not_found","message":"Track not found"}"""),
        )

        assertNull(api.lyrics(url(), "wfa_1", "t"))
    }

    @Test
    fun `un serveur qui demande d'attendre ne dit pas qu'il n'y a pas de paroles`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"code":"rate_limited","message":"Slow down"}"""))

        val echec = runCatching { api.lyrics(url(), "wfa_1", "t") }.exceptionOrNull()

        assertTrue("attendu : Rejected 429, reçu : $echec", (echec as? ServerException.Rejected)?.httpCode == 429)
    }

    @Test
    fun `un identifiant qui sortirait de la route n'est pas demande`() = runTest {
        assertNull(api.lyrics(url(), "wfa_1", "../../admin/users"))

        assertEquals(0, server.requestCount)
    }

    @Test
    fun `une panne du serveur remonte, elle ne vaut pas absence`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"code":"internal","message":"boom"}"""))

        val echec = runCatching { api.lyrics(url(), "wfa_1", "t") }.exceptionOrNull()

        assertTrue("attendu : Unexpected, reçu : $echec", echec is ServerException.Unexpected)
    }

    private companion object {
        /** Deux feuilles : la synchronisée d'abord, comme le serveur les range. */
        val DEUX_FEUILLES = """
            {
              "trackId": "uuid-7",
              "structuredLyrics": [
                {
                  "displayArtist": "Bruit de Fond", "displayTitle": "Résonance", "lang": "fra",
                  "synced": true,
                  "line": [{"start": 4500, "value": "Deux"}, {"start": 1000, "value": "Une"}],
                  "source": "sidecar_lrc"
                },
                {
                  "displayArtist": "Bruit de Fond", "displayTitle": "Résonance", "lang": "fra",
                  "synced": false,
                  "line": [{"value": "Texte des tags"}],
                  "source": "embedded"
                }
              ]
            }
        """.trimIndent()
    }
}
