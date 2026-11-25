package app.waveflow.playback

import android.Manifest
import android.app.Application
import android.app.SearchManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import app.waveflow.MainActivity
import app.waveflow.model.VoiceFocus
import app.waveflow.model.VoiceRequest
import app.waveflow.testing.FauxMediaStore
import app.waveflow.testing.song
import com.google.common.util.concurrent.ListenableFuture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * La recherche vocale, vue par un vrai `MediaController` lié au vrai
 * [PlaybackService] — les règles sont dans `docs/recherche-vocale.md`.
 *
 * La demande prend la forme que Media3 donne à `onPlayFromSearch` (R3) : un
 * élément sans identifiant ni URI, qui porte la phrase. Elle traverse donc la
 * session jusqu'à `BrowseCallback`, comme celle d'Android Auto.
 *
 * La bibliothèque de l'appareil vient de [FauxMediaStore] : sous Robolectric,
 * le MediaStore resterait vide, et la recherche n'aurait rien à trouver.
 */
@RunWith(RobolectricTestRunner::class)
class PlaybackServiceVoiceSearchTest {

    /** Pour toucher l'interface de [MainActivity] : sans effet sur les autres tests. */
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    private var service: ServiceController<PlaybackService>? = null
    private val controleurs = mutableListOf<MediaController>()
    private var lecture: Media3PlaybackController? = null
    private val activites = mutableListOf<ActivityController<MainActivity>>()

    @Before
    fun setUp() {
        FauxMediaStore.installer(BIBLIOTHEQUE)
    }

    @After
    fun tearDown() {
        activites.forEach { it.pause().stop().destroy() }
        lecture?.release()
        controleurs.forEach(MediaController::release)
        service?.destroy()
        FauxMediaStore.vider()
    }

    @Test
    fun `une demande vocale joue l'album nomme`() {
        demarrerLeService()
        val controleur = controleurConnecte()

        controleur.setMediaItem(VoiceRequest("Nevermind").toSearchItem())

        attendre("l'album dans la file") { idsDe(controleur) == ALBUM_NEVERMIND }
    }

    @Test
    fun `les complements de l'hote traversent la session`() {
        // La phrase entière ne trouve rien : seul le nom d'album isolé par
        // l'hôte, transmis dans les compléments, mène à l'album.
        demarrerLeService()
        val controleur = controleurConnecte()

        controleur.setMediaItem(
            VoiceRequest("joue Mezzanine", VoiceFocus.Album, album = "Mezzanine").toSearchItem(),
        )

        attendre("l'album nommé par l'hôte dans la file") {
            idsDe(controleur) == listOf(song(7L).toMediaItem().mediaId)
        }
    }

    @Test
    fun `une demande sans resultat laisse la file en place`() {
        // R9 : rendre une liste vide viderait le lecteur.
        demarrerLeService()
        val controleur = controleurConnecte()
        controleur.setMediaItems(listOf(song(7L).toMediaItem()))
        attendre("la file de départ") { idsDe(controleur) == listOf(song(7L).toMediaItem().mediaId) }

        controleur.setMediaItem(VoiceRequest("Bohemian Rhapsody").toSearchItem())
        repeat(ECOULEMENTS) {
            ecouler()
            Thread.sleep(PAUSE_MS)
        }

        // Un second contrôleur lit l'état de la session elle-même, sans le
        // masquage que le premier applique à ses propres demandes.
        assertEquals(listOf(song(7L).toMediaItem().mediaId), idsDe(controleurConnecte()))
    }

    @Test
    fun `une demande vocale attend la premiere lecture de la bibliotheque`() {
        // R11 : au démarrage à froid, la liaison qui porte la demande est
        // aussi celle qui crée le service ; le MediaStore n'a pas encore
        // répondu.
        FauxMediaStore.retenir()
        demarrerLeService(attendreLaBibliotheque = false)
        val controleur = controleurConnecte()

        controleur.setMediaItem(VoiceRequest("Nevermind").toSearchItem())
        repeat(ECOULEMENTS) {
            ecouler()
            Thread.sleep(PAUSE_MS)
        }
        FauxMediaStore.liberer()

        attendre("l'album dans la file, une fois la bibliothèque lue") { idsDe(controleur) == ALBUM_NEVERMIND }
    }

    @Test
    fun `une demande du telephone arrivee avant la liaison part a la connexion et joue`() {
        // R10 : l'Assistant lance l'activité à froid, et la demande arrive
        // avant que le contrôleur de l'application ne soit lié au service.
        demarrerLeService()
        val lecture = Media3PlaybackController(app).also { lecture = it }

        lecture.playFromSearch(VoiceRequest("Nevermind"))
        lecture.connect()

        attendre("l'album dans la file de l'application") {
            lecture.state.value.queue.map { it.mediaId } == ALBUM_NEVERMIND
        }
        // Robolectric ne décode rien : `isPlaying` reste faux, la demande de
        // lecture se lit sur `playWhenReady`.
        attendre("la lecture demandée") { controleurConnecte().playWhenReady }
    }

