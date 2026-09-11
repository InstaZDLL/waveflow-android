package app.waveflow.playback

import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.source.ForwardingTimeline

/**
 * La timeline de l'ExoPlayer, telle qu'Android doit la voir.
 *
 * Enveloppée et non reconstruite : la navigation — suivant, précédent, ordre
 * aléatoire — reste celle de l'ExoPlayer. Reposer la liste de lecture sous forme
 * de liste fabriquerait une `PlaylistTimeline`, qui ignore l'ordre aléatoire.
 *
 * Trois corrections, sur les seules pistes transcodées :
 *
 * - l'identifiant d'une piste relancée est celui qu'elle avait avant (R5) ;
 * - la durée est celle du catalogue quand le flux n'en dit rien, ou quand un
 *   segment joue et n'en connaît que le reste (R3) ;
 * - la piste est déplaçable (R4), et son élément est le marqueur sans décalage.
 *
 * @param fenetres identifiant logique, par identifiant de fenêtre de l'ExoPlayer.
 * @param periodes identifiant logique, par identifiant de période de l'ExoPlayer.
 * @param decalages décalage du flux, par identifiant de fenêtre de l'ExoPlayer.
 */
internal class LogicalTimeline(
    timeline: Timeline,
    private val fenetres: Map<Any, Any>,
    private val periodes: Map<Any, Any>,
    private val decalages: Map<Any, Long>,
) : ForwardingTimeline(timeline) {

    private val periodesBrutes: Map<Any, Any> = periodes.entries.associate { (brute, logique) -> logique to brute }

    override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
        super.getWindow(windowIndex, window, defaultPositionProjectionUs)
        val brute = window.uid
        fenetres[brute]?.let { window.uid = it }

        val item = window.mediaItem
        if (item.isRemoteTranscode()) {
            window.mediaItem = item.withStreamOffset(0L)
            window.isSeekable = true
            val catalogueMs = item.mediaMetadata.durationMs
            if (catalogueMs != null && (decalages.containsKey(brute) || window.durationUs == C.TIME_UNSET)) {
                window.durationUs = Util.msToUs(catalogueMs)
            }
        }
        return window
    }

    override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
        super.getPeriod(periodIndex, period, setIds)
        if (setIds) period.uid?.let { brute -> periodes[brute]?.let { period.uid = it } }

        // Un flux progressif n'a qu'une période, qui couvre sa fenêtre : sa durée
        // suit celle de la fenêtre corrigée, sans quoi la fin du morceau se lirait
        // à la fin du segment.
        val fenetre = getWindow(period.windowIndex, Window())
        if (fenetre.mediaItem.isRemoteTranscode() && period.positionInWindowUs == 0L) {
            period.durationUs = fenetre.durationUs
        }
        return period
    }

    override fun getIndexOfPeriod(uid: Any): Int = super.getIndexOfPeriod(periodesBrutes[uid] ?: uid)

    override fun getUidOfPeriod(periodIndex: Int): Any =
        super.getUidOfPeriod(periodIndex).let { periodes[it] ?: it }
}
