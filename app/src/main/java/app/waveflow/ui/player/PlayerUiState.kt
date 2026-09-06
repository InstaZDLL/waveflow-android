package app.waveflow.ui.player

import app.waveflow.playback.PlayingTrack
import app.waveflow.playback.RepeatMode

/**
 * État du lecteur, partagé par le mini-player et l'écran plein écran.
 *
 * @property track morceau courant, `null` quand rien n'est chargé — dans ce cas
 *   le lecteur ne s'affiche pas du tout. Décrit par le lecteur lui-même et non
 *   résolu dans la bibliothèque : il peut venir d'un serveur.
 * @property sleepTimerRemainingMs ce qu'il reste avant l'arrêt automatique,
 *   `null` si aucune minuterie ne court. Relevé à chaque tic de position, donc
 *   rafraîchi tant que la lecture avance ; en pause il se fige, alors que la
 *   minuterie, elle, continue de courir — c'est une heure de coucher, pas un
 *   quota d'écoute.
 */
data class PlayerUiState(
    val track: PlayingTrack? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val queue: List<PlayingTrack> = emptyList(),
    val queueIndex: Int = -1,
    val sleepTimerRemainingMs: Long? = null,
) {
    /** Une minuterie court : l'écran l'indique sans avoir à lire le décompte. */
    val sleepTimerActive: Boolean
        get() = sleepTimerRemainingMs != null

    /** Ce qui reste à jouer après le morceau courant. */
    val upNextCount: Int
        get() = (queue.size - queueIndex - 1).coerceAtLeast(0)

    /** Avancement dans le morceau, entre 0 et 1 (0 si la durée est inconnue). */
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}
