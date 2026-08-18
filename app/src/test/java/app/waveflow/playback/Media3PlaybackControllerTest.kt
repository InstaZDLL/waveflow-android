package app.waveflow.playback

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.media3.session.MediaSessionService
import androidx.test.core.app.ApplicationProvider
import app.waveflow.testing.remoteSong
import app.waveflow.testing.song
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.ShadowLooper
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Le contrôleur, éprouvé sur la vraie chaîne Media3.
 *
 * Rien ici n'est simulé : un [PlaybackService] est créé comme Android le ferait,
 * un vrai `MediaController` s'y lie, et l'état observé est celui que
 * `syncFrom` projette depuis le lecteur. C'était jusqu'ici la plus grande zone
 * du dépôt qu'aucun test n'atteignait — neutraliser une ligne de la projection
 * ne faisait tomber personne.
 *
 * Le seul artifice est la liaison au service : Robolectric ne démarre pas de
 * vrai service sur `bindService`, on lui fournit donc le `Binder` que le
 * service rend lui-même. Le contrôleur, lui, emprunte son chemin habituel —
 * `SessionToken` déduit du `ComponentName`, connexion asynchrone comprise.
 */
@RunWith(RobolectricTestRunner::class)
class Media3PlaybackControllerTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    private var service: ServiceController<PlaybackService>? = null
    private var controller: Media3PlaybackController? = null

    @After
    fun tearDown() {
        controller?.release()
        service?.destroy()
    }

    // ------------------------------------------------------------------
    // Connexion
    // ------------------------------------------------------------------

    @Test
    fun `tant que la liaison n'est pas etablie l'etat reste vierge`() {
        val controleur = Media3PlaybackController(app).also { controller = it }

        assertFalse(controleur.state.value.isConnected)
        assertNull(controleur.state.value.current)
    }

    @Test
    fun `les commandes sont ignorees avant la connexion`() {
        // Les gardes sur `controller` ne sont pas décoratives : l'UI peut
        // appeler n'importe quelle commande pendant que la liaison s'établit
        // encore. Elles y passent toutes, faute de quoi celle qu'on oublierait
        // de garder ne se signalerait qu'à l'usage.
        val controleur = Media3PlaybackController(app).also { controller = it }

        controleur.play(listOf(song(1)), startIndex = 0)
        controleur.playRemote(listOf(remoteSong("uuid-1")), startIndex = 0)
        controleur.playShuffled(listOf(song(1), song(2)))
        controleur.playRemoteShuffled(listOf(remoteSong("uuid-1"), remoteSong("uuid-2")))
        controleur.playPause()
        controleur.skipNext()
        controleur.skipPrevious()
        controleur.seekTo(1_000L)
        controleur.toggleShuffle()
        controleur.cycleRepeatMode()

        assertEquals(PlaybackState(), controleur.state.value)
    }

    @Test
    fun `la connexion publie l'etat du lecteur`() {
        val controleur = controleurConnecte()

        assertTrue(controleur.state.value.isConnected)
    }

    // ------------------------------------------------------------------
    // Ce que le lecteur a en main
    // ------------------------------------------------------------------

    @Test
    fun `le morceau courant decrit la piste locale demandee`() {
        val controleur = controleurConnecte()

        controleur.play(listOf(song(1), song(2), song(3)), startIndex = 1)
        attendre("la file posée") { controleur.state.value.current != null }

        val courant = controleur.state.value.current
        assertNotNull(courant)
        // Le préfixe est la seule chose qui distingue un identifiant MediaStore
        // d'un UUID de serveur une fois dans la file.
        assertEquals("local:2", courant!!.mediaId)
        assertEquals(2L, courant.localSongId)
        assertEquals(TrackSource.Local, courant.source)
        assertEquals("Titre 2", courant.title)
        assertEquals("Artiste 2", courant.artist)
        assertEquals("Album 2", courant.album)
    }

    @Test
    fun `le morceau courant decrit la piste distante demandee`() {
        val controleur = controleurConnecte()

        controleur.playRemote(listOf(remoteSong("uuid-1")), startIndex = 0)
        attendre("la file posée") { controleur.state.value.current != null }

        val courant = controleur.state.value.current
        assertNotNull(courant)
        assertEquals("remote:uuid-1", courant!!.mediaId)
        // Aucun identifiant MediaStore à souligner : la piste n'est pas d'ici.
        assertNull(courant.localSongId)
        assertEquals(TrackSource.Remote, courant.source)
    }

    @Test
    fun `une duree inconnue vaut zero et non la sentinelle de Media3`() {
        // `C.TIME_UNSET` vaut Long.MIN_VALUE + 1 : laissé tel quel, il
        // traverserait jusqu'à la barre de progression.
        val controleur = controleurConnecte()

        controleur.play(listOf(song(1)), startIndex = 0)
        attendre("la file posée") { controleur.state.value.current != null }

        // Zéro est aussi la valeur d'un état neuf : sans cette première
        // assertion, le test passerait alors même que la file n'aurait jamais
        // été posée. C'est la piste chargée qui rend la seconde probante.
        assertEquals("local:1", controleur.state.value.current?.mediaId)
        assertEquals(0L, controleur.state.value.durationMs)
    }

    // ------------------------------------------------------------------
    // Aléatoire et répétition
    // ------------------------------------------------------------------

    @Test
    fun `demarrer une lecture ordonnee eteint l'aleatoire laisse par la precedente`() {
        // Sans ça, un aléatoire encore actif ferait démarrer la file ailleurs
        // que sur le morceau demandé — le bouton Lecture paraîtrait sans effet.
        val controleur = controleurConnecte()

        controleur.toggleShuffle()
        attendre("l'aléatoire actif") { controleur.state.value.shuffleEnabled }

        controleur.play(listOf(song(1), song(2)), startIndex = 0)
        attendre("la file posée") { controleur.state.value.current != null }

        assertFalse(controleur.state.value.shuffleEnabled)
        assertEquals("local:1", controleur.state.value.current?.mediaId)
    }

    @Test
    fun `la lecture aleatoire s'annonce dans l'etat`() {
        val controleur = controleurConnecte()

        controleur.playShuffled(listOf(song(1), song(2), song(3)))
        attendre("la file posée") { controleur.state.value.current != null }

        assertTrue(controleur.state.value.shuffleEnabled)
    }

    @Test
    fun `le mode de repetition tourne sur trois positions puis reboucle`() {
        val controleur = controleurConnecte()
        assertEquals(RepeatMode.Off, controleur.state.value.repeatMode)

        var precedent = RepeatMode.Off
        val parcours = List(3) {
            controleur.cycleRepeatMode()
            attendre("le mode de répétition change") {
                controleur.state.value.repeatMode != precedent
            }
            controleur.state.value.repeatMode.also { mode -> precedent = mode }
        }

        assertEquals(listOf(RepeatMode.All, RepeatMode.One, RepeatMode.Off), parcours)
    }

    // ------------------------------------------------------------------
    // Attente et panne
    // ------------------------------------------------------------------

    @Test
    fun `l'attente avant le premier son se voit`() {
        // Pour une piste distante, cette attente couvre l'obtention du ticket,
        // qui précède toute requête de diffusion. C'est là que se joue
        // l'essentiel du délai ressenti.
        //
        // Le tampon est ici la condition d'attente et non une assertion : il
        // n'apparaît pas au même instant que la file, et l'exiger d'un coup
        // rendrait le test instable. Une projection muette fait donc échouer
        // sur le délai, avec le libellé pour le dire.
        val controleur = controleurConnecte()

        controleur.playRemote(listOf(remoteSong("uuid-1")), startIndex = 0)
        attendre("le tampon avant le premier son") { controleur.state.value.isBuffering }

        assertFalse(controleur.state.value.isPlaying)
        assertNull(controleur.state.value.failure)
    }

    @Test
    fun `un morceau que le lecteur n'ouvre pas remonte comme illisible`() {
        // Le `content://` de la fixture ne résout vers aucun fichier : c'est la
        // piste qui est en cause, pas la liaison, et l'écran doit le dire ainsi.
        val controleur = controleurConnecte()

        controleur.play(listOf(song(1)), startIndex = 0)
        attendre("la panne du lecteur", avancerHorloge = true) {
            controleur.state.value.failure != null
        }

        assertEquals(PlaybackFailure.Unplayable, controleur.state.value.failure)
        assertFalse(controleur.state.value.isBuffering)
    }

    // ------------------------------------------------------------------
    // Fin de vie
    // ------------------------------------------------------------------

    @Test
    fun `relacher le controleur remet l'etat a zero`() {
        val controleur = controleurConnecte()
        controleur.play(listOf(song(1)), startIndex = 0)
        attendre("la file posée") { controleur.state.value.current != null }

        controleur.release()

        assertEquals(PlaybackState(), controleur.state.value)
    }

    // ------------------------------------------------------------------
    // Échafaudage
    // ------------------------------------------------------------------

    /** Le service tel qu'Android le crée, et un contrôleur qui s'y est lié. */
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

    /**
     * Attend qu'une condition se réalise, en écoulant les messages entre deux
     * essais.
     *
     * Un nombre de tours fixe serait un pari sur la vitesse de la machine :
     * trop court il rend le test instable, trop long il fait payer l'attente à
     * chaque exécution. La condition dit quand s'arrêter, l'échéance quand
     * renoncer — et le libellé dit ce qu'on attendait.
     *
     * @param avancerHorloge nécessaire pour que la machine à états du lecteur
     *   aille jusqu'à renoncer : son chargement échoue sur un fil bien réel,
     *   mais elle vit sur une boucle que Robolectric fige. Sans avancer le
     *   temps, l'erreur ne remonte jamais.
     */
    private fun attendre(
        quoi: String,
        avancerHorloge: Boolean = false,
        condition: () -> Boolean,
    ) {
        val echeance = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_S)
        while (true) {
            // Écouler d'abord : les messages postés pendant la pause doivent
            // être traités avant qu'on interroge l'état.
            ecouler(avancerHorloge)
            if (condition()) return
            if (System.nanoTime() >= echeance) break
            Thread.sleep(PAUSE_MS)
        }
        fail("Délai dépassé en attendant : $quoi")
    }

    /**
     * Écoule les messages de toutes les boucles encore vivantes.
     *
     * Toutes, et pas seulement la principale : ExoPlayer tient sa machine à
     * états sur un `HandlerThread` à lui.
     */
    private fun ecouler(avancerHorloge: Boolean) {
        ShadowLooper.getAllLoopers()
            .filter { it.thread.isAlive }
            .forEach { boucle ->
                // `getAllLoopers` ramasse aussi les boucles des tests
                // précédents, dont les fils s'arrêtent — « Looper is quitting ».
                // Le filtre écarte le gros du lot, ce garde-fou couvre la
                // course qui reste.
                runCatching {
                    if (avancerHorloge) {
                        shadowOf(boucle).idleFor(Duration.ofMillis(TICK_MS))
                    } else {
                        shadowOf(boucle).idle()
                    }
                }
            }
    }

    private companion object {
        const val TIMEOUT_S = 15L
        const val PAUSE_MS = 5L
        const val TICK_MS = 200L
    }
}
