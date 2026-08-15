package app.waveflow.ui.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import app.waveflow.playback.PlayingTrack
import app.waveflow.playback.TrackSource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ce que le mini-lecteur donne à voir pendant l'attente.
 *
 * Une piste distante obtient son ticket avant la moindre requête de diffusion,
 * et rien ne l'annonçait : le bouton proposait « Lecture » sur une piste déjà
 * demandée, ce qui invite à retaper plutôt qu'à patienter.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class MiniPlayerTest {

    @get:Rule
    val compose = createComposeRule()

    private val track = PlayingTrack(
        mediaId = "remote:a",
        title = "Résonance",
        artist = "Bruit de Fond",
        album = "Écho",
        artworkUri = null,
        localSongId = null,
        source = TrackSource.Remote,
    )

    @Test
    fun `l'attente se voit`() {
        compose.setContent {
            MiniPlayer(
                state = PlayerUiState(track = track, isPlaying = false, isBuffering = true),
                onExpand = {},
                onTogglePlayPause = {},
                onSkipNext = {},
            )
        }

        compose.onNodeWithContentDescription("Chargement du morceau").assertIsDisplayed()
    }

    @Test
    fun `l'attente remplace le bouton plutot que de s'ajouter`() {
        // Laisser « Lecture » sous les doigts pendant le chargement invite à
        // retaper, ce qui relance la file au lieu d'attendre.
        compose.setContent {
            MiniPlayer(
                state = PlayerUiState(track = track, isPlaying = false, isBuffering = true),
                onExpand = {},
                onTogglePlayPause = {},
                onSkipNext = {},
            )
        }

        compose.onNodeWithContentDescription("Lecture").assertDoesNotExist()
        compose.onNodeWithContentDescription("Pause").assertDoesNotExist()
    }

    @Test
    fun `une lecture en cours montre la pause et non l'attente`() {
        compose.setContent {
            MiniPlayer(
                state = PlayerUiState(track = track, isPlaying = true, isBuffering = false),
                onExpand = {},
                onTogglePlayPause = {},
                onSkipNext = {},
            )
        }

        compose.onNodeWithContentDescription("Pause").assertIsDisplayed()
        compose.onNodeWithContentDescription("Chargement du morceau").assertDoesNotExist()
    }

    @Test
    fun `une lecture en pause montre le bouton et non l'attente`() {
        compose.setContent {
            MiniPlayer(
                state = PlayerUiState(track = track, isPlaying = false, isBuffering = false),
                onExpand = {},
                onTogglePlayPause = {},
                onSkipNext = {},
            )
        }

        compose.onNodeWithContentDescription("Lecture").assertIsDisplayed()
        compose.onNodeWithContentDescription("Chargement du morceau").assertDoesNotExist()
    }
}
