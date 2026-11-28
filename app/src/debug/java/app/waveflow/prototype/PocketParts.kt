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

/** L'écart entre deux tuiles. */
val TILE_SPACING = 12.dp

/**
 * Une ligne de titre compacte : vignette au besoin, titre, seconde ligne,
 * durée, menu. Le morceau courant — qu'il joue ou soit en pause — prend
 * l'accent et l'onde à la place de sa durée, le même signe que l'onglet
 * ouvert ; le lecteur d'écran l'annonce « Morceau courant », pas « En
 * lecture ».
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
    current: Boolean,
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
            .semantics { if (current) stateDescription = "Morceau courant" }
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
                color = if (current) colors.accent else colors.ink,
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
        if (current) {
            WaveMark(modifier = Modifier.size(24.dp, 6.dp), color = colors.accent)
        } else {
            Text(formatDuration(durationMs), style = MaterialTheme.typography.labelMedium, color = colors.ink3)
        }
        if (menu.isNotEmpty()) RowMenu(title = title, actions = menu)
    }
}

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
