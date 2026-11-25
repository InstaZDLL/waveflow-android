package app.waveflow.playback

import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import app.waveflow.model.StreamRendering
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Présente à Android la timeline logique d'un morceau transcodé.
 *
 * Un transcodage en direct n'a pas de plages : s'y déplacer, c'est relancer le
 * flux à partir d'un instant (`offset_ms`). ExoPlayer compte alors depuis zéro
 * dans un flux qui commence à 2:13. Cette enveloppe rend à tout le reste —
 * session, notification, Android Auto, et chaque écouteur du service — le
 * morceau tel qu'il est : commencé à 0:00, de sa durée entière.
 *
 * Les règles sont dans `docs/deplacement-dans-un-transcodage.md` ; les renvois
 * R1 à R30 ci-dessous y pointent.
 *
 * @param coalescenceMs ce qu'une relance demandée par un saut attend avant de
 *   partir, pour qu'un saut suivant la remplace (R9).
 */
internal class TranscodeSeekingPlayer(
    player: Player,
    private val coalescenceMs: Long = COALESCENCE_MS,
) : ForwardingSimpleBasePlayer(player) {

    /**
     * L'identifiant logique d'une fenêtre relancée, par identifiant de fenêtre du
     * lecteur enveloppé : celui qu'elle avait avant sa première relance (R5).
     */
    private val fenetresLogiques = HashMap<Any, Any>()

    /** La même chose pour les périodes, d'où `SimpleBasePlayer` tire ses discontinuités. */
    private val periodesLogiques = HashMap<Any, Any>()

    /** Le décalage du flux de chaque fenêtre relancée ; absente, elle part du début. */
    private val decalages = HashMap<Any, Long>()

    /**
     * Le rendu qu'avait une piste avant son repli sur l'original, par
     * identifiant de fenêtre du lecteur enveloppé. Le repli ne vaut que pour
     * l'écoute en cours (R22) : la piste quittée ou répétée le retrouve.
     */
    private val rendusAvantRepli = HashMap<Any, StreamRendering>()

    /**
     * Vrai quand le lecteur enveloppé s'est arrêté sur une erreur, faux dès
     * qu'on le prépare ou qu'on l'arrête. Son `playerError` ne suffit pas à le
     * dire : un `stop` demandé après une panne la lui laisse.
     */
    private var arreteSurErreur = false

    /** La cible d'une relance, à annoncer comme un saut à la place de la discontinuité qu'elle provoque. */
    private var sautAnnonce: Long? = null

    /** Vrai quand un flux vient de finir et de reprendre sur la même piste : R12. */
    private var segmentRepete = false

    /** La relance qu'un saut a demandée et qui n'est pas encore partie (R9). */
    private var sautEnAttente: SautEnAttente? = null

    /** La dernière série de reprises après une coupure (R30) ; un saut l'efface. */
    private var reprises: Reprises? = null

    /**
     * Vrai quand une relance différée vient de partir : le saut a été annoncé au
     * moment du geste, la discontinuité que provoque le remplacement ne doit pas
     * l'annoncer une seconde fois.
     */
    private var relanceTue = false

    /** Sur la boucle de l'application, celle où l'enveloppe vit et où arrivent les sauts. */
    private val minuterie = Handler(player.applicationLooper)

    private val lancerLeSautEnAttente = Runnable { lancerSautEnAttente() }

    init {
        // Trois choses ne se déduisent pas d'un état mais d'un événement : la fin
        // d'un flux qui se répète, une piste relancée qu'on vient de quitter, et
        // un arrêt causé par une panne plutôt que demandé.
        player.addListener(
            object : Player.Listener {
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    if (reason == DISCONTINUITY_REASON_AUTO_TRANSITION &&
                        oldPosition.mediaItemIndex == newPosition.mediaItemIndex
                    ) {
                        segmentRepete = true
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    arreteSurErreur = true
                }

                override fun onPlayerErrorChanged(error: PlaybackException?) {
                    // Préparé de nouveau, d'où qu'en vienne l'ordre.
                    if (error == null) arreteSurErreur = false
                }

                override fun onEvents(player: Player, events: Player.Events) = veillerAuxSegments()
            },
        )
    }

    override fun getState(): State {
        val etat = super.getState()
        val brute = etat.timeline
        oublierLesAbsentes(brute)
        val logique = LogicalTimeline(
            timeline = brute,
            fenetres = HashMap(fenetresLogiques),
            periodes = HashMap(periodesLogiques),
            decalages = HashMap(decalages),
        )
        val builder = etat.buildUpon().setPlaylist(logique, etat.currentTracks, etat.currentMetadata)
        if (!brute.isEmpty) corrigerLaPisteCourante(etat, logique, builder)
        return builder.build()
    }

    private fun corrigerLaPisteCourante(etat: State, logique: Timeline, builder: State.Builder) {
        val index = etat.currentMediaItemIndex.takeIf { it != C.INDEX_UNSET } ?: 0
        val brute = etat.timeline.getWindow(index, Timeline.Window())
        if (!brute.mediaItem.isRemoteTranscode()) return

        val decalage = decalages[brute.uid] ?: 0L
        val dureeMs = logique.getWindow(index, Timeline.Window()).durationMs

        val attente = sautEnAttente?.takeIf { it.fenetre == brute.uid }
        if (attente != null) {
            // R9 et R6 : la relance n'est pas partie, mais la position est déjà
            // la cible — et un « avancer » de plus se compte depuis elle.
            // Constante : une position posée en nombre s'extrapole tant que
            // l'ancien flux joue, et la cible dériverait pendant l'attente.
            val cible = SimpleBasePlayer.PositionSupplier.getConstant(attente.cibleMs)
            builder
                .setContentPositionMs(cible)
                .setContentBufferedPositionMs(cible)
        } else {
            builder
                .setContentPositionMs(etat.contentPositionMsSupplier.logique(decalage, dureeMs))
                .setContentBufferedPositionMs(etat.contentBufferedPositionMsSupplier.logique(decalage, dureeMs))
        }

        builder
            // R4 : ExoPlayer retire ces commandes d'une piste non déplaçable.
            .setAvailableCommands(
                etat.availableCommands.buildUpon()
                    .addAll(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_BACK, COMMAND_SEEK_FORWARD)
                    .build(),
            )

        val saut = sautAnnonce
        if (saut != null) {
            sautAnnonce = null
            builder.setPositionDiscontinuity(DISCONTINUITY_REASON_SEEK, saut)
        } else if (relanceTue) {
            builder.clearPositionDiscontinuity()
        } else if (etat.hasPositionDiscontinuity) {
            builder.setPositionDiscontinuity(
                etat.positionDiscontinuityReason,
                logicalPositionMs(etat.discontinuityPositionMs, decalage, dureeMs),
            )
        }
    }

    /**
     * `BasePlayer` a déjà calculé la cible sur l'état de l'enveloppe, donc sur la
     * position logique ; `ForwardingSimpleBasePlayer` la jetterait pour laisser
     * l'ExoPlayer la recalculer sur sa position brute (R13).
     */
    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        // R9 : un saut chasse le précédent. S'il demande à son tour une
        // relance, [allerA] la remet en attente à sa propre cible.
        annulerSautEnAttente()
        // R30 : la lecture repart d'où l'on a voulu, les reprises d'avant ne
        // disent plus rien du flux à venir.
        reprises = null
        val courant = player.currentMediaItemIndex
        when (seekCommand) {
            COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_BACK, COMMAND_SEEK_FORWARD ->
                allerA(courant, positionMs)

            COMMAND_SEEK_TO_MEDIA_ITEM -> allerA(mediaItemIndex, positionMs)

            // `seekToDefaultPosition()` sans rang, qu'un contrôleur envoie pour
            // revenir au début : le début du morceau est un décalage `0`, là où
            // l'ExoPlayer ramènerait au début du flux, c'est-à-dire au milieu du
            // morceau. La variante avec rang, celle de la file, passe par
            // `COMMAND_SEEK_TO_MEDIA_ITEM`, juste au-dessus.
            COMMAND_SEEK_TO_DEFAULT_POSITION -> allerA(mediaItemIndex, C.TIME_UNSET)

            // Recommencer ou reculer d'une piste : tranché sur la position
            // logique. Reculer d'une piste reste à l'ExoPlayer, qui connaît
            // l'ordre aléatoire.
            COMMAND_SEEK_TO_PREVIOUS ->
                if (mediaItemIndex == courant) allerA(courant, 0L) else player.seekToPreviousMediaItem()

            else -> return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
        }
        return Futures.immediateVoidFuture()
    }

    /** R7 : nativement quand l'ExoPlayer le peut, par relance sinon. */
    private fun allerA(index: Int, positionMs: Long) {
        val timeline = player.currentTimeline
        if (index == C.INDEX_UNSET || index >= timeline.windowCount) return

        val fenetre = timeline.getWindow(index, Timeline.Window())
        val cible = if (positionMs == C.TIME_UNSET) 0L else positionMs.coerceAtLeast(0L)
        val decalage = decalages[fenetre.uid] ?: 0L
        val natif = !fenetre.mediaItem.isRemoteTranscode() ||
            (decalage == 0L && (fenetre.isSeekable || cible == 0L))

        val borne = bornerAuMorceau(cible, fenetre.mediaItem)
        when {
            natif -> player.seekTo(index, positionMs)
            // La relance en attente reprendra le lecteur à son départ.
            index == player.currentMediaItemIndex && coalescenceMs > 0L -> return mettreEnAttente(fenetre, borne)
            else -> relancer(index, fenetre, borne)
        }
        reprendreApresUnePanne()
    }

    /**
     * R9 : un lecteur arrêté sur une panne — le dernier refus d'une liaison
     * facturée — reprend au saut qui suit, natif ou relancé ; sans cela le saut
     * resterait sans suite. Un arrêt demandé (`stop`) se respecte, même après
     * une panne dont le lecteur garde l'erreur.
     */
    private fun reprendreApresUnePanne() {
        if (player.playbackState == STATE_IDLE && arreteSurErreur) player.prepare()
    }

    /**
     * R9 : la relance attend [coalescenceMs] avant de partir, et un saut suivant
     * prend sa place. Une rafale ne coûte ainsi qu'un transcodage au serveur.
     *
     * Seul le saut dans la piste courante attend : changer de piste n'est pas
     * une rafale, et la piste visée doit commencer à charger.
     */
    private fun mettreEnAttente(fenetre: Timeline.Window, cibleMs: Long) {
        sautEnAttente = SautEnAttente(fenetre.uid, cibleMs)
        // R6 : annoncé tout de suite, le curseur ne revient pas en arrière.
        sautAnnonce = cibleMs
        minuterie.postDelayed(lancerLeSautEnAttente, coalescenceMs)
    }

    /**
     * L'attente échue : relance, si la piste visée est toujours la courante. Une
     * file reposée ou une piste quittée entre-temps la rendent caduque.
     */
    private fun lancerSautEnAttente() {
        val saut = sautEnAttente ?: return
        sautEnAttente = null

        val index = player.currentMediaItemIndex
        val timeline = player.currentTimeline
        if (index == C.INDEX_UNSET || index >= timeline.windowCount) return
        val fenetre = timeline.getWindow(index, Timeline.Window())
        if (fenetre.uid != saut.fenetre) return

        remplacer(index, fenetre, saut.cibleMs)
        reprendreApresUnePanne() // Le flux quitté a pu échouer avant le geste ou pendant l'attente.
        // Le lecteur enveloppé a déjà notifié son remplacement, dans l'appel
        // même : l'état relu ici le porte. Le drapeau ne vit que le temps de
        // cette relecture, pour ne jamais taire une discontinuité étrangère.
        relanceTue = true
        try {
            invalidateState()
        } finally {
            relanceTue = false
        }
    }

    private fun annulerSautEnAttente() {
        sautEnAttente = null
        minuterie.removeCallbacks(lancerLeSautEnAttente)
    }

    /**
     * R22 : remplace le flux transcodé de la piste courante par l'original, à la
     * position logique où elle en était, sans changement de piste.
     *
     * L'original se déplace nativement (R7) : sa position est celle de son flux,
     * et la piste n'a plus de décalage. Le lecteur, arrêté sur son erreur, est
     * préparé de nouveau ; il reprend s'il jouait. Le rendu d'avant est retenu :
     * la piste le retrouve une fois quittée ou répétée.
     *
     * @return faux, sans rien changer, si la piste courante n'est pas un
     *   transcodage du serveur.
     */
    fun revenirAlOriginal(): Boolean {
        val index = player.currentMediaItemIndex
        val timeline = player.currentTimeline
        if (index == C.INDEX_UNSET || index >= timeline.windowCount) return false
        val fenetre = timeline.getWindow(index, Timeline.Window())
        val item = fenetre.mediaItem
        val rendu = item.localConfiguration?.uri
            ?.takeIf { item.isRemoteTranscode() }
            ?.let(::renderingOfRemoteUri)
            ?: return false

        // Un saut en attente dit où l'utilisateur veut être : il l'emporte sur
        // la position du flux refusé.
        val position = sautEnAttente?.takeIf { it.fenetre == fenetre.uid }?.cibleMs
            ?: logicalPositionMs(
                streamPositionMs = player.currentPosition,
                offsetMs = decalages[fenetre.uid] ?: 0L,
                durationMs = item.mediaMetadata.durationMs ?: C.TIME_UNSET,
            )
        annulerSautEnAttente()

        val original = remplacerPar(index, fenetre, item.withRendering(StreamRendering.ORIGINAL))
        rendusAvantRepli[original] = rendu
        player.seekTo(index, position.coerceAtLeast(0L))
        player.prepare()
        return true
    }

    /**
     * R25 : reprend le transcodage de la piste courante là où le son s'est tu,
     * après une coupure que le serveur refuse de reprendre à l'octet atteint.
     *
     * Une relance comme une autre (R27) : même rendu, même identité, au décalage
     * de la position logique — ou à la cible d'un saut en attente (R28). Le
     * lecteur, arrêté sur son erreur, est préparé de nouveau ; il reprend s'il
     * jouait.
     *
     * @return faux, sans rien changer, si la piste courante n'est pas un
     *   transcodage du serveur (R26), ou après trois reprises de suite sans
     *   écoute (R30) : l'erreur suit alors son cours.
     */
    fun reprendreApresCoupure(): Boolean {
        val index = player.currentMediaItemIndex
        val timeline = player.currentTimeline
        if (index == C.INDEX_UNSET || index >= timeline.windowCount) return false
        val fenetre = timeline.getWindow(index, Timeline.Window())
        val item = fenetre.mediaItem
        if (!item.isRemoteTranscode()) return false

        val position = sautEnAttente?.takeIf { it.fenetre == fenetre.uid }?.cibleMs
            ?: logicalPositionMs(
                streamPositionMs = player.currentPosition,
                offsetMs = decalages[fenetre.uid] ?: 0L,
                durationMs = item.mediaMetadata.durationMs ?: C.TIME_UNSET,
            ).coerceAtLeast(0L)

        // R30 : la série se compte sur l'écoute, identifiée par sa fenêtre
        // logique, que les relances ne changent pas.
        val ecoute = fenetresLogiques[fenetre.uid] ?: fenetre.uid
        val serie = reprises?.takeIf { it.ecoute == ecoute && position < it.positionMs + REPRISE_ENTENDUE_MS }
        val nombre = (serie?.nombre ?: 0) + 1
        if (nombre > REPRISES_SANS_ECOUTE_MAX) return false
        reprises = Reprises(ecoute, position, nombre)

        annulerSautEnAttente()
        relancer(index, fenetre, bornerAuMorceau(position, item))
        player.prepare()
        return true
    }

    private fun relancer(index: Int, fenetre: Timeline.Window, decalageMs: Long) {
        remplacer(index, fenetre, decalageMs)

        if (index == player.currentMediaItemIndex) {
            // R6 : annoncé tout de suite, le curseur ne revient pas en arrière.
            sautAnnonce = decalageMs
        } else {
            player.seekTo(index, 0L)
        }
    }

    /**
     * Échange le flux d'une piste contre celui qui commence à [decalageMs], en
     * lui gardant son identité.
     *
     * L'ExoPlayer ne sait pas mettre à jour une source dont l'URI change : il
     * retire et ajoute, d'où de nouveaux identifiants. Les rattacher juste après
     * suffit — Media3 livre `onEvents` par un message posté, et l'enveloppe ne
     * relit donc son état qu'ensuite.
     */
    private fun remplacer(index: Int, fenetre: Timeline.Window, decalageMs: Long) =
        remplacerPar(index, fenetre, fenetre.mediaItem.withStreamOffset(decalageMs))

    /**
     * Échange la piste [index] contre [item] en lui gardant son identité ; voir
     * [remplacer].
     *
     * @return l'identifiant brut de la fenêtre qui la remplace.
     */
    private fun remplacerPar(index: Int, fenetre: Timeline.Window, item: MediaItem): Any {
        val avant = player.currentTimeline
        val fenetreLogique = fenetresLogiques[fenetre.uid] ?: fenetre.uid
        val periodeBrute = avant.getUidOfPeriod(fenetre.firstPeriodIndex)
        val periodeLogique = periodesLogiques[periodeBrute] ?: periodeBrute

        // L'ExoPlayer ajoute la nouvelle source juste après l'ancienne, puis
        // retire l'ancienne ; retirer la piste courante lui fait chercher la
        // suivante, selon la répétition et l'ordre aléatoire (éprouvé en 1.11.1).
        // Sous `REPEAT_MODE_ONE`, c'est l'ancienne elle-même, retirée : il
        // s'arrête. En aléatoire, c'est la piste tirée après elle : il change de
        // morceau. Les deux sont suspendus le temps du remplacement, et la
        // suivante est alors la nouvelle source.
        //
        // L'ajout, lui, place la nouvelle source à un rang tiré au hasard dans
        // l'ordre aléatoire, que celui-ci soit actif ou non : ce qui suivait la
        // piste ne la suivrait plus (R1). Un remplacement ne change pas le
        // nombre de pistes, et chacune garde son index : l'ordre d'avant se
        // repose tel quel. Toute piste remplacée y passe, courante ou non (R14).
        val courante = index == player.currentMediaItemIndex
        val repetition = player.repeatMode
        val melange = player.shuffleModeEnabled
        val exoPlayer = player as? ExoPlayer
        val ordre = exoPlayer?.shuffleOrder
        if (courante && repetition != REPEAT_MODE_OFF) player.repeatMode = REPEAT_MODE_OFF
        if (courante && melange) player.shuffleModeEnabled = false
        try {
            player.replaceMediaItem(index, item)
        } finally {
            if (ordre != null && exoPlayer.shuffleOrder.length == ordre.length) exoPlayer.shuffleOrder = ordre
            if (courante && melange) player.shuffleModeEnabled = true
            if (courante && repetition != REPEAT_MODE_OFF) player.repeatMode = repetition
        }

        val apres = player.currentTimeline
        val nouvelle = apres.getWindow(index, Timeline.Window())
        fenetresLogiques[nouvelle.uid] = fenetreLogique
        periodesLogiques[apres.getUidOfPeriod(nouvelle.firstPeriodIndex)] = periodeLogique
        val decalageMs = item.localConfiguration?.uri?.let(::streamOffsetOfRemoteUri) ?: 0L
        if (decalageMs > 0L) decalages[nouvelle.uid] = decalageMs
        return nouvelle.uid
    }

    /**
     * R12 : un flux qui se répète rejouerait les dernières secondes d'un segment
     * sans fin ; on repart du début du morceau. R14 : une piste relancée qu'on a
     * quittée retrouve son marqueur nu, sans quoi y revenir la ferait repartir au
     * milieu.
     */
    private fun veillerAuxSegments() {
        var change = false

        if (segmentRepete) {
            segmentRepete = false
            change = repartirDuDebut()
        }

        val timeline = player.currentTimeline
        for (index in 0 until timeline.windowCount) {
            if (index == player.currentMediaItemIndex) continue
            val fenetre = timeline.getWindow(index, Timeline.Window())
            val rendu = rendusAvantRepli.remove(fenetre.uid)
            if (rendu != null) {
                // R22 : le repli ne valait que pour l'écoute quittée.
                remplacerPar(index, fenetre, fenetre.mediaItem.withRendering(rendu))
                change = true
            } else if ((decalages[fenetre.uid] ?: 0L) > 0L) {
                remplacer(index, fenetre, 0L)
                change = true
            }
        }

        if (change) invalidateState()
    }

    /**
     * Ce que défait un morceau qui recommence : un saut en attente (R9), un
     * segment (R12), un repli sur l'original (R22).
     *
     * @return vrai si l'état est à relire.
     */
    private fun repartirDuDebut(): Boolean {
        val courant = player.currentMediaItemIndex
        val courante = player.currentTimeline
        // Bornée comme dans [allerA] : la file peut s'être vidée entre la
        // discontinuité et cette salve d'événements, et il ne reste alors
        // aucune fenêtre à lire. Garde **non éprouvée** — la fenêtre est
        // trop étroite pour qu'un test la vise —, mais une exception ici
        // emporterait le service de lecture.
        if (courant == C.INDEX_UNSET || courant >= courante.windowCount) return false
        val fenetre = courante.getWindow(courant, Timeline.Window())

        // R9 : le morceau recommence, comme un changement de piste ; un saut
        // demandé dans l'écoute qui vient de finir n'a plus de sens, ni les
        // reprises de cette écoute-là (R30).
        val attendait = sautEnAttente != null
        annulerSautEnAttente()
        reprises = null
        val rendu = rendusAvantRepli.remove(fenetre.uid)
        when {
            // R22 : l'original répété, c'est une nouvelle écoute ; elle
            // redemande le rendu choisi, depuis le début.
            rendu != null -> {
                remplacerPar(courant, fenetre, fenetre.mediaItem.withRendering(rendu))
                sautAnnonce = 0L
            }

            (decalages[fenetre.uid] ?: 0L) > 0L -> relancer(courant, fenetre, 0L)

            // L'état déjà relu pour cette salve annonce encore la cible de
            // l'attente : sans relecture, le curseur y resterait.
            else -> return attendait
        }
        return true
    }

    /** R9 : un arrêt demandé, même après une panne, n'est pas une panne à réparer. */
    override fun handleStop(): ListenableFuture<*> {
        arreteSurErreur = false
        return super.handleStop()
    }

    /** Une relance en attente ne doit pas survivre au lecteur qu'elle viserait. */
    override fun handleRelease(): ListenableFuture<*> {
        annulerSautEnAttente()
        return super.handleRelease()
    }

    /** Ce que le lecteur enveloppé ne connaît plus n'a plus à être rattaché à rien. */
    private fun oublierLesAbsentes(timeline: Timeline) {
        val fenetres = HashSet<Any>()
        val periodes = HashSet<Any>()
        val fenetre = Timeline.Window()
        for (i in 0 until timeline.windowCount) fenetres += timeline.getWindow(i, fenetre).uid
        for (i in 0 until timeline.periodCount) periodes += timeline.getUidOfPeriod(i)
        fenetresLogiques.keys.retainAll(fenetres)
        decalages.keys.retainAll(fenetres)
        rendusAvantRepli.keys.retainAll(fenetres)
        periodesLogiques.keys.retainAll(periodes)
    }

    /** Une relance demandée, par l'identifiant brut de la fenêtre qu'elle vise. */
    private data class SautEnAttente(val fenetre: Any, val cibleMs: Long)

    /**
     * La dernière reprise d'une écoute, et combien l'ont précédée sans qu'on ait
     * rien entendu entre deux (R30).
     *
     * @param ecoute l'identifiant logique de la fenêtre reprise.
     */
    private data class Reprises(val ecoute: Any, val positionMs: Long, val nombre: Int)

    companion object {
        /** R30 : au-delà, la coupure remonte comme une panne du réseau. */
        const val REPRISES_SANS_ECOUTE_MAX = 3

        /** R30 : ce qu'une reprise doit avoir rendu pour compter comme entendue. */
        const val REPRISE_ENTENDUE_MS = 5_000L

        /**
         * Le délai qu'Android laisse entre deux touchers d'un même geste
         * (`ViewConfiguration.getDoubleTapTimeout`) : deux pressions plus
         * rapprochées font une rafale (R9).
         */
        const val COALESCENCE_MS = 300L
    }
}

