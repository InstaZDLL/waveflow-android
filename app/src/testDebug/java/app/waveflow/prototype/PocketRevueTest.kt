package app.waveflow.prototype

import android.net.Uri
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import app.waveflow.model.Library
import app.waveflow.model.Playlist
import app.waveflow.playback.PlayingTrack
import app.waveflow.playback.TrackSource
import app.waveflow.ui.home.HomeUiState
import app.waveflow.ui.player.LyricsUiState
import app.waveflow.ui.player.PlayerUiState
import app.waveflow.ui.playlists.PlaylistsUiState
import app.waveflow.ui.theme.WaveFlowTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Les cas limites relevés à la revue : une bibliothèque illisible n'est ni
 * vide ni prête, les playlists ne dépendent pas des morceaux locaux, et les
 * paroles gardent une hauteur lisible quand les détails prennent l'écran.
 */
@RunWith(RobolectricTestRunner::class)
class PocketRevueTest {

    @get:Rule
    val compose = createComposeRule()

    private val illisible = Library(isLoading = false, errorMessage = "Accès refusé au stockage.")

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun `une bibliotheque illisible le dit sur l'accueil, sans proposer de lecture`() {
        compose.setContent {
            WaveFlowTheme {
                PocketTheme(dark = true) {
                    PocketHome(
                        state = HomeUiState(isLoading = false),
                        library = illisible,
                        player = PlayerUiState(),
                        actions = HomeActions({ _, _ -> }, {}, {}, {}, {}, {}),
                    )
                }
            }
        }

        compose.onNodeWithText("La bibliothèque n'a pas pu être lue.").assertExists()
        compose.onNodeWithText("Au hasard").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun `les playlists se montrent meme sans morceau sur l'appareil`() {
        compose.setContent {
            WaveFlowTheme {
                PocketTheme(dark = true) {
                    PocketLibrary(
                        library = Library(isLoading = false),
                        playlists = PlaylistsUiState(
                            isLoading = false,
                            playlists = listOf(Playlist(1L, "Course du dimanche")),
                        ),
                        player = PlayerUiState(),
                        category = LibraryCategory.Playlists,
                        onCategory = {},
                        actions = LibraryActions({ _, _ -> }, {}, {}, {}, { emptyList() }),
                    )
                }
            }
        }

        compose.onNodeWithText("Course du dimanche").assertExists()
    }

    @Test
    @Config(qualifiers = "w320dp-h420dp-xhdpi", fontScale = 2f)
    fun `les paroles gardent une hauteur lisible quand les details prennent l'ecran`() {
        val piste = PlayingTrack(
            mediaId = "local:1",
            title = "Un titre assez long pour tenir sur deux lignes",
            artist = "Artiste",
            album = "Album",
            artworkUri = Uri.parse("content://media/1/albumart"),
            localSongId = 1L,
            source = TrackSource.Local,
        )
        compose.setContent {
            WaveFlowTheme {
                PocketTheme(dark = true) {
                    PocketNowPlaying(
                        state = PlayerUiState(
                            track = piste,
                            durationMs = 200_000L,
                            queue = listOf(piste),
                            queueIndex = 0,
                        ),
                        lyrics = LyricsUiState(shown = true, mediaId = null),
                        actions = NowPlayingActions(
                            {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, { null }, {}, {}, {}, {}, {},
                        ),
                    )
                }
            }
        }

        // La zone des paroles elle-même : sans plancher, elle recevait ce que
        // les détails laissaient — ici presque rien.
        val zone = compose.onNodeWithTag(LYRICS_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("${zone.bottom - zone.top}", zone.bottom - zone.top >= LYRICS_MIN - 1.dp)
    }
}
