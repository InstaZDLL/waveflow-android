package app.waveflow.prototype

import android.net.Uri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import app.waveflow.model.Library
import app.waveflow.model.Song
import app.waveflow.ui.home.HomeUiState
import app.waveflow.ui.player.PlayerUiState
import app.waveflow.ui.theme.WaveFlowTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * L'accueil de Pocket selon ce que l'appareil sait : la reprise quand il y a
 * une écoute, de quoi commencer sinon, et le dire quand il n'y a pas de
 * musique. Rien d'inventé dans aucun des trois cas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class PocketHomeTest {

    @get:Rule
    val compose = createComposeRule()

    private val morceau = Song(
        id = 1L,
        uri = Uri.parse("content://media/1"),
        title = "Cat's Cradle",
        artist = "Purrple Cat",
        artistId = 2L,
        album = "Morning Coffee",
        albumId = 7L,
        durationMs = 187_000L,
        artworkUri = null,
    )

    private val bibliotheque = Library(isLoading = false, songs = listOf(morceau))

    private val actions = HomeActions(
        onPlay = { _, _ -> },
        onShuffle = {},
        onTogglePlayPause = {},
        onOpenAlbum = {},
        onOpenCategory = {},
        onAnotherSuggestion = {},
    )

    private fun afficher(state: HomeUiState, library: Library) {
        compose.setContent {
            WaveFlowTheme {
                PocketTheme(dark = true) {
                    PocketHome(state = state, library = library, player = PlayerUiState(), actions = actions)
                }
            }
        }
    }

    @Test
    fun `avec une ecoute l'accueil propose de la reprendre`() {
        afficher(
            HomeUiState(
                isLoading = false,
                resume = morceau,
                resumeAlbum = bibliotheque.album(7L),
                songCount = 1,
                albumCount = 1,
            ),
            bibliotheque,
        )

        compose.onNodeWithText("REPRENDRE").assertExists()
        compose.onAllNodesWithText("Au hasard").assertCountEquals(0)
    }

    @Test
    fun `sans historique l'accueil propose de commencer, sans reprise inventee`() {
        afficher(HomeUiState(isLoading = false, songCount = 1, albumCount = 1), bibliotheque)

        compose.onAllNodesWithText("REPRENDRE").assertCountEquals(0)
        compose.onNodeWithText("Au hasard").assertExists()
    }

    @Test
    fun `sans musique l'accueil le dit`() {
        afficher(HomeUiState(isLoading = false), Library(isLoading = false))

        compose.onNodeWithText("Aucun morceau sur l'appareil.").assertExists()
        compose.onAllNodesWithText("Au hasard").assertCountEquals(0)
    }
}
