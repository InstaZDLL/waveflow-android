package app.waveflow.data.remote

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.waveflow.imageLoaderOf
import coil.decode.DataSource
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Ce que le chargeur d'images de l'application fait des en-têtes de cache du
 * serveur (`waveflow-server`, `src/media/artwork.rs`).
 *
 * Le serveur distingue les deux adresses d'une pochette : par son hachage,
 * elle ne peut plus changer et part en `private, max-age=31536000, immutable` ;
 * par l'identifiant d'une entité, elle suit la pochette actuelle de celle-ci et
 * part en `private, no-cache`, avec le hachage pour `ETag`. Coil 2 lit
 * `max-age` (pas `immutable`, qu'il ignore) : c'est ce qui épargne toute
 * requête à une pochette déjà vue. Le cache mémoire est coupé, pour que le
 * second chargement passe par le cache disque et ses en-têtes.
 */
@RunWith(RobolectricTestRunner::class)
class ArtworkCacheTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val imageLoader = imageLoaderOf(context) { OkHttpClient() }
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `une pochette adressee par son hachage se relit du disque sans requete`() {
        server.enqueue(pochette(cacheControl = "private, max-age=31536000, immutable"))

        val premiere = charger("/api/v2/artwork/hachage-immuable")
        val seconde = charger("/api/v2/artwork/hachage-immuable")

        assertEquals(DataSource.NETWORK, premiere.dataSource)
        assertEquals(DataSource.DISK, seconde.dataSource)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `une pochette adressee par son entite se revalide a chaque chargement`() {
        // Le pendant du précédent : sur l'identifiant, chaque affichage hors
        // du cache mémoire coûte un aller-retour, même conclu par un 304.
        // C'est ce que `ArtworkUrls` évite en bâtissant l'adresse sur le
        // hachage.
        server.enqueue(pochette(cacheControl = "private, no-cache"))
        server.enqueue(
            MockResponse()
                .setResponseCode(304)
                .setHeader("Cache-Control", "private, no-cache")
                .setHeader("ETag", ETAG),
        )

        charger("/api/v2/artwork/album-42")
        val seconde = charger("/api/v2/artwork/album-42")

        assertEquals(2, server.requestCount)
        server.takeRequest()
        assertEquals(ETAG, server.takeRequest().getHeader("If-None-Match"))
        // Le 304 resservit l'image déjà sur le disque.
        assertTrue(seconde.drawable.intrinsicWidth > 0)
    }

    /**
     * Charge sans bloquer le fil principal : Coil reprend sur
     * `Dispatchers.Main.immediate`, qu'un `runBlocking` depuis le fil de test
     * empêcherait de tourner.
     */
    private fun charger(chemin: String): SuccessResult {
        val enCours = CoroutineScope(Dispatchers.IO).async {
            imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(server.url(chemin).toString())
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .build(),
            )
        }
        val echeance = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_S)
        while (!enCours.isCompleted && System.nanoTime() < echeance) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue("La pochette n'est jamais arrivée", enCours.isCompleted)
        val resultat = runBlocking { enCours.await() }
        return resultat as? SuccessResult ?: error("Chargement échoué : $resultat")
    }

    private fun pochette(cacheControl: String): MockResponse = MockResponse()
        .setHeader("Content-Type", "image/png")
        .setHeader("Cache-Control", cacheControl)
        .setHeader("ETag", ETAG)
        .setBody(Buffer().write(PNG_1X1))

    private companion object {
        const val ETAG = "\"abc123\""
        const val TIMEOUT_S = 10L

        /** Un PNG d'un pixel, que le décodeur accepte. */
        val PNG_1X1: ByteArray = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
        )
    }
}
