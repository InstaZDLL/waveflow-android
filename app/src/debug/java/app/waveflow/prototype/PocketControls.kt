package app.waveflow.prototype

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.waveflow.ui.theme.WaveFlowTheme

/**
 * Le disque de lecture : l'accent plein, l'action principale, la seule que
 * l'accent remplit.
 *
 * L'attente se dessine **en anneau autour** du disque plutôt qu'à sa place :
 * le disque garde son icône et sa taille, la rangée ne bouge pas. Sous le
 * doigt, il s'enfonce à 96 % — la réponse au contact, avant le relâcher.
 */
@Composable
fun PlayButton(
    playing: Boolean,
    buffering: Boolean,
    onClick: () -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = Pocket.colors
    val interactions = remember { MutableInteractionSource() }
    val appuye by interactions.collectIsPressedAsState()
    val echelle by animateFloatAsState(if (appuye) PRESSED_SCALE else 1f, WaveFlowTheme.motion.tone(), label = "appui")

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size + RING_GAP * 2)) {
        if (buffering) {
            CircularProgressIndicator(
                color = colors.accent,
                trackColor = colors.accent.copy(alpha = RING_TRACK_ALPHA),
                strokeWidth = 2.dp,
                modifier = Modifier
                    .size(size + RING_GAP * 2)
                    .semantics { contentDescription = "Chargement du morceau" },
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = echelle
                    scaleY = echelle
                }
                .clip(PocketShapes.action)
                .background(colors.accent)
                .clickable(
                    interactionSource = interactions,
                    indication = androidx.compose.material3.ripple(color = colors.onAccent),
                    role = Role.Button,
                    onClickLabel = if (playing) "Mettre en pause" else "Lire",
                    onClick = onClick,
                ),
        ) {
            Icon(
                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "Pause" else "Lecture",
                tint = colors.onAccent,
                modifier = Modifier.size(size * ICON_SHARE),
            )
        }
    }
}

/**
 * Une bascule : l'accent **et** un point sous l'icône quand elle est allumée —
 * l'état ne repose pas sur la couleur seule. La seconde encre au repos.
 */
@Composable
fun ToggleIcon(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Pocket.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(PocketShapes.action)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = label
                stateDescription = if (active) "Activé" else "Désactivé"
            }
            .padding(8.dp),
    ) {
        // Le pendant du point, au-dessus : l'icône reste au centre de la
        // rangée, alignée sur ses voisines.
        Spacer(Modifier.height(7.dp))
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) colors.accent else colors.ink2,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .size(4.dp)
                .clip(PocketShapes.action)
                .background(if (active) colors.accent else androidx.compose.ui.graphics.Color.Transparent),
        )
    }
}

/**
 * Une action secondaire à étiquette, sur un ton : les pastilles du dock et
 * celles qui disent un réglage actif. Choisie, elle prend l'accent.
 */
@Composable
fun PocketPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    compact: Boolean = false,
    description: String? = null,
) {
    val colors = Pocket.colors
    val encre = if (selected) colors.accent else colors.ink2
    // `Surface(onClick)` impose 48 dp à sa mise en page ; une pastille
    // compacte ne les réserve pas — Compose étend déjà le toucher d'un
    // élément cliquable plus petit jusqu'à la cible minimale.
    val minimum = if (compact) Dp.Unspecified else LocalMinimumInteractiveComponentSize.current
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides minimum) {
        Surface(
            onClick = onClick,
            shape = PocketShapes.action,
            color = colors.surface,
            modifier = modifier
                .semantics {
                    // Le `Surface(onClick)` de material3 ne pose pas le rôle.
                    role = Role.Button
                    this.selected = selected
                    description?.let { contentDescription = it }
                },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(if (compact) 28.dp else 40.dp)
                    .widthIn(min = if (compact) 28.dp else 40.dp)
                    .padding(horizontal = if (compact) 10.dp else 16.dp),
            ) {
                icon?.let {
                    Icon(
                        it,
                        contentDescription = null,
                        tint = encre,
                        modifier = Modifier.size(if (compact) 14.dp else 18.dp),
                    )
                    Spacer(Modifier.width(if (compact) 4.dp else 8.dp))
                }
                Text(
                    text = text,
                    style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                    color = encre,
                    maxLines = 1,
                )
            }
        }
    }
}

private const val PRESSED_SCALE = 0.96f
private const val ICON_SHARE = 0.46f
private const val RING_TRACK_ALPHA = 0.18f
private val RING_GAP = 6.dp
