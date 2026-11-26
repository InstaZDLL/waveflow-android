package app.waveflow.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Décide qu'une piste a été écoutée, et non seulement traversée.
 *
 * Le compte n'avance que pendant la lecture. Un `delay` posé au changement de
 * piste compterait aussi bien le temps passé en pause, le tampon qui se
 * remplit, ou une piste seulement préparée que personne n'a lancée — trois
 * façons d'inscrire dans l'historique ce que l'utilisateur n'a pas entendu.
 *
 * Le temps déjà accompli est retenu : mettre en pause à quinze secondes puis
 * reprendre demande cinq secondes de plus, pas vingt. Changer de piste, en
 * revanche, remet tout à zéro — c'est une autre écoute.
 *
 * Une piste déjà comptée ne l'est pas deux fois tant qu'on ne la quitte pas :
 * revenir en arrière dans un morceau ne le fait pas compter davantage.
 *
 * Le minuteur ne fait que réveiller le compteur : c'est le temps mesuré par
 * [nowMs] qui décide. Une pause ou un changement de piste traité quand ce temps
 * a atteint le seuil compte la piste, même si le minuteur n'a pas encore sonné.
 *
 * Ce temps se mesure entre les **livraisons** des événements, pas entre les
 * instants où le lecteur a commencé et cessé de jouer. Sur une boucle
 * encombrée, une lecture annoncée en retard raccourcit donc l'écoute mesurée
 * — une écoute réelle de plus de vingt secondes peut encore être perdue — et un
 * enchaînement annoncé en retard l'allonge : une piste de moins de vingt
 * secondes peut être comptée. Seule une mesure sur la position du lecteur
 * lèverait ces deux écarts (#99).
 *
 * @param nowMs horloge injectée pour que les tests mesurent le même temps que
 *   celui où leurs `delay` s'écoulent.
 */
internal class ListeningCounter(
    private val scope: CoroutineScope,
    private val thresholdMs: Long,
    private val nowMs: () -> Long,
    private val onListened: suspend (mediaId: String) -> Unit,
) {

    private var mediaId: String? = null
    private var isPlaying = false

    /** Ce qu'il reste à écouter avant de compter ; `0` une fois compté. */
    private var remainingMs = 0L

    private var job: Job? = null
    private var startedAt = 0L

    /** Une autre piste : le compte repart de zéro. */
    fun trackChanged(mediaId: String?) {
        suspend()
        this.mediaId = mediaId
        remainingMs = thresholdMs
        // Passer d'une piste à l'autre ne rappelle pas la lecture : si elle
        // était en cours, elle l'est toujours, et le compte doit repartir seul.
        if (isPlaying) resume()
    }

    fun playingChanged(isPlaying: Boolean) {
        this.isPlaying = isPlaying
        if (isPlaying) resume() else suspend()
    }

    private fun resume() {
        val id = mediaId ?: return
        if (job != null || remainingMs <= 0L) return

        val restant = remainingMs
        startedAt = nowMs()
        job = scope.launch {
            delay(restant)
            remainingMs = 0L
            job = null
            onListened(id)
        }
    }

    private fun suspend() {
        val enCours = job ?: return
        job = null
        enCours.cancel()
        remainingMs -= nowMs() - startedAt
        // Le temps mesuré décide, le minuteur ne fait que réveiller. Sur une
        // boucle encombrée, une pause ou un changement de piste posté avant le
        // seuil peut être traité après lui, devant le minuteur rangé derrière :
        // le temps mesuré a atteint le seuil, la piste est comptée.
        val id = mediaId
        if (remainingMs <= 0L && id != null) {
            remainingMs = 0L
            scope.launch { onListened(id) }
        }
    }
}