    @Test
    fun `une demande du telephone sans resultat ne relance pas une file en pause`() {
        // R9 : la file reste, et son état de lecture aussi. Envoyer `play`
        // après un `setMediaItem` refusé relancerait la piste en pause.
        demarrerLeService()
        val controleur = controleurConnecte()
        controleur.setMediaItems(listOf(song(7L).toMediaItem()))
        controleur.prepare()
        attendre("la file de départ") { idsDe(controleur) == listOf(song(7L).toMediaItem().mediaId) }
        val lecture = Media3PlaybackController(app).also { lecture = it }
        lecture.connect()
        attendre("la liaison de l'application") { lecture.state.value.isConnected }

        lecture.playFromSearch(VoiceRequest("Bohemian Rhapsody"))
        repeat(ECOULEMENTS) {
            ecouler()
            Thread.sleep(PAUSE_MS)
        }

        val temoin = controleurConnecte()
        assertEquals(listOf(song(7L).toMediaItem().mediaId), idsDe(temoin))
        assertFalse("la file en pause a été relancée", temoin.playWhenReady)
    }

    @Test
    fun `l'activite relaie au service la demande de l'Assistant`() {
        // R10, du lancement de l'activité jusqu'à la file de la session.
        demarrerLeService()
        val temoin = controleurConnecte()

        lancerLActivite(intentionVocale())

        attendre("l'album dans la file") { idsDe(temoin) == ALBUM_NEVERMIND }
    }

    @Test
    fun `l'activite deja lancee relaie une nouvelle demande`() {
        demarrerLeService()
        val temoin = controleurConnecte()
        val activite = lancerLActivite(Intent(Intent.ACTION_MAIN))

        recevoir(activite, intentionVocale())

        attendre("l'album dans la file") { idsDe(temoin) == ALBUM_NEVERMIND }
    }

    @Test
    fun `une demande recue hors des ecrans gardes lance elle-meme la liaison`() {
        // Seuls les écrans gardés par la permission audio lient le service.
        // Sans elle, l'accueil ne le lie pas ; l'utilisateur passe aux
        // Réglages, qui ne sont pas gardés, puis accorde la permission.
        demarrerLeService()
        val temoin = controleurConnecte()
        val activite = Robolectric.buildActivity(MainActivity::class.java, Intent(Intent.ACTION_MAIN))
            .also { activites += it }
            .setup()
        compose.onNodeWithContentDescription("Réglages").performClick()
        compose.waitForIdle()
        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO)

        recevoir(activite, intentionVocale())

