package app.waveflow.playback

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import app.waveflow.WaveFlowApp
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
 * Ce qu'un navigateur déjà connecté apprend des changements de la bibliothèque.
 *
 * Le cas est celui de la voiture : Android Auto se lie au service au démarrage,
 * bien avant que la bibliothèque de l'appareil ne soit lue, et n'a aucune raison
 * de redemander quoi que ce soit ensuite. Si personne ne le prévient, il reste
 * devant l'arbre qu'il a vu la première fois — vide.
 *
 * Rien n'est simulé : un vrai [PlaybackService], un vrai `MediaBrowser` abonné,
 * et un changement provoqué par le chemin ordinaire de l'application — la
 * création d'une playlist, qui fait apparaître une section dans la racine. Le
 * MediaStore n'est pas mobilisé : il resterait vide sous Robolectric, alors que
 * les playlists passent par une base dont l'application est propriétaire.
 */
@RunWith(RobolectricTestRunner::class)
class PlaybackServiceBrowseTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    private var service: ServiceController<PlaybackService>? = null
    private var browser: MediaBrowser? = null

    @After
    fun tearDown() {
        browser?.release()
        service?.destroy()
    }

    @Test
    fun `un navigateur abonne est prevenu quand l'arbre change`() {
        val changements = mutableListOf<String>()
        val navigateur = navigateurConnecte { parentId -> changements += parentId }

        navigateur.subscribe(BrowseTree.ROOT_ID, null).attendu("l'abonnement à la racine")
        // L'état de départ : la bibliothèque de l'appareil est vide et aucune
        // playlist n'existe, la section n'a donc pas lieu d'être.
        assertEquals(
            listOf("Albums", "Artistes", "Toutes les pistes"),
            titresDesEnfants(navigateur),
        )
        changements.clear()

        creerPlaylist("Sur la route")

        attendre("la notification du navigateur") { BrowseTree.ROOT_ID in changements }
        assertEquals(
            listOf("Albums", "Artistes", "Playlists", "Toutes les pistes"),
            titresDesEnfants(navigateur),
        )
    }

    /** Le service tel qu'Android le crée, et un navigateur qui s'y lie. */
    private fun navigateurConnecte(onChildrenChanged: (String) -> Unit): MediaBrowser {
        val demarre = Robolectric.buildService(PlaybackService::class.java).create()
        service = demarre

        // Robolectric ne démarre pas de vrai service sur `bindService` : on lui
        // donne le `Binder` que le service rend lui-même. Les deux actions parce
        // que le navigateur choisit la sienne d'après ce que le manifeste
        // déclare, et que le manifeste les déclare toutes les deux.
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

        val ecouteur = object : MediaBrowser.Listener {
            override fun onChildrenChanged(
                browser: MediaBrowser,
                parentId: String,
                itemCount: Int,
                params: MediaLibraryService.LibraryParams?,
            ) = onChildrenChanged(parentId)
        }

        return MediaBrowser.Builder(app, SessionToken(app, composant))
            .setListener(ecouteur)
            .buildAsync()
            .attendu("la liaison au service")
            .also { browser = it }
    }

    private fun titresDesEnfants(navigateur: MediaBrowser): List<String> =
        navigateur.getChildren(BrowseTree.ROOT_ID, 0, Int.MAX_VALUE, null)
            .attendu("les enfants de la racine")
            .value
            .orEmpty()
            .map { it.mediaMetadata.title.toString() }

    /**
     * Crée une playlist par le chemin de l'application.
     *
     * Le service observe le même dépôt : l'écriture fait donc émettre le flux
     * qu'il collecte, exactement comme un ajout depuis l'écran des playlists.
     */
    private fun creerPlaylist(nom: String) {
        runBlocking { (app as WaveFlowApp).container.playlistRepository.create(nom) }
    }

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
     * Écoule les messages de toutes les boucles encore vivantes.
     *
     * Toutes, et pas seulement la principale : ExoPlayer tient sa machine à
     * états sur un `HandlerThread` à lui.
     */
    private fun ecouler() {
        ShadowLooper.getAllLoopers()
            .filter { it.thread.isAlive }
            // `getAllLoopers` ramasse aussi les boucles des tests précédents,
            // dont les fils s'arrêtent — « Looper is quitting ».
            .forEach { boucle -> runCatching { shadowOf(boucle).idle() } }
    }

    private companion object {
        const val TIMEOUT_S = 15L
        const val PAUSE_MS = 5L
    }
}
