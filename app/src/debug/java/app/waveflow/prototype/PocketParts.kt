package app.waveflow.prototype

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.waveflow.ui.components.Artwork
import app.waveflow.ui.components.WaveMark
import app.waveflow.ui.formatDuration
import app.waveflow.ui.theme.oklchToColor
import coil.compose.AsyncImage

/*
 * Les pièces communes à l'accueil, à la bibliothèque et à la page d'un
 * album : peu de composants, nommés par intention (`docs/design/sonic-editorial.md`,
 * « Composants »).
 */

/**
 * Le titre d'une section : la taille de section (20 sp), et une action à
 * droite quand la section mène plus loin. Pas d'onde dessous : elle marque
 * l'onglet ouvert et ce qui joue, elle ne décore pas chaque titre.
 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = Pocket.colors.ink,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

/**
 * Une tuile d'album : la pochette sans conteneur, le titre et l'artiste
 * dessous. Ni carte autour ni ombre : la pochette est l'objet.
 */
@Composable
fun AlbumTile(
    title: String,
    subtitle: String,
    artworkUri: Uri?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(PocketShapes.tile)
            .clickable(role = Role.Button, onClickLabel = "Ouvrir", onClick = onClick),
    ) {
        Artwork(
            artworkUri = artworkUri,
            shape = PocketShapes.tile,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = Pocket.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = Pocket.colors.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
    }
}

/** Une rangée de tuiles qui défile, où une troisième dépasse : l'invitation à faire défiler. */
@Composable
fun <T> TileCarousel(
    items: List<T>,
    key: (T) -> Any,
    gutter: Dp,
    tileWidth: Dp,
    tile: @Composable (T, Modifier) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = gutter),
        horizontalArrangement = Arrangement.spacedBy(TILE_SPACING),
    ) {
        items(items, key = key) { item -> tile(item, Modifier.width(tileWidth)) }
    }
}

/**
 * La largeur d'une tuile de carrousel dans une largeur [available] : deux
 * tuiles et le bord d'une troisième, bornée entre [min] et [max] — une
 * tablette montre plus de tuiles, pas des tuiles géantes.
 */
fun carouselTileWidth(available: Dp, gutter: Dp, min: Dp = 132.dp, max: Dp = 200.dp): Dp {
    val visibles = CAROUSEL_VISIBLE
    val largeur = (available - gutter - TILE_SPACING * 2) / visibles
    return largeur.coerceIn(min, max)
}

/** Deux tuiles pleines et un peu plus du tiers de la troisième. */
private const val CAROUSEL_VISIBLE = 2.35f

/** L'écart entre deux tuiles. */
val TILE_SPACING = 12.dp

/**
 * Une ligne de titre compacte : vignette au besoin, titre, seconde ligne,
 * durée, menu. Celle qui joue prend l'accent et l'onde à la place de sa
 * durée — le même signe que l'onglet ouvert.
 *
 * @param subtitle la seconde ligne ; nulle quand elle ne dirait que ce que la
 *   page dit déjà — l'artiste de l'album, sur la page de l'album.
 * @param artworkUri la vignette ; nulle sur la page d'un album, où la
 *   pochette ne se répète pas à chaque ligne.
 */
@Composable
fun TrackRow(
    title: String,
    subtitle: String?,
    durationMs: Long,
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    artworkUri: Uri? = null,
    showArtwork: Boolean = true,
    menu: List<RowAction> = emptyList(),
    horizontalPadding: Dp = 20.dp,
) {
    val colors = Pocket.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .semantics { if (playing) stateDescription = "En lecture" }
            .padding(start = horizontalPadding, end = if (menu.isEmpty()) horizontalPadding else 4.dp),
    ) {
        if (showArtwork) {
            Artwork(artworkUri = artworkUri, shape = PocketShapes.thumbnail, modifier = Modifier.size(48.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (playing) colors.accent else colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        if (playing) {
            WaveMark(modifier = Modifier.size(24.dp, 6.dp), color = colors.accent)
        } else {
            Text(formatDuration(durationMs), style = MaterialTheme.typography.labelMedium, color = colors.ink3)
        }
        if (menu.isNotEmpty()) RowMenu(title = title, actions = menu)
    }
}

/** Une action du menu d'une ligne : seulement ce que l'application sait faire. */
data class RowAction(val label: String, val onClick: () -> Unit)

@Composable
private fun RowMenu(title: String, actions: List<RowAction>) {
    var ouvert by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { ouvert = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Options de $title", tint = Pocket.colors.ink3)
        }
        DropdownMenu(
            expanded = ouvert,
            onDismissRequest = { ouvert = false },
            shape = MaterialTheme.shapes.medium,
            containerColor = Pocket.colors.floating,
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = {
                        Text(action.label, style = MaterialTheme.typography.bodyLarge, color = Pocket.colors.ink)
                    },
                    onClick = {
                        ouvert = false
                        action.onClick()
                    },
                )
            }
        }
    }
}

/**
 * Le portrait d'un artiste : un disque. Le MediaStore n'a pas d'image
 * d'artiste — c'est la pochette d'un de ses albums, comme dans l'application ;
 * dessous, ses initiales sur une teinte tirée de son nom, qui se voient quand
 * la pochette manque ou ne se charge pas. Pas une note de musique : deux
 * artistes sans pochette restent deux visages différents.
 */
