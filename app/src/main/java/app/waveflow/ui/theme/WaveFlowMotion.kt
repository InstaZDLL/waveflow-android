package app.waveflow.ui.theme

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Le mouvement de WaveFlow, commun aux deux matières : une seule courbe, trois
 * durées, une apparition. Ni ressort ni rebond.
 *
 * Quand l'utilisateur a retiré les animations (*Accessibilité ▸ Supprimer les
 * animations*, qui met à zéro l'échelle des animations), [reduced] est vrai et
 * il ne reste que l'opacité : ce qui se déplace ou change de forme saute à sa
 * place, l'apparition ne monte plus et ne se décale plus d'un bloc à l'autre.
 * Compose écourte déjà ses propres animations à cette échelle ; [reduced] sert
 * à ce qu'il ne voit pas — une boucle dessinée image par image, une onde, une
 * dérive — que leur auteur doit arrêter lui-même.
 *
 * @property reduced vrai quand le système demande de supprimer les animations.
 */
@Immutable
class WaveFlowMotion internal constructor(val reduced: Boolean) {

    /** La courbe unique, `cubic-bezier(0.22, 1, 0.36, 1)` : un départ vif, une arrivée douce. */
    val easing: Easing get() = Ease

    /** Couleur et ton (180 ms). Un fondu : il reste sous réduction. */
    fun <T> tone(): FiniteAnimationSpec<T> = tween(TONE_MS, easing = Ease)

    /** Position et forme (320 ms). Rien sous réduction : l'élément saute à sa place. */
    fun <T> shape(): FiniteAnimationSpec<T> = if (reduced) snap() else tween(SHAPE_MS, easing = Ease)

    /** Une lumière qui s'allume (900 ms) : la couleur d'une pochette. Un fondu, gardé. */
    fun <T> light(): FiniteAnimationSpec<T> = tween(LIGHT_MS, easing = Ease)

    /** Hauteur dont monte un bloc qui apparaît ; nulle sous réduction. */
    val revealOffset: Dp get() = if (reduced) 0.dp else REVEAL_OFFSET

    /**
     * L'apparition du bloc de rang [index] sur une page : 640 ms, chaque bloc
     * 70 ms après le précédent. Sous réduction, tous ensemble.
     */
    fun <T> reveal(index: Int = 0): FiniteAnimationSpec<T> =
        tween(REVEAL_MS, delayMillis = if (reduced) 0 else index * STAGGER_MS, easing = Ease)

    internal companion object {
        val Ease = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
        const val TONE_MS = 180
        const val SHAPE_MS = 320
        const val LIGHT_MS = 900
        const val REVEAL_MS = 640
        const val STAGGER_MS = 70
        val REVEAL_OFFSET = 10.dp
    }
}

/**
 * Suit le réglage « Supprimer les animations » pendant que l'application est
 * ouverte : le changer depuis les réglages rapides s'applique sans relancer.
 */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var reduced by remember(resolver) { mutableStateOf(readReducedMotion(resolver)) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = readReducedMotion(resolver)
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        // Relu une fois l'observateur posé : un changement tombé entre la
        // lecture de `remember` et l'abonnement ne serait jamais signalé.
        reduced = readReducedMotion(resolver)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

private fun readReducedMotion(resolver: ContentResolver): Boolean =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
