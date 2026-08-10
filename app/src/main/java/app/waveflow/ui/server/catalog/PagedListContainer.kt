package app.waveflow.ui.server.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.waveflow.ui.components.CenteredMessage

/**
 * États d'une liste paginée qui n'a encore rien à montrer.
 *
 * Une fois du contenu affiché, l'écran ne le remplace plus : une page qui
 * échoue se signale en pied de liste, pas en effaçant ce qui est déjà lu.
 */
@Composable
fun PagedListContainer(
    state: PagedList<*>,
    emptyMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    when {
        state.hasContent -> content()

        state.isInitialLoad -> Box(modifier = modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        state.errorMessage != null -> Box(modifier = modifier.fillMaxSize()) {
            CenteredMessage(
                message = state.errorMessage,
                modifier = Modifier.align(Alignment.Center),
                action = { Button(onClick = onRetry) { Text("Réessayer") } },
            )
        }

        state.isEmpty -> Box(modifier = modifier.fillMaxSize()) {
            CenteredMessage(
                message = emptyMessage,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/**
 * Pied de liste : progression de la page suivante, ou son échec.
 *
 * L'erreur est ici plutôt qu'à la place de la liste : le contenu déjà chargé
 * reste consultable, et réessayer ne fait repartir que la page manquante.
 */
@Composable
fun PagedListFooter(
    state: PagedList<*>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }

        state.errorMessage != null -> CenteredMessage(
            message = state.errorMessage,
            modifier = modifier.fillMaxWidth(),
            action = { Button(onClick = onRetry) { Text("Réessayer") } },
        )
    }
}

/**
 * Demande la page suivante quand le bas de la liste approche.
 *
 * [PREFETCH_DISTANCE] éléments d'avance, pour que la page arrive avant que le
 * doigt n'atteigne le vide.
 */
@Composable
fun LoadMoreOnApproachingEnd(
    listState: LazyListState,
    itemCount: Int,
    onLoadMore: () -> Unit,
) {
    val shouldLoad by remember(itemCount) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= itemCount - PREFETCH_DISTANCE
        }
    }

    LaunchedEffect(shouldLoad, itemCount) {
        if (shouldLoad) onLoadMore()
    }
}

private const val PREFETCH_DISTANCE = 5
