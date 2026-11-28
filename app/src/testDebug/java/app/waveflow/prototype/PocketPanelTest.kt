package app.waveflow.prototype

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.waveflow.playback.PlayingTrack
import app.waveflow.playback.TrackSource
import app.waveflow.ui.player.PlayerUiState
import app.waveflow.ui.theme.WaveFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Le panneau de lecture des fenêtres étendues : la suite de la file quand
 * l'ordre de la file est celui de la lecture, la file entière sinon.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1280dp-h800dp-xhdpi")
class PocketPanelTest {

    @get:Rule
    val compose = createComposeRule()

    private val file = listOf("Alpha", "Bravo", "Charlie").mapIndexed { i, titre ->
        PlayingTrack(
            mediaId = "m$i",
            title = titre,
            artist = "Artiste",
            album = "Album",
            artworkUri = null,
            localSongId = i.toLong(),
            source = TrackSource.Local,
        )
    }

    private fun afficher(state: PlayerUiState, onPlayQueueItem: (Int) -> Unit = {}) {
        compose.setContent {
            WaveFlowTheme {
                PocketTheme(dark = true) {
                    PocketPlaybackPanel(
                        state = state,
                        actions = PocketNowPlayingTest.ACTIONS.copy(onPlayQueueItem = onPlayQueueItem),
                        onExpand = {},
                        modifier = Modifier.width(PANEL_WIDTH),
                    )
                }
            }
        }
    }

    @Test
    fun `dans l'ordre de la file le panneau montre la suite`() {
        afficher(PlayerUiState(track = file[1], queue = file, queueIndex = 1))

        compose.onNodeWithText("À SUIVRE").assertExists()
        compose.onNodeWithText("Charlie").assertExists()
        // Ce qui est déjà passé n'est pas « à suivre ».
        compose.onAllNodesWithText("Alpha").assertCountEquals(0)
    }

    @Test
    fun `en aleatoire le panneau montre toute la file, sans pretendre a une suite`() {
        var joue = -1
        afficher(
            PlayerUiState(track = file[1], queue = file, queueIndex = 1, shuffleEnabled = true),
            onPlayQueueItem = { joue = it },
        )

        compose.onNodeWithText("LA FILE").assertExists()
        compose.onAllNodesWithText("À SUIVRE").assertCountEquals(0)
        // Le morceau d'avant dans la liste peut encore jouer : il est là, et
        // un appui le lance à sa vraie place.
        compose.onNodeWithText("Alpha").performClick()
        assertEquals(0, joue)
    }
}
