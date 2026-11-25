package app.waveflow.ui.player

import app.waveflow.playback.AbLoopState

/**
 * Les bornes d'une boucle A-B, en part de la durée du morceau.
 *
 * @property start A.
 * @property end B, `null` tant qu'il n'est pas posé.
 */
data class LoopMarks(val start: Float, val end: Float?)

/**
 * Les bornes de [abLoop] à dessiner sur la progression de [mediaId], ou
 * `null` s'il n'y a rien à dessiner : pas de boucle, une durée inconnue, ou
 * une boucle posée sur un autre morceau — le service l'efface au changement
 * de piste, mais l'écran peut recevoir la nouvelle piste un tic avant.
 */
fun loopMarks(abLoop: AbLoopState, mediaId: String, durationMs: Long): LoopMarks? {
    if (durationMs <= 0L || abLoop.mediaId != mediaId) return null
    fun part(ms: Long) = (ms.toFloat() / durationMs).coerceIn(0f, 1f)
    return when (abLoop) {
        AbLoopState.Off -> null
        is AbLoopState.Started -> LoopMarks(part(abLoop.startMs), null)
        is AbLoopState.Armed -> LoopMarks(part(abLoop.startMs), part(abLoop.endMs))
    }
}
