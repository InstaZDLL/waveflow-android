package app.waveflow.playback

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Looper
import androidx.core.net.toUri
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import app.waveflow.data.remote.ServerImageAuthInterceptor
import app.waveflow.data.remote.ServerSessionRepository
import app.waveflow.model.ServerSession
import app.waveflow.testing.FakeServerApi
import app.waveflow.testing.FakeSessionStore
import coil.Coil
import coil.ImageLoader
import coil.request.CachePolicy
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import java.util.concurrent.TimeUnit

/**
 * La pochette que Media3 va chercher lui-même.
 *
 * La notification, l'écran de verrouillage et Android Auto ne sont pas peuplés
 * par l'application : Media3 lit `artworkUri` et charge l'image avec le
 * chargeur de la session. Son chargeur par défaut ignore tout de la session
 * serveur — `/api/v2/artwork/` lui répondait donc **401**, et la vignette
 * restait vide alors que les écrans de l'app l'affichaient.
 *
 * Le test emprunte le même chemin que
 * `DefaultMediaNotificationProvider` : `session.bitmapLoader` puis
 * `loadBitmapFromMetadata`. Le serveur factice ne sert l'image qu'aux requêtes
 * signées, si bien que retirer le chargeur de [PlaybackService] fait retomber
 * ce test sur le 401 d'origine.
 */
@RunWith(RobolectricTestRunner::class)
class PlaybackServiceArtworkTest {

    private lateinit var server: MockWebServer
    private var service: ServiceController<PlaybackService>? = null

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.getHeader("Authorization") == "Bearer $JETON") {
                    MockResponse()
                        .setHeader("Content-Type", "image/png")
                        .setBody(Buffer().write(PNG_1X1))
                } else {
                    MockResponse().setResponseCode(401)
                }
        }

        Coil.setImageLoader(imageLoaderConnecteA(server.url("/").toString().trimEnd('/')))
    }

    @After
    fun tearDown() {
        service?.destroy()
        Coil.reset()
        server.shutdown()
    }

    @Test
    fun `la pochette de la notification part avec le jeton de session`() {
        val session = sessionDuService()
        val metadata = MediaMetadata.Builder()
            .setArtworkUri(server.url("/api/v2/artwork/abc").toString().toUri())
            .build()

        val bitmap = session.bitmapLoader.loadBitmapFromMetadata(metadata)!!.attendue()

        assertNotNull(bitmap)
        // Borné : sans échéance, un chargement qui ne partirait plus sur le
        // réseau ferait attendre le test au lieu de le faire échouer.
        val requete = server.takeRequest(TIMEOUT_S, TimeUnit.SECONDS)
        assertEquals("Bearer $JETON", requete?.getHeader("Authorization"))
    }

    /** Le service tel qu'Android le crée, session comprise. */
    private fun sessionDuService(): MediaSession {
        val controller = Robolectric.buildService(PlaybackService::class.java).create()
        service = controller
        return controller.get().onGetSession(appelant())!!
    }

    private fun appelant(): MediaSession.ControllerInfo =
        MediaSession.ControllerInfo.createTestOnlyControllerInfo(
            /* packageName = */ "app.waveflow",
            /* pid = */ 0,
            /* uid = */ 0,
            /* libraryVersion = */ 0,
            /* interfaceVersion = */ 0,
            /* trusted = */ true,
            /* connectionHints = */ Bundle.EMPTY,
        )

    /**
     * Le chargeur d'images de l'application, avec une session ouverte.
     *
     * `Coil.setImageLoader` remplace le singleton que [PlaybackService] ira
     * chercher : c'est le seul point d'injection, le service ne prend pas de
     * dépendance en paramètre.
     */
    private fun imageLoaderConnecteA(serverUrl: String): ImageLoader {
        val sessions = ServerSessionRepository(
            api = FakeServerApi(),
            store = FakeSessionStore(
                stored = ServerSession.Connected(
                    serverUrl = serverUrl,
                    username = "admin",
                    accessToken = JETON,
                    refreshToken = "wfr_stocke",
                    deviceId = "appareil-1",
                    accessExpiresAtMs = Long.MAX_VALUE,
                ),
            ),
            deviceName = "Pixel de test",
            now = { 0L },
        ).also { runBlocking { it.restore() } }

        return ImageLoader.Builder(ApplicationProvider.getApplicationContext<android.content.Context>())
            // Le test doit mesurer ce qui part sur le réseau, pas ce qu'un
            // cache aurait retenu.
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor(ServerImageAuthInterceptor(sessions))
                    .build()
            }
            .build()
    }

    /**
     * Attend le futur sans bloquer le fil principal.
     *
     * Le chargement reprend sur `Dispatchers.Main.immediate` — un `get()`
     * bloquant depuis le fil de test empêcherait justement cette reprise.
     */
    private fun ListenableFuture<Bitmap>.attendue(): Bitmap {
        val echeance = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_S)
        while (!isDone && System.nanoTime() < echeance) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue("La pochette n'est jamais arrivée", isDone)
        return get()
    }

    private companion object {
        const val JETON = "wfa_stocke"
        const val TIMEOUT_S = 10L

        val PNG_1X1: ByteArray = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
        )
    }
}
