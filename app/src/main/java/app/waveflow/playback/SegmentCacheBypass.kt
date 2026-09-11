package app.waveflow.playback

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Fait passer un segment à côté du cache.
 *
 * Un segment — un flux demandé à partir d'un instant, `offset_ms` — n'est pas le
 * morceau : ses octets ne commencent pas là où commencent ceux du morceau. Or le
 * cache est posé **avant** le résolveur. Un segment qui s'y présenterait se
 * verrait servir le morceau déjà en cache, depuis 0:00, pendant que l'écran
 * afficherait l'instant demandé ; ou il y rangerait son reste sous un nom qui
 * n'est pas le sien. Il va donc droit au résolveur, ni lu ni écrit.
 *
 * L'aiguillage se décide à l'ouverture, sur le marqueur même que le résolveur
 * relira : aucun état partagé ne s'intercale entre les deux. Voir
 * `docs/deplacement-dans-un-transcodage.md`, R11.
 */
internal class SegmentCacheBypass(
    private val cached: DataSource,
    private val direct: DataSource,
) : DataSource {

    private var ouverte: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        cached.addTransferListener(transferListener)
        direct.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val source = if (streamOffsetOfRemoteUri(dataSpec.uri) > 0L) direct else cached
        ouverte = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(ouverte) { "lecture d'une source fermée" }.read(buffer, offset, length)

    override fun getUri(): Uri? = ouverte?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = ouverte?.responseHeaders.orEmpty()

    override fun close() {
        try {
            ouverte?.close()
        } finally {
            ouverte = null
        }
    }
}
