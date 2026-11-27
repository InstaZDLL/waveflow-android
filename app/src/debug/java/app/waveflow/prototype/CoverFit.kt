package app.waveflow.prototype

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * La place que Now Playing donne à la pochette.
 *
 * La pochette n'a pas de taille : elle reçoit la hauteur que le reste de
 * l'écran lui laisse, plafonnée à la largeur et à [COVER_CAP]. Sous
 * [COMPACT_BELOW] — une fenêtre basse, une police agrandie —, l'écran passe
 * en disposition compacte plutôt que de montrer un timbre-poste au-dessus de
 * commandes coupées.
 */
sealed interface CoverFit {
    /** La pochette en grand, carrée, de côté [side]. */
    data class Full(val side: Dp) : CoverFit

    /** La pochette en vignette à côté du titre ; l'écran défile au besoin. */
    data object Compact : CoverFit
}

/**
 * Où va la pochette dans une zone de [width] × [height], quand le titre, la
 * progression, les commandes et le dock mesurent [details] de haut et que
 * [gap] les sépare de la pochette.
 */
fun fitCover(
    width: Dp,
    height: Dp,
    details: Dp,
    gap: Dp,
    cap: Dp = COVER_CAP,
    compactBelow: Dp = COMPACT_BELOW,
): CoverFit {
    val side = minOf(width, height - details - gap, cap)
    return if (side < compactBelow) CoverFit.Compact else CoverFit.Full(side)
}

/**
 * Deux volets — la pochette à gauche, le reste à droite — dans une fenêtre
 * en paysage assez large pour les porter.
 */
fun usesTwoPanes(width: Dp, height: Dp): Boolean = width > height && width >= TWO_PANES_MIN_WIDTH

/** Plafond de la pochette : au-delà, sur une tablette, elle écrase le titre. */
val COVER_CAP = 520.dp

/**
 * Sous ce côté, la pochette passe en vignette. Bas à dessein : une pochette
 * de 190 dp au-dessus des commandes (un petit téléphone) vaut mieux qu'une
 * vignette sous un grand vide ; la vignette ne sert qu'aux fenêtres basses.
 */
val COMPACT_BELOW = 120.dp

/** Le côté de la vignette en disposition compacte. */
val COMPACT_COVER = 96.dp

private val TWO_PANES_MIN_WIDTH = 560.dp
