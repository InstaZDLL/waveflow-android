package app.waveflow.playback

import app.waveflow.model.RemoteSong
import app.waveflow.model.Song
import kotlinx.coroutines.flow.StateFlow

/** Mode de répétition, indépendant des constantes Media3. */
enum class RepeatMode {
    /** La file se termine après le dernier morceau. */
    Off,

    /** La file reboucle au début. */
    All,

    /** Le morceau courant se répète. */
    One,
}

/**
 * État de lecture observable, projeté depuis le lecteur Media3.
 *
 * @property isConnected `true` une fois la liaison au service établie ; tant
 *   qu'il est `false`, les commandes sont ignorées.
 * @property current morceau courant, `null` si la file est vide. Décrit par ce
 *   que le lecteur en sait : il peut venir de l'appareil comme d'un serveur.
 * @property isPlaying lecture réellement en cours (pas seulement demandée).
 * @property positionMs position de lecture en millisecondes.
 * @property durationMs durée du morceau courant, 0 si inconnue.
 * @property shuffleEnabled lecture aléatoire active.
 * @property repeatMode mode de répétition courant.
 */
data class PlaybackState(
    val isConnected: Boolean = false,
    val current: PlayingTrack? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
)

/**
 * Façade de la lecture audio.
 *
 * Concentre tout ce qui touche à Media3 — connexion au service, possession du
 * `MediaController`, traduction des callbacks en [StateFlow] — pour que les
 * ViewModels restent de simples orchestrateurs.
 */
interface PlaybackController {

    val state: StateFlow<PlaybackState>

    /** Établit la liaison avec le service de lecture. Idempotent. */
    fun connect()

    /** Charge [songs] comme file d'attente et démarre à [startIndex]. */
    fun play(songs: List<Song>, startIndex: Int)

    /**
     * Même chose pour des morceaux du serveur.
     *
     * File distincte plutôt que mêlée à la locale : les deux sources sont
     * séparées partout ailleurs dans l'app, et rien ne permet de dire qu'une
     * piste distante et une piste locale sont le même enregistrement.
     */
    fun playRemote(songs: List<RemoteSong>, startIndex: Int)

    /**
     * Charge [songs] en activant la lecture aléatoire et démarre sur un
     * morceau au hasard.
     */
    fun playShuffled(songs: List<Song>)

    fun playPause()

    fun skipNext()

    fun skipPrevious()

    fun seekTo(positionMs: Long)

    fun toggleShuffle()

    /** Fait tourner le mode de répétition : Off -> All -> One -> Off. */
    fun cycleRepeatMode()

    /** Libère le contrôleur ; le service, lui, continue de jouer. */
    fun release()
}
