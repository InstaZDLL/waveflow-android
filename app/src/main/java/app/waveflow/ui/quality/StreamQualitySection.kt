package app.waveflow.ui.quality

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.waveflow.model.StreamNetwork
import app.waveflow.model.StreamQuality

/**
 * La qualité de lecture, choisie parmi des profils, ou selon le réseau.
 *
 * Sous le compte serveur, à côté du cache : elle ne concerne que les pistes du
 * serveur, et décide de ce que le cache contiendra.
 *
 * La ligne entière porte le geste et le bouton radio n'est qu'un témoin, comme
 * pour le thème : c'est ce qui fait annoncer « 2 sur 4 » par le lecteur d'écran,
 * et donne à la cible toute la largeur.
 *
 * *Automatique* fait paraître, sous lui, le profil de chaque réseau. Ils ne se
 * montrent qu'alors : choisis sous un profil fixe, ils ne feraient rien. Voir
 * `docs/qualite-automatique.md`, Q1.
 */
@Composable
fun StreamQualitySection(
    state: StreamQualityUiState,
    onChoose: (StreamQuality) -> Unit,
    onChooseAutomatic: () -> Unit,
    onChooseOn: (StreamNetwork, StreamQuality) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        ) {
            Text(
                text = "Qualité de lecture",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Column(Modifier.selectableGroup()) {
                StreamQuality.entries.forEach { quality ->
                    ChoiceRow(
                        label = quality.label,
                        summary = quality.summary,
                        selected = !state.automatic && quality == state.current,
                        onClick = { onChoose(quality) },
                    )
                }
                ChoiceRow(
                    label = AUTOMATIQUE,
                    summary = "Une qualité en Wi-Fi, une autre en données mobiles.",
                    selected = state.automatic,
                    onClick = onChooseAutomatic,
                )
            }

            if (state.automatic) {
                StreamNetwork.entries.forEach { network ->
                    NetworkRow(network = network, quality = state.on(network), onChoose = onChooseOn)
                }
            }

            Text(
                // Le rendu est posé sur chaque piste quand elle entre dans la
                // file : changer d'avis en pleine écoute sans que rien ne bouge
                // se lirait sinon comme une panne. Sous la qualité automatique,
                // le réseau, lui, atteint les pistes à venir (Q5 et Q6).
                text = if (state.automatic) {
                    "Un changement de réglage s'applique aux pistes lancées ensuite. " +
                        "Un changement de réseau, aussi aux pistes à venir de la file en cours ; " +
                        "celle qui joue garde sa qualité."
                } else {
                    "S'applique aux pistes lancées ensuite, pas à la file en cours."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(start = 16.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Le profil d'un réseau, sous *Automatique* : son nom, et le profil choisi, qui
 * ouvre la liste des trois profils fixes.
 *
 * Une liste déroulante plutôt que trois boutons radio de plus : six choix
 * empilés sous le quatrième noieraient la section. Alignée sur le texte des
 * profils, pour se lire comme le détail d'*Automatique*.
 */
@Composable
private fun NetworkRow(
    network: StreamNetwork,
    quality: StreamQuality,
    onChoose: (StreamNetwork, StreamQuality) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.DropdownList,
                    onClickLabel = "Choisir la qualité en ${network.label}",
                    onClick = { expanded = true },
                )
                .padding(start = DECALAGE_DETAIL, end = 8.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Text(
                text = network.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = quality.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        // Au niveau flottant des documents, comme le menu des playlists.
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            StreamQuality.entries.forEach { choix ->
                DropdownMenuItem(
                    text = { Text(choix.label) },
                    leadingIcon = { RadioButton(selected = choix == quality, onClick = null) },
                    onClick = {
                        expanded = false
                        onChoose(network, choix)
                    },
                )
            }
        }
    }
}

/** Le libellé du quatrième choix. */
internal const val AUTOMATIQUE = "Automatique"

/**
 * Le retrait des lignes de réseau : celui du texte des profils, après la marge
 * de la ligne, le bouton radio — 20 dp et 2 dp de marge de chaque côté, sans
 * cible tactile à lui puisque la ligne porte le geste — et son écart.
 */
private val DECALAGE_DETAIL = 16.dp + 24.dp + 16.dp
