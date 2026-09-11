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
import org.junit.Assert.assertTrue
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
 * Le déplacement dans un transcodage, vu par un vrai `MediaController`.
 *
 * `TranscodeSeekingPlayerTest` éprouve l'enveloppe seule. Il reste à montrer que
 * c'est bien elle que la **session** tient : la notification et Android Auto ne
 * lisent que la session, et une enveloppe posée à côté ne les corrigerait pas
 * (R1 de `docs/deplacement-dans-un-transcodage.md`).
 *
 * Le vrai magasin de l'application, remis à l'origine après chaque test : le
 * délégué `preferencesDataStore` mémorise son instance pour toute la machine
 * virtuelle, et ce qu'on laisse derrière soi, un autre test le relira.
 */
@RunWith(RobolectricTestRunner::class)
class PlaybackServiceSeekTest {

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
    fun `la session annonce la duree du catalogue et le saut dans un transcodage`() {
        // Sans l'enveloppe, l'ExoPlayer ne connaît ni la durée d'un transcodage
        // en direct ni le moyen de s'y déplacer : le contrôleur n'aurait ni l'une
        // ni l'autre.
        runBlocking { preferences.setStreamQuality(StreamQuality.Economie) }
        demarrerLeService()
        val controleur = controleurConnecte()

        controleur.setMediaItems(listOf(remoteSong(id = "c07f8d98", durationMs = DUREE_MS).toMediaItem()))
        attendre("la durée du catalogue côté contrôleur") { controleur.duration == DUREE_MS }

        assertTrue(controleur.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
    }

    /** Le service tel qu'Android le crée. */
    private fun demarrerLeService() {
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
        checkNotNull(demarre.get().onGetSession(appelant()))
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
     * Bornée : un état qui n'arriverait plus doit faire échouer le test, pas le
     * faire attendre indéfiniment.
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
        const val DUREE_MS = 245_000L
        const val TIMEOUT_S = 15L
        const val PAUSE_MS = 5L
    }
}
