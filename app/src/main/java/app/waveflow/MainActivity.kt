package app.waveflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.waveflow.ui.library.LibraryScreen
import app.waveflow.ui.library.LibraryViewModel
import app.waveflow.ui.permission.AudioPermissionGate
import app.waveflow.ui.player.MiniPlayer
import app.waveflow.ui.player.NowPlayingScreen
import app.waveflow.ui.theme.WaveFlowTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WaveFlowTheme {
                WaveFlowRoot()
            }
        }
    }
}

/** Hauteur réservée sous la liste pour que le mini-player ne masque rien. */
private val MiniPlayerSpace = 76.dp

private const val PLAYER_TRANSITION_MS = 300

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WaveFlowRoot() {
    val viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
    val libraryState by viewModel.uiState.collectAsState()
    val playerState by viewModel.playerState.collectAsState()

    var playerExpanded by rememberSaveable { mutableStateOf(false) }
    val hasTrack = playerState.song != null

    // Si la file se vide, il n'y a plus rien à afficher en plein écran.
    LaunchedEffect(hasTrack) {
        if (!hasTrack) playerExpanded = false
    }

    BackHandler(enabled = playerExpanded) { playerExpanded = false }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = { TopAppBar(title = { Text("WaveFlow") }) },
        ) { innerPadding ->
            AudioPermissionGate(modifier = Modifier.padding(innerPadding)) {
                // Ne démarre le scan et la connexion au service qu'une fois la
                // permission acquise.
                LaunchedEffect(Unit) { viewModel.onAudioAccessGranted() }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    LibraryScreen(
                        state = libraryState,
                        onSongClick = viewModel::playSong,
                        onRetry = viewModel::retry,
                        contentPadding = PaddingValues(
                            bottom = if (hasTrack) MiniPlayerSpace else 0.dp,
                        ),
                    )

                    // Inutile de le composer sous le lecteur plein écran, qui
                    // le recouvre entièrement.
                    if (!playerExpanded) {
                        MiniPlayer(
                            state = playerState,
                            onExpand = { playerExpanded = true },
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onSkipNext = viewModel::skipNext,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }

        // Le lecteur plein écran est posé par-dessus le Scaffold (et donc
        // par-dessus la barre de titre), comme une feuille qui remonte.
        AnimatedVisibility(
            visible = playerExpanded,
            enter = slideInVertically(animationSpec = tween(PLAYER_TRANSITION_MS)) { it },
            exit = slideOutVertically(animationSpec = tween(PLAYER_TRANSITION_MS)) { it },
            modifier = Modifier.fillMaxSize(),
        ) {
            NowPlayingScreen(
                state = playerState,
                onCollapse = { playerExpanded = false },
                onTogglePlayPause = viewModel::togglePlayPause,
                onSkipNext = viewModel::skipNext,
                onSkipPrevious = viewModel::skipPrevious,
                onSeek = viewModel::seekTo,
                onToggleShuffle = viewModel::toggleShuffle,
                onCycleRepeat = viewModel::cycleRepeatMode,
            )
        }
    }
}
