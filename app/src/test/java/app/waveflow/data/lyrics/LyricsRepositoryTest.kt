package app.waveflow.data.lyrics

import android.net.Uri
import app.waveflow.data.remote.ServerException
import app.waveflow.model.Lyrics
import app.waveflow.model.SyncedLine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * D'où viennent les paroles d'un morceau, et ce que le dépôt en garde
 * (`docs/paroles.md`, P1, P4, P5 et P7).
 */
@RunWith(RobolectricTestRunner::class)
class LyricsRepositoryTest {

    private val simples = Lyrics.Plain(listOf("Dans les tags"))
    private val synchronisees = Lyrics.Synced(listOf(SyncedLine(1_000, "Dans le .lrc")))
    private val duServeur = Lyrics.Plain(listOf("Du serveur"))

    /** Les fichiers lus et les pistes demandées au serveur, dans l'ordre. */
    private val appels = mutableListOf<String>()

    private var tags: Lyrics? = null
    private var voisin: Lyrics? = null
    private var serveur: suspend (String) -> Lyrics? = { duServeur }

    private val depot = LyricsRepository(
        embedded = { uri: Uri ->
            appels += "tags:${uri.lastPathSegment}"
            tags
        },
        sidecar = { uri: Uri ->
            appels += "lrc:${uri.lastPathSegment}"
            voisin
        },
        server = { id: String ->
            appels += "serveur:$id"
            serveur(id)
        },
    )

    @Test
    fun `une piste locale prend d'abord les paroles de ses tags`() = runTest {
        tags = simples
        voisin = synchronisees

        // Même simples, les paroles des tags passent devant un .lrc synchronisé :
        // c'est l'ordre décidé (P1).
        assertEquals(LyricsResult.Found(simples), depot.lyricsFor("local:42"))
        assertEquals(listOf("tags:42"), appels)
    }

    @Test
    fun `sans paroles dans ses tags, une piste locale lit son lrc voisin`() = runTest {
        voisin = synchronisees

        assertEquals(LyricsResult.Found(synchronisees), depot.lyricsFor("local:42"))
        assertEquals(listOf("tags:42", "lrc:42"), appels)
    }

    @Test
    fun `une piste locale ne demande jamais rien au serveur`() = runTest {
        assertEquals(LyricsResult.None, depot.lyricsFor("local:42"))
        assertTrue(appels.none { it.startsWith("serveur") })
    }

    @Test
    fun `une piste du serveur ne lit aucun fichier`() = runTest {
        assertEquals(LyricsResult.Found(duServeur), depot.lyricsFor("remote:uuid-7"))
        assertEquals(listOf("serveur:uuid-7"), appels)
    }

    @Test
    fun `ce qui a ete trouve, ou pas, ne se cherche pas deux fois`() = runTest {
        voisin = synchronisees
        depot.lyricsFor("local:1")
        depot.lyricsFor("local:2")
        depot.lyricsFor("local:1")
        depot.lyricsFor("local:2")

        assertEquals(listOf("tags:1", "lrc:1", "tags:2", "lrc:2"), appels)
    }

    @Test
    fun `un serveur qui ne repond pas n'est pas garde, la demande suivante reessaie`() = runTest {
        serveur = { throw ServerException.Unreachable("coupure") }
        assertEquals(LyricsResult.Unavailable, depot.lyricsFor("remote:uuid-7"))

        serveur = { duServeur }
        assertEquals(LyricsResult.Found(duServeur), depot.lyricsFor("remote:uuid-7"))
        assertEquals(listOf("serveur:uuid-7", "serveur:uuid-7"), appels)
    }

    @Test
    fun `une session fermee rend les paroles indisponibles, pas absentes`() = runTest {
        serveur = { throw ServerException.Unauthorized("Aucune session serveur.") }

        assertEquals(LyricsResult.Unavailable, depot.lyricsFor("remote:uuid-7"))
    }

    @Test
    fun `le cache oublie les morceaux les plus anciens au-dela de trente-deux`() = runTest {
        (1..33).forEach { depot.lyricsFor("local:$it") }
        appels.clear()

        depot.lyricsFor("local:33")
        depot.lyricsFor("local:1")

        // Le 33ᵉ est encore là ; le premier, le plus ancien, a été oublié.
        assertEquals(listOf("tags:1", "lrc:1"), appels)
    }
}
