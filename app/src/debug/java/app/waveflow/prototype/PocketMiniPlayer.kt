package app.waveflow.prototype

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.waveflow.model.orUnknownArtist
import app.waveflow.ui.components.Artwork
import app.waveflow.ui.player.PlayerUiState
import app.waveflow.ui.player.rememberGlidingProgress
import kotlin.math.abs

/**
 * Le mini-lecteur de Pocket : le lecteur rangé, avec les mêmes signes — la
 * pochette, le disque vert, le sillage.
 *
 * **Le haut du dock**, d'un seul tenant avec la barre de navigation : ni
 * carte, ni marges en double. Il n'a pas de fond à lui — le dock le porte. Son
 * bord haut est le sillage, une ligne de 2 dp sur toute la largeur, sans onde :
 * il reste à l'écran pendant toute la navigation, et rien n'y tourne sans
 * fin. Une rangée de 64 dp : la vignette, le titre, le disque, le suivant —
 * des cibles de 48 dp au moins.
 *
 * Un appui l'ouvre, un balayage vers le haut aussi ; un balayage horizontal
 * change de morceau.
 *
 * Ne se compose pas tant qu'aucun morceau n'est chargé.
 */
@Composable
fun PocketMiniPlayer(
    state: PlayerUiState,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.track ?: return
    val colors = Pocket.colors
    val avance by rememberGlidingProgress(state.positionMs, state.durationMs, track.mediaId, state.isPlaying)
    val ouvrir by rememberUpdatedState(onExpand)
    val suivant by rememberUpdatedState(onSkipNext)
    val precedent by rememberUpdatedState(onSkipPrevious)

    Column(
        modifier = modifier
            .fillMaxWidth()
            // Relancé à chaque morceau, comme le balayage de la pochette.
            .pointerInput(track.mediaId) {
                var total = Offset.Zero
                val seuil = MINI_SWIPE.toPx()
                detectDragGestures(
                    onDragStart = { total = Offset.Zero },
                    onDragEnd = {
                        when {
                            abs(total.y) > abs(total.x) && total.y <= -seuil -> ouvrir()
                            abs(total.x) > abs(total.y) && total.x <= -seuil -> suivant()
                            abs(total.x) > abs(total.y) && total.x >= seuil -> precedent()
                        }
                    },
                ) { change, glisse ->
                    change.consume()
                    total += glisse
                }
            }
            .clickable(onClickLabel = "Ouvrir le lecteur", onClick = onExpand)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Morceau précédent") { precedent().let { true } },
                )
            },
    ) {
        CompactSillage(
            progress = { avance },
            modifier = Modifier
                .fillMaxWidth()
                .height(PROGRESS_HEIGHT),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(start = 12.dp, end = 4.dp),
        ) {
            Artwork(
                artworkUri = track.artworkUri,
                shape = PocketShapes.thumbnail,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist.orUnknownArtist(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PlayButton(
                playing = state.isPlaying,
                buffering = state.isBuffering,
                onClick = onTogglePlayPause,
                size = 40.dp,
            )
            IconButton(onClick = onSkipNext) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Morceau suivant", tint = colors.ink)
            }
        }
    }
}

/** Le bord haut du dock : la ligne et sa tête, sans marge. */
private val PROGRESS_HEIGHT = 6.dp

/** La course d'un balayage du mini-lecteur. */
private val MINI_SWIPE = 48.dp
