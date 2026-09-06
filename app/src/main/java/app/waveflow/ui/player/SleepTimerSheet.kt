package app.waveflow.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.minutes

/**
 * Les durées proposées.
 *
 * Cinq minutes pour la sieste, une heure pour la nuit ; au-delà, l'utilisateur
 * s'est endormi bien avant. Rien de plus fin qu'un quart d'heure : personne ne
 * règle son coucher à la minute près, et chaque ligne de plus est une ligne à
 * lire dans le noir.
 */
private val DUREES = listOf(5, 15, 30, 45, 60)

/**
 * Le choix d'une minuterie de veille.
 *
 * Une feuille modale plutôt qu'un menu : les cibles y sont assez grandes pour
 * être touchées d'une main, dans un lit, sans regarder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    remainingMs: () -> Long?,
    onPick: (durationMs: Long) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Le décompte se relit ici, à la seconde, plutôt que de suivre l'état du
    // lecteur : celui-ci n'émet plus quand la lecture est en pause, et la
    // minuterie, elle, continue de courir. Le tic ne vit que le temps de la
    // feuille — c'est le seul moment où quelqu'un lit vraiment le chiffre.
    var restant by remember { mutableStateOf(remainingMs()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            restant = remainingMs()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "Minuterie de veille",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            Spacer(Modifier.height(4.dp))

            Text(
                // Sans minuterie, dire qu'il n'y en a pas : « la lecture
                // s'arrêtera d'elle-même » décrivait ce qui *arriverait* en
                // choisissant une durée, mais se lisait comme si une minuterie
                // courait déjà.
                text = restant
                    ?.let { "Arrêt dans ${formatRemaining(it)}" }
                    ?: "Aucune minuterie : la lecture continuera.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            Spacer(Modifier.height(12.dp))

            DUREES.forEach { minutes ->
                ListItem(
                    headlineContent = { Text("$minutes minutes") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(minutes.minutes.inWholeMilliseconds) },
                )
            }

            if (restant != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onCancelTimer,
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    Text("Annuler la minuterie")
                }
            }
        }
    }
}

/**
 * Le temps restant, arrondi à la minute supérieure.
 *
 * Arrondi vers le haut : afficher « 0 minute » pendant les dernières secondes
 * ferait croire que la minuterie est passée sans rien faire.
 */
internal fun formatRemaining(remainingMs: Long): String {
    // Division entière arrondie vers le haut : ajouter une minute moins un
    // millième de seconde avant de diviser fait basculer tout reste non nul.
    val minutes = ((remainingMs + MS_PAR_MINUTE - 1L) / MS_PAR_MINUTE).toInt()
    return if (minutes <= 1) "1 minute" else "$minutes minutes"
}

private const val MS_PAR_MINUTE = 60_000L
