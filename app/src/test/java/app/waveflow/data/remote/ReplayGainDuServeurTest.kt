package app.waveflow.data.remote

import app.waveflow.model.RemoteReplayGain
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Les quatre champs `replay_gain_*` d'un `SongItem`, tels que le serveur 2.3 les
 * publie (`docs/guides/api-v2-guide.md` côté serveur). Voir G2 de
 * `docs/audio-avance.md`.
 */
@RunWith(RobolectricTestRunner::class)
class ReplayGainDuServeurTest {

    private lateinit var server: MockWebServer
    private val api: CatalogApi = HttpCatalogApi()

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

    @Test
    fun `les champs replay_gain d'une piste arrivent dans le modele`() = runTest {
        server.enqueue(MockResponse().setBody(ALBUM))

        // Par identifiant : le détail d'album trie ses pistes à sa façon.
        val pistes = api.album(url(), "wfa_1", "a1").songs.associateBy { it.id }

        assertEquals(RemoteReplayGain(-7.89, 0.98, -6.5, 0.995), pistes.getValue("s1").replayGain)
        // Un serveur dont le scan n'a pas encore rempli les champs, ou qui les
        // rend nuls : aucune valeur, et le gain par défaut s'appliquera.
        assertEquals(RemoteReplayGain(), pistes.getValue("s2").replayGain)
        assertEquals(RemoteReplayGain(), pistes.getValue("s3").replayGain)
    }

    private companion object {
        val ALBUM = """
            {
              "id": "a1", "library_id": "l", "title": "Nuit Blanche",
              "songs": [
                {
                  "id": "s1", "library_id": "l", "title": "Marqué", "album_id": "a1",
                  "duration_ms": 1000, "suffix": "flac", "size": 1, "created_at": 0,
                  "replay_gain_track_gain": -7.89, "replay_gain_track_peak": 0.98,
                  "replay_gain_album_gain": -6.5, "replay_gain_album_peak": 0.995
                },
                {
                  "id": "s2", "library_id": "l", "title": "Nul", "album_id": "a1",
                  "duration_ms": 1000, "suffix": "flac", "size": 1, "created_at": 0,
                  "replay_gain_track_gain": null, "replay_gain_track_peak": null,
                  "replay_gain_album_gain": null, "replay_gain_album_peak": null
                },
                {
                  "id": "s3", "library_id": "l", "title": "Absent", "album_id": "a1",
                  "duration_ms": 1000, "suffix": "flac", "size": 1, "created_at": 0
                }
              ]
            }
        """.trimIndent()
    }
}
