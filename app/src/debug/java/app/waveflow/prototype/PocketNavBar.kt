package app.waveflow.prototype

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waveflow.ui.components.WaveMark

/**
 * La barre de navigation du téléphone, dans le dock : trois destinations, et
 * **l'onde** sous l'onglet ouvert — l'ornement du bureau devenu indicateur.
 * L'onglet ouvert : son icône pleine en encre, son étiquette ; les autres :
 * l'icône au trait en troisième encre. Pas de pastille Material.
 *
 * 56 dp, et non plus 64 : avec le mini-lecteur au-dessus, le dock ne prend pas
 * plus que ce qu'il sert. Elle n'a pas de fond à elle : c'est le dock qui la
 * porte, d'un seul tenant avec le mini-lecteur.
 */
@Composable
fun PocketNavBar(selected: PocketTab, onSelect: (PocketTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .selectableGroup(),
    ) {
        PocketTab.entries.forEach { tab ->
            // Chaque onglet a sa hauteur minimale et grandit avec la police ;
            // la barre prend celle du plus haut. Pas de `fillMaxHeight` : sous
            // une hauteur minimale, il réclamerait tout l'écran.
            NavItem(
                tab = tab,
                selected = tab == selected,
                onSelect = { onSelect(tab) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = BAR_HEIGHT),
            )
        }
    }
}

/**
 * Le rail de navigation des fenêtres moyennes et étendues : les mêmes trois
 * destinations, la même onde sous l'onglet ouvert, et, au pied, l'entrée des
 * réglages. Il remplace la barre du bas, qu'une tablette étirerait sur toute sa
 * largeur.
 */
@Composable
fun PocketRail(
    selected: PocketTab,
    onSelect: (PocketTab) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .windowInsetsPadding(
                WindowInsets.statusBars
                    .union(WindowInsets.navigationBars)
                    .union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Vertical + WindowInsetsSides.Start),
            )
            .width(RAIL_WIDTH),
    ) {
        // Les réglages au pied du rail quand il a la hauteur ; dans une fenêtre
        // trop basse, le rail défile plutôt que de les pousser hors de l'écran.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(vertical = 16.dp),
        ) {
            Column(Modifier.selectableGroup()) {
                PocketTab.entries.forEach { tab ->
                    NavItem(
                        tab = tab,
                        selected = tab == selected,
                        onSelect = { onSelect(tab) },
                        modifier = Modifier
                            .width(RAIL_WIDTH)
                            .heightIn(min = RAIL_ITEM_HEIGHT),
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
            SettingsButton(onSettings)
        }
    }
}

/** L'entrée des réglages : une icône au trait, qui dit ce qu'elle ouvre. */
@Composable
fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.Outlined.Settings, contentDescription = "Réglages", tint = Pocket.colors.ink2)
    }
}

@Composable
private fun NavItem(tab: PocketTab, selected: Boolean, onSelect: () -> Unit, modifier: Modifier) {
    val colors = Pocket.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.selectable(selected = selected, role = Role.Tab, onClick = onSelect),
    ) {
        Icon(
            imageVector = if (selected) tab.iconSelected else tab.icon,
            contentDescription = null,
            tint = if (selected) colors.ink else colors.ink3,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(2.dp))
        // À police agrandie, « Bibliothèque » ne tient plus dans un tiers
        // d'écran : l'étiquette rétrécit d'un cran plutôt que d'être coupée —
        // elle nomme la destination.
        BasicText(
            text = tab.label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = if (selected) colors.ink else colors.ink3,
            ),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Box(Modifier.height(6.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (selected) WaveMark(modifier = Modifier.size(20.dp, 5.dp), color = colors.accent)
        }
    }
}

/** La barre du bas : assez pour l'icône, l'étiquette et l'onde, sans plus. */
private val BAR_HEIGHT = 56.dp
private val RAIL_WIDTH = 88.dp
private val RAIL_ITEM_HEIGHT = 64.dp