/**
 * R8 : le serveur refuse un décalage au-delà de la durée. Un saut à la toute fin
 * rend un flux vide, qui finit aussitôt — la fin du morceau.
 */
private fun bornerAuMorceau(cibleMs: Long, item: MediaItem): Long {
    val dureeMs = item.mediaMetadata.durationMs ?: return cibleMs
    return cibleMs.coerceAtMost((dureeMs - 1L).coerceAtLeast(0L))
}

private fun SimpleBasePlayer.PositionSupplier.logique(decalageMs: Long, dureeMs: Long) =
    SimpleBasePlayer.PositionSupplier { logicalPositionMs(get(), decalageMs, dureeMs) }

/**
 * La position logique d'une position lue dans le flux (R2).
 *
 * Une position inconnue le reste : lui ajouter le décalage publierait un instant
 * plausible et faux. Et la position reste dans le morceau, un flux transcodé
 * pouvant durer un peu plus que ce que le catalogue annonce.
 */
internal fun logicalPositionMs(streamPositionMs: Long, offsetMs: Long, durationMs: Long): Long {
    if (streamPositionMs == C.TIME_UNSET) return C.TIME_UNSET
    val logique = (streamPositionMs + offsetMs).coerceAtLeast(0L)
    return if (durationMs == C.TIME_UNSET) logique else logique.coerceAtMost(durationMs)
}

/** Une piste du serveur servie transcodée : la seule dont le flux peut devoir être relancé. */
internal fun MediaItem.isRemoteTranscode(): Boolean {
    val uri = localConfiguration?.uri ?: return false
    return trackIdOfRemoteUri(uri) != null && !renderingOfRemoteUri(uri).isOriginal
}
