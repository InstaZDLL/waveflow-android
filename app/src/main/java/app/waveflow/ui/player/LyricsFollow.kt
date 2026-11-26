package app.waveflow.ui.player

import app.waveflow.model.SyncedLine

/**
 * La ligne chantée à [positionMs] : la dernière dont le début est atteint, `-1`
 * avant la première (`docs/paroles.md`, P12).
 *
 * [lines] est rangée par début croissant ; entre deux lignes qui commencent au
 * même instant, la dernière l'emporte — elle est celle que le texte place
 * après.
 */
fun currentLineIndex(lines: List<SyncedLine>, positionMs: Long): Int {
    var bas = 0
    var haut = lines.size - 1
    var trouve = -1
    while (bas <= haut) {
        val milieu = (bas + haut) ushr 1
        if (lines[milieu].startMs <= positionMs) {
            trouve = milieu
            bas = milieu + 1
        } else {
            haut = milieu - 1
        }
    }
    return trouve
}

/**
 * La position entre deux tics de lecture (P13) : la dernière position reçue,
 * prolongée du temps écoulé depuis à la vitesse de lecture, **sans prolonger
 * au-delà d'un tic** — un lecteur qui cale sans le dire ne fait pas défiler des paroles
 * qu'il ne chante pas. Un temps négatif (horloge qui recule) ne recule pas la
 * position.
 */
fun extrapolatedPositionMs(lastPositionMs: Long, elapsedMs: Long, speed: Float): Long {
    val avance = (elapsedMs.coerceIn(0L, POSITION_TICK_MS) * speed).toLong()
    return lastPositionMs + avance
}

/**
 * L'intervalle entre deux positions publiées par le lecteur pendant la lecture.
 * Celui de `Media3PlaybackController` : la prolongation ne va pas au-delà.
 */
const val POSITION_TICK_MS = 500L
