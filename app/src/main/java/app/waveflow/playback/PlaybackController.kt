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
 * Ce qui a empêché la lecture, quand elle s'est arrêtée sans reprendre.
 *
 * Media3 distingue une trentaine de codes d'erreur ; l'écran n'a besoin que de
 * savoir s'il faut incriminer la liaison ou la piste, car ce n'est pas la même
 * chose à dire ni le même geste à suggérer.
 */
enum class PlaybackFailure {
    /** Le serveur n'a pas répondu : hors ligne, ou ticket impossible à obtenir. */
    Unreachable,

    /** La piste elle-même n'a pu être ni ouverte ni décodée. */
    Unplayable,
}

/**
 * État de lecture observable, projeté depuis le lecteur Media3.
 *
 * @property isConnected `true` une fois la liaison au service établie ; tant
 *   qu'il est `false`, les commandes sont ignorées.
 * @property current morceau courant, `null` si la file est vide. Décrit par ce
 *   que le lecteur en sait : il peut venir de l'appareil comme d'un serveur.
 * @property isPlaying lecture réellement en cours (pas seulement demandée).
 * @property isBuffering le lecteur travaille sans encore produire de son. Pour
 *   une piste distante, cela couvre l'obtention du ticket, qui précède toute
 *   requête de diffusion — c'est là que se joue l'essentiel de l'attente.
 * @property positionMs position de lecture en millisecondes.
 * @property durationMs durée du morceau courant, 0 si inconnue.
 * @property shuffleEnabled lecture aléatoire active.
 * @property repeatMode mode de répétition courant.
 * @property failure panne en cours, `null` tant que le lecteur va bien. Elle
 *   s'efface d'elle-même à la reprise : Media3 oublie son erreur dès qu'on le
 *   prépare à nouveau.
 */
data class PlaybackState(
    val isConnected: Boolean = false,
    val current: PlayingTrack? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val failure: PlaybackFailure? = null,
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

    /** Même chose pour des morceaux du serveur. */
    fun playRemoteShuffled(songs: List<RemoteSong>)

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
