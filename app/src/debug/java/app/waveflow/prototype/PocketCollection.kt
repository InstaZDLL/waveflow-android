package app.waveflow.prototype

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.waveflow.model.Song
import app.waveflow.ui.components.Artwork
import app.waveflow.ui.player.PlayerUiState
import app.waveflow.ui.theme.WaveFlowTheme
import app.waveflow.ui.trackCountLabel

/** Ce qu'est la page : elle se dessine un peu autrement pour chacun. */
enum class CollectionKind(val label: String) {
    Album("Album"),
    Artiste("Artiste"),
    Playlist("Playlist"),
}

/** Ce que la page d'une collection sait faire. */
data class CollectionActions(
    val onBack: () -> Unit,
    val onPlay: (queue: List<Song>, song: Song) -> Unit,
    val onShuffle: (List<Song>) -> Unit,
    val rowMenu: (Song) -> List<RowAction>,
)

/**
 * La page d'un album — et, avec la même grammaire, celle d'un artiste ou
 * d'une playlist (`docs/design/sonic-editorial.md`, « Album »).
 *
 * La pochette en grand sur sa nappe, le nom en titre d'écran, une ligne
 * « Album · 9 titres · 42 min », **Lire** (l'accent) et **Aléatoire** (un ton),
 * puis les pistes — sans pochette répétée sur la page d'un album. En
 * défilant, la pochette recule et pâlit avec le geste (vers son bord bas, sans
 * mordre sur le titre), et le nom passe dans
 * la barre du haut : rien ne saute, tout suit le doigt. Sous « Supprimer les
 * animations », la pochette défile simplement, et seul le nom paraît en fondu.
 *
 * Pas de numéro de piste ni d'année : l'application ne les lit pas dans le
 * MediaStore, et le prototype ne les invente pas. Les pistes sont dans l'ordre
 * de la bibliothèque.
 *
 * @param songs les pistes ; vide quand la collection a disparu de l'appareil.
 */
