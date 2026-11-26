package app.waveflow.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waveflow.ui.components.AccentPill
import app.waveflow.ui.components.Artwork
import app.waveflow.ui.components.GhostPill
import app.waveflow.ui.components.Kicker
import app.waveflow.ui.components.WaveMark
import app.waveflow.ui.components.glassEdge
import app.waveflow.ui.player.boundedGlowAlpha
import app.waveflow.ui.player.rememberPlayerLight
import app.waveflow.ui.theme.WaveFlowMaterial
import app.waveflow.ui.theme.WaveFlowTheme

/**
 * La bande « suggestion du jour », sous le canevas de l'accueil (S14 de
 * `docs/suggestion-du-jour.md`) : la `SuggestionOfTheDay` du bureau, ramenée à
 * la largeur d'un téléphone.
 *
 * Une bande sur la surface de la page, la pochette à gauche avec sa propre
 * lumière derrière elle en Fluid, posée comme un objet de verre en Liquid ; la
 * sorte dans la voix éditoriale (« Vous l'adoriez »), le morceau dans le corps
 * de titre, la raison en troisième encre, puis les deux actions. Elles passent
 * sous le texte plutôt qu'à côté : à côté, sur un téléphone, le titre n'aurait
 * plus la place d'un mot.
 */
@Composable
internal fun SuggestionOfTheDay(
    band: SuggestionBand,
    onPlay: () -> Unit,
    onAnother: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val material = WaveFlowTheme.material
    val shape = WaveFlowTheme.shapes.card
    val ground = MaterialTheme.colorScheme.surfaceContainerLow
    val glow = rememberPlayerLight(band.song.artworkUri).glow
    val song = band.song

    Surface(
        shape = shape,
        color = ground,
        modifier = modifier
            .fillMaxWidth()
            .glassEdge(shape, material, glow),
    ) {
        Column(
            modifier = Modifier
                .then(
                    if (material == WaveFlowMaterial.Fluid) {
                        Modifier.bandLight(glow, boundedGlowAlpha(glow, FLUID_AURA, ground, material))
                    } else {
                        Modifier
                    },
                )
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Kicker(SUGGESTION_LABEL, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.width(12.dp))
                WaveMark()
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(
                    artworkUri = song.artworkUri,
                    shape = WaveFlowTheme.shapes.tile,
                    modifier = Modifier
                        .size(COVER_SIZE)
                        .glassEdge(WaveFlowTheme.shapes.tile, material, glow),
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = band.kindLabel,
                        style = WaveFlowTheme.editorial.copy(fontSize = KIND_SIZE.sp, lineHeight = KIND_LINE.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // L'album se tait quand il porte le nom du morceau, comme
                    // sur le bureau.
                    val nomAlbum = song.album?.takeIf { it.isNotBlank() && it != song.title }
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                                append(song.displayArtist)
                            }
                            if (nomAlbum != null) append("  ·  $nomAlbum")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            band.reason?.let { raison ->
                Spacer(Modifier.height(14.dp))
                // Trois lignes : à police doublée, deux coupaient la date
                // d'ajout en son milieu (vu sur l'émulateur).
                Text(
                    text = raison,
                    style = MaterialTheme.typography.bodySmall,
                    color = WaveFlowTheme.colors.ink3,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(20.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                AccentPill(
                    text = "Écouter",
                    icon = Icons.Filled.PlayArrow,
                    onClick = onPlay,
                    modifier = Modifier.semantics { contentDescription = "Écouter ${song.title}" },
                )
                GhostPill(text = ANOTHER_LABEL, icon = Icons.Filled.Shuffle, onClick = onAnother)
            }
        }
    }
}

/**
 * La lumière de la pochette sous la moitié gauche de la bande (Fluid), comme
 * l'aura du bureau : centrée sur la pochette, éteinte avant le bord droit.
 */
private fun Modifier.bandLight(glow: Color, alpha: Float): Modifier = drawBehind {
    val rayon = size.width * AURA_RADIUS
    val centre = Offset(size.width * AURA_X, size.height * AURA_Y)
    drawCircle(
        brush = Brush.radialGradient(
            0f to glow.copy(alpha = alpha),
            1f to Color.Transparent,
            center = centre,
            radius = rayon,
        ),
        radius = rayon,
        center = centre,
    )
}

/** Le surtitre de la bande, celui du bureau. */
internal const val SUGGESTION_LABEL = "Suggestion du jour"

/** Le libellé du bureau en français. */
internal const val ANOTHER_LABEL = "Autre suggestion"

private val COVER_SIZE = 96.dp

/** La sorte : 1,375 rem, comme sur le bureau. */
private const val KIND_SIZE = 22
private const val KIND_LINE = 28

/** Un voile, comme l'aura du canevas : la bande reste une surface, pas une affiche. */
private const val FLUID_AURA = 0.22f
private const val AURA_X = 0.18f
private const val AURA_Y = 0.45f
private const val AURA_RADIUS = 0.7f
