package app.waveflow

import android.app.Application
import app.waveflow.data.MediaStoreMusicRepository
import app.waveflow.data.MusicRepository
import app.waveflow.playback.Media3PlaybackController
import app.waveflow.playback.PlaybackController

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
    }
}

/** Conteneur d'objets partagés à l'échelle de l'application. */
class AppContainer(app: Application) {

    val musicRepository: MusicRepository = MediaStoreMusicRepository(app.contentResolver)

    private val appContext = app.applicationContext

    /**
     * Nouvelle instance à chaque appel : un [PlaybackController] tient une
     * liaison vivante avec le service, il doit donc être possédé — et libéré —
     * par le composant qui le demande (voir `LibraryViewModel.onCleared`).
     */
    fun createPlaybackController(): PlaybackController = Media3PlaybackController(appContext)
}
