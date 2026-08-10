package app.waveflow.ui.player

import app.waveflow.playback.PlayingTrack
import app.waveflow.playback.RepeatMode

/**
 * État du lecteur, partagé par le mini-player et l'écran plein écran.
 *
 * @property track morceau courant, `null` quand rien n'est chargé — dans ce cas
 *   le lecteur ne s'affiche pas du tout. Décrit par le lecteur lui-même et non
 *   résolu dans la bibliothèque : il peut venir d'un serveur.
 */
data class PlayerUiState(
    val track: PlayingTrack? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
) {
    /** Avancement dans le morceau, entre 0 et 1 (0 si la durée est inconnue). */
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}
