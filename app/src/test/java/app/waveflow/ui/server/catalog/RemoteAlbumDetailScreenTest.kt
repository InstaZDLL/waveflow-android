package app.waveflow.ui.server.catalog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.waveflow.model.RemoteAlbum
import app.waveflow.model.RemoteAlbumDetail
import app.waveflow.model.RemoteSong
import app.waveflow.testing.remoteSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** L'écran d'un album distant : ce qu'il affiche et ce qu'il déclenche. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp-xhdpi")
class RemoteAlbumDetailScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val songs = listOf(
        remoteSong(id = "a", title = "Première Lueur", trackNumber = 1),
        remoteSong(id = "b", title = "Ciel Bas", trackNumber = 2),
    )

    private val detail = RemoteAlbumDetail(
        album = RemoteAlbum(
            id = "album-1",
            title = "Nuit Blanche",
            artist = "Aurore",
            artistId = "artiste-1",
            year = 2024,
            artworkUri = null,
        ),
        songs = songs,
    )

    private val joues = mutableListOf<RemoteSong>()
    private var lectures = 0
    private var aleatoires = 0

    private fun afficher(
        state: AlbumDetailState,
        nowPlayingMediaId: String? = null,
    ) {
        compose.setContent {
            RemoteAlbumDetailScreen(
                state = state,
                nowPlayingMediaId = nowPlayingMediaId,
                onSongClick = { joues += it },
                onPlay = { lectures++ },
                onShuffle = { aleatoires++ },
                onRetry = {},
            )
        }
    }

    @Test
    fun `l'album affiche ses commandes et ses morceaux`() {
        afficher(AlbumDetailState(value = detail))

        compose.onNodeWithText("Nuit Blanche").assertIsDisplayed()
        compose.onNodeWithText("Lecture").assertIsEnabled()
        compose.onNodeWithText("Aléatoire").assertIsEnabled()
        compose.onNodeWithText("Première Lueur").assertIsDisplayed()
    }

    @Test
    fun `Lecture et Aleatoire sont deux commandes distinctes`() {
        // Le piège déjà rencontré sur les albums locaux : les deux boutons
        // avaient fini par faire la même chose.
        afficher(AlbumDetailState(value = detail))

        compose.onNodeWithText("Lecture").performClick()
        compose.onNodeWithText("Aléatoire").performClick()

        assertEquals(1, lectures)
        assertEquals(1, aleatoires)
    }

    @Test
    fun `un album vide n'offre pas de lecture`() {
        afficher(AlbumDetailState(value = detail.copy(songs = emptyList())))

        compose.onNodeWithText("Lecture").assertIsNotEnabled()
    }

    @Test
    fun `toucher un morceau le demande a l'appelant`() {
        afficher(AlbumDetailState(value = detail))

        compose.onNodeWithText("Ciel Bas").performClick()

        assertEquals(listOf(songs[1]), joues)
    }

    @Test
    fun `un chargement en cours n'affiche ni commande ni morceau`() {
        afficher(AlbumDetailState(isLoading = true))

        assertTrue(compose.onAllNodes(hasText("Lecture")).fetchSemanticsNodes().isEmpty())
    }
}
