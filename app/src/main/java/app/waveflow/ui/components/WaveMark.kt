package app.waveflow.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Le motif de l'onde, seul ornement que la direction permet.
 *
 * Une courte ligne qui ondule — le tracé du document, `M0.5 5 C 5 0.5, 9 0.5,
 * 13.5 5 S 22 9.5, 26.5 5 S 35 0.5, 39.5 5` dans une boîte de 40 × 10 —, à
 * 1,6 dp dans l'accent. Il souligne un titre de section, marque l'élément
 * ouvert ; il ne remplit jamais rien. Ce n'est pas le symbole : le W ne sert
 * jamais de soulignement, et ce motif ne tient jamais lieu du produit.
 *
 * Décoratif : il n'a pas de sémantique, le titre qu'il souligne porte le sens.
 */
@Composable
fun WaveMark(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(modifier = modifier.size(MARK_WIDTH.dp, MARK_HEIGHT.dp)) {
        val echelle = markScale(size.width, size.height)
        if (echelle <= 0f) return@Canvas
        // Le trait est donné en dp à la taille dessinée, pas à l'échelle de
        // la boîte : agrandi, le motif garde son épaisseur de filet.
        val trait = Stroke(width = MARK_STROKE.dp.toPx() / echelle, cap = StrokeCap.Round)
        // Centré dans la boîte quand elle n'a pas les proportions du motif.
        translate(
            left = (size.width - MARK_WIDTH * echelle) / 2f,
            top = (size.height - MARK_HEIGHT * echelle) / 2f,
        ) {
            scale(echelle, pivot = Offset.Zero) {
                drawPath(MarkPath, color = color, style = trait)
            }
        }
    }
}

/**
 * L'échelle du motif dans une boîte de [width] × [height] : la plus grande à
 * laquelle il y tient entier, sans déformation.
 */
internal fun markScale(width: Float, height: Float): Float = min(width / MARK_WIDTH, height / MARK_HEIGHT)

/** Le tracé du document, ses `S` dépliés en `C` (le point de contrôle reflété). */
private val MarkPath = Path().apply {
    moveTo(0.5f, 5f)
    cubicTo(5f, 0.5f, 9f, 0.5f, 13.5f, 5f)
    cubicTo(18f, 9.5f, 22f, 9.5f, 26.5f, 5f)
    cubicTo(31f, 0.5f, 35f, 0.5f, 39.5f, 5f)
}

private const val MARK_WIDTH = 40f
private const val MARK_HEIGHT = 10f
private const val MARK_STROKE = 1.6f
