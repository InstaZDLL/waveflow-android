package app.waveflow.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.waveflow.model.PlaybackSpeed

/**
 * Le choix d'une vitesse de lecture.
 *
 * Une feuille et non un bouton qui fait tourner les valeurs : elles sont sept,
 * et parcourir six paliers pour revenir à ×1 ferait de chaque essai un aller
 * sans retour.
 *
 * @param speed la vitesse en vigueur, celle qui porte la coche. Elle vient des
 *   préférences déjà enregistrées et non d'un choix en attente : la feuille
 *   n'entretient aucun état, ce qui la dispense de le réconcilier avec le
 *   réglage que le service applique.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSpeedSheet(
    speed: Float,
    onPick: (speed: Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "Vitesse de lecture",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            Spacer(Modifier.height(4.dp))

            Text(
                // Ce que le réglage engage, dit une fois ici plutôt que deviné :
                // il ne vaut pas que pour le morceau en cours, et on le
                // retrouvera au lancement suivant.
                text = "S'applique à toute la lecture, et se retrouve au prochain lancement.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            Spacer(Modifier.height(12.dp))

            PlaybackSpeed.PROPOSEES.forEach { proposee ->
                val choisie = proposee == speed

                ListItem(
                    headlineContent = {
                        Text(
                            text = PlaybackSpeed.format(proposee) +
                                if (proposee == PlaybackSpeed.NORMALE) " (normale)" else "",
                        )
                    },
                    trailingContent = if (choisie) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                // Rien à décrire : `selectable` porte déjà
                                // l'état sélectionné, et le lecteur d'écran
                                // annoncerait deux fois la même chose.
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        // `selectable` et non `clickable` : ces lignes sont les
                        // valeurs d'un même réglage, et c'est ce qui fait dire
                        // « sélectionné » au lecteur d'écran plutôt que de
                        // laisser la coche muette.
                        .selectable(
                            selected = choisie,
                            role = Role.RadioButton,
                            onClick = { onPick(proposee) },
                        ),
                )
            }
        }
    }
}
