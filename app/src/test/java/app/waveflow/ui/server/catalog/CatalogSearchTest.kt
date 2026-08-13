package app.waveflow.ui.server.catalog

import app.waveflow.data.remote.CatalogRepository
import app.waveflow.data.remote.ServerException
import app.waveflow.data.remote.ServerSessionRepository
import app.waveflow.model.ServerSession
import app.waveflow.testing.FakeServerApi
import app.waveflow.testing.FakeSessionStore
import app.waveflow.testing.MainDispatcherRule
import app.waveflow.testing.PagingCatalogApi
import app.waveflow.testing.remoteSong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * La recherche sur le serveur.
 *
 * Contrairement à la recherche locale, qui filtre en mémoire, chaque frappe
 * pourrait partir sur le réseau : c'est surtout l'anti-rebond qu'on vérifie ici.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CatalogSearchTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val connected = ServerSession.Connected(
        serverUrl = "https://musique.test",
        username = "admin",
        accessToken = "wfa_1",
        refreshToken = "wfr_1",
        deviceId = "appareil-1",
        accessExpiresAtMs = Long.MAX_VALUE,
    )

    private val catalogue = listOf(
        remoteSong(id = "a", title = "Écho lointain"),
        remoteSong(id = "b", title = "Résonance"),
    )

    private suspend fun viewModel(catalog: PagingCatalogApi): CatalogViewModel {
        val sessions = ServerSessionRepository(
            api = FakeServerApi(),
            store = FakeSessionStore(stored = connected),
            deviceName = "Pixel de test",
            now = { 0L },
        )
        sessions.restore()
        return CatalogViewModel(
            CatalogRepository(catalog, sessions),
            MutableStateFlow(connected),
        )
    }

    @Test
    fun `une requete interroge le serveur et expose ses resultats`() =
        runTest(mainDispatcherRule.dispatcher) {
            val catalog = PagingCatalogApi(searchResults = catalogue)
            val viewModel = viewModel(catalog)

            viewModel.onSearchQueryChange("Écho")
            advanceUntilIdle()

            val state = viewModel.search.value
            assertEquals(listOf("Écho"), catalog.searchQueries)
            assertEquals(listOf("Écho lointain"), state.results.songs.map { it.title })
            assertFalse(state.isSearching)
        }

    @Test
    fun `taper vite ne lance qu'une requete`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Le cas qui compte : sur le réseau, une requête par frappe est un
            // gâchis que la recherche locale, en mémoire, ne connaît pas.
            val catalog = PagingCatalogApi(searchResults = catalogue)
            val viewModel = viewModel(catalog)

            "Écho".forEachIndexed { index, _ ->
                viewModel.onSearchQueryChange("Écho".take(index + 1))
                advanceTimeBy(50)
            }
            advanceUntilIdle()

            assertEquals(listOf("Écho"), catalog.searchQueries)
        }

    @Test
    fun `la requete attend la fin de la frappe, sans plus`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Épingle le délai dans les deux sens. `advanceUntilIdle` avance le
            // temps virtuel sans limite : un anti-rebond démesuré y passerait
            // inaperçu, seule une avance mesurée le révèle.
            val catalog = PagingCatalogApi(searchResults = catalogue)
            val viewModel = viewModel(catalog)

            viewModel.onSearchQueryChange("Écho")
            advanceTimeBy(299)
            runCurrent()
            assertTrue("rien tant que la frappe peut continuer", catalog.searchQueries.isEmpty())

            // La milliseconde qui suit : le délai est épinglé à sa valeur, pas
            // à un intervalle où n'importe quelle constante passerait.
            advanceTimeBy(1)
            runCurrent()
            assertEquals(listOf("Écho"), catalog.searchQueries)
        }

    @Test
    fun `une requete differente relance la recherche`() =
        runTest(mainDispatcherRule.dispatcher) {
            val catalog = PagingCatalogApi(searchResults = catalogue)
            val viewModel = viewModel(catalog)

            viewModel.onSearchQueryChange("Écho")
            advanceUntilIdle()
            viewModel.onSearchQueryChange("Réso")
            advanceUntilIdle()

            assertEquals(listOf("Écho", "Réso"), catalog.searchQueries)
        }

    @Test
    fun `vider la requete n'interroge pas le serveur et efface les resultats`() =
        runTest(mainDispatcherRule.dispatcher) {
            val catalog = PagingCatalogApi(searchResults = catalogue)
            val viewModel = viewModel(catalog)
            viewModel.onSearchQueryChange("Écho")
            advanceUntilIdle()

            viewModel.onSearchQueryChange("")
            advanceUntilIdle()

            assertEquals("une requête vide ne vaut pas un appel", 1, catalog.searchQueries.size)
            assertTrue(viewModel.search.value.results.isEmpty)
            assertFalse(viewModel.search.value.isActive)
        }

    @Test
    fun `les espaces seuls ne valent pas une requete`() =
        runTest(mainDispatcherRule.dispatcher) {
            val catalog = PagingCatalogApi(searchResults = catalogue)
            val viewModel = viewModel(catalog)

            viewModel.onSearchQueryChange("   ")
            advanceUntilIdle()

            assertTrue(catalog.searchQueries.isEmpty())
        }

    @Test
    fun `la frappe qui continue pendant l'appel n'est pas ecrasee`() =
        runTest(mainDispatcherRule.dispatcher) {
            // L'état porte à la fois la requête et les résultats : les remplacer
            // ensemble ferait reculer le curseur de l'utilisateur.
            val portail = CompletableDeferred<Unit>()
            val catalog = PagingCatalogApi(searchResults = catalogue, searchGate = portail)
            val viewModel = viewModel(catalog)

            viewModel.onSearchQueryChange("Écho")
            advanceTimeBy(400)
            runCurrent()

            viewModel.onSearchQueryChange("Écho lo")
            portail.complete(Unit)
            advanceUntilIdle()

            assertEquals("Écho lo", viewModel.search.value.query)
        }

    @Test
    fun `une frappe annule la recherche en cours, resultats compris`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Le délai est sous `flatMapLatest`, pas avant : placé avant, il
            // retarderait la nouvelle requête et la précédente continuerait de
            // courir, jusqu'à rendre ses résultats sous une requête changée.
            val portail = CompletableDeferred<Unit>()
            val catalog = PagingCatalogApi(searchResults = catalogue, searchGate = portail)
            val viewModel = viewModel(catalog)

            viewModel.onSearchQueryChange("Écho")
            advanceTimeBy(400)
            runCurrent()
            assertEquals("la première est partie", listOf("Écho"), catalog.searchQueries)

            // Elle est toujours en vol quand la suivante arrive, et se
            // débloque juste après.
            viewModel.onSearchQueryChange("Réso")
            portail.complete(Unit)

            // Moins que le délai de grâce : la seconde recherche n'est pas
            // encore partie. C'est l'instant qui départage les deux montages —
            // le délai placé avant `flatMapLatest` laisserait la première
            // rendre ici ses résultats, sous une requête déjà changée. Les deux
            // convergent ensuite, ce que la fin du test ne prouverait donc pas.
            advanceTimeBy(100)
            runCurrent()
            assertTrue(
                "aucun résultat périmé sous la nouvelle requête",
                viewModel.search.value.results.isEmpty,
            )

            advanceUntilIdle()
            val state = viewModel.search.value
            assertEquals("Réso", state.query)
            assertEquals(
                "les résultats sont ceux de la requête affichée",
                listOf("Résonance"),
                state.results.songs.map { it.title },
            )
        }

    @Test
    fun `un echec devient un message et non des resultats vides`() =
        runTest(mainDispatcherRule.dispatcher) {
            val catalog = PagingCatalogApi(
                searchResults = catalogue,
                searchFailure = ServerException.Unreachable("coupure"),
            )
            val viewModel = viewModel(catalog)

            viewModel.onSearchQueryChange("Écho")
            advanceUntilIdle()

            val state = viewModel.search.value
            assertEquals("Serveur injoignable.", state.errorMessage)
            assertFalse("un échec n'est pas une absence de résultat", state.foundNothing)
        }

    @Test
    fun `la deconnexion efface la recherche`() =
        runTest(mainDispatcherRule.dispatcher) {
            val session = MutableStateFlow<ServerSession>(connected)
            val sessions = ServerSessionRepository(
                api = FakeServerApi(),
                store = FakeSessionStore(stored = connected),
                deviceName = "Pixel de test",
                now = { 0L },
            )
            sessions.restore()
            val viewModel = CatalogViewModel(
                CatalogRepository(PagingCatalogApi(searchResults = catalogue), sessions),
                session,
            )

            viewModel.onSearchQueryChange("Écho")
            advanceUntilIdle()

            session.value = ServerSession.Disconnected
            advanceUntilIdle()

            assertEquals("", viewModel.search.value.query)
            assertTrue(viewModel.search.value.results.isEmpty)
            assertNull(viewModel.search.value.errorMessage)
        }
}
