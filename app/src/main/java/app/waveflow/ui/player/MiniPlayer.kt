package app.waveflow.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.waveflow.model.orUnknownArtist
import app.waveflow.ui.components.Artwork
import app.waveflow.ui.theme.LiquidGlassRim
import app.waveflow.ui.theme.WaveFlowMaterial
import app.waveflow.ui.theme.WaveFlowTheme

/**
 * Le lecteur réduit, posé au pied de l'écran au-dessus de la bibliothèque.
 *
 * Une plaque de la matière — un ton au-dessus de la page en Fluid, le verre
 * structurel en Liquid —, que la pochette éclaire d'un dégradé venu d'en
 * haut et dont la progression forme le bord haut : l'onde en sombre, la ligne
 * de lumière en clair. Comme le plein écran, elle prend l'accent de la
 * pochette, et lui seul.
 *
 * Un tap n'importe où sur la plaque ouvre le lecteur plein écran ; seules la
 * lecture/pause et le morceau suivant restent accessibles directement.
 * Ne s'affiche pas tant qu'aucun morceau n'est chargé.
 */
@Composable
fun MiniPlayer(
    state: PlayerUiState,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.track ?: return
    val light = rememberPlayerLight(track.artworkUri)
    val material = WaveFlowTheme.material
    val avance by rememberGlidingProgress(state.positionMs, state.durationMs, track.mediaId, state.isPlaying)
    val plaque = MaterialTheme.colorScheme.surfaceContainer
    // Bornée comme les nappes du plein écran : une pochette sombre ne doit pas
    // assombrir la plaque claire au point d'y noyer l'encre.
    val lueur = boundedGlowAlpha(light.glow, PLATE_GLOW_ALPHA, plaque, material)

    PlayerAccent(light) {
        Surface(
            shape = WaveFlowTheme.shapes.plate,
            color = plaque,
            // Ni ombre ni filet sombre : en Fluid la plaque se détache par son
            // ton ; en Liquid par la lumière que prend son bord, un liseré
            // blanc.
            border = if (material == WaveFlowMaterial.Liquid) BorderStroke(1.dp, LiquidGlassRim) else null,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Column(
                modifier = Modifier
                    .clickable(onClick = onExpand)
                    // « Cette surface appartient à ce qui joue » : la lumière de
                    // la pochette descend du haut de la plaque et s'éteint avant
                    // sa moitié.
                    .background(
                        Brush.verticalGradient(
                            0f to light.glow.copy(alpha = lueur),
                            PLATE_GLOW_END to Color.Transparent,
                        ),
                    ),
            ) {
                // Le bord haut : la progression, retirée des coins arrondis.
                ProgressLine(
                    progress = { avance },
                    playing = state.isPlaying,
                    compact = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(8.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 6.dp, bottom = 8.dp),
                ) {
                    Artwork(
                        artworkUri = track.artworkUri,
                        modifier = Modifier.size(44.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = track.artist.orUnknownArtist(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // Pendant l'attente, l'indicateur prend la place du bouton :
                    // une piste distante met le temps d'obtenir son ticket avant
                    // le moindre son, et rien ne le disait.
                    if (state.isBuffering) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(48.dp).semantics {
                                contentDescription = "Chargement du morceau"
                            },
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    } else {
                        // L'action principale, pleine dans l'accent comme au
                        // plein écran.
                        FilledIconButton(
                            onClick = onTogglePlayPause,
                            shape = WaveFlowTheme.shapes.pill,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Lecture",
                            )
                        }
                    }
                    IconButton(onClick = onSkipNext) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Morceau suivant",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/** La lumière de la pochette en haut de la plaque : 28 %, comme la barre du bureau. */
private const val PLATE_GLOW_ALPHA = 0.28f

/** Où ce dégradé s'éteint, en part de la hauteur. */
private const val PLATE_GLOW_END = 0.55f
