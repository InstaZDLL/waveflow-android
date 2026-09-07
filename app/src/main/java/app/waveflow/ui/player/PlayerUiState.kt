package app.waveflow.ui.player

import app.waveflow.model.PlaybackSpeed
import app.waveflow.playback.PlayingTrack
import app.waveflow.playback.RepeatMode

/**
 * État du lecteur, partagé par le mini-player et l'écran plein écran.
 *
 * @property track morceau courant, `null` quand rien n'est chargé — dans ce cas
 *   le lecteur ne s'affiche pas du tout. Décrit par le lecteur lui-même et non
 *   résolu dans la bibliothèque : il peut venir d'un serveur.
 * @property sleepTimerActive une minuterie de veille court. Un booléen et non le
 *   temps restant : l'état n'est reconstruit qu'aux tics de position, donc plus
 *   du tout quand la lecture est en pause, alors que la minuterie continue de
 *   courir. Le décompte se demande à la minuterie au moment de l'afficher — ce
 *   que fait la feuille de réglage, seul endroit où quelqu'un le lit.
 * @property playbackSpeed la vitesse de lecture. Elle vient des préférences et
 *   non du lecteur : le flux des préférences émet au moment du choix, quand
 *   celui du lecteur, lui, ne se rafraîchit qu'aux tics de position — donc plus
 *   du tout en pause, où l'on règle pourtant volontiers sa vitesse.
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
    val sleepTimerActive: Boolean = false,
    val playbackSpeed: Float = PlaybackSpeed.NORMALE,
) {

    /** Ce qui reste à jouer après le morceau courant. */
    val upNextCount: Int
        get() = (queue.size - queueIndex - 1).coerceAtLeast(0)

    /** Avancement dans le morceau, entre 0 et 1 (0 si la durée est inconnue). */
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}