@Composable
fun ArtistPortrait(name: String, artworkUri: Uri?, size: Dp, modifier: Modifier = Modifier) {
    val colors = Pocket.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(PocketShapes.action)
            .background(artistTint(name, colors.dark)),
    ) {
        Text(
            text = initialsOf(name),
            style = if (size >=
                LARGE_PORTRAIT
            ) {
                MaterialTheme.typography.displaySmall
            } else {
                MaterialTheme.typography.titleMedium
            },
            color = colors.ink,
        )
        if (artworkUri != null) {
            AsyncImage(
                model = artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private val LARGE_PORTRAIT = 96.dp

/**
 * Les initiales d'un artiste : la première lettre de ses deux premiers mots,
 * en capitales. « ? » pour un artiste inconnu, plutôt que « AI ».
 */
fun initialsOf(name: String): String {
    if (name.isBlank() || name == UNKNOWN_ARTIST) return "?"
    return name.split(' ', '-', '_')
        .filter { mot -> mot.firstOrNull()?.isLetterOrDigit() == true }
        .take(2)
        .joinToString("") { mot -> mot.first().uppercase() }
        .ifEmpty { name.first().uppercase() }
}

/** Ce que l'application affiche pour un artiste sans tag (`orUnknownArtist`). */
private const val UNKNOWN_ARTIST = "Artiste inconnu"

/**
 * La teinte du disque d'un artiste : une teinte tirée de son nom — la même à
 * chaque fois —, à une clarté et une vivacité fixes, pour que l'encre y tienne
 * dans les deux apparences.
 */
fun artistTint(name: String, dark: Boolean): Color {
    val teinte = ((name.hashCode() % HUE_TURN) + HUE_TURN) % HUE_TURN
    return oklchToColor(
        if (dark) TINT_DARK_L else TINT_LIGHT_L,
        if (dark) TINT_DARK_C else TINT_LIGHT_C,
        teinte.toFloat(),
    )
}

private const val HUE_TURN = 360
private const val TINT_DARK_L = 0.36f
private const val TINT_DARK_C = 0.06f
private const val TINT_LIGHT_L = 0.88f
private const val TINT_LIGHT_C = 0.05f

/**
 * Le sélecteur de catégorie : des pastilles qui défilent. Celle qui est
 * ouverte est **pleine d'encre** — encre claire sur le sombre, encre sombre sur
 * le clair : on sait toujours où l'on est, sans soulignement serré.
 */
@Composable
fun <T> CategorySelector(
    entries: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    gutter: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = Pocket.colors
    LazyRow(
        contentPadding = PaddingValues(horizontal = gutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.selectableGroup(),
    ) {
        items(entries, key = { label(it) }) { entree ->
            val ouverte = entree == selected
            Surface(
                shape = PocketShapes.action,
                color = if (ouverte) colors.ink else colors.surface,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .padding(vertical = 4.dp)
                    .selectable(selected = ouverte, role = Role.Tab, onClick = { onSelect(entree) }),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = label(entree),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (ouverte) colors.canvas else colors.ink2,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Étend [amount] de part et d'autre au-delà de la place reçue : une rangée
 * qui défile dans une grille déjà bordée de sa gouttière va jusqu'au bord de
 * l'écran, au lieu d'être coupée net au bord de la gouttière.
 */
fun Modifier.bleed(amount: Dp): Modifier = layout { mesurable, contraintes ->
    val extra = amount.roundToPx() * 2
    val largeur = contraintes.maxWidth + extra
    val place = mesurable.measure(contraintes.copy(minWidth = largeur, maxWidth = largeur))
    layout(contraintes.maxWidth, place.height) { place.place(-extra / 2, 0) }
}

/**
 * Un état vide : ce qui manque, et ce qu'on peut faire. Jamais une page
 * blanche, jamais un contenu inventé.
 */
@Composable
fun EmptyState(title: String, detail: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Pocket.colors.ink)
        Spacer(Modifier.height(4.dp))
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = Pocket.colors.ink2)
        action?.let {
            Spacer(Modifier.height(12.dp))
            it()
        }
    }
}

/**
 * L'action principale d'une page : une pilule pleine d'accent. Les
 * secondaires sont des [PocketPill] tonales.
 */
@Composable
fun PrimaryPill(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Pocket.colors
    Surface(
        onClick = onClick,
        shape = PocketShapes.action,
        color = colors.accent,
        modifier = modifier.semantics { contentDescription = text },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(horizontal = 22.dp),
        ) {
            Icon(icon, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = colors.onAccent, maxLines = 1)
        }
    }
}

/**
 * Le total d'un ensemble de pistes, pour la ligne d'un album : « 42 min »,
 * « 1 h 12 min ». Les secondes n'y disent rien.
 */
fun formatTotal(durationMs: Long): String {
    val minutes = ((durationMs + MS_PER_MINUTE / 2) / MS_PER_MINUTE).coerceAtLeast(if (durationMs > 0) 1 else 0)
    val heures = minutes / MINUTES_PER_HOUR
    val reste = minutes % MINUTES_PER_HOUR
    return when {
        heures == 0L -> "$minutes min"
        reste == 0L -> "$heures h"
        else -> "$heures h $reste min"
    }
}

private const val MS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
