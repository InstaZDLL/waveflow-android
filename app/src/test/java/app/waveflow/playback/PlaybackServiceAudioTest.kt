package app.waveflow.playback

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.waveflow.WaveFlowApp
import app.waveflow.data.PreferencesStore
import app.waveflow.model.ModeDeGain
import app.waveflow.model.Prereglage
import app.waveflow.model.ReglagesEgaliseur
import app.waveflow.model.ReglagesReplayGain
import app.waveflow.playback.audio.ChaineAudio
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import java.util.concurrent.TimeUnit

/**
 * Le ReplayGain et l'égaliseur, du fichier de préférences jusqu'aux traitements
 * de la sortie audio. Voir A2 et A4 de `docs/audio-avance.md`.
 *
 * Le maillon que `PreferencesStoreTest` et `TraitementsTest` laissent entre eux :
 * **quelqu'un** doit poser les réglages sur la chaîne, et ce quelqu'un est le
 * service, qui joue aussi écran fermé. Comme pour la vitesse, le vrai magasin de
 * l'application, remis à ses défauts après chaque test.
 */
@RunWith(RobolectricTestRunner::class)
class PlaybackServiceAudioTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val preferences: PreferencesStore get() = (app as WaveFlowApp).container.preferencesStore

    private var service: ServiceController<PlaybackService>? = null

    @After
    fun tearDown() {
        service?.destroy()
        runBlocking {
            preferences.updateReplayGain { ReglagesReplayGain() }
            preferences.updateEqualizer { ReglagesEgaliseur.PAR_DEFAUT }
        }
    }

    @Test
    fun `les reglages enregistres sont poses des le demarrage du service`() {
        val choisi = ReglagesReplayGain(mode = ModeDeGain.ParAlbum, preampliDb = 3f)
        runBlocking {
            preferences.updateReplayGain { choisi }
            preferences.updateEqualizer { it.avecActif(true).avecPrereglage(Prereglage.Jazz) }
        }

        val chaine = chaineDuService()

        assertEquals(choisi, chaine.attendre(choisi) { replayGain })
        val jazz = ReglagesEgaliseur.PAR_DEFAUT.avecActif(true).avecPrereglage(Prereglage.Jazz)
        assertEquals(jazz, chaine.attendre(jazz) { egaliseur })
    }

    @Test
    fun `changer un reglage pendant la lecture le porte jusqu'aux traitements`() {
        val chaine = chaineDuService()
        chaine.attendre(ReglagesReplayGain()) { replayGain }

        runBlocking { preferences.updateReplayGain { it.copy(eviterSaturation = false) } }
        val attendu = ReglagesReplayGain(eviterSaturation = false)

        assertEquals(attendu, chaine.attendre(attendu) { replayGain })
    }

    private fun chaineDuService(): ChaineAudio {
        val controller = Robolectric.buildService(PlaybackService::class.java).create()
        service = controller
        return controller.get().chaineAudio!!
    }

    /**
     * Attend que [lire] rende [attendu], puis rend ce qu'il lit. Bornée, comme
     * dans `PlaybackServiceSpeedTest` : la collecte traverse le fil du DataStore
     * puis le fil principal, que Robolectric n'anime que si on le lui demande.
     */
    private fun <T> ChaineAudio.attendre(attendu: T, lire: ChaineAudio.() -> T): T {
        val echeance = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_S)
        while (lire() != attendu && System.nanoTime() < echeance) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        return lire()
    }

    private companion object {
        const val TIMEOUT_S = 5L
    }
}
