package app.waveflow.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.util.BitmapLoader
import coil.ImageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Charge les pochettes de Media3 par le chargeur d'images de l'application.
 *
 * Media3 peuple lui-même la notification et l'écran de verrouillage : il lit
 * `artworkUri` des métadonnées et va chercher l'image, sans rien savoir de la
 * session serveur. Son chargeur par défaut — un `DataSourceBitmapLoader` sur
 * `DefaultDataSource` — demande donc `/api/v2/artwork/` sans jeton et reçoit un
 * **401** : la notification restait sans vignette alors que les écrans de l'app,
 * eux, l'affichaient.
 *
 * Passer par Coil suffit à corriger ça, puisque son client porte déjà
 * [app.waveflow.data.remote.ServerImageAuthInterceptor] — avec sa vérification
 * d'origine et son renouvellement de jeton. Le même chemin sert aux pochettes
 * locales, qui sont des `content://` que Coil sait ouvrir.
 *
 * La [scope] appartient à l'appelant : la session vit aussi longtemps que le
 * service, et un chargement en cours doit s'arrêter avec lui.
 */
class CoilBitmapLoader(
    context: Context,
    private val imageLoader: ImageLoader,
    private val scope: CoroutineScope,
) : BitmapLoader {

    private val appContext = context.applicationContext

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    /** Pochette embarquée dans les métadonnées : aucun réseau, juste un décodage. */
    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = loadAsync {
        withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(data, 0, data.size)
                ?: throw IllegalArgumentException("Données de pochette illisibles")
        }
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = loadAsync {
        val request = ImageRequest.Builder(appContext)
            .data(uri)
            // Une bitmap matérielle n'a pas de pixels lisibles : le système
            // redimensionne la vignette de notification et l'artwork de session
            // avec `Bitmap.createScaledBitmap`, qui la refuserait.
            .allowHardware(false)
            .build()

        when (val result = imageLoader.execute(request)) {
            is SuccessResult -> result.drawable.toBitmap()
            is ErrorResult -> throw result.throwable
        }
    }

    /**
     * Fait le pont entre une coroutine et le [ListenableFuture] qu'attend Media3.
     *
     * L'écoute posée sur le future propage l'annulation dans l'autre sens :
     * Media3 abandonne un chargement dès que la piste courante change, et sans
     * ça la requête continuerait pour une pochette dont personne ne veut plus.
     */
    private fun loadAsync(block: suspend () -> Bitmap): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()

        val job = scope.launch {
            try {
                future.set(block())
            } catch (cancellation: CancellationException) {
                future.cancel(/* mayInterruptIfRunning = */ false)
                throw cancellation
            } catch (error: Throwable) {
                future.setException(error)
            }
        }

        future.addListener(
            { if (future.isCancelled) job.cancel() },
            MoreExecutors.directExecutor(),
        )
        return future
    }
}
