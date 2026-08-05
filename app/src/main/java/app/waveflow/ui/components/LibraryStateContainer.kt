package app.waveflow.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Enveloppe commune aux écrans alimentés par la bibliothèque : chargement,
 * erreur avec relance, vide, ou contenu.
 *
 * Les trois onglets partagent la même source de données, donc les mêmes états
 * intermédiaires ; les factoriser évite qu'ils divergent.
 */
@Composable
fun LibraryStateContainer(
    isLoading: Boolean,
    errorMessage: String?,
    isEmpty: Boolean,
    emptyMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            errorMessage != null -> CenteredMessage(
                message = errorMessage,
                modifier = Modifier.align(Alignment.Center),
            ) {
                TextButton(onClick = onRetry) { Text("Réessayer") }
            }

            isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

            isEmpty -> CenteredMessage(
                message = emptyMessage,
                modifier = Modifier.align(Alignment.Center),
            )

            else -> content()
        }
    }
}

@Composable
fun CenteredMessage(
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}
