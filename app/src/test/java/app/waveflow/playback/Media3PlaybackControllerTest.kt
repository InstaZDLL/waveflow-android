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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.ShadowLooper
import java.time.Duration

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
        // Les gardes `controller ?: return` ne sont pas décoratives : l'UI peut
        // appeler une commande pendant que la liaison s'établit encore.
        val controleur = Media3PlaybackController(app).also { controller = it }

        controleur.play(listOf(song(1)), startIndex = 0)
        controleur.playPause()
        controleur.skipNext()
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
        reposer()

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
        reposer()

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
        reposer()

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
        reposer()
        assertTrue("L'aléatoire n'a pas été activé", controleur.state.value.shuffleEnabled)

        controleur.play(listOf(song(1), song(2)), startIndex = 0)
        reposer()

        assertFalse(controleur.state.value.shuffleEnabled)
        assertEquals("local:1", controleur.state.value.current?.mediaId)
    }

    @Test
    fun `la lecture aleatoire s'annonce dans l'etat`() {
        val controleur = controleurConnecte()

        controleur.playShuffled(listOf(song(1), song(2), song(3)))
        reposer()

        assertTrue(controleur.state.value.shuffleEnabled)
    }

    @Test
    fun `le mode de repetition tourne sur trois positions puis reboucle`() {
        val controleur = controleurConnecte()
        assertEquals(RepeatMode.Off, controleur.state.value.repeatMode)

        val parcours = List(3) {
            controleur.cycleRepeatMode()
            reposer()
            controleur.state.value.repeatMode
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
        val controleur = controleurConnecte()

        controleur.playRemote(listOf(remoteSong("uuid-1")), startIndex = 0)
        reposer()

        assertTrue(controleur.state.value.isBuffering)
        assertFalse(controleur.state.value.isPlaying)
    }

    @Test
    fun `un morceau que le lecteur n'ouvre pas remonte comme illisible`() {
        // Le `content://` de la fixture ne résout vers aucun fichier : c'est la
        // piste qui est en cause, pas la liaison, et l'écran doit le dire ainsi.
        val controleur = controleurConnecte()

        controleur.play(listOf(song(1)), startIndex = 0)
        reposerJusquALaPanne()

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
        reposer()
        assertNotNull(controleur.state.value.current)

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
            reposer()
            assertTrue("La liaison au service n'a pas abouti", it.state.value.isConnected)
        }
    }

    /**
     * Écoule les messages en attente sans avancer l'horloge.
     *
     * Le lecteur atteint ainsi son état de départ — file posée, tampon en
     * cours — sans que sa machine à états aille jusqu'à renoncer.
     */
    private fun reposer(tours: Int = 30) = repeat(tours) {
        bouclesVivantes().forEach { boucle -> runCatching { shadowOf(boucle).idle() } }
        Thread.sleep(IDLE_PAUSE_MS)
    }

    /**
     * Avance l'horloge de toutes les boucles jusqu'à ce que le lecteur renonce.
     *
     * Le chargement échoue sur un fil bien réel, mais la machine à états du
     * lecteur vit sur une boucle que Robolectric fige : sans avancer son
     * horloge, l'erreur ne remonterait jamais.
     */
    private fun reposerJusquALaPanne(controleur: Media3PlaybackController? = controller) {
        repeat(TOURS_MAX) {
            bouclesVivantes().forEach { boucle ->
                runCatching { shadowOf(boucle).idleFor(Duration.ofMillis(TICK_MS)) }
            }
            Thread.sleep(IDLE_PAUSE_MS)
            if (controleur?.state?.value?.failure != null) return
        }
    }

    /**
     * Les boucles de messages qu'on peut encore faire tourner.
     *
     * `getAllLoopers` ramasse aussi celles des tests précédents : leurs fils
     * s'arrêtent, et les solliciter lève « Looper is quitting ». Le filtre
     * écarte le gros du lot, le `runCatching` couvre la course qui reste.
     */
    private fun bouclesVivantes() =
        ShadowLooper.getAllLoopers().filter { it.thread.isAlive }

    private companion object {
        const val IDLE_PAUSE_MS = 5L
        const val TICK_MS = 200L
        const val TOURS_MAX = 200
    }
}
