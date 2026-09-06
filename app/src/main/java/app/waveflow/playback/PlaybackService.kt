package app.waveflow.playback

import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import app.waveflow.WaveFlowApp
import app.waveflow.data.PlayHistoryRepository
import coil.imageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Service de lecture porté par Media3.
 *
 * Un [MediaLibraryService] héberge un [ExoPlayer] + une [MediaLibrarySession] :
 * Android en tire automatiquement la notification média, les contrôles de
 * l'écran de verrouillage et la lecture en arrière-plan. L'UI se connecte via un
 * `MediaController` (voir [PlaybackController]) — elle ne parle jamais
 * directement à l'ExoPlayer.
 *
 * C'est un [MediaLibraryService] et non un simple `MediaSessionService` parce
 * qu'Android Auto ne se contente pas de commander la lecture : il veut parcourir
 * la bibliothèque. Cette différence tient à l'arbre exposé par [BrowseTree] ;
 * pour l'application, rien ne change — un `MediaLibrarySession` est une
 * `MediaSession`.
 */
class PlaybackService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null

    /**
     * Portée des chargements de pochette : ils n'ont plus de destinataire une
     * fois la session détruite.
     */
    private val artworkScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Ce que l'arbre de navigation donne à voir, tenu à jour en continu.
     *
     * `@Volatile` parce qu'il est écrit par la coroutine qui observe et lu par
     * le fil d'où l'hôte pose ses questions. L'instantané est remplacé d'un
     * bloc, jamais modifié en place : un lecteur voit donc toujours un état
     * cohérent, fût-il d'un instant plus tôt.
     */
    @Volatile
    private var browseSnapshot = BrowseSnapshot()

    /**
     * Ce que la session répond aux hôtes qui parcourent la bibliothèque.
     *
     * Tenu ici, et non construit au vol dans le constructeur de la session,
     * parce qu'il faut pouvoir lui redemander de prévenir ses abonnés à chaque
     * fois que [browseSnapshot] change.
     */
    private val browseCallback = BrowseCallback(BrowseTree { browseSnapshot })

    override fun onCreate() {
        super.onCreate()

        // Les pistes distantes portent un marqueur `waveflow://` que rien ne sait
        // ouvrir : un résolveur l'échange contre une URL de diffusion au moment
        // où le lecteur en a besoin, et le cache s'intercale avant lui pour
        // qu'une piste déjà lue ne redemande ni ticket ni octets.
        val container = (application as WaveFlowApp).container
        val dataSourceFactory = container.remoteMediaCache.dataSourceFactory(
            RemoteStreamResolver(container.catalogRepository),
        )

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            // Route audio "musique" + gestion du focus audio (pause si un appel
            // arrive, etc.).
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // Met en pause quand le casque est débranché.
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Sans ce chargeur, Media3 irait chercher les pochettes avec son propre
        // client HTTP, qui ne porte pas le jeton de session : `/api/v2/artwork/`
        // lui répondait 401 et la notification restait sans vignette.
        // `CacheBitmapLoader` reprend ce que Media3 fait par défaut — il évite
        // de recharger la même image à chaque rafraîchissement.
        val bitmapLoader = CacheBitmapLoader(CoilBitmapLoader(this, imageLoader, artworkScope))

        mediaSession = MediaLibrarySession.Builder(this, player, browseCallback)
            .setBitmapLoader(bitmapLoader)
            .build()

        player.addListener(historyListener(container.playHistoryRepository))
        observeSleepTimer(container.sleepTimer, player)

        // Après la session, et pas avant : la première valeur du flux arrive
        // sans délai, et elle a des abonnés à prévenir.
        observeLibrary(container)
    }

    /**
     * Met la lecture en pause quand la minuterie de veille arrive à échéance.
     *
     * Une pause et non un arrêt : on se rendort rarement pour de bon, et
     * reprendre là où l'on s'est endormi vaut mieux que de retrouver une file
     * vide. La minuterie ignore tout du lecteur — c'est le service, qui le
     * tient, qui fait le geste.
     *
     * L'abonnement vit dans [artworkScope], donc tombe avec le service. Une
     * minuterie qui expirerait après lui n'aurait de toute façon plus rien à
     * mettre en pause.
     */
    private fun observeSleepTimer(timer: SleepTimer, player: Player) {
        artworkScope.launch {
            timer.expirations.collect { player.pause() }
        }
    }

    /**
     * Note ce qu'on écoute, mais pas ce qu'on saute.
     *
     * Compté ici et non dans l'application : le service joue aussi quand aucun
     * écran n'est ouvert — en voiture, depuis la notification — et un historique
     * qui manquerait ces écoutes-là décrirait mal ce qu'on écoute vraiment.
     *
     * `elapsedRealtime` plutôt que l'heure courante : elle ne recule pas quand
     * l'horloge du téléphone est remise à l'heure, ce qui rallongerait ou
     * abrégerait une écoute en cours.
     *
     * Voir [ListeningCounter] pour ce qui distingue une écoute d'un survol.
     */
    private fun historyListener(history: PlayHistoryRepository): Player.Listener {
        val compteur = ListeningCounter(
            scope = artworkScope,
            thresholdMs = DELAI_ECOUTE_MS,
            nowMs = SystemClock::elapsedRealtime,
            onListened = history::record,
        )

        return object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                compteur.trackChanged(mediaItem?.mediaId)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                compteur.playingChanged(isPlaying)
            }
        }
    }

    /**
     * Tient [browseSnapshot] à jour tant que le service vit.
     *
     * `load()` est demandé ici parce que le service peut démarrer sans que
     * l'application ait été ouverte — Android Auto s'y connecte directement. Si
     * la permission audio manque, la bibliothèque restera vide et l'arbre le
     * sera aussi : c'est le seul comportement honnête, l'hôte n'ayant aucun
     * moyen de la demander.
     */
    private fun observeLibrary(container: app.waveflow.AppContainer) {
        container.libraryStore.load()

        artworkScope.launch {
            combine(
                container.libraryStore.library,
                container.playlistRepository.observePlaylists(),
                container.playlistRepository.observeEntries(),
            ) { library, playlists, entries ->
                BrowseSnapshot(library, playlists, entries)
            }.collect { snapshot ->
                browseSnapshot = snapshot
                // L'instantané seul ne suffit pas : un navigateur déjà connecté
                // ne redemande rien de lui-même, il attend qu'on lui dise que
                // ce qu'il affiche a changé.
                mediaSession?.let(browseCallback::notifySubscribers)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    // Si l'app est balayée depuis les récents alors que rien ne joue, on arrête
    // le service pour ne pas laisser une notification fantôme.
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        artworkScope.cancel()
        // Le cache n'est pas relâché ici : il appartient au conteneur, qui le
        // partage avec l'écran des réglages. Son verrou tombe avec le processus,
        // et le service peut redémarrer sur la même instance.
        super.onDestroy()
    }

    private companion object {
        /**
         * Vingt secondes : assez pour distinguer une écoute d'un survol, assez
         * peu pour qu'une piste courte compte quand même.
         */
        const val DELAI_ECOUTE_MS = 20_000L
    }
}
