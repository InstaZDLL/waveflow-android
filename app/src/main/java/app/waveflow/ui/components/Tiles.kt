package app.waveflow.ui.components

import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waveflow.ui.theme.LiquidGlassRim
import app.waveflow.ui.theme.WaveFlowMaterial
import app.waveflow.ui.theme.WaveFlowTheme
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Les tuiles : ce que la direction met à la place des lignes partout où l'on
 * parcourt des disques — « square cover, radius 14 px, the title and artist
 * under it in ink and ink-2 ». Une tuile de tête, deux fois plus grande, porte
 * un surtitre.
 *
 * En Fluid la pochette est posée nue sur la page : la profondeur vient du ton,
 * pas d'une ombre que le fond presque noir mangerait. En Liquid c'est un objet
 * de verre : un liseré de lumière et, dessous, une ombre teintée (« a rim and
 * the glow under it »), jamais grise. Dans une grille, cette teinte est la
 * lumière par défaut du thème : échantillonner chaque pochette coûterait une
 * lecture d'image par tuile. La pochette d'un en-tête, seule, prend la sienne.
 */

/**
 * Une tuile d'album : la pochette carrée, le titre, l'artiste.
 *
 * Toute la tuile répond au doigt, et TalkBack la lit d'un seul tenant.
 *
 * @param kicker le surtitre d'une tuile de tête (« Dernier ajout »).
 * @param lead la tuile de tête : titre en corps de titre plutôt qu'en texte.
 */
@Composable
fun CoverTile(
    artworkUri: Uri?,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kicker: String? = null,
    lead: Boolean = false,
) {
    val shape = WaveFlowTheme.shapes.tile
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Artwork(
            artworkUri = artworkUri,
            shape = shape,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .glassEdge(shape, WaveFlowTheme.material, WaveFlowTheme.colors.glow),
        )
        Spacer(Modifier.height(if (lead) 14.dp else 10.dp))
        if (kicker != null) {
            Kicker(kicker)
            Spacer(Modifier.height(6.dp))
        }
        Text(
            text = title,
            style = if (lead) {
                MaterialTheme.typography.titleLarge.copy(fontSize = LEAD_TITLE_SIZE.sp)
            } else {
                MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            },
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = if (lead) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Une tuile d'artiste : le portrait en disque, le nom et, dessous, ce qu'on
 * en a. Centrée, comme le disque qu'elle porte.
 */
@Composable
fun PortraitTile(
    artworkUri: Uri?,
    name: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = WaveFlowTheme.shapes.portrait
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Artwork(
            artworkUri = artworkUri,
            shape = shape,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .glassEdge(shape, WaveFlowTheme.material, WaveFlowTheme.colors.glow),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Le bord d'un objet de verre en Liquid : son liseré de lumière et, sous lui,
 * une ombre teintée de [glow]. Rien en Fluid.
 *
 * À poser **avant** le découpage de l'objet : le liseré se dessine par-dessus
 * l'image, l'ombre en dehors de sa forme.
 *
 * @param lift la hauteur de l'ombre — celle d'une tuile par défaut, plus
 *   grande pour la pochette d'un en-tête.
 */
fun Modifier.glassEdge(
    shape: Shape,
    material: WaveFlowMaterial,
    glow: Color,
    lift: Dp = TILE_LIFT,
): Modifier = when (material) {
    WaveFlowMaterial.Fluid -> this
    WaveFlowMaterial.Liquid ->
        this
            .shadow(lift, shape, ambientColor = glow, spotColor = glow)
            .border(1.dp, LiquidGlassRim, shape)
}

/**
 * Une largeur de [fraction] de la place offerte, plafonnée à [max] : la grande
 * pochette d'un en-tête garde sa proportion sur un téléphone sans envahir une
 * tablette.
 *
 * `fillMaxWidth(fraction).widthIn(max = …)` ne le fait pas : la première fixe
 * la largeur, et la seconde ne peut plus que s'y plier.
 */
fun Modifier.cappedWidthFraction(fraction: Float, max: Dp): Modifier = layout { mesurable, contraintes ->
    val plafond = max.roundToPx()
    val voulue = if (contraintes.hasBoundedWidth) {
        min((contraintes.maxWidth * fraction).roundToInt(), plafond)
    } else {
        plafond
    }
    val largeur = voulue.coerceIn(contraintes.minWidth, contraintes.maxWidth)
    val place = mesurable.measure(contraintes.copy(minWidth = largeur, maxWidth = largeur))
    layout(place.width, place.height) { place.place(0, 0) }
}

/*
 * Les mesures communes des grilles de tuiles, pour que les albums, les
 * artistes et le catalogue du serveur aient la même trame.
 */

/** La marge de la page autour d'une grille : celle des en-têtes. */
val TileGutter = 24.dp

/** L'intervalle entre deux tuiles d'une rangée. */
val TileSpacing = 16.dp

/** L'intervalle entre deux rangées : le titre sous la tuile a besoin d'air. */
val TileRowSpacing = 24.dp

/** La largeur minimale d'une tuile d'album : deux colonnes sur un téléphone. */
val CoverTileMinWidth = 140.dp

/** La largeur minimale d'un portrait : trois colonnes sur un téléphone. */
val PortraitTileMinWidth = 96.dp

/** L'ombre d'une tuile de verre : courte, une tuile défile. */
private val TILE_LIFT = 10.dp

/** Le titre d'une tuile de tête, 1,25 rem. */
private const val LEAD_TITLE_SIZE = 20
