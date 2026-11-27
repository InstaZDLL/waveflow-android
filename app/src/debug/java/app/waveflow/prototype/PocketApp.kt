package app.waveflow.prototype

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.waveflow.model.Library
import app.waveflow.model.Song
import app.waveflow.ui.components.Artwork
import app.waveflow.ui.formatDuration
import app.waveflow.ui.player.LyricsUiState
import app.waveflow.ui.player.PlayerUiState

/**
 * La coquille du prototype : la page ouverte, le mini-lecteur posé sur la
 * barre de navigation, et Now Playing qui monte par-dessus.
 *
 * Au point d'étape, seul l'onglet Bibliothèque porte une page — un
 * **échafaudage** (la liste des titres, pour lancer la lecture), qui n'est pas
 * la bibliothèque proposée. Accueil et Recherche disent qu'ils viennent après
 * la validation de la direction.
 */
@Composable
fun PocketApp(
    library: Library,
    player: PlayerUiState,
    lyrics: LyricsUiState,
    actions: NowPlayingActions,
    onPlay: (queue: List<Song>, song: Song) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    var onglet by rememberSaveable { mutableStateOf(PocketTab.Bibliotheque) }
    val colors = Pocket.colors
    BackHandler(enabled = expanded) { onExpandedChange(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (onglet) {
                    PocketTab.Bibliotheque -> Echafaudage(library, player, onPlay)
                    else -> AVenir(onglet)
                }
            }
            PocketMiniPlayer(
                state = player,
                onExpand = { onExpandedChange(true) },
                onTogglePlayPause = actions.onTogglePlayPause,
                onSkipNext = actions.onSkipNext,
                onSkipPrevious = actions.onSkipPrevious,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            PocketNavBar(selected = onglet, onSelect = { onglet = it })
        }

        AnimatedVisibility(
            visible = expanded && player.track != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            PocketNowPlaying(state = player, lyrics = lyrics, actions = actions)
        }
    }
}

/** La liste des titres qui sert à lancer la lecture : un échafaudage, pas la bibliothèque proposée. */
@Composable
private fun Echafaudage(library: Library, player: PlayerUiState, onPlay: (List<Song>, Song) -> Unit) {
    val colors = Pocket.colors
    LazyColumn(
        contentPadding = PaddingValues(bottom = 16.dp),
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        item(key = "titre") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)) {
                Text("Bibliothèque", style = MaterialTheme.typography.displaySmall, color = colors.ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Échafaudage du prototype : la bibliothèque proposée vient après la validation de Now Playing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink3,
                )
            }
        }
        when {
            library.isLoading -> item(key = "attente") { Message("Lecture de la bibliothèque…") }
            library.isEmpty -> item(key = "vide") { Message("Aucun morceau sur l'appareil.") }
            else -> items(library.songs, key = { it.id }) { song ->
                val joue = player.track?.localSongId == song.id
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable { onPlay(library.songs, song) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Artwork(song.artworkUri, shape = PocketShapes.thumbnail, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            song.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (joue) colors.accent else colors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            song.displayArtist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.ink2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        formatDuration(song.durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.ink3,
                    )
                }
            }
        }
    }
}

@Composable
private fun AVenir(onglet: PocketTab) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Text(onglet.label, style = MaterialTheme.typography.titleLarge, color = Pocket.colors.ink)
        Spacer(Modifier.height(8.dp))
        Text(
            "Cet écran sera prototypé une fois la direction de Now Playing validée.",
            style = MaterialTheme.typography.bodyMedium,
            color = Pocket.colors.ink2,
        )
    }
}

@Composable
private fun Message(texte: String) {
    Text(
        texte,
        style = MaterialTheme.typography.bodyMedium,
        color = Pocket.colors.ink2,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
    )
}
