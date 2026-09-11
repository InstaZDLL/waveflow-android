package app.waveflow.playback

import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.Timeline
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
 * R1 à R16 ci-dessous y pointent.
 */
internal class TranscodeSeekingPlayer(player: Player) : ForwardingSimpleBasePlayer(player) {

    /**
     * L'identifiant logique d'une fenêtre relancée, par identifiant de fenêtre du
     * lecteur enveloppé : celui qu'elle avait avant sa première relance (R5).
     */
    private val fenetresLogiques = HashMap<Any, Any>()

    /** La même chose pour les périodes, d'où `SimpleBasePlayer` tire ses discontinuités. */
    private val periodesLogiques = HashMap<Any, Any>()

    /** Le décalage du flux de chaque fenêtre relancée ; absente, elle part du début. */
    private val decalages = HashMap<Any, Long>()

    /** La cible d'une relance, à annoncer comme un saut à la place de la discontinuité qu'elle provoque. */
    private var sautAnnonce: Long? = null

    /** Vrai quand un flux vient de finir et de reprendre sur la même piste : R12. */
    private var segmentRepete = false

    init {
        // Deux choses ne se déduisent pas d'un état mais d'un événement : la fin
        // d'un flux qui se répète, et une piste relancée qu'on vient de quitter.
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

        builder
            .setContentPositionMs(etat.contentPositionMsSupplier.logique(decalage, dureeMs))
            .setContentBufferedPositionMs(etat.contentBufferedPositionMsSupplier.logique(decalage, dureeMs))
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
        val courant = player.currentMediaItemIndex
        when (seekCommand) {
            COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_BACK, COMMAND_SEEK_FORWARD ->
                allerA(courant, positionMs)

            COMMAND_SEEK_TO_MEDIA_ITEM -> allerA(mediaItemIndex, positionMs)

            // Le début du morceau est un décalage `0`, pas le début du flux.
            // C'est ce que demande un appui sur la ligne en cours de la file :
            // `seekToDefaultPosition`, que l'ExoPlayer ramènerait au début du
            // segment.
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

        if (natif) {
            player.seekTo(index, positionMs)
        } else {
            relancer(index, fenetre, bornerAuMorceau(cible, fenetre.mediaItem))
        }
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
    private fun remplacer(index: Int, fenetre: Timeline.Window, decalageMs: Long) {
        val avant = player.currentTimeline
        val fenetreLogique = fenetresLogiques[fenetre.uid] ?: fenetre.uid
        val periodeBrute = avant.getUidOfPeriod(fenetre.firstPeriodIndex)
        val periodeLogique = periodesLogiques[periodeBrute] ?: periodeBrute

        player.replaceMediaItem(index, fenetre.mediaItem.withStreamOffset(decalageMs))

        val apres = player.currentTimeline
        val nouvelle = apres.getWindow(index, Timeline.Window())
        fenetresLogiques[nouvelle.uid] = fenetreLogique
        periodesLogiques[apres.getUidOfPeriod(nouvelle.firstPeriodIndex)] = periodeLogique
        if (decalageMs > 0L) decalages[nouvelle.uid] = decalageMs
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
            val courant = player.currentMediaItemIndex
            val courante = player.currentTimeline
            // Bornée comme dans [allerA] : la file peut s'être vidée entre la
            // discontinuité et cette salve d'événements, et il ne reste alors
            // aucune fenêtre à lire. Garde **non éprouvée** — la fenêtre est
            // trop étroite pour qu'un test la vise —, mais une exception ici
            // emporterait le service de lecture.
            if (courant != C.INDEX_UNSET && courant < courante.windowCount) {
                val fenetre = courante.getWindow(courant, Timeline.Window())
                if ((decalages[fenetre.uid] ?: 0L) > 0L) {
                    relancer(courant, fenetre, 0L)
                    change = true
                }
            }
        }

        val timeline = player.currentTimeline
        for (index in 0 until timeline.windowCount) {
            if (index == player.currentMediaItemIndex) continue
            val fenetre = timeline.getWindow(index, Timeline.Window())
            if ((decalages[fenetre.uid] ?: 0L) > 0L) {
                remplacer(index, fenetre, 0L)
                change = true
            }
        }

        if (change) invalidateState()
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
        periodesLogiques.keys.retainAll(periodes)
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
