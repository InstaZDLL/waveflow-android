package app.waveflow.ui.cache

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Le cache de lecture, montré et vidable.
 *
 * Il ne contient que des pistes du serveur — les fichiers de l'appareil n'y
 * passent jamais — d'où sa place sous le compte plutôt que dans des réglages
 * généraux qui n'existent pas.
 */
@Composable
fun CacheSection(
    state: CacheUiState,
    onClear: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var confirmation by remember { mutableStateOf(false) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = "Cache de lecture",
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                text = when {
                    state.usedBytes == null -> "Calcul de la place occupée…"
                    else -> "%s occupés sur %s".format(
                        Formatter.formatShortFileSize(context, state.usedBytes),
                        Formatter.formatShortFileSize(context, state.maxBytes),
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            if (state.usedBytes != null && state.maxBytes > 0L) {
                LinearProgressIndicator(
                    progress = {
                        (state.usedBytes.toFloat() / state.maxBytes).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                text = "Les pistes déjà écoutées depuis le serveur repartent d'ici plutôt " +
                    "que du réseau. Les vider ne perd rien : elles restent sur le serveur.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            TextButton(
                // Rien à vider, ou vidage déjà en cours.
                enabled = !state.isClearing && state.usedBytes != null && !state.isEmpty,
                onClick = { confirmation = true },
            ) {
                Text(if (state.isClearing) "Vidage…" else "Vider le cache")
            }
        }
    }

    if (confirmation) {
        AlertDialog(
            onDismissRequest = { confirmation = false },
            title = { Text("Vider le cache ?") },
            // Ce que ça coûte vraiment : du réseau, pas de la musique perdue.
            text = {
                Text(
                    "Les pistes déjà téléchargées seront reprises au serveur à la " +
                        "prochaine écoute.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmation = false
                        onDismissError()
                        onClear()
                    },
                ) { Text("Vider") }
            },
            dismissButton = {
                TextButton(onClick = { confirmation = false }) { Text("Annuler") }
            },
        )
    }
}
