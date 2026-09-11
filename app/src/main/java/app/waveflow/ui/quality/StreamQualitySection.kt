package app.waveflow.ui.quality

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.waveflow.model.StreamQuality

/**
 * La qualité de lecture, choisie parmi des profils.
 *
 * Sous le compte serveur, à côté du cache : elle ne concerne que les pistes du
 * serveur, et décide de ce que le cache contiendra.
 *
 * La ligne entière porte le geste et le bouton radio n'est qu'un témoin, comme
 * pour le thème : c'est ce qui fait annoncer « 2 sur 3 » par le lecteur d'écran,
 * et donne à la cible toute la largeur.
 */
@Composable
fun StreamQualitySection(
    state: StreamQualityUiState,
    onChoose: (StreamQuality) -> Unit,
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
                    QualityRow(
                        quality = quality,
                        selected = quality == state.current,
                        enabled = state.isSelectable(quality),
                        onChoose = onChoose,
                    )
                }
            }

            Text(
                text = note(state),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.isCurrentUnavailable) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun QualityRow(
    quality: StreamQuality,
    selected: Boolean,
    enabled: Boolean,
    onChoose: (StreamQuality) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = { onChoose(quality) },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(start = 16.dp),
        ) {
            Text(
                text = quality.label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = quality.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Ce qu'il faut savoir sous les choix.
 *
 * Par défaut, que le réglage ne touche pas la file en cours : le rendu est posé
 * sur chaque piste quand elle y entre, et changer d'avis en pleine écoute sans
 * que rien ne bouge se lirait sinon comme une panne.
 */
internal fun note(state: StreamQualityUiState): String = when {
    state.isCurrentUnavailable ->
        "Ce serveur ne sait pas transcoder : ses pistes ne pourront pas être lues " +
            "dans la qualité choisie. Revenez à la qualité d'origine."

    state.transcodingAvailable == false ->
        "Ce serveur ne sait pas transcoder : seule la qualité d'origine est disponible."

    else -> "S'applique aux pistes lancées ensuite, pas à la file en cours."
}
