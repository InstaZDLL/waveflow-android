package app.waveflow.ui.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.waveflow.model.Song
import app.waveflow.ui.components.LibraryStateContainer
import app.waveflow.ui.components.SongRow

/**
 * Onglet Titres : tous les morceaux de l'appareil.
 *
 * Vue pure : ne dépend que de [LibraryUiState], donc prévisualisable et
 * testable sans ViewModel. Le lecteur est posé par-dessus par l'appelant.
 *
 * @param bottomPadding dégage la hauteur du mini-player pour que le dernier
 *   morceau reste atteignable.
 */
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onSongClick: (Song) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    onSongLongClick: (Song) -> Unit = {},
) {
    LibraryStateContainer(
        isLoading = state.isLoading,
        errorMessage = state.errorMessage,
        isEmpty = state.isEmpty,
        emptyMessage = "Aucun morceau trouvé sur cet appareil.",
        onRetry = onRetry,
        modifier = modifier,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = bottomPadding),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.songs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    isCurrent = song.id == state.nowPlayingId,
                    onClick = { onSongClick(song) },
                    onLongClick = { onSongLongClick(song) },
                )
            }
        }
    }
}
