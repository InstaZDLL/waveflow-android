package app.waveflow.ui.browse

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.waveflow.ui.components.AccentPill
import app.waveflow.ui.components.Artwork
import app.waveflow.ui.components.GhostPill
import app.waveflow.ui.components.Kicker
import app.waveflow.ui.components.cappedWidthFraction
import app.waveflow.ui.components.glassEdge
import app.waveflow.ui.formatDuration
import app.waveflow.ui.theme.WaveFlowMaterial
import app.waveflow.ui.theme.WaveFlowTheme
import app.waveflow.ui.trackCountLabel

/**
 * L'en-tête d'une page de détail — album, artiste, playlist, locaux ou du
 * serveur —, composé comme celui du bureau : la pochette, un surtitre, le
 * titre en affichage, **une** ligne de méta, puis l'action principale en
 * pilule d'accent et la lecture aléatoire en pilule fantôme. Ni carte autour,
 * ni ligne en italique : le surtitre et le titre portent la page.
 *
 * Aligné à gauche, comme une page éditoriale, et non centré comme une fiche.
 *
 * @param kicker ce qu'est la page : « Album », « Artiste », « Playlist ».
 * @param subtitle le premier élément de la méta, dans la seconde encre
 *   (l'artiste d'un album, le nombre d'albums d'un artiste) ; les compteurs
 *   le suivent dans la troisième.
 * @param trackCount le nombre de morceaux ; nul pour n'en rien dire (un
 *   artiste distant, dont l'écran ne charge que les albums).
 * @param onPlay `null` pour une page sans file à lancer : les deux pilules
 *   disparaissent alors ensemble.
 * @param margins la marge de la page autour de l'en-tête ; une grille qui pose
 *   déjà la sienne n'en garde que le haut et le bas.
 */
@Composable
fun DetailHeader(
    artworkUri: Uri?,
    kicker: String,
    title: String,
    subtitle: String?,
    trackCount: Int?,
    durationMs: Long,
    onPlay: (() -> Unit)?,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    artworkShape: Shape = WaveFlowTheme.shapes.cover,
    playEnabled: Boolean = true,
    margins: PaddingValues = PaddingValues(start = GUTTER, end = GUTTER, top = 16.dp, bottom = 28.dp),
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(margins),
    ) {
        HeroCover(artworkUri = artworkUri, shape = artworkShape)

        Spacer(Modifier.height(28.dp))
        Kicker(kicker)
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(14.dp))
        MetaLine(subtitle = subtitle, trackCount = trackCount, durationMs = durationMs)

        if (onPlay != null) {
            Spacer(Modifier.height(24.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AccentPill(text = "Lecture", icon = Icons.Filled.PlayArrow, onClick = onPlay, enabled = playEnabled)
                GhostPill(text = "Aléatoire", icon = Icons.Filled.Shuffle, onClick = onShuffle, enabled = playEnabled)
            }
        }
    }
}

/**
 * La ligne de méta : le sous-titre dans la seconde encre, les compteurs dans
 * la troisième, en chiffres tabulaires.
 */
@Composable
private fun MetaLine(subtitle: String?, trackCount: Int?, durationMs: Long) {
    val compteurs = buildList {
        if (trackCount != null) add(trackCountLabel(trackCount))
        if (durationMs > 0L) add(formatDuration(durationMs))
    }.joinToString(SEPARATOR)
    val tete = subtitle?.takeIf { it.isNotBlank() }
    if (tete == null && compteurs.isEmpty()) return

    Text(
        text = buildAnnotatedString {
            if (tete != null) append(tete)
            if (compteurs.isNotEmpty()) {
                withStyle(SpanStyle(color = WaveFlowTheme.colors.ink3)) {
                    if (tete != null) append(SEPARATOR)
                    append(compteurs)
                }
            }
        },
        style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * La pochette d'en-tête, sur sa lumière : une ombre teintée en Fluid — la
 * seule ombre que la direction permet, sous une pochette —, l'objet de verre
 * en Liquid.
 */
@Composable
private fun HeroCover(artworkUri: Uri?, shape: Shape) {
    val material = WaveFlowTheme.material
    val colors = WaveFlowTheme.colors
    val bord = when (material) {
        WaveFlowMaterial.Fluid -> Modifier.shadow(
            COVER_LIFT,
            shape,
            ambientColor = colors.glow,
            spotColor = colors.glow,
        )
        WaveFlowMaterial.Liquid -> Modifier.glassEdge(shape, material, colors, COVER_LIFT)
    }
    Artwork(
        artworkUri = artworkUri,
        shape = shape,
        modifier = Modifier
            .cappedWidthFraction(COVER_FRACTION, COVER_MAX)
            .aspectRatio(1f)
            .then(bord),
    )
}

private const val SEPARATOR = "  ·  "

/** La marge de la page. */
private val GUTTER = 24.dp

/** La pochette d'en-tête : un peu plus de la moitié de la largeur. */
private const val COVER_FRACTION = 0.56f
private val COVER_MAX = 280.dp
private val COVER_LIFT = 20.dp
