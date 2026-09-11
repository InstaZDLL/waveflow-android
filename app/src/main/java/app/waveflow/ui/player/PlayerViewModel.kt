package app.waveflow.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.waveflow.WaveFlowApp
import app.waveflow.data.PreferencesStore
import app.waveflow.model.RemoteSong
import app.waveflow.model.Song
import app.waveflow.playback.AbLoop
import app.waveflow.playback.PlaybackController
import app.waveflow.playback.PlaybackFailure
import app.waveflow.playback.SleepTimer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Tout ce qui touche à la lecture : état du lecteur et commandes.
 *
 * Il possède le [PlaybackController] et le libère avec lui-même — une liaison
 * vivante empêcherait le service de s'arrêter.
 */
class PlayerViewModel(
    private val playbackController: PlaybackController,
    private val sleepTimer: SleepTimer,
    private val preferencesStore: PreferencesStore,
    private val abLoop: AbLoop,
) : ViewModel() {

    // Plus de croisement avec la bibliothèque : le lecteur décrit lui-même sa
    // piste, ce qui vaut aussi pour celles du serveur, absentes du MediaStore.
    // Cette projection est réévaluée à chaque tic de position — la garder sans
    // recherche est ce qui la rend gratuite.
    //
    // La minuterie s'y joint plutôt que d'être un flux à part : son décompte
    // n'a de sens qu'à côté du reste, et les tics de position le rafraîchissent
    // sans qu'elle ait à entretenir une horloge pour l'affichage.
    //
    // La vitesse aussi, et c'est ce qui la rend visible : elle se règle
    // volontiers en pause, quand le lecteur n'émet plus rien. Prise du flux des
    // préférences, elle apporte son propre battement — l'état se reconstruit au
    // moment du choix. Réduite à la seule vitesse pour qu'un changement de
    // thème ne traverse pas jusqu'ici.
    val state: StateFlow<PlayerUiState> = combine(
        playbackController.state,
        sleepTimer.endsAtMs,
        preferencesStore.preferences.map { it.playbackSpeed }.distinctUntilChanged(),
        abLoop.state,
    ) { playback, endsAt, speed, boucle ->
        PlayerUiState(
            track = playback.current,
            isPlaying = playback.isPlaying,
            isBuffering = playback.isBuffering,
            positionMs = playback.positionMs,
            durationMs = playback.durationMs,
            shuffleEnabled = playback.shuffleEnabled,
            repeatMode = playback.repeatMode,
            queue = playback.queue,
            queueIndex = playback.queueIndex,
            sleepTimerActive = endsAt != null,
            playbackSpeed = speed,
            abLoop = boucle,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = PlayerUiState(),
    )

    /**
     * Les pannes de lecture, à dire une fois chacune.
     *
     * Un événement, pas un état : le message se montre au moment où la lecture
     * échoue, et n'a pas à réapparaître ensuite à chaque recomposition. Media3
     * efface son erreur dès qu'on le prépare à nouveau, si bien qu'un second
     * échec repasse par `null` et se dit de nouveau.
     *
     * Le flux est partagé, et sans rejeu. Dérivé par collecteur, il repartirait
     * de la valeur courante du [StateFlow] amont, laquelle porte encore la
     * panne tant que le lecteur n'a pas été repréparé : l'écran recréé après
     * une rotation redirait alors une erreur déjà lue, et à chaque rotation.
     * Le partage commence avec le ViewModel plutôt qu'au premier abonné, pour
     * ne pas se rabattre sur cette même valeur courante à chaque réabonnement.
     */
    val errors: Flow<String> = playbackController.state
        .map { it.failure }
        .distinctUntilChanged()
        .filterNotNull()
        .map { failure ->
            when (failure) {
                PlaybackFailure.Unreachable -> "Serveur injoignable : lecture impossible."
                PlaybackFailure.Unplayable -> "Ce morceau n'a pas pu être lu."
            }
        }
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 0)

    fun connect() = playbackController.connect()

    /**
     * Démarre [song] avec [queue] comme file d'attente : la bibliothèque
     * entière depuis l'onglet Titres, l'album, l'artiste ou la playlist depuis
     * leur écran.
     */
    fun playFrom(queue: List<Song>, song: Song) {
        val startIndex = queue.indexOfFirst { it.id == song.id }
        if (startIndex < 0) return
        playbackController.play(queue, startIndex)
    }

    /** Démarre [queue] par son premier morceau. Sans effet si elle est vide. */
    fun playFirst(queue: List<Song>) {
        queue.firstOrNull()?.let { playFrom(queue, it) }
    }

    fun playShuffled(queue: List<Song>) = playbackController.playShuffled(queue)

    /**
     * Démarre [song] depuis une file de morceaux du serveur.
     *
     * Chemin distinct de [playFrom] : les deux catalogues ne partagent ni type
     * ni identifiant, et la file remplace l'autre plutôt que de s'y mêler.
     */
    fun playRemoteFrom(queue: List<RemoteSong>, song: RemoteSong) {
        val startIndex = queue.indexOfFirst { it.id == song.id }
        if (startIndex < 0) return
        playbackController.playRemote(queue, startIndex)
    }

    /** Démarre [queue] distante par son premier morceau. */
    fun playRemoteFirst(queue: List<RemoteSong>) {
        queue.firstOrNull()?.let { playRemoteFrom(queue, it) }
    }

    fun playRemoteShuffled(queue: List<RemoteSong>) =
        playbackController.playRemoteShuffled(queue)

    fun togglePlayPause() = playbackController.playPause()

    fun skipNext() = playbackController.skipNext()

    fun skipPrevious() = playbackController.skipPrevious()

    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)

    fun toggleShuffle() = playbackController.toggleShuffle()

    fun cycleRepeatMode() = playbackController.cycleRepeatMode()

    fun playQueueItem(index: Int) = playbackController.playQueueItem(index)

    fun moveQueueItem(from: Int, to: Int) = playbackController.moveQueueItem(from, to)

    fun removeQueueItem(index: Int) = playbackController.removeQueueItem(index)

    /** Arme la minuterie de veille, en remplaçant celle qui courait. */
    fun startSleepTimer(durationMs: Long) = sleepTimer.start(durationMs)

    /** Éteint la minuterie sans toucher à la lecture en cours. */
    fun cancelSleepTimer() = sleepTimer.cancel()

    /**
     * Change la vitesse de lecture.
     *
     * Écrite dans les préférences et non posée sur le lecteur : c'est le
     * service qui les observe et applique, ce qui la fait tenir d'une session à
     * l'autre et jusque dans les lectures démarrées sans écran ouvert. Le
     * chemin de retour passe par le même flux, si bien que l'affichage suit ce
     * qui a réellement été enregistré plutôt que ce qui a été demandé.
     */
    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch { preferencesStore.setPlaybackSpeed(speed) }
    }

    /**
     * Pose la borne suivante de la boucle : A, puis B, puis efface.
     *
     * La position est demandée au lecteur et non prise dans l'état affiché :
     * celui-ci est échantillonné toutes les demi-secondes, et une borne posée
     * un quart de seconde trop tôt s'entend sur un passage qu'on repique.
     *
     * Sans piste courante il n'y a rien à borner : les bornes appartiennent à
     * un morceau.
     */
    fun markAbLoop() {
        val mediaId = playbackController.state.value.current?.mediaId ?: return
        abLoop.mark(mediaId, playbackController.currentPositionMs())
    }

    /** Efface la boucle sans toucher à la lecture en cours. */
    fun clearAbLoop() = abLoop.clear()

    /**
     * Ce qu'il reste avant l'arrêt automatique, lu à l'instant de la demande.
     *
     * Une fonction et non un champ de [PlayerUiState] : celui-ci n'est
     * reconstruit qu'aux tics de position, donc plus du tout en pause, alors que
     * la minuterie continue de courir.
     */
    fun sleepTimerRemainingMs(): Long? = sleepTimer.remainingMs()

    override fun onCleared() {
        // Le service, lui, survit et continue la lecture en arrière-plan.
        playbackController.release()
        super.onCleared()
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WaveFlowApp
                PlayerViewModel(
                    playbackController = app.container.createPlaybackController(),
                    sleepTimer = app.container.sleepTimer,
                    preferencesStore = app.container.preferencesStore,
                    abLoop = app.container.abLoop,
                )
            }
        }
    }
}
