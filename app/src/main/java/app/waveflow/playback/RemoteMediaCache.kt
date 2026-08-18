package app.waveflow.playback

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Cache des pistes lues depuis un serveur.
 *
 * Réécouter un morceau ne le retélécharge pas, ce qui compte d'autant plus sur
 * un forfait mobile. Les fichiers de l'appareil, eux, n'y passent jamais : ils
 * sont déjà sur le disque, les recopier doublerait leur place pour rien.
 *
 * Le répertoire est celui du cache applicatif : le système peut le vider quand
 * la place manque, ce qui est exactement le contrat qu'on veut ici.
 */
class RemoteMediaCache(context: Context) : PlaybackCache {

    private val appContext = context.applicationContext

    /**
     * Une seule instance par répertoire : `SimpleCache` refuse d'en ouvrir une
     * seconde, et l'exception tomberait au démarrage du service.
     */
    private val cache: SimpleCache by lazy {
        SimpleCache(
            File(appContext.cacheDir, DIRECTORY),
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            StandaloneDatabaseProvider(appContext),
        )
    }

    /**
     * La chaîne de lecture complète.
     *
     * L'ordre décide de tout :
     *
     * - le cache **enveloppe** le résolveur de tickets. Une piste déjà en cache
     *   ne demande donc aucun ticket au serveur, et surtout la clé de cache est
     *   celle de l'élément — non l'URL de diffusion, qui change à chaque ticket
     *   et ne coïnciderait jamais avec elle-même ;
     * - `DefaultDataSource` aiguille en amont selon le schéma : `content://` et
     *   `file://` partent vers les sources locales sans jamais toucher au cache.
     */
    fun dataSourceFactory(resolver: ResolvingDataSource.Resolver): DataSource.Factory {
        val resolving = ResolvingDataSource.Factory(DefaultHttpDataSource.Factory(), resolver)

        val cached = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(resolving)
            // Un cache illisible doit dégrader vers le réseau, pas interrompre
            // la lecture.
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return DefaultDataSource.Factory(appContext, cached)
    }

    override val maxBytes: Long = MAX_BYTES

    /**
     * Place occupée, à l'octet près.
     *
     * Lue depuis l'index du cache et non par un parcours du répertoire : c'est
     * le même chiffre que celui sur lequel l'éviction se déclenche.
     */
    override suspend fun usedBytes(): Long = withContext(Dispatchers.IO) { cache.cacheSpace }

    /**
     * Vide le cache de tout ce qu'il contient.
     *
     * Rien n'est perdu : chaque piste reste sur le serveur, et une lecture la
     * retéléchargera. Une lecture en cours n'est pas interrompue — elle tient
     * déjà ses fichiers ouverts, et les supprimer ne les lui retire pas.
     */
    override suspend fun clear() = withContext(Dispatchers.IO) {
        // `keys` reflète l'index à l'instant de l'appel ; en prendre une copie
        // évite de le parcourir pendant qu'on le modifie.
        cache.keys.toList().forEach { cache.removeResource(it) }
    }

    /** À la fermeture : sans quoi le verrou du répertoire subsiste. */
    fun release() {
        cache.release()
    }

    private companion object {
        const val DIRECTORY = "media-serveur"

        /** De quoi tenir quelques albums sans peser sur un téléphone. */
        const val MAX_BYTES = 200L * 1024 * 1024
    }
}
