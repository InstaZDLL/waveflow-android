package app.waveflow.playback

import androidx.core.net.toUri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import app.waveflow.data.remote.CatalogRepository
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * Échange le marqueur `waveflow://track/<id>` contre une URL de diffusion.
 *
 * Media3 appelle ce résolveur juste avant d'ouvrir une piste, sur son fil de
 * chargement. C'est exactement le moment voulu : le ticket obtenu ne vit qu'une
 * heure, et le demander à la constitution de la file périmerait celui des
 * derniers morceaux avant qu'on ne les atteigne. Un déplacement dans le morceau
 * rouvre la source et en redemande un, ce qui règle aussi l'expiration en cours
 * d'écoute.
 *
 * L'appel est bloquant parce que le contrat de [ResolvingDataSource.Resolver]
 * l'est ; il s'exécute hors du fil principal, sur le fil de chargement.
 */
class RemoteStreamResolver(
    private val catalogRepository: CatalogRepository,
) : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val trackId = trackIdOfRemoteUri(dataSpec.uri) ?: return dataSpec

        val url = try {
            runBlocking { catalogRepository.streamUrl(trackId) }
        } catch (error: Exception) {
            // Media3 n'attend que des IOException ici : toute autre remonterait
            // brute jusqu'au lecteur et ferait tomber le service au lieu de
            // signaler une piste illisible.
            throw IOException("Diffusion indisponible pour la piste $trackId", error)
        }

        return dataSpec.withUri(url.toUri())
    }
}
