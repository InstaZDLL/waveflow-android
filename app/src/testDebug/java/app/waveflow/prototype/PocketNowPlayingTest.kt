package app.waveflow.prototype

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import app.waveflow.playback.PlayingTrack
import app.waveflow.playback.TrackSource
import app.waveflow.ui.player.LyricsUiState
import app.waveflow.ui.player.PlayerUiState
import app.waveflow.ui.theme.WaveFlowTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Now Playing de Pocket : la pochette s'adapte à la hauteur qui reste, et les
 * pastilles d'état ne paraissent que quand un réglage s'écarte de l'ordinaire.
 *
 * Le lecteur est en pause : la pochette y recule à 92 %, d'où des tailles
 * mesurées avec cette marge.
 */
@RunWith(RobolectricTestRunner::class)
class PocketNowPlayingTest {

    @get:Rule
    val compose = createComposeRule()

    private fun afficher(state: PlayerUiState = ETAT) {
        compose.setContent {
            WaveFlowTheme {
                PocketTheme(dark = true) {
                    PocketNowPlaying(state = state, lyrics = LyricsUiState(), actions = ACTIONS)
                }
            }
        }
    }

    private fun largeurPochette() =
        compose.onNodeWithContentDescription("Pochette").getUnclippedBoundsInRoot().let { it.right - it.left }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun `sur un Pixel 8 la pochette prend la largeur`() {
        afficher()

        // 411 dp moins deux gouttières de 24 : 363, à 92 % en pause.
        assertTrue("${largeurPochette()}", largeurPochette() > 320.dp)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f)
    fun `sur un petit telephone a police doublee la pochette reste en grand`() {
        afficher()

        // Android grossit les grands textes moins que les petits : le titre
        // ne double pas, et il reste plus de 200 dp à la pochette.
        assertTrue("${largeurPochette()}", largeurPochette() > COMPACT_COVER)
    }

    @Test
    @Config(qualifiers = "w360dp-h480dp-xhdpi", fontScale = 2f)
    fun `sur un ecran bas a police doublee la pochette passe en vignette`() {
        afficher()

        assertTrue("${largeurPochette()}", largeurPochette() <= COMPACT_COVER)
        // Les commandes restent : la disposition compacte ne les sacrifie pas.
        compose.onAllNodesWithContentDescription("Lecture").assertCountEquals(1)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun `a vitesse normale aucune pastille d'etat`() {
        afficher()

        compose.onAllNodesWithContentDescription("Vitesse de lecture", substring = true).assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Minuterie de veille active").assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun `une vitesse et une minuterie actives se disent entre les durees`() {
        afficher(ETAT.copy(playbackSpeed = 1.5f, sleepTimerActive = true))

        compose.onAllNodesWithContentDescription("Vitesse de lecture", substring = true).assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Minuterie de veille active").assertCountEquals(1)
    }

    private companion object {
        val PISTE = PlayingTrack(
            mediaId = "local:1",
            title = "Un titre assez long pour tenir sur deux lignes au moins",
            artist = "Artiste",
            album = "Album",
            artworkUri = null,
            localSongId = 1L,
            source = TrackSource.Local,
        )

        val ETAT = PlayerUiState(
            track = PISTE,
            isPlaying = false,
            positionMs = 30_000L,
            durationMs = 200_000L,
            queue = listOf(PISTE),
            queueIndex = 0,
        )

        val ACTIONS = NowPlayingActions(
            onCollapse = {},
            onTogglePlayPause = {},
            onSkipNext = {},
            onSkipPrevious = {},
            onSeek = {},
            onToggleShuffle = {},
            onCycleRepeat = {},
            onPlayQueueItem = {},
            onMoveQueueItem = { _, _ -> },
            onRemoveQueueItem = {},
            onStartSleepTimer = {},
            onCancelSleepTimer = {},
            onSleepTimerRemainingMs = { null },
            onSetPlaybackSpeed = {},
            onMarkAbLoop = {},
            onToggleLyrics = {},
            onRetryLyrics = {},
            onLyricsOnScreen = {},
        )
    }
}
