package app.waveflow

import android.app.Application
import android.os.Build
import android.os.SystemClock
import app.waveflow.data.LibraryStore
import app.waveflow.data.MediaStoreMusicRepository
import app.waveflow.data.MusicRepository
import app.waveflow.data.PlayHistoryRepository
import app.waveflow.data.PlaylistRepository
import app.waveflow.data.PreferencesStore
import app.waveflow.data.RoomPlayHistoryRepository
import app.waveflow.data.RoomPlaylistRepository
import app.waveflow.data.local.WaveFlowDatabase
import app.waveflow.data.preferencesStoreOf
import app.waveflow.data.remote.CatalogRepository
import app.waveflow.data.remote.DataStoreSessionStore
import app.waveflow.data.remote.HttpCatalogApi
import app.waveflow.data.remote.HttpServerApi
import app.waveflow.data.remote.ServerHttp
import app.waveflow.data.remote.ServerImageAuthInterceptor
import app.waveflow.data.remote.ServerSessionRepository
import app.waveflow.playback.AbLoop
import app.waveflow.playback.Media3PlaybackController
import app.waveflow.playback.PlaybackController
import app.waveflow.playback.RemoteMediaCache
import app.waveflow.playback.SleepTimer
import coil.ImageLoader
import coil.ImageLoaderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
            ServerHttp.imageClient(ServerImageAuthInterceptor(container.serverSessionRepository))
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

    /**
     * Les choix de l'utilisateur, hors de la session serveur : ils n'ont
     * aucune raison de disparaître quand il se déconnecte.
     */
    val preferencesStore: PreferencesStore = preferencesStoreOf(app)

    // Room n'ouvre réellement le fichier qu'à la première requête, donc rien
    // de coûteux ne se passe ici.
    private val database = WaveFlowDatabase.build(app)

    val playlistRepository: PlaylistRepository = RoomPlaylistRepository(database.playlistDao())

    val playHistoryRepository: PlayHistoryRepository =
        RoomPlayHistoryRepository(database.playHistoryDao())

    private val appContext = app.applicationContext

    /**
     * Nouvelle instance à chaque appel : un [PlaybackController] tient une
     * liaison vivante avec le service, il doit donc être possédé — et libéré —
     * par le composant qui le demande (voir `PlayerViewModel.onCleared`).
     */
    fun createPlaybackController(): PlaybackController = Media3PlaybackController(appContext)

    /**
     * La minuterie de veille, portée par l'application et non par le service.
     *
     * On la règle depuis l'écran de lecture puis on quitte souvent
     * l'application elle-même : elle doit survivre à l'écran. Le service écoute
     * ses expirations pour mettre en pause ; elle ne connaît pas le lecteur.
     */
    val sleepTimer = SleepTimer(applicationScope, SystemClock::elapsedRealtime)

    /**
     * Les bornes de la boucle A-B, portées par l'application comme la minuterie.
     *
     * On pose une boucle pour repiquer un passage, puis on éteint l'écran et on
     * prend son instrument : elle doit survivre à l'écran. Le service
     * échantillonne la position et rembobine ; elle-même ne connaît pas le
     * lecteur.
     */
    val abLoop = AbLoop()

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
