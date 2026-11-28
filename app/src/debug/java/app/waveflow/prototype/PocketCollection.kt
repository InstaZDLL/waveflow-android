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

/**
 * La page d'un album — et, avec la même grammaire, celle d'un artiste ou
 * d'une playlist (`docs/design/sonic-editorial.md`, « Album »).
 *
 * La pochette sur sa nappe, le nom en titre d'écran, une ligne « Album ·
 * 9 titres · 42 min », **Lire** (l'accent) et **Aléatoire** (un ton), puis les
 * pistes — sans pochette répétée sur la page d'un album. La pochette est
 * mesurée pour que, sur un téléphone, plusieurs pistes se voient dès
 * l'ouverture ([coverSideFor]).
 *
 * En défilant, la pochette recule et pâlit avec le geste (vers son bord bas,
 * sans mordre sur le titre) ; la barre du haut prend son fond, puis le nom,
 * **au fil du geste** — rien ne bascule d'un coup, tout suit le doigt. Sous
 * « Supprimer les animations », la pochette défile simplement.
 *
 * Pas de numéro de piste ni d'année : l'application ne les lit pas dans le
 * MediaStore, et le prototype ne les invente pas. Les pistes sont dans l'ordre
 * de la bibliothèque.
 *
 * @param songs les pistes ; vide quand la collection a disparu de l'appareil.
 * @param loading la source n'est pas encore lue (une page restaurée après la
 *   mort du processus) : la page attend au lieu de se dire introuvable.
 * @param error l'échec de lecture de sa source : la page le dit, au lieu de se
 *   dire introuvable.
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
    loading: Boolean = false,
    error: String? = null,
) {
    val colors = Pocket.colors
    val liste = rememberLazyListState()
    val reduit = WaveFlowTheme.motion.reduced
    val nappe = rememberAtmosphere(artworkUri)
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxSize().background(colors.canvas)) {
        val gouttiere = if (maxWidth < NARROW_WIDTH) 20.dp else 24.dp
        val large = maxWidth >= SIDE_BY_SIDE
        val cote = coverSideFor(maxWidth, maxHeight, large)
        val repli = with(density) { cote.toPx() }
        // Relu à chaque nouvelle mesure : après un redimensionnement, le titre et
        // la pochette se replient au même seuil.
        val barre by remember(liste, repli) {
            derivedStateOf { collapseProgress(liste.firstVisibleItemIndex, liste.firstVisibleItemScrollOffset, repli) }
        }
        // Le fond de la barre suit le repli ; le nom n'y paraît que sur sa fin.
        val fondBarre = barBackgroundAlpha(barre)
        val titreVisible = barTitleAlpha(barre)

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
                        Spacer(Modifier.height(14.dp))
                        HeaderText(kind, title, subtitle, songs, actions)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (error != null && songs.isEmpty()) {
                item(key = "erreur") { EmptyState("Cette page n'a pas pu être lue.", error.orEmpty()) }
            } else if (loading && songs.isEmpty()) {
                item(key = "attente") {
                    EmptyState("Lecture de la bibliothèque…", "Un instant : l'appareil compte ses morceaux.")
                }
            } else if (songs.isEmpty()) {
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
                    current = player.track?.localSongId == song.id,
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
                .background(colors.canvas.copy(alpha = fondBarre))
                .windowInsetsPadding(WindowInsets.statusBars)
                .heightIn(min = BAR_HEIGHT)
                .padding(horizontal = 4.dp),
        ) {
            // En colonne, la pochette, plus étroite que l'écran, ne passe pas
            // sous la flèche. Côte à côte, elle part du bord : un disque de la
            // page garde la flèche lisible tant que la barre est transparente.
            IconButton(
                onClick = actions.onBack,
                modifier = if (large) {
                    Modifier.background(colors.canvas.copy(alpha = BACK_SCRIM * (1f - fondBarre)), CircleShape)
                } else {
                    Modifier
                },
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
 * Le côté de la pochette de l'en-tête. Sur un téléphone, 62 % de la largeur,
 * bornée par le tiers de la hauteur et par 260 dp : sur un Pixel 8, l'en-tête
 * laisse voir plusieurs pistes avant le dock, là où la pochette pleine largeur
 * n'en laissait qu'une. À côté du texte sur un écran large, plus de place.
 */
fun coverSideFor(width: Dp, height: Dp, sideBySide: Boolean): Dp =
    if (sideBySide) {
        minOf(width * SIDE_COVER_SHARE, height * SIDE_HEIGHT_SHARE, SIDE_COVER_MAX)
    } else {
        minOf(width * COVER_WIDTH_SHARE, height * COVER_HEIGHT_SHARE, COVER_MAX)
    }

/**
 * L'opacité du fond de la barre : pleine dès la moitié du repli. Une barre à
 * demi transparente laissait voir la pochette passer dessous, et son nom en
 * double avec celui de l'en-tête.
 */
fun barBackgroundAlpha(progress: Float): Float = (progress / BACKGROUND_UNTIL).coerceIn(0f, 1f)

/** L'opacité du nom dans la barre : nulle jusqu'à 65 % du repli, pleine au bout. */
fun barTitleAlpha(progress: Float): Float = ((progress - TITLE_FROM) / (1f - TITLE_FROM)).coerceIn(0f, 1f)

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
            Spacer(Modifier.height(2.dp))
            Text(
                it,
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
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
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryPill(text = "Lire", icon = Icons.Rounded.PlayArrow, onClick = {
                    actions.onPlay(songs, songs.first())
                })
                PocketPill(text = "Aléatoire", icon = Icons.Rounded.Shuffle, onClick = { actions.onShuffle(songs) })
            }
        }
    }
}

private val BAR_HEIGHT = 48.dp
private const val BACK_SCRIM = 0.7f
private val SIDE_BY_SIDE = 600.dp
private const val SIDE_COVER_SHARE = 0.36f
private const val SIDE_HEIGHT_SHARE = 0.5f
private val SIDE_COVER_MAX = 360.dp
private const val COVER_WIDTH_SHARE = 0.62f
private const val COVER_HEIGHT_SHARE = 0.32f
private val COVER_MAX = 260.dp
private const val TITLE_FROM = 0.65f
private const val BACKGROUND_UNTIL = 0.5f
private const val HEADER_POOL_Y = 0.35f
private const val HEADER_POOL_RADIUS = 0.9f
private const val COLLAPSE_SCALE = 0.12f
private const val COLLAPSE_FADE = 0.5f
