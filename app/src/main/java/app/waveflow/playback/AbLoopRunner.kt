package app.waveflow.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Ramène la lecture en A chaque fois qu'elle dépasse B.
 *
 * Media3 **n'a pas** de « répéter entre deux points » : `REPEAT_MODE_ONE` reprend
 * la piste entière. Il faut donc échantillonner la position et rembobiner
 * soi-même, ce qui place le mécanisme du côté du lecteur — le service — et non
 * de l'interface, qui n'est pas là quand l'écran est éteint.
 *
 * **Le pas d'échantillonnage se règle sur ce qui reste à parcourir.** Un pas
 * fixe obligerait à choisir entre un dépassement audible et un réveil toutes les
 * quelques dizaines de millisecondes pendant des minutes. Ici on dort le temps
 * qui reste avant B, borné des deux côtés : loin de la borne le réveil est rare,
 * près d'elle il se resserre. [PAS_MAX] garde la main sur les sauts de position
 * — un `seek` de l'utilisateur, ou une piste qui n'avance pas comme prévu —, et
 * [PAS_MIN] empêche de tourner à vide quand la position stagne.
 *
 * Le dépassement résiduel tient donc à [PAS_MIN] : quelques dizaines de
 * millisecondes, en deçà de ce qu'on distingue d'un rembobinage instantané.
 *
 * La position, le rembobinage et l'état de lecture sont **reçus** et non pris
 * sur un `Player` : c'est ce qui rend la boucle éprouvable sur la JVM. Sous
 * Robolectric, faute de codec, la position n'avance pas et `isPlaying` reste
 * faux — un test qui passerait par un vrai lecteur ne prouverait rien.
 *
 * @param isPlaying vrai tant que la lecture avance. **Rien n'est échantillonné
 *   quand elle est fausse** : une position à l'arrêt reste éternellement sous B,
 *   et la surveillance réveillerait le service toutes les [PAS_MAX] — toutes les
 *   [PAS_MIN] si l'on a mis en pause juste avant la borne — pour constater
 *   qu'aucune position n'a bougé. Une pause dure ce que dure une pause : le
 *   coût, lui, ne s'arrête pas.
 * @param positionMs la position de lecture, lue à l'instant de la demande.
 * @param seekTo rembobine. Appelé sur le fil d'où tourne [scope], que Media3
 *   exige principal.
 */
internal class AbLoopRunner(
    private val scope: CoroutineScope,
    private val loop: AbLoop,
    private val isPlaying: StateFlow<Boolean>,
    private val positionMs: () -> Long,
    private val seekTo: (Long) -> Unit,
) {

    /**
     * Surveille tant que [scope] vit.
     *
     * `collectLatest` : une boucle remplacée — bornes refaites, piste changée,
     * lecture suspendue — n'a plus à être surveillée, et sa coroutine tombe avec
     * l'état qui l'a lancée. C'est aussi ce qui arrête l'échantillonnage à la
     * pause sans avoir à le demander : la surveillance n'est pas mise en attente,
     * elle est annulée, puis relancée à la reprise.
     */
    fun start() {
        scope.launch {
            combine(loop.state, isPlaying) { etat, joue -> etat.takeIf { joue } }
                .collectLatest { etat ->
                    if (etat is AbLoopState.Armed) surveiller(etat)
                }
        }
    }

    /**
     * Rembobine tant que [armee] est la boucle en vigueur et que la lecture avance.
     *
     * L'état est **relu après chaque sommeil**, et pas seulement à l'entrée.
     * C'est la garde contre le défaut que ce dépôt a vu revenir trois fois :
     * entre le réveil du `delay` et les lignes qui suivent, la coroutine est
     * déjà repartie, et l'annulation de `collectLatest` ne la rattrape plus.
     * Sans cette relecture, une boucle effacée à cet instant précis rembobinerait
     * une dernière fois — au milieu d'un morceau que l'utilisateur venait de
     * libérer.
     *
     * **Cette garde est posée et non éprouvée.** La course qu'elle ferme ne se
     * reproduit pas sous `runTest`, dont l'ordonnanceur est mono-fil : c'est
     * `collectLatest` qui y annule la surveillance avant tout réveil, si bien
     * qu'un test écrit pour elle passe aussi une fois la garde retirée. Même
     * conclusion que pour [SleepTimer], et même refus d'un test qui laisserait
     * croire la zone couverte.
     */
    private suspend fun surveiller(armee: AbLoopState.Armed) {
        while (scope.isActive) {
            val position = positionMs()

            if (position >= armee.endMs) {
                if (!enVigueur(armee)) return
                seekTo(armee.startMs)
                // Repartir de A plutôt que de relire : la position mettrait un
                // instant à refléter le rembobinage, et ce délai ferait
                // reboucler une seconde fois.
                delay(PAS_MIN)
                continue
            }

            delay((armee.endMs - position).coerceIn(PAS_MIN, PAS_MAX))

            if (!enVigueur(armee)) return
        }
    }

    /**
     * [armee] commande-t-elle encore le lecteur ?
     *
     * Les deux conditions sont lues **ensemble et au dernier moment** : une
     * boucle effacée et une lecture mise en pause interdisent l'une comme
     * l'autre de rembobiner, et rien ne dit laquelle des deux est arrivée
     * pendant le sommeil.
     */
    private fun enVigueur(armee: AbLoopState.Armed) =
        loop.state.value == armee && isPlaying.value

    private companion object {
        /**
         * Le dépassement qu'on s'autorise au plus près de B.
         *
         * Cinquante millisecondes : sous le seuil où l'oreille distingue le
         * rembobinage d'une coupure nette, et assez pour ne pas réveiller le
         * processeur en pure perte.
         */
        const val PAS_MIN = 50L

        /**
         * Le sommeil le plus long, même si B est loin.
         *
         * La position peut sauter sans prévenir — l'utilisateur déplace le
         * curseur, la piste bute sur son tampon. Se rendormir jusqu'à B sur la
         * foi d'une position d'il y a une minute laisserait la boucle muette
         * tout ce temps.
         */
        const val PAS_MAX = 500L
    }
}
