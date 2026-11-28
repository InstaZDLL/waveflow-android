package app.waveflow.prototype

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.waveflow.model.Library
import app.waveflow.model.Playlist
import app.waveflow.model.Song
import app.waveflow.playback.PlayingTrack
import app.waveflow.playback.TrackSource
import app.waveflow.ui.home.HomeUiState
import app.waveflow.ui.player.LyricsUiState
import app.waveflow.ui.player.PlayerUiState
import app.waveflow.ui.playlists.PlaylistsUiState
import app.waveflow.ui.theme.WaveFlowTheme
import org.junit.Assert.assertEquals
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

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi", fontScale = 2f)
    fun `les paroles restent cherchees quand leur zone change de place`() {
        val signaux = mutableListOf<Boolean>()
        var hauteur by mutableStateOf(800.dp)
        val piste = PlayingTrack(
            mediaId = "local:1",
            title = "Un titre assez long pour tenir sur deux lignes",
            artist = "Artiste",
            album = "Album",
            artworkUri = null,
            localSongId = 1L,
            source = TrackSource.Local,
        )
        compose.setContent {
            WaveFlowTheme {
                PocketTheme(dark = true) {
                    Box(Modifier.height(hauteur)) {
                        PocketNowPlaying(
                            state = PlayerUiState(
                                track = piste,
                                durationMs = 200_000L,
                                queue = listOf(piste),
                                queueIndex = 0,
                            ),
                            lyrics = LyricsUiState(shown = true, mediaId = null),
                            actions = NowPlayingActions(
                                {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, { null }, {}, {}, {}, {},
                                { signaux += it },
                            ),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()

        // La place se réduit : les paroles passent dans la colonne qui défile.
        hauteur = 380.dp
        compose.waitForIdle()

        assertTrue("$signaux", signaux.last())
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun `une page restauree avant la bibliotheque attend au lieu de se dire introuvable`() {
        compose.setContent {
            WaveFlowTheme {
                PocketTheme(dark = true) {
                    PocketCollection(
                        kind = CollectionKind.Album,
                        title = "",
                        subtitle = null,
                        artworkUri = null,
                        songs = emptyList(),
                        player = PlayerUiState(),
                        actions = CollectionActions({}, { _, _ -> }, {}, { emptyList() }),
                        loading = true,
                    )
                }
            }
        }

        compose.onNodeWithText("Lecture de la bibliothèque…").assertExists()
        compose.onNodeWithText("Rien à écouter ici.").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun `la reprise rejoue le morceau sur l'album ou la bibliotheque le range maintenant`() {
        val ancien = Song(1L, Uri.parse("content://media/1"), "Titre", "Artiste", 2L, "Album", 7L, 180_000L, null)
        // Après un rechargement, le morceau est rangé dans l'album 8, avec un voisin.
        val deplace = ancien.copy(albumId = 8L)
        val voisin = Song(2L, Uri.parse("content://media/2"), "Voisin", "Artiste", 2L, "Album", 8L, 180_000L, null)
        val bibliotheque = Library(isLoading = false, songs = listOf(deplace, voisin))
        var file: List<Song>? = null
        compose.setContent {
            WaveFlowTheme {
                PocketTheme(dark = true) {
                    PocketHome(
                        state = HomeUiState(
                            isLoading = false,
                            resume = ancien,
                            resumeAlbum = null,
                            songCount = 2,
                            albumCount = 1,
                        ),
                        library = bibliotheque,
                        player = PlayerUiState(),
                        actions = HomeActions({ queue, _ -> file = queue }, {}, {}, {}, {}, {}),
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("Lecture").performClick()

        assertEquals(listOf(1L, 2L), file?.map { it.id })
    }
}
