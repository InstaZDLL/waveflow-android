package app.waveflow

import android.app.Application
import android.os.Build
import app.waveflow.data.LibraryStore
import app.waveflow.data.MediaStoreMusicRepository
import app.waveflow.data.MusicRepository
import app.waveflow.data.PlaylistRepository
import app.waveflow.data.RoomPlaylistRepository
import app.waveflow.data.local.WaveFlowDatabase
import app.waveflow.data.remote.CatalogRepository
import app.waveflow.data.remote.DataStoreSessionStore
import app.waveflow.data.remote.HttpCatalogApi
import app.waveflow.data.remote.HttpServerApi
import app.waveflow.data.remote.ServerHttp
import app.waveflow.data.remote.ServerImageAuthInterceptor
import app.waveflow.data.remote.ServerSessionRepository
import app.waveflow.playback.Media3PlaybackController
import app.waveflow.playback.PlaybackController
import app.waveflow.playback.RemoteMediaCache
import coil.ImageLoader
import coil.ImageLoaderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * Point d'entrée de l'application.
 *
 * Pour l'instant l'injection de dépendances est manuelle via [container] :
 * un simple conteneur suffit tant que le graphe reste petit. On migrera vers
 * Hilt quand le nombre de dépendances le justifiera.
 */
class WaveFlowApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.restoreServerSession()
    }

    /**
     * Chargeur d'images unique, partagé par les pochettes locales et distantes.
     *
     * Les secondes viennent de `/api/v2/artwork/`, derrière le même jeton que le
     * reste de l'API : Coil ne connaît rien de la session, c'est l'intercepteur
     * qui la lui apporte.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient {
            OkHttpClient.Builder()
                .addInterceptor(ServerImageAuthInterceptor(container.serverSessionRepository))
                .build()
        }
        .build()
}

/** Conteneur d'objets partagés à l'échelle de l'application. */
class AppContainer(app: Application) {

    private val musicRepository: MusicRepository = MediaStoreMusicRepository(app.contentResolver)

    /**
     * Portée de vie du processus : la bibliothèque n'a pas de raison de cesser
     * d'être observée tant que l'application tourne.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val libraryStore = LibraryStore(musicRepository, applicationScope)

    // Room n'ouvre réellement le fichier qu'à la première requête, donc rien
    // de coûteux ne se passe ici.
    private val database = WaveFlowDatabase.build(app)

    val playlistRepository: PlaylistRepository = RoomPlaylistRepository(database.playlistDao())

    private val appContext = app.applicationContext

    /**
     * Nouvelle instance à chaque appel : un [PlaybackController] tient une
     * liaison vivante avec le service, il doit donc être possédé — et libéré —
     * par le composant qui le demande (voir `PlayerViewModel.onCleared`).
     */
    fun createPlaybackController(): PlaybackController = Media3PlaybackController(appContext)

    /**
     * Cache des pistes distantes, unique pour le processus.
     *
     * Porté ici et non par le service : `SimpleCache` refuse d'ouvrir deux fois
     * le même répertoire, et l'écran des réglages doit pouvoir en lire la taille
     * et le vider pendant que le service tourne. Il vit donc aussi longtemps que
     * le processus, ce qui suffit — le verrou tombe avec lui.
     */
    val remoteMediaCache: RemoteMediaCache by lazy { RemoteMediaCache(appContext) }

    /**
     * Un seul transport pour tous les appels serveur : un pool de connexions
     * partagé, et surtout un seul endroit qui classe les erreurs.
     */
    private val serverHttp = ServerHttp()

    /**
     * Session serveur, indépendante de la bibliothèque locale : elle n'a besoin
     * ni de la permission audio ni du MediaStore.
     *
     * Le nom d'appareil est celui que le serveur affichera dans la liste des
     * sessions ; `Build.MODEL` est ce que l'utilisateur reconnaîtra.
     */
    val serverSessionRepository = ServerSessionRepository(
        api = HttpServerApi(serverHttp),
        store = DataStoreSessionStore(app),
        deviceName = Build.MODEL ?: "Android",
    )

    val catalogRepository = CatalogRepository(
        api = HttpCatalogApi(serverHttp),
        sessionRepository = serverSessionRepository,
    )

    /** Relit la session persistée, sans bloquer le démarrage. */
    fun restoreServerSession() {
        applicationScope.launch { serverSessionRepository.restore() }
    }
}
