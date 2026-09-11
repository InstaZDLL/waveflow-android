package app.waveflow.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.ContentMetadata

/**
 * Retire du cache le début qu'un transcodage quitté en route y a laissé.
 *
 * Un transcodage en direct arrive sans longueur et refuse toute plage qui ne
 * part pas du premier octet. Son début gardé en cache ne sert donc à rien : à la
 * réécoute, `CacheDataSource` le relirait puis demanderait la suite par une
 * plage, que le serveur refuse en 416 — une erreur que Media3 ne retente jamais.
 * La piste tomberait en erreur là où le cache s'arrêtait.
 *
 * C'est la longueur consignée qui départage, et `CacheDataSource` la consigne
 * lui-même : dès l'ouverture quand le serveur l'annonce — l'original, qui se sert
 * par plages et dont le début reste utile —, à la fin du flux sinon. Une entrée
 * refermée sans longueur est un transcodage abandonné avant sa fin.
 *
 * Ne couvre pas la **coupure réseau** en cours de transcodage : Media3 reprend
 * alors à l'octet atteint, et le serveur refuse cette plage-là aussi, cache ou
 * pas. C'est la relance par décalage temporel qui la couvrira.
 */
internal class IncompleteTranscodeEviction(
    private val cached: DataSource,
    private val cache: Cache,
    private val cacheKeyFactory: CacheKeyFactory,
) : DataSource {

    private var key: String? = null

    override fun addTransferListener(transferListener: TransferListener) {
        cached.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        key = cacheKeyFactory.buildCacheKey(dataSpec)
        return cached.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = cached.read(buffer, offset, length)

    override fun getUri(): Uri? = cached.uri

    override fun getResponseHeaders(): Map<String, List<String>> = cached.responseHeaders

    override fun close() {
        try {
            cached.close()
        } finally {
            key?.let { entree ->
                if (ContentMetadata.getContentLength(cache.getContentMetadata(entree)) == C.LENGTH_UNSET.toLong()) {
                    cache.removeResource(entree)
                }
            }
            key = null
        }
    }
}
