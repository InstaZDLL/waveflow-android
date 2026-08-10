package app.waveflow.playback

import androidx.core.net.toUri
import androidx.media3.datasource.DataSpec
import app.waveflow.data.remote.CatalogRepository
import app.waveflow.data.remote.ServerException
import app.waveflow.data.remote.ServerSessionRepository
import app.waveflow.model.ServerSession
import app.waveflow.testing.FakeCatalogApi
import app.waveflow.testing.FakeServerApi
import app.waveflow.testing.FakeSessionStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/**
 * L'échange du marqueur contre une URL de diffusion.
 *
 * Media3 appelle ce résolveur de façon bloquante, sur son fil de chargement :
 * les tests l'appellent donc directement, sans coroutine.
 */
@RunWith(RobolectricTestRunner::class)
class RemoteStreamResolverTest {

    private val session = ServerSession.Connected(
        serverUrl = "https://musique.test",
        username = "admin",
        accessToken = "wfa_1",
        refreshToken = "wfr_1",
        deviceId = "appareil-1",
        accessExpiresAtMs = Long.MAX_VALUE,
    )

    private suspend fun resolver(catalog: FakeCatalogApi): RemoteStreamResolver {
        val sessions = ServerSessionRepository(
            api = FakeServerApi(),
            store = FakeSessionStore(stored = session),
            deviceName = "Pixel de test",
            now = { 0L },
        )
        sessions.restore()
        return RemoteStreamResolver(CatalogRepository(catalog, sessions))
    }

    private fun specOf(uri: String) = DataSpec(uri.toUri())

    @Test
    fun `un marqueur distant devient une URL de diffusion`() = runTest {
        val catalog = FakeCatalogApi()
        val resolved = resolver(catalog).resolveDataSpec(specOf("waveflow://track/c07f8d98"))

        assertEquals(
            "https://musique.test/api/v2/stream/ticket-c07f8d98",
            resolved.uri.toString(),
        )
    }

    @Test
    fun `une piste locale traverse le resolveur sans etre touchee`() = runTest {
        // Les fichiers de l'appareil passent par la même chaîne : leur laisser
        // leur URI est ce qui permet de n'avoir qu'un lecteur.
        val catalog = FakeCatalogApi()
        val spec = specOf("content://media/external/audio/media/42")

        val resolved = resolver(catalog).resolveDataSpec(spec)

        assertEquals(spec.uri, resolved.uri)
        assertEquals("aucun appel au serveur", 0, catalog.calls)
    }

    @Test
    fun `le reste du DataSpec est preserve`() = runTest {
        // La position et la longueur portent la reprise après un déplacement :
        // les perdre relancerait le morceau depuis le début.
        val spec = DataSpec.Builder()
            .setUri("waveflow://track/abc".toUri())
            .setPosition(4_096L)
            .setLength(1_024L)
            .build()

        val resolved = resolver(FakeCatalogApi()).resolveDataSpec(spec)

        assertEquals(4_096L, resolved.position)
        assertEquals(1_024L, resolved.length)
    }

    @Test
    fun `un echec du serveur devient une IOException`() = runTest {
        // Media3 n'attend que ça ici : toute autre exception remonterait brute
        // jusqu'au lecteur et ferait tomber le service.
        val catalog = FakeCatalogApi(failure = ServerException.Unreachable("coupure"))

        val error = runCatching {
            resolver(catalog).resolveDataSpec(specOf("waveflow://track/abc"))
        }.exceptionOrNull()

        assertTrue(error.toString(), error is IOException)
        assertTrue(error?.cause is ServerException.Unreachable)
    }
}
