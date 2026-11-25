package app.waveflow.ui.home

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.waveflow.model.Album
import app.waveflow.model.Song
import app.waveflow.testing.song
import app.waveflow.ui.theme.WaveFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/** Le canevas de l'accueil : le salut, les compteurs, la reprise. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp-xhdpi")
class HomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `le salut suit l'heure comme sur le bureau`() {
        assertEquals("Bonne nuit", greetingFor(4))
        assertEquals("Bonjour", greetingFor(5))
        assertEquals("Bonjour", greetingFor(17))
        assertEquals("Bonsoir", greetingFor(18))
        assertEquals("Bonsoir", greetingFor(22))
        assertEquals("Bonne nuit", greetingFor(23))
    }

    @Test
    fun `la ligne de compteurs donne la date puis la bibliotheque`() {
        assertEquals(
            "mercredi 25 novembre · 1 titre · 12 albums",
            countersLine(LocalDateTime.of(2026, 11, 25, 10, 0), songCount = 1, albumCount = 12),
        )
    }

    @Test
    fun `reprendre et voir l'album renvoient a la derniere ecoute`() {
        val reprise = song(3, title = "Nocturne", albumId = 20)
        val album = Album(
            id = 20,
            title = "Album 3",
            artist = "Artiste 3",
            artworkUri = null,
            trackCount = 1,
            durationMs = 60_000L,
        )
        val joues = mutableListOf<Song>()
        val ouverts = mutableListOf<Album>()

        compose.setContent {
            WaveFlowTheme {
                HomeScreen(
                    state = HomeUiState(isLoading = false, resume = reprise, resumeAlbum = album, songCount = 1),
                    onSongClick = { joues += it },
                    onAlbumClick = { ouverts += it },
                    now = { LocalDateTime.of(2026, 11, 25, 21, 0) },
                )
            }
        }

        compose.onNodeWithText("Bonsoir").assertExists()
        compose.onNodeWithText("Nocturne").assertExists()
        compose.onNodeWithText("Reprendre").performClick()
        compose.onNodeWithText("Voir l'album").performClick()

        assertEquals(listOf(reprise), joues)
        assertEquals(listOf(album), ouverts)
    }
}
