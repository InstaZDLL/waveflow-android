package app.waveflow.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.waveflow.ui.theme.AccentIconShade
import app.waveflow.ui.theme.GlassTopLight
import app.waveflow.ui.theme.LiquidGlassRim
import app.waveflow.ui.theme.WaveFlowMaterial
import app.waveflow.ui.theme.WaveFlowTheme
import java.util.Locale

/*
 * Les pièces d'une page de WaveFlow 2.0, communes aux deux matières : le
 * surtitre, le titre de section souligné de l'onde, l'action principale en
 * pilule d'accent et les actions secondaires en pilules fantômes. Les
 * documents les décrivent pour l'en-tête de page et le canevas de l'accueil
 * (« kicker, display title, one meta line, a pill CTA ») ; chaque écran les
 * prend ici plutôt que de les redessiner.
 */

/**
 * Le surtitre d'une page ou d'un bloc : l'étiquette du document, en capitales,
 * dans la troisième encre. Liquid l'espace un peu plus (0,14 em).
 */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.labelSmall
    Text(
        text = text.uppercase(Locale.FRENCH),
        style = if (WaveFlowTheme.material == WaveFlowMaterial.Liquid) {
            style.copy(letterSpacing = LIQUID_LABEL_TRACKING.em)
        } else {
            style
        },
        color = WaveFlowTheme.colors.ink3,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * Le titre d'une section de page (1,5 rem) et, dessous, le motif de l'onde :
 * c'est le premier rôle que le document lui donne. Les sections se séparent
 * par l'espace, jamais par un filet.
 *
 * @param action ce qui se pose au bout de la ligne, au pied du titre.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.Bottom, modifier = modifier) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            WaveMark()
        }
        action?.invoke()
    }
}

/**
 * L'action principale d'une page : une pilule pleine dans l'accent, l'icône
 * dans une pastille assombrie, le libellé dans l'encre de l'accent.
 *
 * Sous elle, la lumière de l'accent (« glass-accent-glow » en clair, l'ombre
 * colorée du CTA en sombre) ; en Liquid, un filet de lumière court sur son bord
 * haut. Inactive, elle prend les tons inactifs de Material et perd sa lumière.
 *
 * @param trailing ce qui suit le libellé dans la pilule — un temps restant,
 *   un indicateur d'attente.
 */
@Composable
fun AccentPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = WaveFlowTheme.shapes.pill
    val liquid = WaveFlowTheme.material == WaveFlowMaterial.Liquid
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = if (enabled) scheme.primary else scheme.onSurface.copy(alpha = DISABLED_CONTAINER),
        contentColor = if (enabled) scheme.onPrimary else scheme.onSurface.copy(alpha = DISABLED_CONTENT),
        modifier = modifier.semantics { role = Role.Button }.then(
            if (enabled) {
                Modifier.shadow(ACCENT_GLOW, shape, ambientColor = scheme.primary, spotColor = scheme.primary)
            } else {
                Modifier
            },
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            modifier = Modifier
                .then(if (liquid && enabled) Modifier.topLight() else Modifier)
                .heightIn(min = PILL_HEIGHT)
                .padding(start = if (icon != null) 8.dp else 24.dp, end = 22.dp),
        ) {
            if (icon != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .background(AccentIconShade, shape),
                ) {
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            // Pesé sans remplir : la pastille de fin est mesurée avant le libellé,
            // pour qu'un libellé trop long s'abrège au lieu de la pousser dehors.
            Text(
                text = text,
                style = pillLabelStyle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            trailing?.invoke()
        }
    }
}

/**
 * Une action secondaire : une pilule « fantôme », un ton à peine levé (blanc
 * 6 % en sombre, le niveau `tone` du verre en clair, cerné de sa lumière).
 */
@Composable
fun GhostPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = WaveFlowTheme.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = WaveFlowTheme.shapes.pill,
        color = colors.ghost,
        contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else DISABLED_CONTENT),
        border = if (WaveFlowTheme.material == WaveFlowMaterial.Liquid) BorderStroke(1.dp, LiquidGlassRim) else null,
        modifier = modifier.semantics { role = Role.Button },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .heightIn(min = PILL_HEIGHT)
                .padding(horizontal = 20.dp),
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(text = text, style = pillLabelStyle(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Le libellé d'une pilule : DM Sans 560, 15 sp, à peine resserré. */
@Composable
private fun pillLabelStyle() = MaterialTheme.typography.labelLarge.copy(
    fontWeight = FontWeight(PILL_WEIGHT),
    fontSize = 15.sp,
    letterSpacing = (-0.01).em,
)

/** Le filet de lumière que prend le bord haut d'un objet de verre (blanc 35 %). */
private fun Modifier.topLight(): Modifier = drawBehind {
    val y = 1.dp.toPx() / 2f
    drawLine(GlassTopLight, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
}

/** Une pilule tient le doigt : 48 dp, la hauteur du CTA du bureau. */
private val PILL_HEIGHT = 48.dp

/** La lumière sous l'action principale. */
private val ACCENT_GLOW = 10.dp

private const val PILL_WEIGHT = 560
private const val LIQUID_LABEL_TRACKING = 0.14f

/** Les opacités de Material pour un bouton inactif. */
private const val DISABLED_CONTAINER = 0.12f
private const val DISABLED_CONTENT = 0.38f
