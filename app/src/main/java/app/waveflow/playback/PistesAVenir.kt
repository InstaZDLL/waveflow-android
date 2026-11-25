package app.waveflow.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
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
     * dans la file (Q7), ou la suivante protégée d'une réécriture (Q8). Une
     * piste retirée de la file en sort à la réécriture suivante.
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
     * peut déjà la lire pour l'enchaîner (Q8) — protégée une fois, elle est
     * tenue pour commencée et le reste. Une piste qui porte déjà le rendu
     * est laissée telle quelle : simple économie, l'ExoPlayer mettant de
     * lui-même à jour sur place un élément identique — **sans effet
     * observable**, donc non éprouvée.
     *
     * L'ordre aléatoire ne bouge à aucune étape des remplacements, et revient
     * tel quel après (Q9).
     */
    fun reecrire(rendu: StreamRendering) {
        noterLaCourante()
        val timeline = enveloppe.currentTimeline
        if (timeline.isEmpty) return
        val courante = enveloppe.currentMediaItemIndex
        val protegee = suivanteDejaLue(timeline, courante)
        if (protegee != C.INDEX_UNSET) commencees += timeline.getWindow(protegee, Timeline.Window()).uid

        val fenetre = Timeline.Window()
        val presentes = HashSet<Any>()
        val aReecrire = ArrayList<Pair<Int, MediaItem>>()
        for (index in 0 until timeline.windowCount) {
            timeline.getWindow(index, fenetre)
            presentes += fenetre.uid
            if (index == courante || fenetre.uid in commencees) continue
            val item = fenetre.mediaItem
            val reecrite = item.withRendering(rendu)
            if (reecrite.localConfiguration != item.localConfiguration) aReecrire += index to reecrite
        }
        commencees.retainAll(presentes)
        if (aReecrire.isEmpty()) return

        // Q9 : chaque remplacement ajoute la nouvelle source puis retire
        // l'ancienne, et le fil de lecture reçoit l'ordre de chaque étape.
        // L'ordre par défaut placerait l'ajout à un rang tiré au hasard —
        // parfois entre la courante et la suivante protégée, qu'il retirerait.
        // Le temps des remplacements, l'ordre en place met la nouvelle juste
        // après l'ancienne ; les rangs et la longueur ne changent pas, et
        // l'ordre relevé est rétabli tel quel ensuite, même si un remplacement
        // échoue (garde non éprouvée).
        val ordre = exoPlayer.shuffleOrder
        exoPlayer.setShuffleOrder(OrdreEnPlace.de(ordre))
        try {
            aReecrire.forEach { (index, item) -> enveloppe.replaceMediaItem(index, item) }
        } finally {
            exoPlayer.setShuffleOrder(ordre)
        }
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
 * Un ordre aléatoire figé, qui range chaque piste ajoutée **juste après celle
 * qui la précède dans la file**, et non à un rang tiré au hasard.
 *
 * L'ExoPlayer remplace une piste en ajoutant la nouvelle juste derrière
 * l'ancienne, puis en retirant l'ancienne : sous cet ordre, la nouvelle prend
 * à chaque étape la place exacte de l'ancienne, et rien d'autre ne bouge (Q9).
 *
 * @param rangs les rangs des pistes dans la file, dans l'ordre de lecture.
 */
internal class OrdreEnPlace private constructor(private val rangs: IntArray) : ShuffleOrder {

    /** La place de chaque rang dans [rangs]. */
    private val places = IntArray(rangs.size).also { places ->
        rangs.forEachIndexed { place, rang -> places[rang] = place }
    }

    override fun getLength(): Int = rangs.size

    override fun getNextIndex(index: Int): Int = rangs.getOrElse(places[index] + 1) { C.INDEX_UNSET }

    override fun getPreviousIndex(index: Int): Int = rangs.getOrElse(places[index] - 1) { C.INDEX_UNSET }

    override fun getLastIndex(): Int = rangs.lastOrNull() ?: C.INDEX_UNSET

    override fun getFirstIndex(): Int = rangs.firstOrNull() ?: C.INDEX_UNSET

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        val decales = rangs.map { if (it >= insertionIndex) it + insertionCount else it }
        // En tête quand rien ne les précède : `indexOf` rend alors -1.
        val place = decales.indexOf(insertionIndex - 1) + 1
        val ajoutes = List(insertionCount) { insertionIndex + it }
        return OrdreEnPlace((decales.take(place) + ajoutes + decales.drop(place)).toIntArray())
    }

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
        val retires = indexToExclusive - indexFrom
        return OrdreEnPlace(
            rangs.filter { it !in indexFrom until indexToExclusive }
                .map { if (it >= indexToExclusive) it - retires else it }
                .toIntArray(),
        )
    }

    override fun cloneAndClear(): ShuffleOrder = OrdreEnPlace(IntArray(0))

    companion object {
        /** L'ordre de lecture de [ordre], figé. */
        fun de(ordre: ShuffleOrder): OrdreEnPlace {
            val rangs = IntArray(ordre.length)
            var rang = ordre.firstIndex
            for (place in rangs.indices) {
                rangs[place] = rang
                rang = ordre.getNextIndex(rang)
            }
            return OrdreEnPlace(rangs)
        }
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
