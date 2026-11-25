package app.waveflow.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import app.waveflow.model.AppPreferences
import app.waveflow.model.StreamNetwork
import app.waveflow.model.StreamRendering
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * Fait prendre aux pistes pas encore commencées le rendu d'un nouveau réseau.
 *
 * Les règles sont dans `docs/qualite-automatique.md` ; les renvois Q5 à Q9
 * ci-dessous y pointent.
 *
 * Les pistes se lisent et se remplacent **par l'enveloppe**, qui leur garde une
 * identité logique à travers ses propres relances (R5 de
 * `docs/deplacement-dans-un-transcodage.md`) : c'est elle qui dit qu'une piste a
 * déjà été jouée. L'ExoPlayer ne sert qu'à l'ordre aléatoire, que le [Player]
 * ne sait ni lire ni poser.
 *
 * Tout se passe sur la boucle de l'application, celle du lecteur.
 *
 * @param margeEnchainementMs voir [suivanteDejaLue].
 */
internal class PistesAVenir(
    private val exoPlayer: ExoPlayer,
    private val enveloppe: Player,
    private val margeEnchainementMs: Long = MARGE_ENCHAINEMENT_MS,
) {

    /**
     * L'identité logique des pistes qui ont été la courante depuis leur entrée
     * dans la file (Q7). Une piste retirée de la file en sort à la réécriture
     * suivante.
     */
    private val commencees = HashSet<Any>()

    init {
        noterLaCourante()
        enveloppe.addListener(
            object : Player.Listener {
                // `onEvents` et non `onMediaItemTransition` : une file posée
                // d'un bloc rend courante sa première piste sans transition
                // annoncée à tous les coups, et le moindre changement d'état
                // suffit à relire laquelle joue.
                override fun onEvents(player: Player, events: Player.Events) = noterLaCourante()
            },
        )
    }

    /**
     * Pose [rendu] sur chaque piste distante pas encore commencée.
     *
     * La courante n'est jamais touchée (Q6), ni la suivante quand le lecteur
     * peut déjà la lire pour l'enchaîner (Q8). Une piste qui porte déjà le rendu
     * est laissée telle quelle : simple économie, l'ExoPlayer mettant de
     * lui-même à jour sur place un élément identique — **sans effet
     * observable**, donc non éprouvée.
     *
     * L'ordre aléatoire est relevé avant et rétabli après (Q9).
     */
    fun reecrire(rendu: StreamRendering) {
        noterLaCourante()
        val timeline = enveloppe.currentTimeline
        if (timeline.isEmpty) return
        val courante = enveloppe.currentMediaItemIndex
        val protegee = suivanteDejaLue(timeline, courante)

        val fenetre = Timeline.Window()
        val presentes = HashSet<Any>()
        val aReecrire = ArrayList<Pair<Int, MediaItem>>()
        for (index in 0 until timeline.windowCount) {
            timeline.getWindow(index, fenetre)
            presentes += fenetre.uid
            if (index == courante || index == protegee || fenetre.uid in commencees) continue
            val item = fenetre.mediaItem
            val reecrite = item.withRendering(rendu)
            if (reecrite.localConfiguration != item.localConfiguration) aReecrire += index to reecrite
        }
        commencees.retainAll(presentes)
        if (aReecrire.isEmpty()) return

        // Q9 : chaque remplacement ajoute la nouvelle source puis retire
        // l'ancienne, et l'ajout la place à un rang tiré au hasard. Les rangs
        // et la longueur ne changent pas : l'ordre relevé reste valable tel
        // quel.
        val ordre = exoPlayer.shuffleOrder
        aReecrire.forEach { (index, item) -> enveloppe.replaceMediaItem(index, item) }
        exoPlayer.setShuffleOrder(ordre)
    }

    /**
     * Le rang de la piste que l'ExoPlayer peut déjà lire pour l'enchaîner sans
     * blanc, ou [C.INDEX_UNSET] (Q8).
     *
     * Media3 ne dit pas publiquement laquelle il lit ; on la suppose lue quand
     * il reste à la courante moins de [margeEnchainementMs] d'écoute, vitesse
     * comprise, ou quand on ne sait pas ce qu'il lui reste. La suivante est
     * celle de l'ordre réel — répétition et aléatoire compris —, celle que le
     * lecteur prépare.
     */
    private fun suivanteDejaLue(timeline: Timeline, courante: Int): Int {
        val suivante = timeline.getNextWindowIndex(courante, enveloppe.repeatMode, enveloppe.shuffleModeEnabled)
        if (suivante == C.INDEX_UNSET) return C.INDEX_UNSET

        val dureeMs = enveloppe.duration
        if (dureeMs == C.TIME_UNSET) return suivante
        val restantMs = (dureeMs - enveloppe.currentPosition).coerceAtLeast(0L) / enveloppe.playbackParameters.speed
        return if (restantMs < margeEnchainementMs) suivante else C.INDEX_UNSET
    }

    private fun noterLaCourante() {
        val timeline = enveloppe.currentTimeline
        val index = enveloppe.currentMediaItemIndex
        if (index == C.INDEX_UNSET || index >= timeline.windowCount) return
        commencees += timeline.getWindow(index, Timeline.Window()).uid
    }

    companion object {
        /**
         * Dix secondes : ce que l'ExoPlayer lit d'avance est de l'ordre de la
         * seconde — le tampon de l'`AudioTrack` en PCM plafonne à 750 ms —, et
         * la marge couvre le décodeur et l'imprécision de la position.
         */
        const val MARGE_ENCHAINEMENT_MS = 10_000L
    }
}

/**
 * Tient [rendu] à jour selon la qualité choisie et le réseau, et réécrit les
 * pistes à venir quand le réseau change sous la qualité automatique.
 *
 * Seul un **changement de réseau** réécrit la file (Q5) : changer le réglage
 * lui-même vaut pour les pistes lancées ensuite, comme l'écran le dit. Le
 * premier réseau reçu n'est pas un changement — la file présente l'a déjà pris
 * en entrant (Q4).
 *
 * Ne se termine qu'avec la portée qui l'appelle.
 *
 * @param rendu le rendu que prendra la prochaine file, lu par `BrowseCallback`.
 * @param reecrire voir [PistesAVenir.reecrire] ; appelé dans le fil du collecteur.
 */
internal suspend fun suivreLaQualite(
    preferences: Flow<AppPreferences>,
    reseaux: Flow<StreamNetwork>,
    rendu: MutableStateFlow<StreamRendering?>,
    reecrire: (StreamRendering) -> Unit,
) {
    var reseauPrecedent: StreamNetwork? = null
    combine(preferences, reseaux, ::Pair).collect { (choix, reseau) ->
        val renduDuReseau = choix.renderingOn(reseau)
        rendu.value = renduDuReseau
        val change = reseauPrecedent != null && reseau != reseauPrecedent
        reseauPrecedent = reseau
        if (change && choix.automaticQuality) reecrire(renduDuReseau)
    }
}
