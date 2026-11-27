package app.waveflow.playback

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.test.core.app.ApplicationProvider
import app.waveflow.WaveFlowApp
import app.waveflow.testing.song
import kotlinx.coroutines.flow.first
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
import org.robolectric.shadows.ShadowSystemClock
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Duration

/**
 * L'historique d'écoute, tenu par le service sur la vraie chaîne Media3 (#99).
 *
 * `ListeningCounterTest` éprouve le compteur seul. Il reste à montrer que le
 * service le branche sur le lecteur qu'il tient, et ce qui arrive quand la
 * boucle principale prend du retard : sur une machine chargée, un geste posté
 * avant le seuil peut n'être traité qu'après lui, quand le temps joué l'a déjà
 * atteint mais que le minuteur du compteur, rangé derrière ce geste, n'a pas
 * encore sonné.
 *
 * Le retard se fabrique en avançant l'horloge sans écouler les boucles
 * (`ShadowSystemClock.advanceBy`) : le geste posté plus tôt passe alors avant le
 * minuteur, comme sur la boucle encombrée d'un émulateur.
 *
 * Seul le retard d'un geste est éprouvé ici. Une lecture **annoncée** en
 * retard raccourcit encore l'écoute mesurée : ce n'est pas corrigé (#102, voir
 * `ListeningCounter`), et rien ici ne prétend le couvrir.
 *
 * Des WAV de silence : Robolectric ne décode rien, mais le PCM se joue sans
 * décodeur — `isPlaying` passe donc à vrai pour de bon.
 */
