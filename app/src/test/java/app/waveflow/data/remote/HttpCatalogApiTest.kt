package app.waveflow.data.remote

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
 * Le client du catalogue face à un serveur de test.
 *
 * Les corps sont ceux relevés sur `waveflow-server` 2.0.0-beta.0 : notamment le
 * fait que les détails sont **aplatis** — `/albums/{id}` renvoie les champs de
 * l'album au premier niveau, avec `songs` à côté, et non un objet imbriqué.
 *
 * Robolectric depuis que le catalogue porte des adresses de pochettes : ce sont
 * des `android.net.Uri`, que `ArtworkUrls` construit sans jamais lever — une
 * JVM nue les rendrait donc toutes nulles, en silence.
 */
@RunWith(RobolectricTestRunner::class)
class HttpCatalogApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: CatalogApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = HttpCatalogApi()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun url(): String = server.url("/").toString().trimEnd('/')

    private suspend fun echecDe(bloc: suspend () -> Unit): Throwable =
        runCatching { bloc() }.exceptionOrNull()
            ?: throw AssertionError("aucune exception levée")

    @Test
    fun `la liste d'albums est paginee et authentifiee`() = runTest {
        server.enqueue(MockResponse().setBody(ALBUMS_BODY))

        val albums = api.albums(url(), "wfa_1", offset = 50, limit = 25)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v2/albums?offset=50&limit=25", request.path)
        assertEquals("Bearer wfa_1", request.getHeader("Authorization"))

        assertEquals(2, albums.size)
        assertEquals("Nuit Blanche", albums[0].title)
        assertEquals("Aurore", albums[0].artist)
        assertEquals("f7ba66f7-dfae-4e86-b1e1-f356f4c092b7", albums[0].artistId)
        // `year` est nul sur ces fichiers : le champ doit rester facultatif.
        assertNull(albums[0].year)
    }

    @Test
    fun `la liste d'artistes reprend le compte d'albums`() = runTest {
        server.enqueue(MockResponse().setBody(ARTISTS_BODY))

        val artists = api.artists(url(), "wfa_1", offset = 0, limit = 50)

        assertEquals("/api/v2/artists?offset=0&limit=50", server.takeRequest().path)
        assertEquals("Aurore", artists[0].name)
        assertEquals(2, artists[0].albumCount)
    }

    @Test
    fun `le detail d'un album est aplati et ses morceaux ordonnes`() = runTest {
        server.enqueue(MockResponse().setBody(ALBUM_DETAIL_BODY))

        val detail = api.album(url(), "wfa_1", "1daf991a")

        assertEquals("/api/v2/albums/1daf991a", server.takeRequest().path)
        assertEquals("Nuit Blanche", detail.album.title)
        assertEquals("Aurore", detail.album.artist)
        // Le corps les donne dans le désordre : c'est le numéro de piste qui
        // fait foi, pas l'ordre d'arrivée.
        assertEquals(listOf("Première Lueur", "Ciel Bas", "Aube Grise"), detail.songs.map { it.title })
        assertEquals(3030L, detail.songs[0].durationMs)
    }

    @Test
    fun `un morceau sans numero de piste passe apres les autres`() = runTest {
        server.enqueue(MockResponse().setBody(ALBUM_WITHOUT_TRACK_NUMBERS))

        val detail = api.album(url(), "wfa_1", "album")

        assertEquals(listOf("Avec numéro", "Sans numéro"), detail.songs.map { it.title })
    }

    @Test
    fun `le detail d'un artiste porte ses albums et pas de compte`() = runTest {
        server.enqueue(MockResponse().setBody(ARTIST_DETAIL_BODY))

        val detail = api.artist(url(), "wfa_1", "f7ba66f7")

        assertEquals("/api/v2/artists/f7ba66f7", server.takeRequest().path)
        assertEquals("Aurore", detail.artist.name)
        // Le serveur ne le renvoie pas sur ce chemin : ne rien inventer.
        assertNull(detail.artist.albumCount)
        assertEquals(listOf("Nuit Blanche", "Second Souffle"), detail.albums.map { it.title })
    }

    @Test
    fun `un identifiant est encode et ne peut pas designer un autre point d'API`() = runTest {
        // L'identifiant vient d'une réponse serveur ou d'un argument de
        // navigation : interpolé dans le chemin, un `/` qui s'y glisserait
        // pointerait ailleurs.
        server.enqueue(MockResponse().setBody(ALBUM_DETAIL_BODY))

        api.album(url(), "wfa_1", "../artists/autre")

        assertEquals(
            "/api/v2/albums/..%2Fartists%2Fautre",
            server.takeRequest().path,
        )
    }

    @Test
    fun `le hachage de pochette devient une adresse, sur toutes les entites`() = runTest {
        // La liste d'albums, la liste d'artistes, le détail d'un album et ses
        // pistes : chacun porte son propre `artwork_hash`, et chacun doit le
        // voir devenir une adresse.
        server.enqueue(MockResponse().setBody(ALBUMS_WITH_ARTWORK))
        server.enqueue(MockResponse().setBody(ARTISTS_WITH_ARTWORK))
        server.enqueue(MockResponse().setBody(ALBUM_DETAIL_WITH_ARTWORK))

        val albums = api.albums(url(), "wfa_1", 0, 50)
        assertEquals("${url()}/api/v2/artwork/hash-album", albums[0].artworkUri.toString())
        // Le second n'en a pas : aucune adresse, pas une adresse vers un 404.
        assertNull(albums[1].artworkUri)

        val artists = api.artists(url(), "wfa_1", 0, 50)
        assertEquals("${url()}/api/v2/artwork/hash-artiste", artists[0].artworkUri.toString())

        val detail = api.album(url(), "wfa_1", "1daf991a")
        assertEquals("${url()}/api/v2/artwork/hash-album", detail.album.artworkUri.toString())
        assertEquals("${url()}/api/v2/artwork/hash-piste", detail.songs[0].artworkUri.toString())
    }

    @Test
    fun `le detail d'un artiste porte les pochettes de ses albums`() = runTest {
        server.enqueue(MockResponse().setBody(ARTIST_DETAIL_WITH_ARTWORK))

        val detail = api.artist(url(), "wfa_1", "f7ba66f7")
        assertEquals("${url()}/api/v2/artwork/hash-artiste", detail.artist.artworkUri.toString())
        assertEquals("${url()}/api/v2/artwork/hash-album", detail.albums[0].artworkUri.toString())
    }

    @Test
    fun `le ticket de diffusion devient une URL absolue`() = runTest {
        server.enqueue(MockResponse().setBody(TICKET_BODY))

        val streamUrl = api.streamTicket(url(), "wfa_1", "c07f8d98")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v2/tracks/c07f8d98/stream-ticket", request.path)
        assertEquals("Bearer wfa_1", request.getHeader("Authorization"))

        assertEquals("${url()}/api/v2/stream/VkdLrczM", streamUrl)
    }

    @Test
    fun `le prefixe de proxy est conserve dans l'URL de diffusion`() = runTest {
        // Le serveur rend `/api/v2/stream/…` sans savoir qu'un proxy le préfixe.
        // Résoudre ce chemin contre la racine effacerait le préfixe, et l'URL
        // n'atteindrait plus le serveur.
        server.enqueue(MockResponse().setBody(TICKET_BODY))

        val streamUrl = api.streamTicket("${url()}/musique", "wfa_1", "c07f8d98")

        assertEquals("${url()}/musique/api/v2/stream/VkdLrczM", streamUrl)
    }

    @Test
    fun `un ticket en reference reseau est refuse`() = runTest {
        // `//hôte/…` emprunte le schéma courant et change d'hôte sans en avoir
        // l'air : la lecture partirait ailleurs que sur le serveur où
        // l'utilisateur s'est authentifié.
        server.enqueue(
            MockResponse().setBody("""{"url":"//ailleurs.test/api/v2/stream/x","expires_at":0}"""),
        )

        val error = echecDe { api.streamTicket(url(), "wfa_1", "c07f8d98") }

        assertTrue(error.toString(), error is ServerException.Unexpected)
    }

    @Test
    fun `un ticket en URL complete est refuse`() = runTest {
        // Même conclusion par un autre chemin : le contrat est un chemin du
        // serveur, pas une URL qu'il choisirait librement.
        server.enqueue(
            MockResponse().setBody(
                """{"url":"https://ailleurs.test/api/v2/stream/x","expires_at":0}""",
            ),
        )

        val error = echecDe { api.streamTicket(url(), "wfa_1", "c07f8d98") }

        assertTrue(error.toString(), error is ServerException.Unexpected)
    }

    @Test
    fun `un jeton refuse remonte comme tel`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"code":"unauthorized","message":"Authentication failed"}"""),
        )

        val error = echecDe { api.albums(url(), "wfa_perime", 0, 50) }

        assertTrue(error.toString(), error is ServerException.Unauthorized)
    }

    @Test
    fun `un catalogue illisible est signale comme inattendu`() = runTest {
        server.enqueue(MockResponse().setBody("""{"pas":"un tableau"}"""))

        val error = echecDe { api.albums(url(), "wfa_1", 0, 50) }

        assertTrue(error.toString(), error is ServerException.Unexpected)
    }

    @Test
    fun `les champs inconnus du catalogue ne cassent rien`() = runTest {
        server.enqueue(MockResponse().setBody(ALBUMS_BODY_WITH_EXTRAS))

        assertEquals(2, api.albums(url(), "wfa_1", 0, 50).size)
    }

    private companion object {
        val ALBUMS_BODY = """
            [
              {
                "id": "1daf991a-5f98-4b02-a885-fb64a664d308",
                "library_id": "ad75e269-f5b6-4960-b90f-c940008ce014",
                "title": "Nuit Blanche",
                "artist": "Aurore",
                "artist_id": "f7ba66f7-dfae-4e86-b1e1-f356f4c092b7",
                "artwork_hash": null,
                "year": null,
                "created_at": 1786372969163,
                "starred_at": null,
                "user_rating": null,
                "play_count": 0,
                "last_played_at": null
              },
              {
                "id": "ecbc899a-5d7b-41a0-aabf-5c594e77591b",
                "library_id": "ad75e269-f5b6-4960-b90f-c940008ce014",
                "title": "Second Souffle",
                "artist": "Aurore",
                "artist_id": "f7ba66f7-dfae-4e86-b1e1-f356f4c092b7",
                "artwork_hash": null,
                "year": 2024,
                "created_at": 1786372969163,
                "starred_at": null,
                "user_rating": null,
                "play_count": 0,
                "last_played_at": null
              }
            ]
        """.trimIndent()

        val ALBUMS_BODY_WITH_EXTRAS = ALBUMS_BODY.replace(
            "\"play_count\": 0,",
            "\"play_count\": 0, \"nouveaute\": {\"a\": 1},",
        )

        val ARTISTS_BODY = """
            [
              {
                "id": "f7ba66f7-dfae-4e86-b1e1-f356f4c092b7",
                "library_id": "ad75e269-f5b6-4960-b90f-c940008ce014",
                "name": "Aurore",
                "artwork_hash": null,
                "starred_at": null,
                "user_rating": null,
                "album_count": 2
              }
            ]
        """.trimIndent()

        /** Les morceaux sont volontairement donnés dans le désordre. */
        val ALBUM_DETAIL_BODY = """
            {
              "id": "1daf991a-5f98-4b02-a885-fb64a664d308",
              "library_id": "ad75e269-f5b6-4960-b90f-c940008ce014",
              "title": "Nuit Blanche",
              "artist": "Aurore",
              "artist_id": "f7ba66f7-dfae-4e86-b1e1-f356f4c092b7",
              "year": null,
              "songs": [
                {
                  "id": "c3", "library_id": "l", "album_id": "a",
                  "title": "Aube Grise", "album": "Nuit Blanche", "artist": "Aurore",
                  "track": 3, "duration_ms": 3030, "suffix": "mp3", "size": 1,
                  "created_at": 0
                },
                {
                  "id": "c1", "library_id": "l", "album_id": "a",
                  "title": "Première Lueur", "album": "Nuit Blanche", "artist": "Aurore",
                  "track": 1, "duration_ms": 3030, "suffix": "mp3", "size": 1,
                  "created_at": 0
                },
                {
                  "id": "c2", "library_id": "l", "album_id": "a",
                  "title": "Ciel Bas", "album": "Nuit Blanche", "artist": "Aurore",
                  "track": 2, "duration_ms": 3030, "suffix": "mp3", "size": 1,
                  "created_at": 0
                }
              ]
            }
        """.trimIndent()

        val ALBUM_WITHOUT_TRACK_NUMBERS = """
            {
              "id": "a", "library_id": "l", "title": "Sans ordre",
              "songs": [
                {
                  "id": "s2", "library_id": "l", "title": "Sans numéro",
                  "duration_ms": 1000, "suffix": "mp3", "size": 1, "created_at": 0
                },
                {
                  "id": "s1", "library_id": "l", "title": "Avec numéro",
                  "track": 1, "duration_ms": 1000, "suffix": "mp3", "size": 1,
                  "created_at": 0
                }
              ]
            }
        """.trimIndent()

        /** Un album avec pochette, un sans : les deux cas dans une même page. */
        val ALBUMS_WITH_ARTWORK = """
            [
              {
                "id": "1daf991a", "library_id": "l", "title": "Nuit Blanche",
                "artist": "Aurore", "artwork_hash": "hash-album",
                "created_at": 0, "play_count": 0
              },
              {
                "id": "ecbc899a", "library_id": "l", "title": "Second Souffle",
                "artist": "Aurore", "artwork_hash": null,
                "created_at": 0, "play_count": 0
              }
            ]
        """.trimIndent()

        /**
         * Un artiste avec pochette.
         *
         * Le serveur n'en produit aucun aujourd'hui — son scanner ne peuple pas
         * `artist.artwork_hash` — mais la colonne existe, la requête la
         * sélectionne, et la route artwork autorise déjà un hachage porté par un
         * artiste. Figer l'absence ici ferait échouer ce test le jour où le
         * scanner s'y met, pour un comportement pourtant juste.
         */
        val ARTISTS_WITH_ARTWORK = """
            [
              {
                "id": "f7ba66f7", "library_id": "l", "name": "Aurore",
                "artwork_hash": "hash-artiste", "album_count": 2
              }
            ]
        """.trimIndent()

        val ALBUM_DETAIL_WITH_ARTWORK = """
            {
              "id": "1daf991a", "library_id": "l", "title": "Nuit Blanche",
              "artist": "Aurore", "artwork_hash": "hash-album",
              "songs": [
                {
                  "id": "c1", "library_id": "l", "title": "Première Lueur",
                  "track": 1, "duration_ms": 3030, "suffix": "mp3", "size": 1,
                  "artwork_hash": "hash-piste", "created_at": 0
                }
              ]
            }
        """.trimIndent()

        val ARTIST_DETAIL_WITH_ARTWORK = """
            {
              "id": "f7ba66f7", "library_id": "l", "name": "Aurore",
              "artwork_hash": "hash-artiste",
              "albums": [
                {
                  "id": "1daf991a", "library_id": "l", "title": "Nuit Blanche",
                  "artwork_hash": "hash-album", "created_at": 0, "play_count": 0
                }
              ]
            }
        """.trimIndent()

        /** L'URL est relative au serveur, c'est ce que rend `stream-ticket`. */
        val TICKET_BODY = """
            {"url": "/api/v2/stream/VkdLrczM", "expires_at": 1786395364096}
        """.trimIndent()

        val ARTIST_DETAIL_BODY = """
            {
              "id": "f7ba66f7-dfae-4e86-b1e1-f356f4c092b7",
              "library_id": "ad75e269-f5b6-4960-b90f-c940008ce014",
              "name": "Aurore",
              "artwork_hash": null,
              "albums": [
                {
                  "id": "1daf991a", "library_id": "l", "title": "Nuit Blanche",
                  "artist": "Aurore", "created_at": 0, "play_count": 0
                },
                {
                  "id": "ecbc899a", "library_id": "l", "title": "Second Souffle",
                  "artist": "Aurore", "created_at": 0, "play_count": 0
                }
              ]
            }
        """.trimIndent()
    }
}
