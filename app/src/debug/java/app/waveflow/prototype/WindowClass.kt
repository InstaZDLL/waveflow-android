package app.waveflow.prototype

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Les trois compositions de Pocket, choisies par la **largeur de la fenêtre**
 * — ni par le nom de l'appareil, ni par l'orientation seule : un pliable
 * ouvert, une tablette en portrait et une fenêtre partagée tombent chacun dans
 * la classe que leur largeur leur donne.
 *
 * Les seuils sont ceux des classes de fenêtre de Material (600 et 840 dp).
 * `androidx.window:window-core`, qui les porte, n'est sur le classpath de
 * l'application qu'à l'exécution (tiré par material3) : plutôt que d'ajouter
 * une dépendance au prototype, il les relit ici sur `LocalWindowInfo`, que
 * Compose fournit.
 */
enum class WindowClass {
    /** Un téléphone : la barre du bas et le dock du lecteur. */
    Compact,

    /** Une tablette en portrait, un pliable ouvert : le rail de navigation. */
    Medium,

    /** Une tablette en paysage, un grand écran : le rail, et le panneau de lecture. */
    Expanded,
}

/** La classe d'une fenêtre de [width] de large. */
fun windowClassOf(width: Dp): WindowClass = when {
    width < MEDIUM_MIN -> WindowClass.Compact
    width < EXPANDED_MIN -> WindowClass.Medium
    else -> WindowClass.Expanded
}

/**
 * Le panneau de lecture n'a sa place qu'en fenêtre étendue **assez haute** pour
 * porter une pochette, un titre et des commandes : un téléphone en paysage, large
 * mais bas, garde le dock.
 */
fun showsPlaybackPanel(width: Dp, height: Dp): Boolean =
    windowClassOf(width) == WindowClass.Expanded && height >= PANEL_MIN_HEIGHT

/** La largeur et la hauteur de la fenêtre, en dp. */
@Composable
fun rememberWindowSize(): Pair<Dp, Dp> {
    val taille = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { taille.width.toDp() to taille.height.toDp() }
}

private val MEDIUM_MIN = 600.dp
private val EXPANDED_MIN = 840.dp
private val PANEL_MIN_HEIGHT = 560.dp
