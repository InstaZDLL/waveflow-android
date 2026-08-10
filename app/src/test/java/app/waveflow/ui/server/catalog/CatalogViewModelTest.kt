package app.waveflow.ui.server.catalog

import app.waveflow.data.remote.CATALOG_PAGE_SIZE
import app.waveflow.data.remote.CatalogRepository
import app.waveflow.data.remote.ServerException
import app.waveflow.model.RemoteAlbum
import app.waveflow.model.ServerSession
import app.waveflow.testing.FakeServerApi
import app.waveflow.testing.FakeSessionStore
import app.waveflow.testing.MainDispatcherRule
import app.waveflow.testing.PagingCatalogApi
import app.waveflow.data.remote.ServerSessionRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Le parcours paginé du catalogue.
 *
 * Robolectric parce que le ViewModel journalise les échecs inattendus par
 * `android.util.Log`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CatalogViewModelTest {

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

    private suspend fun viewModel(
        catalog: PagingCatalogApi,
        session: MutableStateFlow<ServerSession> = MutableStateFlow(connected),
    ): CatalogViewModel {
        val sessions = ServerSessionRepository(
            api = FakeServerApi(),
            store = FakeSessionStore(stored = connected),
            deviceName = "Pixel de test",
            now = { 0L },
        )
        sessions.restore()
        return CatalogViewModel(CatalogRepository(catalog, sessions), session)
    }

    private fun albums(count: Int): List<RemoteAlbum> =
        (1..count).map { RemoteAlbum("id-$it", "Album $it", "Aurore", "artiste-1", null) }

    @Test
    fun `une session ouverte declenche la premiere page`() =
        runTest(mainDispatcherRule.dispatcher) {
            val catalog = PagingCatalogApi(albums = albums(3))
            val viewModel = viewModel(catalog)
            advanceUntilIdle()

            val state = viewModel.albums.value
            assertEquals(3, state.items.size)
            assertFalse(state.isLoading)
            // Une page plus courte que demandée : il n'y a rien après.
            assertTrue(state.endReached)
        }

    @Test
    fun `une page pleine n'est pas prise pour la derniere`() =
        runTest(mainDispatcherRule.dispatcher) {
            val catalog = PagingCatalogApi(albums = albums(CATALOG_PAGE_SIZE))
            val viewModel = viewModel(catalog)
            advanceUntilIdle()

            assertFalse(viewModel.albums.value.endReached)
        }

    @Test
    fun `la page suivante s'ajoute a la precedente sans la remplacer`() =
        runTest(mainDispatcherRule.dispatcher) {
            val catalog = PagingCatalogApi(albums = albums(CATALOG_PAGE_SIZE + 3))
            val viewModel = viewModel(catalog)
            advanceUntilIdle()

            viewModel.loadMoreAlbums()
            advanceUntilIdle()

            val state = viewModel.albums.value
            assertEquals(CATALOG_PAGE_SIZE + 3, state.items.size)
            assertEquals("Album 1", state.items.first().title)
            assertTrue(state.endReached)
            assertEquals("deux pages, deux appels", 2, catalog.albumCalls)
        }

    @Test
    fun `arrive au bout on cesse de redemander`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Le défilement appelle en continu : sans cette garde, chaque
            // recomposition relancerait une requête inutile.
            val catalog = PagingCatalogApi(albums = albums(2))
            val viewModel = viewModel(catalog)
            advanceUntilIdle()

            viewModel.loadMoreAlbums()
            viewModel.loadMoreAlbums()
            advanceUntilIdle()

            assertEquals(1, catalog.albumCalls)
        }

    @Test
    fun `une page en vol n'est pas doublee`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Le portail maintient la première page en vol : sans lui, elle
            // s'achèverait avant la seconde demande et la garde ne servirait
            // jamais. Le défilement, lui, redemande sans attendre.
            val portail = CompletableDeferred<Unit>()
            val catalog = PagingCatalogApi(albums = albums(CATALOG_PAGE_SIZE), gate = portail)
            val viewModel = viewModel(catalog)
            runCurrent()

            viewModel.loadMoreAlbums()
            portail.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, catalog.albumCalls)
            assertEquals(CATALOG_PAGE_SIZE, viewModel.albums.value.items.size)
        }

    @Test
    fun `un echec laisse un message sans effacer ce qui est deja la`() =
        runTest(mainDispatcherRule.dispatcher) {
            val catalog = PagingCatalogApi(
                albums = albums(CATALOG_PAGE_SIZE),
                failFromCall = 2,
            )
            val viewModel = viewModel(catalog)
            advanceUntilIdle()

            viewModel.loadMoreAlbums()
            advanceUntilIdle()

            val state = viewModel.albums.value
            assertEquals("la première page reste lisible", CATALOG_PAGE_SIZE, state.items.size)
            assertEquals("Serveur injoignable.", state.errorMessage)
        }

    @Test
    fun `reessayer repart de la premiere page`() =
        runTest(mainDispatcherRule.dispatcher) {
            val catalog = PagingCatalogApi(albums = albums(3), failFromCall = 1)
            val viewModel = viewModel(catalog)
            advanceUntilIdle()
            assertTrue(viewModel.albums.value.errorMessage != null)

            catalog.stopFailing()
            viewModel.retryAlbums()
            advanceUntilIdle()

            val state = viewModel.albums.value
            assertEquals(3, state.items.size)
            assertNull(state.errorMessage)
        }

    @Test
    fun `la deconnexion vide le catalogue`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Les pages appartiennent à un compte : l'écran suivant pourrait
            // être celui d'un autre.
            val session = MutableStateFlow<ServerSession>(connected)
            val viewModel = viewModel(PagingCatalogApi(albums = albums(3)), session)
            advanceUntilIdle()
            assertEquals(3, viewModel.albums.value.items.size)

            session.value = ServerSession.Disconnected
            advanceUntilIdle()

            assertEquals(0, viewModel.albums.value.items.size)
        }

    @Test
    fun `ouvrir un album expose son detail`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = viewModel(PagingCatalogApi(albums = albums(1)))
            advanceUntilIdle()

            viewModel.openAlbum("id-1")
            advanceUntilIdle()

            assertEquals("id-1", viewModel.albumDetail.value.value?.album?.id)
            assertNull(viewModel.albumDetail.value.errorMessage)
        }

    @Test
    fun `un detail qui echoue devient un message`() =
        runTest(mainDispatcherRule.dispatcher) {
            val catalog = PagingCatalogApi(
                albums = albums(1),
                detailFailure = ServerException.Unauthorized("périmé"),
            )
            val viewModel = viewModel(catalog)
            advanceUntilIdle()

            viewModel.openAlbum("id-1")
            advanceUntilIdle()

            assertEquals("Session expirée. Reconnectez-vous.", viewModel.albumDetail.value.errorMessage)
            assertNull(viewModel.albumDetail.value.value)
        }
}
