package app.waveflow.playback

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import app.waveflow.WaveFlowApp
import app.waveflow.data.PreferencesStore
import app.waveflow.model.StreamQuality
import app.waveflow.testing.remoteSong
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * La qualité de lecture, du fichier de préférences jusqu'à la file du lecteur.
 *
 * Le maillon que rien d'autre ne couvre. `PreferencesStoreTest` prouve que le
 * choix survit, `BrowseCallbackTest` que le rappel pose le rendu qu'on lui
 * donne ; il reste à montrer que le service **les relie**, et qu'une file
 * envoyée par un vrai `MediaController` passe bien par ce rappel.
 *
 * Le vrai magasin de l'application, remis à l'origine après chaque test : le
 * délégué `preferencesDataStore` mémorise son instance pour toute la machine
 * virtuelle, et ce qu'on laisse derrière soi, un autre test le relira.
 */
@RunWith(RobolectricTestRunner::class)
class PlaybackServiceQualityTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val preferences: PreferencesStore get() = (app as WaveFlowApp).container.preferencesStore

    private var service: ServiceController<PlaybackService>? = null
    private var controleur: MediaController? = null

    @After
    fun tearDown() {
        controleur?.release()
        service?.destroy()
        runBlocking { preferences.setStreamQuality(StreamQuality.Original) }
    }

    @Test
    fun `une piste du serveur entre dans la file dans la qualite choisie`() {
        runBlocking { preferences.setStreamQuality(StreamQuality.Economie) }
        val lecteur = lecteurDuService()

        controleurConnecte().setMediaItems(listOf(remoteSong(id = "c07f8d98").toMediaItem()))
        attendre("la piste dans la file du service") { lecteur.mediaItemCount == 1 }

        val config = lecteur.getMediaItemAt(0).localConfiguration!!
        val rendu = StreamQuality.Economie.rendering
        assertEquals(rendu, renderingOfRemoteUri(config.uri))
        assertEquals(cacheKeyOf("c07f8d98", rendu.format, rendu.bitrate), config.customCacheKey)
    }

    /** Le service tel qu'Android le crée, et le lecteur qu'il tient. */
    private fun lecteurDuService(): Player {
        val demarre = Robolectric.buildService(PlaybackService::class.java).create()
        service = demarre

        // Robolectric ne démarre pas de vrai service sur `bindService` : on lui
        // donne le `Binder` que le service rend lui-même, pour les deux actions
        // que le manifeste déclare.
        val composant = ComponentName(app, PlaybackService::class.java)
        listOf(MediaLibraryService.SERVICE_INTERFACE, MediaSessionService.SERVICE_INTERFACE)
            .forEach { action ->
                val intent = Intent(action).setComponent(composant)
                shadowOf(app).setComponentNameAndServiceForBindServiceForIntent(
                    intent,
                    composant,
                    demarre.get().onBind(intent),
                )
            }

        return demarre.get().onGetSession(appelant())!!.player
    }

    /** Un contrôleur lié au service, par le même chemin que l'application. */
    private fun controleurConnecte(): MediaController {
        val composant = ComponentName(app, PlaybackService::class.java)
        return MediaController.Builder(app, SessionToken(app, composant))
            .buildAsync()
            .attendu("la liaison au service")
            .also { controleur = it }
    }

    private fun appelant(): MediaSession.ControllerInfo =
        MediaSession.ControllerInfo.createTestOnlyControllerInfo(
            /* packageName = */ "app.waveflow",
            /* pid = */ 0,
            /* uid = */ 0,
            /* libraryVersion = */ 0,
            /* interfaceVersion = */ 0,
            /* trusted = */ true,
            /* connectionHints = */ Bundle.EMPTY,
            /* isPackageNameVerified = */ true,
        )

    /**
     * Attend un futur en écoulant les messages entre deux essais.
     *
     * Un `get()` bloquant depuis le fil de test figerait la boucle principale,
     * dont dépend justement la réponse de la session.
     */
    private fun <T> ListenableFuture<T>.attendu(quoi: String): T {
        attendre(quoi) { isDone }
        return get()
    }

    /**
     * Bornée : une file qui n'arriverait plus doit faire échouer le test, pas
     * le faire attendre indéfiniment.
     */
    private fun attendre(quoi: String, condition: () -> Boolean) {
        val echeance = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_S)
        while (true) {
            ecouler()
            if (condition()) return
            if (System.nanoTime() >= echeance) break
            Thread.sleep(PAUSE_MS)
        }
        fail("Délai dépassé en attendant : $quoi")
    }

    /**
     * Écoule les messages de toutes les boucles encore vivantes : ExoPlayer
     * tient sa machine à états sur un `HandlerThread` à lui, et le DataStore
     * lit sur son propre dispatcher.
     */
    private fun ecouler() {
        ShadowLooper.getAllLoopers()
            .filter { it.thread.isAlive }
            .forEach { boucle -> runCatching { shadowOf(boucle).idle() } }
    }

    private companion object {
        const val TIMEOUT_S = 15L
        const val PAUSE_MS = 5L
    }
}
