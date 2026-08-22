package app.waveflow.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import coil.ComponentRegistry
import coil.ImageLoader
import coil.decode.DataSource
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.DefaultRequestOptions
import coil.request.Disposable
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.SuccessResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * Le pont entre Media3 et le chargeur d'images de l'application.
 *
 * Ce que Media3 attend est un `ListenableFuture` : ni un chargement qui reste
 * en suspens sur une erreur ou une annulation, ni une bitmap que le système
 * refusera de redimensionner.
 */
@RunWith(RobolectricTestRunner::class)
class CoilBitmapLoaderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * Non confiné : le futur est alors résolu au retour de l'appel, ce qui
     * évite d'attendre un ordonnanceur pour observer un résultat immédiat.
     */
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun `la pochette est reclamee au chargeur d'images de l'application`() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val imageLoader = RecordingImageLoader { request ->
            SuccessResult(BitmapDrawable(context.resources, bitmap), request, DataSource.NETWORK)
        }

        val future = CoilBitmapLoader(context, imageLoader, scope)
            .loadBitmap("https://exemple.test/api/v2/artwork/abc".toUri())

        assertEquals(bitmap, future.get(TIMEOUT_S, TimeUnit.SECONDS))
        assertEquals(
            "https://exemple.test/api/v2/artwork/abc",
            imageLoader.lastRequest?.data.toString(),
        )
    }

    @Test
    fun `une bitmap materielle n'est jamais demandee`() {
        // Le système redimensionne la vignette de notification et l'artwork de
        // session avec `Bitmap.createScaledBitmap`, qui n'a pas de pixels à
        // lire sur une bitmap matérielle. Coil en produit une par défaut dès
        // l'API 26, et le défaut ne se verrait que sur un vrai appareil.
        val imageLoader = RecordingImageLoader { request ->
            SuccessResult(
                BitmapDrawable(context.resources, Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)),
                request,
                DataSource.NETWORK,
            )
        }

        CoilBitmapLoader(context, imageLoader, scope)
            .loadBitmap("https://exemple.test/api/v2/artwork/abc".toUri())
            .get(TIMEOUT_S, TimeUnit.SECONDS)

        assertFalse(imageLoader.lastRequest!!.allowHardware)
    }

    @Test
    fun `un chargement qui echoue termine le futur au lieu de le laisser en suspens`() {
        // Media3 attend la vignette avant de publier la notification : un futur
        // qui ne se résout jamais la retiendrait indéfiniment.
        val panne = IOException("serveur injoignable")
        val imageLoader = RecordingImageLoader { request -> ErrorResult(null, request, panne) }

        val future = CoilBitmapLoader(context, imageLoader, scope)
            .loadBitmap("https://exemple.test/api/v2/artwork/abc".toUri())

        assertTrue(future.isDone)
        val leve = runCatching { future.get(TIMEOUT_S, TimeUnit.SECONDS) }.exceptionOrNull()
        assertTrue(leve is ExecutionException)
        assertEquals(panne, leve?.cause)
    }

    @Test
    fun `une portee deja annulee termine le futur sans rien charger`() {
        // `PlaybackService.onDestroy` annule la portée, et Media3 peut réclamer
        // une vignette juste après. La coroutine ne tourne alors jamais : s'en
        // remettre à son seul corps laisserait le futur en suspens pour de bon.
        val imageLoader = RecordingImageLoader { error("aucun chargement ne devait partir") }
        val porteeMorte = CoroutineScope(Dispatchers.Unconfined).also { it.cancel() }

        val future = CoilBitmapLoader(context, imageLoader, porteeMorte)
            .loadBitmap("https://exemple.test/api/v2/artwork/abc".toUri())

        assertTrue("Le futur est resté en suspens", future.isDone)
        assertNull(imageLoader.lastRequest)
    }

    @Test
    fun `annuler le futur arrete le chargement en cours`() {
        // Media3 abandonne un chargement dès que la piste courante change. Sans
        // propagation, la requête continuerait pour une pochette dont plus
        // personne ne veut.
        val demarre = CompletableDeferred<Unit>()
        val interrompu = CompletableDeferred<Unit>()
        val imageLoader = RecordingImageLoader {
            demarre.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                interrompu.complete(Unit)
            }
        }

        val future = CoilBitmapLoader(context, imageLoader, scope)
            .loadBitmap("https://exemple.test/api/v2/artwork/abc".toUri())
        assertTrue("Le chargement n'a jamais démarré", demarre.isCompleted)

        future.cancel(/* mayInterruptIfRunning = */ false)

        assertTrue(future.isCancelled)
        runBlocking {
            withTimeout(TimeUnit.SECONDS.toMillis(TIMEOUT_S)) { interrompu.await() }
        }
    }

    @Test
    fun `une pochette embarquee est decodee sans rien demander au reseau`() {
        val imageLoader = RecordingImageLoader { error("aucun chargement ne devait partir") }

        val future = CoilBitmapLoader(context, imageLoader, scope).decodeBitmap(PNG_1X1)

        assertNotNull(future.get(TIMEOUT_S, TimeUnit.SECONDS))
        assertNull(imageLoader.lastRequest)
    }

    private companion object {
        const val TIMEOUT_S = 5L

        /** Un PNG 1×1 valide : de quoi traverser un vrai décodeur. */
        val PNG_1X1: ByteArray = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
        )
    }
}

/**
 * Chargeur d'images factice qui retient la dernière requête reçue.
 *
 * Coil n'expose aucun double : l'interface est petite, l'implémenter coûte
 * moins qu'un vrai chargeur qu'il faudrait ensuite museler. [result] est
 * suspendable pour qu'un test puisse tenir un chargement ouvert et l'annuler.
 */
private class RecordingImageLoader(
    private val result: suspend (ImageRequest) -> ImageResult,
) : ImageLoader {

    var lastRequest: ImageRequest? = null
        private set

    override val defaults = DefaultRequestOptions()
    override val components = ComponentRegistry()
    override val memoryCache: MemoryCache? = null
    override val diskCache: DiskCache? = null

    override suspend fun execute(request: ImageRequest): ImageResult {
        lastRequest = request
        return result(request)
    }

    override fun enqueue(request: ImageRequest): Disposable =
        throw UnsupportedOperationException("Le chargeur de Media3 passe par execute")

    override fun newBuilder(): ImageLoader.Builder = throw UnsupportedOperationException()

    override fun shutdown() = Unit
}