@Composable
fun PocketCollection(
    kind: CollectionKind,
    title: String,
    subtitle: String?,
    artworkUri: Uri?,
    songs: List<Song>,
    player: PlayerUiState,
    actions: CollectionActions,
    modifier: Modifier = Modifier,
) {
    val colors = Pocket.colors
    val liste = rememberLazyListState()
    val reduit = WaveFlowTheme.motion.reduced
    val nappe = rememberAtmosphere(artworkUri)
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxSize().background(colors.canvas)) {
        val gouttiere = if (maxWidth < NARROW_WIDTH) 20.dp else 24.dp
        val large = maxWidth >= SIDE_BY_SIDE
        val cote = coverSideFor(maxWidth, maxHeight, gouttiere, large)
        val repli = with(density) { cote.toPx() }
        // Le nom passe dans la barre une fois l'en-tête presque sorti.
        val barre by remember {
            derivedStateOf { collapseProgress(liste.firstVisibleItemIndex, liste.firstVisibleItemScrollOffset, repli) }
        }
        val titreVisible by animateFloatAsState(
            if (barre >=
                1f
            ) {
                1f
            } else {
                0f
            },
            WaveFlowTheme.motion.tone(),
            label = "titreBarre",
        )

        LazyColumn(
            state = liste,
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "entete") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            // La nappe de la pochette, derrière l'en-tête seul :
                            // elle défile avec lui et ne teint pas les pistes.
                            drawRect(
                                Brush.radialGradient(
                                    0f to nappe.copy(alpha = POOL_ALPHA),
                                    1f to Color.Transparent,
                                    center = Offset(size.width / 2f, size.height * HEADER_POOL_Y),
                                    radius = size.width * HEADER_POOL_RADIUS,
                                ),
                            )
                        }
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = BAR_HEIGHT, start = gouttiere, end = gouttiere),
                ) {
                    val pochette = Modifier
                        .size(cote)
                        .graphicsLayer {
                            if (reduit) return@graphicsLayer
                            val p =
                                collapseProgress(liste.firstVisibleItemIndex, liste.firstVisibleItemScrollOffset, repli)
                            val echelle = 1f - COLLAPSE_SCALE * p
                            scaleX = echelle
                            scaleY = echelle
                            alpha = 1f - COLLAPSE_FADE * p
                            // Elle recule vers son bord bas : elle se retire
                            // sans jamais mordre sur le titre qui la suit.
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        }
                    if (large) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Cover(kind, title, artworkUri, nappe, cote, pochette)
                            Spacer(Modifier.width(32.dp))
                            HeaderText(kind, title, subtitle, songs, actions, Modifier.weight(1f))
                        }
                    } else {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Cover(kind, title, artworkUri, nappe, cote, pochette)
                        }
                        Spacer(Modifier.height(20.dp))
                        HeaderText(kind, title, subtitle, songs, actions)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
            if (songs.isEmpty()) {
                item(key = "vide") {
                    EmptyState(
                        title = "Rien à écouter ici.",
                        detail = when (kind) {
                            CollectionKind.Playlist ->
                                "Cette playlist est vide, ou ses titres ne sont plus sur l'appareil."
                            else -> "Ces titres ne sont plus sur l'appareil."
                        },
                    )
                }
            }
            items(songs, key = { it.id }) { song ->
                TrackRow(
                    title = song.title,
                    subtitle = if (kind == CollectionKind.Album) {
                        song.displayArtist.takeIf { it != subtitle }
                    } else {
                        "${song.displayArtist} · ${song.displayAlbum}"
                    },
                    durationMs = song.durationMs,
                    playing = player.track?.localSongId == song.id,
                    artworkUri = song.artworkUri,
                    showArtwork = kind != CollectionKind.Album,
                    onClick = { actions.onPlay(songs, song) },
                    menu = actions.rowMenu(song),
                    horizontalPadding = gouttiere,
                )
            }
        }

        // La barre du haut : le retour, toujours ; le nom et un fond, une fois
        // l'en-tête replié.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.canvas.copy(alpha = titreVisible))
                .windowInsetsPadding(WindowInsets.statusBars)
                .heightIn(min = BAR_HEIGHT)
                .padding(horizontal = 4.dp),
        ) {
            // Tant que l'en-tête est déployé, la flèche passe sur la pochette :
            // un disque de la page la garde lisible sur une pochette claire.
            IconButton(
                onClick = actions.onBack,
                modifier = Modifier.background(
                    colors.canvas.copy(alpha = BACK_SCRIM * (1f - titreVisible)),
                    CircleShape,
                ),
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour", tint = colors.ink)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink.copy(alpha = titreVisible),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Le côté de la pochette de l'en-tête : la largeur moins les gouttières,
 * bornée par la moitié de la hauteur (les premières pistes restent en vue) et
 * par 400 dp ; à côté du texte sur un écran large.
 */
fun coverSideFor(width: Dp, height: Dp, gutter: Dp, sideBySide: Boolean): Dp {
    val largeur = if (sideBySide) width * SIDE_COVER_SHARE else width - gutter * 2
    return minOf(largeur, height * HEADER_HEIGHT_SHARE, HEADER_COVER_MAX)
}

/**
 * Où en est le repli de l'en-tête, de 0 à 1, pour une liste dont le premier
 * élément visible est [index] décalé de [offset] pixels, quand le repli se
 * fait sur [range] pixels. Un en-tête sorti de l'écran est replié.
 */
fun collapseProgress(index: Int, offset: Int, range: Float): Float = when {
    index > 0 -> 1f
    range <= 0f -> 0f
    else -> (offset / range).coerceIn(0f, 1f)
}

@Composable
private fun Cover(kind: CollectionKind, title: String, artworkUri: Uri?, glow: Color, side: Dp, modifier: Modifier) {
    if (kind == CollectionKind.Artiste) {
        // Un artiste est un disque, partout.
        ArtistPortrait(name = title, artworkUri = artworkUri, size = side, modifier = modifier)
    } else {
        Artwork(
            artworkUri = artworkUri,
            shape = PocketShapes.cover,
            modifier = modifier.shadow(24.dp, PocketShapes.cover, ambientColor = glow, spotColor = glow),
        )
    }
}

@Composable
private fun HeaderText(
    kind: CollectionKind,
    title: String,
    subtitle: String?,
    songs: List<Song>,
    actions: CollectionActions,
    modifier: Modifier = Modifier,
) {
    val colors = Pocket.colors
    Column(modifier) {
        Text(
            title,
            style = MaterialTheme.typography.displaySmall,
            color = colors.ink,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        subtitle?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                it,
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            listOf(
                kind.label,
                trackCountLabel(songs.size),
                formatTotal(
                    songs.sumOf {
                        it.durationMs
                    },
                ),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink3,
        )
        if (songs.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryPill(text = "Lire", icon = Icons.Rounded.PlayArrow, onClick = {
                    actions.onPlay(songs, songs.first())
                })
                PocketPill(text = "Aléatoire", icon = Icons.Rounded.Shuffle, onClick = { actions.onShuffle(songs) })
            }
        }
    }
}

private val BAR_HEIGHT = 56.dp
private const val BACK_SCRIM = 0.7f
private val SIDE_BY_SIDE = 600.dp
private const val SIDE_COVER_SHARE = 0.4f
private const val HEADER_HEIGHT_SHARE = 0.5f
private val HEADER_COVER_MAX = 400.dp
private const val HEADER_POOL_Y = 0.35f
private const val HEADER_POOL_RADIUS = 0.9f
private const val COLLAPSE_SCALE = 0.12f
private const val COLLAPSE_FADE = 0.5f
