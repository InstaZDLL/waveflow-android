package app.waveflow.playback

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Ce que le service a fait de lui-même et que l'écran doit dire. */
enum class PlaybackNotice {
    /**
     * Le serveur a refusé le transcodage jusqu'au bout : la piste se joue en
     * qualité d'origine. Voir `docs/deplacement-dans-un-transcodage.md`, R22.
     */
    OriginalAfterRefusal,
}

/**
 * Les annonces du service à l'écran, quand la lecture continue.
 *
 * Une panne qui arrête la lecture passe par l'état du lecteur, que l'écran lit
 * à travers la session. Un changement que le service fait sans s'arrêter n'y
 * laisse aucune trace : il faut le dire au moment où il arrive. Portées par
 * l'application, comme [SleepTimer] et [AbLoop], que le service et l'écran
 * partagent dans le même processus.
 *
 * Sans rejeu : une annonce faite écran fermé est perdue, comme le serait le
 * message qu'elle porte. Le tampon d'une place laisse `tryEmit` réussir sans
 * suspendre le fil du lecteur, d'où l'on annonce.
 */
class PlaybackNotices {

    private val _notices = MutableSharedFlow<PlaybackNotice>(extraBufferCapacity = 1)
    val notices: SharedFlow<PlaybackNotice> = _notices.asSharedFlow()

    fun announce(notice: PlaybackNotice) {
        _notices.tryEmit(notice)
    }
}
