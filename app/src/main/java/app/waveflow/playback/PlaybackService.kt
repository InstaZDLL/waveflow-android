package app.waveflow.playback

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.waveflow.WaveFlowApp
import coil.imageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Service de lecture porté par Media3.
 *
 * Un [MediaSessionService] héberge un [ExoPlayer] + une [MediaSession] : Android
 * en tire automatiquement la notification média, les contrôles de l'écran de
 * verrouillage et la lecture en arrière-plan. L'UI se connecte via un
 * `MediaController` (voir [PlaybackController]) — elle ne parle jamais
 * directement à l'ExoPlayer.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    /**
     * Portée des chargements de pochette : ils n'ont plus de destinataire une
     * fois la session détruite.
     */
    private val artworkScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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

        mediaSession = MediaSession.Builder(this, player)
            .setBitmapLoader(bitmapLoader)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

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
}
