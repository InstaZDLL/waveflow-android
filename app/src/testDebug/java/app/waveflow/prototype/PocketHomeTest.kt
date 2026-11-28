package app.waveflow.prototype

import android.net.Uri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.waveflow.model.Library
import app.waveflow.model.Song
import app.waveflow.playback.PlayingTrack
import app.waveflow.playback.TrackSource
import app.waveflow.suggestion.SorteDeSuggestion
import app.waveflow.ui.home.HomeUiState
import app.waveflow.ui.home.SuggestionBand
import app.waveflow.ui.player.PlayerUiState
import app.waveflow.ui.theme.WaveFlowTheme
import org.junit.Assert.assertEquals
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

    private val piste = PlayingTrack(
        mediaId = "1",
        title = "Cat's Cradle",
        artist = "Purrple Cat",
        album = "Morning Coffee",
        artworkUri = null,
        localSongId = 1L,
        source = TrackSource.Local,
    )

    private fun afficherEnLecture(playbackShown: Boolean) {
        compose.setContent {
            WaveFlowTheme {
                PocketTheme(dark = true) {
                    PocketHome(
                        state = HomeUiState(isLoading = false, songCount = 1, albumCount = 1),
                        library = bibliotheque,
                        player = PlayerUiState(track = piste),
                        actions = actions,
                        playbackShown = playbackShown,
                    )
                }
            }
        }
    }

    @Test
    fun `l'accueil dit ce qui joue`() {
        afficherEnLecture(playbackShown = false)

        compose.onNodeWithText("EN ÉCOUTE").assertExists()
    }

    @Test
    fun `a cote du panneau de lecture l'accueil ne repete pas ce qui joue`() {
        afficherEnLecture(playbackShown = true)

        compose.onAllNodesWithText("EN ÉCOUTE").assertCountEquals(0)
        // Le reste de l'accueil est là : seule la carte s'efface.
        compose.onNodeWithText("Albums").assertExists()
    }

    @Test
    fun `le disque de la suggestion deja chargee met en pause ou reprend, sans relancer`() {
        var bascules = 0
        var relances = 0
        val bande = SuggestionBand(morceau, SorteDeSuggestion.JamaisEcoutee, "Jamais écouté", null)
        compose.setContent {
            WaveFlowTheme {
                PocketTheme(dark = true) {
                    PocketHome(
                        state = HomeUiState(isLoading = false, songCount = 1, albumCount = 1, suggestion = bande),
                        library = bibliotheque,
                        player = PlayerUiState(track = piste),
                        actions = actions.copy(onTogglePlayPause = { bascules++ }, onPlay = { _, _ -> relances++ }),
                        // Sans la carte « En écoute » : le seul disque est celui de la suggestion.
                        playbackShown = true,
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("Lecture").performClick()

        assertEquals(1, bascules)
        assertEquals(0, relances)
    }

    @Test
    fun `un morceau charge rend a l'album de la reprise sa place dans les ecoutes`() {
        compose.setContent {
            WaveFlowTheme {
                PocketTheme(dark = true) {
                    PocketHome(
                        state = avecReprise,
                        library = bibliotheque,
                        player = PlayerUiState(track = piste),
                        actions = actions,
                    )
                }
            }
        }

        // La carte dit « En écoute », pas « Reprendre » : l'album n'est nulle part ailleurs.
        compose.onNodeWithText("Écoutés récemment").assertExists()
    }

    @Test
    fun `la reprise a l'ecran garde son album hors des ecoutes`() {
        afficher(avecReprise, bibliotheque)

        compose.onNodeWithText("REPRENDRE").assertExists()
        compose.onAllNodesWithText("Écoutés récemment").assertCountEquals(0)
    }

    private val avecReprise
        get() = HomeUiState(
            isLoading = false,
            resume = morceau,
            resumeAlbum = bibliotheque.album(7L),
            recentlyPlayed = listOf(morceau),
            songCount = 1,
            albumCount = 1,
        )

    @Test
    fun `sans musique l'accueil le dit`() {
        afficher(HomeUiState(isLoading = false), Library(isLoading = false))

        compose.onNodeWithText("Aucun morceau sur l'appareil.").assertExists()
        compose.onAllNodesWithText("Au hasard").assertCountEquals(0)
    }
}