        attendre("l'album dans la file") { idsDe(temoin) == ALBUM_NEVERMIND }
    }

    @Test
    fun `sans la permission audio la demande attend qu'elle soit accordee`() {
        // Le faux MediaStore répond même sans permission : si la demande liait
        // le service, l'album serait joué.
        demarrerLeService()
        val temoin = controleurConnecte()
        val activite = Robolectric.buildActivity(MainActivity::class.java, intentionVocale())
            .also { activites += it }
            .setup()
        repeat(ECOULEMENTS) {
            ecouler()
            Thread.sleep(PAUSE_MS)
        }
        assertEquals(emptyList<String>(), idsDe(temoin))

        // L'accueil, gardé, relit la permission à chaque retour au premier
        // plan — ici des paramètres système —, et lie alors le service.
        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO)
        activite.pause().stop().restart().resume()
        compose.waitForIdle()

        attendre("l'album dans la file, la permission accordée") { idsDe(temoin) == ALBUM_NEVERMIND }
    }

    @Test
    fun `la permission accordee au retour sur un ecran non garde lance la liaison`() {
        // La demande arrive sans la permission ; l'utilisateur passe aux
        // Réglages, l'accorde dans les paramètres système et revient : aucun
        // écran gardé n'est composé pour lier le service.
        demarrerLeService()
        val temoin = controleurConnecte()
        val activite = Robolectric.buildActivity(MainActivity::class.java, intentionVocale())
            .also { activites += it }
            .setup()
        compose.onNodeWithContentDescription("Réglages").performClick()
        compose.waitForIdle()

        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO)
        activite.pause().stop().restart().resume()

        attendre("l'album dans la file") { idsDe(temoin) == ALBUM_NEVERMIND }
    }

    @Test
    fun `la permission accordee en ecran partage apres une recreation lance la liaison`() {
        // Recréée, l'activité ne rejoue pas l'intention : la demande attend
        // dans le contrôleur. En écran partagé, l'activité reste visible
        // pendant que l'utilisateur accorde la permission : elle n'est que
        // reprise, pas redémarrée.
        demarrerLeService()
        val temoin = controleurConnecte()
        val activite = Robolectric.buildActivity(MainActivity::class.java, intentionVocale())
            .also { activites += it }
            .setup()
        compose.onNodeWithContentDescription("Réglages").performClick()
        compose.waitForIdle()
        activite.recreate()
        compose.waitForIdle()

        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO)
        activite.pause().resume()

        attendre("l'album dans la file") { idsDe(temoin) == ALBUM_NEVERMIND }
    }

    @Test
    fun `l'activite ne rejoue pas une demande deja relayee`() {
        // Recréée, ou rouverte depuis les récents, l'activité reçoit encore
        // l'intention qui l'a lancée : la file posée depuis doit rester.
        demarrerLeService()
        val temoin = controleurConnecte()
        temoin.setMediaItems(listOf(song(7L).toMediaItem()))
        attendre("la file de départ") { idsDe(temoin) == listOf(song(7L).toMediaItem().mediaId) }

        lancerLActivite(intentionVocale(), etatSauve = Bundle())
        lancerLActivite(intentionVocale().addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
        repeat(ECOULEMENTS) {
            ecouler()
            Thread.sleep(PAUSE_MS)
        }

        assertEquals(listOf(song(7L).toMediaItem().mediaId), idsDe(temoin))
    }

    /**
     * Une intention reçue par l'activité déjà lancée. Android la met en pause
     * avant `onNewIntent` et la reprend après ; `ActivityController.newIntent`
     * ne le fait pas de lui-même.
     */
    private fun recevoir(activite: ActivityController<MainActivity>, intention: Intent) {
        activite.pause().newIntent(intention).resume()
    }

    private fun intentionVocale(): Intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
        .setClass(app, MainActivity::class.java)
        .putExtra(SearchManager.QUERY, "Nevermind")

    /**
     * L'activité telle que l'Assistant la lance, la permission audio accordée :
     * sans elle, l'application ne se lie pas au service. Avec [etatSauve],
     * c'est une activité recréée.
     */
    private fun lancerLActivite(intention: Intent, etatSauve: Bundle? = null): ActivityController<MainActivity> {
        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO)
        val activite = Robolectric.buildActivity(MainActivity::class.java, intention).also { activites += it }
        if (etatSauve == null) activite.setup() else activite.setup(etatSauve)
        return activite
    }

    /**
     * Le service tel qu'Android le crée, et par défaut une fois la bibliothèque
     * lue : seul le test du démarrage à froid éprouve la demande arrivée avant.
     */
    private fun demarrerLeService(attendreLaBibliotheque: Boolean = true) {
        val demarre = Robolectric.buildService(PlaybackService::class.java).create()
        service = demarre

        // Robolectric ne démarre pas de vrai service sur `bindService` : on lui
        // donne le `Binder` que le service rend lui-même.
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

        if (!attendreLaBibliotheque) return

        val navigateur = MediaBrowser.Builder(app, SessionToken(app, composant))
            .buildAsync()
            .attendu("la liaison du navigateur")
            .also { controleurs += it }
        attendre("la bibliothèque lue par le service") {
            navigateur.getChildren(TOUTES_LES_PISTES, 0, Int.MAX_VALUE, null)
                .attendu("les pistes")
                .value
                ?.size == BIBLIOTHEQUE.size
        }
    }

    private fun controleurConnecte(): MediaController {
        val composant = ComponentName(app, PlaybackService::class.java)
        return MediaController.Builder(app, SessionToken(app, composant))
            .buildAsync()
            .attendu("la liaison au service")
            .also { controleurs += it }
    }

    private fun idsDe(controleur: MediaController): List<String> =
        (0 until controleur.mediaItemCount).map { controleur.getMediaItemAt(it).mediaId }

    /**
     * Attend un futur en écoulant les messages entre deux essais : un `get()`
     * bloquant figerait la boucle principale, dont dépend la réponse.
     */
    private fun <T> ListenableFuture<T>.attendu(quoi: String): T {
        attendre(quoi) { isDone }
        return get()
    }

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
     * tient sa machine à états sur un `HandlerThread` à lui.
     */
    private fun ecouler() {
        ShadowLooper.getAllLoopers()
            .filter { it.thread.isAlive }
            .forEach { boucle -> runCatching { shadowOf(boucle).idle() } }
    }

    private companion object {
        const val TIMEOUT_S = 15L
        const val PAUSE_MS = 5L

        /** Une demi-seconde d'écoulement, pour laisser la session répondre. */
        const val ECOULEMENTS = 100

        const val TOUTES_LES_PISTES = "${BrowseTree.BROWSE_PREFIX}songs"

        /** Dans l'ordre du MediaStore, par titre. */
        val BIBLIOTHEQUE = listOf(
            song(1L, "Come As You Are", artist = "Nirvana", artistId = 10L, album = "Nevermind", albumId = 100L),
            song(2L, "Lithium", artist = "Nirvana", artistId = 10L, album = "Nevermind", albumId = 100L),
            song(3L, "Nevermind", artist = "Tribute Band", artistId = 20L, album = "Covers", albumId = 200L),
            song(7L, "Teardrop", artist = "Massive Attack", artistId = 70L, album = "Mezzanine", albumId = 700L),
        )

        val ALBUM_NEVERMIND = listOf(1L, 2L).map { song(it).toMediaItem().mediaId }
    }
}
