package app.waveflow.playback

import android.content.ComponentName
import android.content.Context
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.waveflow.data.remote.ServerException
import app.waveflow.model.RemoteSong
import app.waveflow.model.Song
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Implémentation Media3 de [PlaybackController].
 *
 * Se connecte au [PlaybackService] via un [MediaController] et republie l'état
 * du lecteur sous forme de [PlaybackState]. La synchronisation passe par
 * `onEvents`, ce qui garantit que l'état est correct dès la connexion — y
 * compris quand l'UI rejoint une lecture déjà en cours (retour depuis les
 * récents, rotation de l'écran).
 *
 * Toutes les interactions avec le contrôleur ont lieu sur le thread principal,
 * comme l'exige Media3.
 */
class Media3PlaybackController(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : PlaybackController {

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var positionJob: Job? = null

    private val playerListener = object : Player.Listener {
        // Un seul point de synchronisation plutôt qu'un callback par champ :
        // on relit l'état complet du lecteur à chaque salve d'événements.
        override fun onEvents(player: Player, events: Player.Events) = syncFrom(player)
    }

    override fun connect() {
        if (controllerFuture != null) return

        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            // Sans ça, Media3 déduit le Looper du thread appelant. Il est
            // toujours principal aujourd'hui, mais autant le fixer plutôt que
            // d'en dépendre.
            .setApplicationLooper(Looper.getMainLooper())
            .buildAsync()
        controllerFuture = future

        future.addListener(
            {
                // La connexion peut échouer (service arrêté, future annulé) :
                // on reste alors simplement déconnecté.
                val ctrl = runCatching { future.get() }.getOrNull() ?: return@addListener
                controller = ctrl
                ctrl.addListener(playerListener)
                syncFrom(ctrl)
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    override fun play(songs: List<Song>, startIndex: Int) {
        val ctrl = controller ?: return
        if (songs.isEmpty()) return

        // Symétrique de [playShuffled] : démarrer explicitement sur un morceau
        // veut dire « dans cet ordre ». Sans ça, un aléatoire laissé actif par
        // une lecture précédente rendrait le bouton Lecture sans effet.
        ctrl.shuffleModeEnabled = false
        ctrl.setMediaItems(songs.map { it.toMediaItem() }, startIndex.coerceIn(songs.indices), 0L)
        ctrl.prepare()
        ctrl.play()
    }

    override fun playRemote(songs: List<RemoteSong>, startIndex: Int) {
        val ctrl = controller ?: return
        if (songs.isEmpty()) return

        ctrl.shuffleModeEnabled = false
        ctrl.setMediaItems(songs.map { it.toMediaItem() }, startIndex.coerceIn(songs.indices), 0L)
        ctrl.prepare()
        ctrl.play()
    }

    override fun playShuffled(songs: List<Song>) {
        val ctrl = controller ?: return
        if (songs.isEmpty()) return

        // Le mode aléatoire doit être posé avant la file : Media3 construit
        // son ordre de lecture au moment où les items arrivent.
        ctrl.shuffleModeEnabled = true
        ctrl.setMediaItems(songs.map { it.toMediaItem() }, songs.indices.random(), 0L)
        ctrl.prepare()
        ctrl.play()
    }

    override fun playRemoteShuffled(songs: List<RemoteSong>) {
        val ctrl = controller ?: return
        if (songs.isEmpty()) return

        // Comme pour la file locale : le mode aléatoire doit être posé avant
        // les items, Media3 construisant son ordre de lecture à leur arrivée.
        ctrl.shuffleModeEnabled = true
        ctrl.setMediaItems(songs.map { it.toMediaItem() }, songs.indices.random(), 0L)
        ctrl.prepare()
        ctrl.play()
    }

    override fun playPause() {
        val ctrl = controller ?: return
        if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
    }

    override fun skipNext() {
        controller?.takeIf { it.hasNextMediaItem() }?.seekToNextMediaItem()
    }

    override fun skipPrevious() {
        controller?.seekToPreviousMediaItem()
    }

    override fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    override fun toggleShuffle() {
        val ctrl = controller ?: return
        ctrl.shuffleModeEnabled = !ctrl.shuffleModeEnabled
    }

    override fun cycleRepeatMode() {
        val ctrl = controller ?: return
        ctrl.repeatMode = when (ctrl.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    override fun release() {
        stopPositionUpdates()
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        controllerFuture = null
        _state.value = PlaybackState()
    }

    private fun syncFrom(player: Player) {
        _state.value = PlaybackState(
            isConnected = true,
            current = player.currentMediaItem?.toPlayingTrack(),
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.All
                Player.REPEAT_MODE_ONE -> RepeatMode.One
                else -> RepeatMode.Off
            },
            failure = player.playerError?.toPlaybackFailure(),
        )

        if (player.isPlaying) startPositionUpdates() else stopPositionUpdates()
    }

    // Le lecteur ne notifie pas l'avancée du temps : on l'échantillonne, mais
    // seulement pendant la lecture effective.
    private fun startPositionUpdates() {
        if (positionJob?.isActive == true) return
        positionJob = scope.launch {
            while (isActive) {
                delay(POSITION_POLL_MS)
                val ctrl = controller ?: break
                _state.update { it.copy(positionMs = ctrl.currentPosition.coerceAtLeast(0L)) }
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }

    private companion object {
        const val POSITION_POLL_MS = 500L
    }
}

/**
 * Traduit un échec Media3 en ce que l'écran saura dire.
 *
 * Deux chemins mènent à « injoignable », et il faut les deux.
 *
 * Le code d'erreur d'abord, quand la pile HTTP de Media3 a elle-même échoué.
 * Les autres codes d'entrée-sortie restent volontairement dehors : un fichier
 * local effacé rend `ERROR_CODE_IO_FILE_NOT_FOUND`, et accuser le serveur
 * enverrait chercher la panne là où il n'y en a pas.
 *
 * La cause ensuite, qui est le cas réel d'une piste distante. Le ticket est
 * résolu avant toute requête de diffusion, et son échec remonte enveloppé dans
 * un `IOException` que Media3 ne peut que classer `ERROR_CODE_IO_UNSPECIFIED` —
 * indiscernable, par son seul code, d'un fichier illisible. Le type que l'appli
 * a posé elle-même en profondeur, lui, ne laisse aucun doute.
 */
internal fun PlaybackException.toPlaybackFailure(): PlaybackFailure =
    if (errorCode in NETWORK_ERROR_CODES || hasUnreachableCause()) {
        PlaybackFailure.Unreachable
    } else {
        PlaybackFailure.Unplayable
    }

/** Cherche un serveur injoignable sous les couches d'emballage successives. */
private fun Throwable.hasUnreachableCause(): Boolean =
    generateSequence(cause) { it.cause.takeIf { next -> next !== it } }
        .take(MAX_CAUSE_DEPTH)
        .any { it is ServerException.Unreachable }

/** Garde-fou : une chaîne de causes circulaire ne doit pas figer le lecteur. */
private const val MAX_CAUSE_DEPTH = 10

/** Les codes qui désignent la liaison, et non le contenu. */
private val NETWORK_ERROR_CODES = setOf(
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
)
