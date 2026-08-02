package app.waveflow.playback

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

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

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
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

        mediaSession = MediaSession.Builder(this, player).build()
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
        super.onDestroy()
    }
}
