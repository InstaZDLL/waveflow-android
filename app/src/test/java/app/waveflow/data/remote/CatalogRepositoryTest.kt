package app.waveflow.data.remote

import app.waveflow.testing.FakeCatalogApi
import app.waveflow.testing.FakeServerApi
import app.waveflow.testing.FakeSessionStore
import app.waveflow.model.ServerSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Le catalogue muni d'une session : ce qui se passe quand le jeton lâche. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CatalogRepositoryTest {

    private val session = ServerSession.Connected(
        serverUrl = "https://musique.test",
        username = "admin",
        accessToken = "wfa_stocke",
        refreshToken = "wfr_stocke",
        deviceId = "appareil-1",
        accessExpiresAtMs = Long.MAX_VALUE,
    )

    private suspend fun connectedSessionRepository(
        api: FakeServerApi = FakeServerApi(),
    ): ServerSessionRepository = ServerSessionRepository(
        api = api,
        store = FakeSessionStore(stored = session),
        deviceName = "Pixel de test",
        now = { 0L },
    ).also { it.restore() }

    @Test
    fun `les appels portent l'adresse et le jeton de la session`() = runTest {
        val catalog = FakeCatalogApi()
        val repository = CatalogRepository(catalog, connectedSessionRepository())

        repository.albums(offset = 10, limit = 20)

        assertEquals("https://musique.test", catalog.lastServerUrl)
        assertEquals("wfa_stocke", catalog.lastAccessToken)
        assertEquals(10 to 20, catalog.lastPage)
    }

    @Test
    fun `sans session l'appel echoue plutot que de partir sans jeton`() = runTest {
        val catalog = FakeCatalogApi()
        val repository = CatalogRepository(
            catalog,
            ServerSessionRepository(
                api = FakeServerApi(),
                store = FakeSessionStore(),
                deviceName = "Pixel de test",
                now = { 0L },
            ),
        )

        val error = runCatching { repository.albums(0) }.exceptionOrNull()

        assertTrue(error is ServerException.Unauthorized)
        assertEquals(0, catalog.calls)
    }

    @Test
    fun `un jeton refuse est renouvele et l'appel rejoue`() = runTest {
        // Cas réel : le jeton est révoqué depuis un autre appareil. L'horloge
        // locale le croit encore valide, seul le serveur sait qu'il ne l'est
        // plus — donc pas de renouvellement anticipé possible.
        val catalog = FakeCatalogApi(failuresBeforeSuccess = 1)
        val serverApi = FakeServerApi()
        val repository = CatalogRepository(catalog, connectedSessionRepository(serverApi))

        repository.albums(0)

        assertEquals("deux appels : le refusé, puis celui d'après", 2, catalog.calls)
        assertEquals(1, serverApi.refreshCalls)
        assertEquals("wfa_1", catalog.lastAccessToken)
    }

    @Test
    fun `un second refus n'est pas rejoue indefiniment`() = runTest {
        val catalog = FakeCatalogApi(failuresBeforeSuccess = Int.MAX_VALUE)
        val repository = CatalogRepository(catalog, connectedSessionRepository())

        val error = runCatching { repository.albums(0) }.exceptionOrNull()

        assertTrue(error is ServerException.Unauthorized)
        assertEquals(2, catalog.calls)
    }

    @Test
    fun `une panne reseau n'est pas prise pour un jeton perime`() = runTest {
        val catalog = FakeCatalogApi(failure = ServerException.Unreachable("coupure"))
        val serverApi = FakeServerApi()
        val repository = CatalogRepository(catalog, connectedSessionRepository(serverApi))

        val error = runCatching { repository.albums(0) }.exceptionOrNull()

        assertTrue(error is ServerException.Unreachable)
        assertEquals("rien à renouveler", 0, serverApi.refreshCalls)
        assertEquals(1, catalog.calls)
    }
}
