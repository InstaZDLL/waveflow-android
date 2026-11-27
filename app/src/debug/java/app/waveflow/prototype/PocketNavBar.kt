package app.waveflow.prototype

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waveflow.ui.components.WaveMark

/** Les trois destinations, gardées de l'application actuelle. */
enum class PocketTab(val label: String, val icon: ImageVector, val iconSelected: ImageVector) {
    Accueil("Accueil", Icons.Outlined.Home, Icons.Rounded.Home),
    Bibliotheque("Bibliothèque", Icons.Outlined.LibraryMusic, Icons.Rounded.LibraryMusic),
    Recherche("Recherche", Icons.Outlined.Search, Icons.Rounded.Search),
}

/**
 * La barre de navigation de Pocket.
 *
 * L'onglet ouvert : son icône pleine en encre, son étiquette, et **l'onde**
 * dessous dans l'accent — l'ornement du bureau devenu indicateur. Les autres :
 * l'icône au trait en troisième encre. Pas de pastille Material.
 */
@Composable
fun PocketNavBar(selected: PocketTab, onSelect: (PocketTab) -> Unit, modifier: Modifier = Modifier) {
    val colors = Pocket.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.raised)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .selectableGroup(),
    ) {
        PocketTab.entries.forEach { tab ->
            val ouvert = tab == selected
            // Chaque onglet a sa hauteur minimale et grandit avec la police ;
            // la barre prend celle du plus haut. Pas de `fillMaxHeight` : sous
            // une hauteur minimale, il réclamerait tout l'écran.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 64.dp)
                    .selectable(selected = ouvert, role = Role.Tab, onClick = { onSelect(tab) }),
            ) {
                Spacer(Modifier.height(4.dp))
                Icon(
                    imageVector = if (ouvert) tab.iconSelected else tab.icon,
                    contentDescription = null,
                    tint = if (ouvert) colors.ink else colors.ink3,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.height(2.dp))
                // À police agrandie, « Bibliothèque » ne tient plus dans un tiers
                // d'écran : l'étiquette rétrécit d'un cran plutôt que d'être
                // coupée — elle nomme la destination.
                BasicText(
                    text = tab.label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = if (ouvert) colors.ink else colors.ink3,
                    ),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp),
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Box(Modifier.height(8.dp), contentAlignment = Alignment.Center) {
                    if (ouvert) WaveMark(modifier = Modifier.size(20.dp, 5.dp), color = colors.accent)
                }
            }
        }
    }
}