@RunWith(RobolectricTestRunner::class)
class PlaybackServiceHistoryTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val history get() = (app as WaveFlowApp).container.playHistoryRepository

    private var service: ServiceController<PlaybackService>? = null
    private var controller: Media3PlaybackController? = null

    @After
    fun tearDown() {
        controller?.release()
        service?.destroy()
    }

    @Test
    fun `vingt secondes jouees d'affilee notent la piste`() {
        val debut = lancerDeuxPistes()

        jouerJusqua(debut, SEUIL_MS + 5_000L)

        assertEquals(listOf("local:1"), noteesAttendues())
    }

    @Test
    fun `une piste quittee apres le seuil est notee meme si le minuteur n'a pas encore sonne`() {
        // Le passage à la suivante est posté avant le seuil, traité après lui :
        // le compteur mesure plus de vingt secondes jouées, et annulait pourtant
        // le minuteur qui devait les noter.
        val debut = lancerDeuxPistes()
        jouerJusqua(debut, SEUIL_MS - 1_000L)

        traiterEnRetard { seekToNextMediaItem() }

        assertEquals(listOf("local:1"), noteesAttendues())
    }

    @Test
    fun `une pause traitee apres le seuil note la piste`() {
        // Même retard, par la pause : le compte restant tombait sous zéro, et
        // la reprise le tenait pour déjà noté.
        val debut = lancerDeuxPistes()
        jouerJusqua(debut, SEUIL_MS - 1_000L)

        traiterEnRetard { pause() }

        assertEquals(listOf("local:1"), noteesAttendues())
    }

    @Test
    fun `une piste quittee avant le seuil n'est pas notee, meme traitee en retard`() {
        // Le pendant : le retard ne doit pas faire compter un survol. Dix
        // secondes, plus le retard, restent loin du seuil.
        val debut = lancerDeuxPistes()
        jouerJusqua(debut, SEUIL_MS / 2)

        traiterEnRetard { seekToNextMediaItem() }

        // La barrière : la suivante, jouée au-delà du seuil, est notée par le
        // même chemin. Room, sans pilote, passe ses transactions une à une
        // dans l'ordre où elles lui sont soumises (`TransactionExecutor`), et
        // le compteur les soumet dès le geste, sur `Main.immediate` : une note
        // fautive de la première serait donc écrite avant celle-ci.
        val reprise = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - reprise < SEUIL_MS + 1_000L) ecouler(avancerHorloge = true)
        attendre("la note de la piste suivante") { "local:2" in notees() }

        assertEquals(listOf("local:2"), notees())
    }

    /**
     * Lance deux pistes de deux minutes et rend l'instant où la lecture a
     * commencé, vu du contrôleur.
     *
     * Le service l'a su au plus un tic plus tôt : son minuteur ne sonne donc
     * pas avant `debut + SEUIL_MS - TICK_MS`.
     */
    private fun lancerDeuxPistes(): Long {
        val controleur = controleurConnecte()
        val morceaux = (1..2).map { n ->
            val wav = File.createTempFile("silence$n", ".wav").apply {
                deleteOnExit()
                writeBytes(wavSilencieux(secondes = 120))
            }
            song(n.toLong()).copy(uri = Uri.fromFile(wav), durationMs = 120_000L)
        }

        controleur.play(morceaux, startIndex = 0)
        // L'horloge avance pendant l'attente : la machine à états de
        // l'ExoPlayer progresse par messages différés, que rien d'autre ne fait
        // échoir.
        attendre("la lecture", avancerHorloge = true) { controleur.state.value.isPlaying }
        return SystemClock.elapsedRealtime()
    }

    /** Fait jouer jusqu'à [ms] après [debut], boucles écoulées à chaque tic. */
    private fun jouerJusqua(debut: Long, ms: Long) {
        while (SystemClock.elapsedRealtime() - debut < ms) ecouler(avancerHorloge = true)
        assertEquals("la première piste joue encore", "local:1", lecteur().currentMediaItem?.mediaId)
    }

    /**
     * Poste [geste] sur la boucle principale, puis laisse passer [RETARD_MS]
     * avant qu'elle ne le traite.
     *
     * Posté environ une seconde avant que le minuteur du compteur ne sonne, le
     * geste passe devant lui ; traité une seconde après le seuil, il trouve le
     * temps joué au-delà de vingt secondes.
     */
    private fun traiterEnRetard(geste: Player.() -> Unit) {
        val lecteur = lecteur()
        Handler(Looper.getMainLooper()).post { lecteur.geste() }
        ShadowSystemClock.advanceBy(Duration.ofMillis(RETARD_MS))
        // Quelques tours sans avancer l'horloge, qui ferait compter la piste
        // suivante, pour que le geste soit traité ; les tests attendent
        // ensuite chacun leur note.
        repeat(TOURS_APRES_GESTE) {
            ecouler(avancerHorloge = false)
            Thread.sleep(PAUSE_MS)
        }
    }

    private fun notees(): List<String> = runBlocking { history.observeRecent(LIMITE).first() }.map { it.mediaId }

    /**
     * L'historique, une fois une note arrivée : l'écriture traverse la base sur
     * ses propres fils. Sans note, le test échoue à l'échéance de [attendre].
     */
    private fun noteesAttendues(): List<String> {
        attendre("une écoute notée") { notees().isNotEmpty() }
        return notees()
    }

    /** Le lecteur que tient la session du service : l'enveloppe, et non l'ExoPlayer. */
    private fun lecteur(): Player = checkNotNull(service).get().onGetSession(appelant())!!.player

    /** Un WAV PCM 16 bits mono à 8 kHz, tout en silence. */
    private fun wavSilencieux(secondes: Int): ByteArray {
        val donnees = 8_000 * 2 * secondes
        return ByteBuffer.allocate(44 + donnees).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()).putInt(36 + donnees).put("WAVE".toByteArray())
            put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            putInt(8_000).putInt(8_000 * 2).putShort(2).putShort(16)
            put("data".toByteArray()).putInt(donnees)
        }.array()
    }

    private fun controleurConnecte(): Media3PlaybackController {
        val demarre = Robolectric.buildService(PlaybackService::class.java).create()
        service = demarre

        val composant = ComponentName(app, PlaybackService::class.java)
        val intent = Intent(MediaSessionService.SERVICE_INTERFACE).setComponent(composant)
        shadowOf(app).setComponentNameAndServiceForBindServiceForIntent(
            intent,
            composant,
            demarre.get().onBind(intent),
        )

        return Media3PlaybackController(app).also {
            controller = it
            it.connect()
            attendre("la liaison au service") { it.state.value.isConnected }
        }
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
     * Bornée : un état qui n'arriverait plus doit faire échouer le test, pas le
     * faire attendre indéfiniment.
     *
     * Par un nombre de tours, et non par `System.nanoTime()` : Robolectric le
     * tire de son horloge, que rien n'avance ici.
     */
    private fun attendre(quoi: String, avancerHorloge: Boolean = false, condition: () -> Boolean) {
        repeat(TOURS_MAX) {
            ecouler(avancerHorloge)
            if (condition()) return
            Thread.sleep(PAUSE_MS)
        }
        fail("Délai dépassé en attendant : $quoi")
    }

    /**
     * Écoule les messages de toutes les boucles encore vivantes : ExoPlayer
     * tient sa machine à états sur un `HandlerThread` à lui.
     *
     * L'horloge, partagée par toutes les boucles, avance d'un seul [TICK_MS]
     * par tour, sur la boucle principale : les marges des tests autour du
     * minuteur se comptent ainsi en tics, quel que soit le nombre de boucles.
     */
    private fun ecouler(avancerHorloge: Boolean) {
        if (avancerHorloge) shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(TICK_MS))
        ShadowLooper.getAllLoopers()
            .filter { it.thread.isAlive }
            .forEach { boucle ->
                try {
                    shadowOf(boucle).idle()
                } catch (arretee: IllegalStateException) {
                    // `getAllLoopers` ramasse aussi les boucles des tests
                    // précédents, dont les fils s'arrêtent : « Looper is
                    // quitting ». Toute autre erreur — un rappel du lecteur, une
                    // écriture — doit faire échouer le test.
                    if (arretee.message?.contains("quit", ignoreCase = true) != true) throw arretee
                }
            }
    }

    private companion object {
        /** `PlaybackService.DELAI_ECOUTE_MS`. */
        const val SEUIL_MS = 20_000L

        /** Ce que la boucle principale met à traiter un geste, sur la machine chargée. */
        const val RETARD_MS = 2_000L

        const val LIMITE = 10
        const val TOURS_APRES_GESTE = 20

        /** Quinze secondes au moins, chaque tour dormant [PAUSE_MS]. */
        const val TOURS_MAX = 3_000
        const val PAUSE_MS = 5L
        const val TICK_MS = 100L
    }
}
