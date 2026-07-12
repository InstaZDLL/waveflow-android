package app.waveflow

import android.app.Application
import app.waveflow.data.MusicRepository

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
    val musicRepository: MusicRepository = MusicRepository(app)
}
