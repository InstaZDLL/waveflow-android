package app.waveflow.playback

import android.app.Application
import android.os.Bundle
import android.os.Looper
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import app.waveflow.WaveFlowApp
import app.waveflow.data.PreferencesStore
import app.waveflow.model.PlaybackSpeed
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
 * La vitesse de lecture, du fichier de préférences jusqu'au lecteur.
 *
 * C'est le maillon que rien d'autre ne couvre. `PreferencesStoreTest` prouve
 * que la vitesse survit au redémarrage, `PlayerViewModelTest` qu'elle remonte à
 * l'écran ; entre les deux, il reste à montrer que **quelqu'un l'applique** — et
 * ce quelqu'un est le service, précisément parce qu'il joue aussi quand aucun
 * écran n'est ouvert.
 *
 * Le vrai magasin de l'application, et non un faux : le service va le chercher
 * lui-même dans le conteneur, il ne reçoit aucune dépendance en paramètre. La
 * vitesse est donc remise à l'ordinaire après chaque test — le délégué
 * `preferencesDataStore` mémorise son instance pour toute la machine virtuelle,
 * et ce qu'on laisse derrière soi, un autre test le relira.
 */
@RunWith(RobolectricTestRunner::class)
class PlaybackServiceSpeedTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val preferences: PreferencesStore get() = (app as WaveFlowApp).container.preferencesStore

    private var service: ServiceController<PlaybackService>? = null

    @After
    fun tearDown() {
        service?.destroy()
        runBlocking { preferences.setPlaybackSpeed(PlaybackSpeed.NORMALE) }
    }

    @Test
    fun `la vitesse enregistree est appliquee des le demarrage du service`() {
        // Le cas qui motive tout le montage : on règle ×1,5, on ferme
        // l'application, puis la lecture repart depuis Android Auto ou la
        // notification. Si personne n'applique la préférence à ce moment-là,
        // elle retombe à ×1 exactement là où on ne peut pas la corriger.
        runBlocking { preferences.setPlaybackSpeed(1.5f) }

        val lecteur = lecteurDuService()

        assertEquals(1.5f, lecteur.attendreVitesse(1.5f), 0f)
    }

    @Test
    fun `changer la vitesse pendant la lecture la porte jusqu'au lecteur`() {
        // Le service observe, il ne lit pas une fois pour toutes : sans cela le
        // réglage n'aurait d'effet qu'au redémarrage suivant.
        val lecteur = lecteurDuService()
        lecteur.attendreVitesse(PlaybackSpeed.NORMALE)

        runBlocking { preferences.setPlaybackSpeed(0.75f) }

        assertEquals(0.75f, lecteur.attendreVitesse(0.75f), 0f)
    }

    /** Le lecteur que le service tient, tel qu'Android le lui a fait construire. */
    private fun lecteurDuService(): androidx.media3.common.Player {
        val controller = Robolectric.buildService(PlaybackService::class.java).create()
        service = controller
        return controller.get().onGetSession(appelant())!!.player
    }

    /**
     * Attend que la vitesse devienne [attendue], puis la rend.
     *
     * L'observation traverse deux fils : le DataStore lit sur son propre
     * dispatcher, et la pose sur le lecteur revient au fil principal, que
     * Robolectric n'anime que si on le lui demande. Sans cette attente, le test
     * lirait la vitesse avant que la collecte ait eu lieu — et passerait ou
     * échouerait selon l'humeur de la machine.
     *
     * Bornée : une vitesse qui n'arriverait plus doit faire échouer le test, pas
     * le faire attendre indéfiniment. L'assertion de l'appelant tranche ensuite,
     * y compris quand l'échéance est atteinte.
     */
    private fun androidx.media3.common.Player.attendreVitesse(attendue: Float): Float {
        val echeance = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_S)
        while (playbackParameters.speed != attendue && System.nanoTime() < echeance) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        return playbackParameters.speed
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

    private companion object {
        const val TIMEOUT_S = 5L
    }
}
