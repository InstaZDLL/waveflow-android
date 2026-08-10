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
import app.waveflow.data.remote.ServerSessionRepository
import app.waveflow.playback.Media3PlaybackController
import app.waveflow.playback.PlaybackController
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
class WaveFlowApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.restoreServerSession()
    }
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
