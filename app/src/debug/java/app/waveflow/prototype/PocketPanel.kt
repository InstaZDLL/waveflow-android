package app.waveflow.prototype

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.waveflow.model.orUnknownArtist
import app.waveflow.ui.components.Artwork
import app.waveflow.ui.components.WaveFlowSymbol
import app.waveflow.ui.player.PlayerUiState
import app.waveflow.ui.player.loopMarks

/**
 * Le panneau de lecture des fenêtres étendues : à droite de la page, ce qui
 * joue — sa pochette sur sa lumière, son titre, le sillage, les commandes —,
 * et **la suite de la file**, que le téléphone range dans une feuille. Il
 * remplace le dock : une tablette en paysage a la place de montrer la lecture
 * sans la cacher derrière un geste.
 *
 * Un appui sur la pochette ouvre le lecteur plein écran (paroles, vitesse,
 * minuterie, boucle A-B) ; un appui sur un morceau de la suite le joue.
 */
@Composable
fun PocketPlaybackPanel(
    state: PlayerUiState,
    actions: NowPlayingActions,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.track ?: return
    val colors = Pocket.colors
    val nappe = rememberAtmosphere(track.artworkUri)
    // Dans l'ordre de la file, la suite ; en aléatoire ou en répétition, la
    // file ne dit pas ce qui jouera ensuite : toute la file, le morceau courant
    // marqué.
    val ordre = followsQueueOrder(state)
    val depart = if (ordre) state.queueIndex + 1 else 0
    val suite = state.queue.drop(depart.coerceAtLeast(0))

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .background(colors.raised)
            // La lumière de la pochette descend du haut du panneau, comme au
            // bord haut du pont du lecteur.
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0f to nappe.copy(alpha = DECK_GLOW_ALPHA),
                        PANEL_GLOW_END to Color.Transparent,
                    ),
                )
            }
            .windowInsetsPadding(
                WindowInsets.statusBars
                    .union(WindowInsets.navigationBars)
                    .union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Vertical + WindowInsetsSides.End),
            )
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        val cote = panelCoverSide(maxWidth, maxHeight)
        // Une seule liste qui défile : à police agrandie, ou dans une fenêtre
        // basse, ce qui joue ne repousse jamais la suite hors d'atteinte.
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "lecture") {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WaveFlowSymbol(playing = state.isPlaying, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("EN LECTURE", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                        Spacer(Modifier.weight(1f))
                        Text(
                            playbackContext(state, track),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.ink3,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Artwork(
                        artworkUri = track.artworkUri,
                        shape = PocketShapes.cover,
                        modifier = Modifier
                            .size(cote)
                            .shadow(20.dp, PocketShapes.cover, ambientColor = nappe, spotColor = nappe)
                            .clip(PocketShapes.cover)
                            .clickable(role = Role.Button, onClickLabel = "Ouvrir le lecteur", onClick = onExpand)
                            .semantics { contentDescription = "Pochette" },
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        track.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(track.artist.orUnknownArtist(), track.album).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Sillage(
                        positionMs = state.positionMs,
                        durationMs = state.durationMs,
                        trackKey = track.mediaId,
                        playing = state.isPlaying,
                        onSeek = actions.onSeek,
                        loop = loopMarks(state.abLoop, track.mediaId, state.durationMs),
                    )
                    Transport(state, actions)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (ordre) "À SUIVRE" else "LA FILE",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.ink3,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (ordre && suite.isEmpty()) {
                        Text(
                            "Fin de la file.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.ink2,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }
            itemsIndexed(suite, key = { i, piste -> "${piste.mediaId}-$i" }) { i, piste ->
                TrackRow(
                    title = piste.title,
                    subtitle = piste.artist.orUnknownArtist(),
                    durationMs = 0L,
                    current = depart + i == state.queueIndex,
                    artworkUri = piste.artworkUri,
                    onClick = { actions.onPlayQueueItem(depart + i) },
                    horizontalPadding = 0.dp,
                    showDuration = false,
                )
            }
        }
    }
}

/**
 * Le côté de la pochette du panneau : toute sa largeur quand la hauteur le
 * permet, sinon ce que lui laissent le titre, le sillage, les commandes et le
 * début de la suite — dans une fenêtre étendue basse, la pochette rétrécit
 * plutôt que de pousser les commandes hors du panneau.
 */
fun panelCoverSide(width: Dp, height: Dp): Dp =
    minOf(width, (height - PANEL_RESERVED).coerceAtLeast(PANEL_COVER_MIN))

/** La largeur du panneau de lecture. */
val PANEL_WIDTH = 360.dp

private const val PANEL_GLOW_END = 0.35f

/** Ce que le panneau garde sous la pochette : titre sur deux lignes, sillage, commandes, début de la suite. */
private val PANEL_RESERVED = 380.dp
private val PANEL_COVER_MIN = 96.dp
