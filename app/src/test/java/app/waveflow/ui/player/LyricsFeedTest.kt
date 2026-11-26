package app.waveflow.ui.player

import app.waveflow.data.lyrics.LyricsRepository
import app.waveflow.data.lyrics.LyricsResult
import app.waveflow.data.remote.ServerException
import app.waveflow.model.Lyrics
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Les paroles du morceau courant, cherchées seulement quand on les regarde
 * (`docs/paroles.md`, P6 à P8, P17).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LyricsFeedTest {

    /** Le morceau courant, tel que le lecteur le nomme. */
    private val courant = MutableStateFlow<String?>("remote:a")

    /** Les pistes demandées au serveur, dans l'ordre. */
    private val demandes = mutableListOf<String>()

    /** Ce que le serveur répond, par piste ; une piste absente attend sa porte. */
    private val reponses = mutableMapOf<String, suspend () -> Lyrics?>()

    private fun paroles(texte: String) = Lyrics.Plain(listOf(texte))

    private val depot = LyricsRepository(
        embedded = { null },
        sidecar = { null },
        server = { id ->
            demandes += id
            (reponses[id] ?: { paroles("paroles de $id") })()
        },
    )

    /** Le flux, sa zone à l'écran dès que les paroles sont choisies, sauf [enVue] faux. */
    private fun TestScope.flux(enVue: Boolean = true) = LyricsFeed(
        scope = backgroundScope,
        mediaIds = courant,
        repository = depot,
        started = SharingStarted.Eagerly,
    ).also { it.onScreen(enVue) }

    @Test
    fun `des paroles choisies mais cachees par la file ne se cherchent pas`() = runTest {
        val flux = flux(enVue = false)
        flux.toggle()
        runCurrent()
        courant.value = "remote:b"
        runCurrent()

        assertTrue(demandes.isEmpty())
        // Le choix tient : refermer la file rend les paroles.
        assertEquals(LyricsUiState(shown = true, mediaId = "remote:b"), flux.state.value)

        flux.onScreen(true)
        runCurrent()
        assertEquals(listOf("b"), demandes)
        assertEquals(LyricsResult.Found(paroles("paroles de b")), flux.state.value.result)
    }

    @Test
    fun `tant que les paroles ne sont pas affichees, rien n'est cherche`() = runTest {
        val flux = flux()
        runCurrent()
        courant.value = "remote:b"
        runCurrent()

        assertTrue(demandes.isEmpty())
        assertEquals(LyricsUiState(shown = false, mediaId = "remote:b"), flux.state.value)
    }

    @Test
    fun `afficher les paroles les cherche pour le morceau courant`() = runTest {
        val flux = flux()
        flux.toggle()
        runCurrent()

        assertEquals(
            LyricsUiState(shown = true, mediaId = "remote:a", result = LyricsResult.Found(paroles("paroles de a"))),
            flux.state.value,
        )
        assertEquals(listOf("a"), demandes)
    }

    @Test
    fun `pendant la recherche, rien n'est affirme`() = runTest {
        val porte = CompletableDeferred<Unit>()
        reponses["a"] = {
            porte.await()
            paroles("enfin")
        }
        val flux = flux()
        flux.toggle()
        runCurrent()

        assertEquals(LyricsUiState(shown = true, mediaId = "remote:a", result = null), flux.state.value)

        porte.complete(Unit)
        runCurrent()
        assertEquals(LyricsResult.Found(paroles("enfin")), flux.state.value.result)
    }

    @Test
    fun `une reponse tardive ne s'affiche pas sous le morceau suivant`() = runTest {
        val porte = CompletableDeferred<Unit>()
        reponses["a"] = {
            porte.await()
            paroles("celles de a")
        }
        val flux = flux()
        flux.toggle()
        runCurrent()

        courant.value = "remote:b"
        runCurrent()

        // La recherche de « a » est toujours bloquée : les paroles de « b » ne
        // peuvent être là que si elle a été abandonnée, pas attendue.
        val attendu =
            LyricsUiState(shown = true, mediaId = "remote:b", result = LyricsResult.Found(paroles("paroles de b")))
        assertEquals(attendu, flux.state.value)

        porte.complete(Unit)
        runCurrent()
        assertEquals(attendu, flux.state.value)
    }

    @Test
    fun `les tics de position ne relancent pas la recherche`() = runTest {
        // Le lecteur publie son état à chaque tic, et le morceau avec : le même
        // identifiant revient sans cesse. La recherche reste en vol — rien
        // n'entre au cache —, si bien qu'une relance se compterait.
        reponses["a"] = { CompletableDeferred<Lyrics?>().await() }
        val etat = MutableStateFlow("remote:a" to 0L)
        val flux = LyricsFeed(
            scope = backgroundScope,
            mediaIds = etat.map { it.first },
            repository = depot,
            started = SharingStarted.Eagerly,
        )
        flux.onScreen(true)
        flux.toggle()
        runCurrent()

        for (position in 500L..2_000L step 500L) {
            etat.value = "remote:a" to position
            runCurrent()
        }

        assertEquals(listOf("a"), demandes)
    }

    @Test
    fun `rouvrir les paroles ne cherche pas deux fois`() = runTest {
        val flux = flux()
        flux.toggle()
        runCurrent()
        flux.toggle()
        runCurrent()
        assertNull(flux.state.value.result)

        flux.toggle()
        runCurrent()

        assertEquals(LyricsResult.Found(paroles("paroles de a")), flux.state.value.result)
        assertEquals(listOf("a"), demandes)
    }

    @Test
    fun `reessayer apres un serveur muet relance la demande`() = runTest {
        reponses["a"] = { throw ServerException.Unreachable("coupure") }
        val flux = flux()
        flux.toggle()
        runCurrent()
        assertEquals(LyricsResult.Unavailable, flux.state.value.result)

        reponses.remove("a")
        flux.retry()
        runCurrent()

        assertEquals(LyricsResult.Found(paroles("paroles de a")), flux.state.value.result)
        assertEquals(listOf("a", "a"), demandes)
    }
}
