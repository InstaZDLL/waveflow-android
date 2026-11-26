package app.waveflow.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.waveflow.data.lyrics.LyricsResult
import app.waveflow.model.Lyrics
import app.waveflow.model.SyncedLine
import app.waveflow.playback.PlayingTrack
import app.waveflow.playback.TrackSource
import app.waveflow.ui.theme.WaveFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Les paroles dans le lecteur plein écran (`docs/paroles.md`, P11) : un appui
 * sur la pochette la remplace par les paroles, un autre la ramène, et
 * l'en-tête garde ses quatre boutons.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class NowPlayingLyricsTest {

    @get:Rule
    val compose = createComposeRule()

    private val piste = PlayingTrack(
        mediaId = "remote:a",
        title = "Résonance",
        artist = "Bruit de Fond",
        album = "Écho",
        artworkUri = null,
        localSongId = null,
        source = TrackSource.Remote,
    )

    /** L'état des paroles comme le ViewModel le tient : la bascule le change. */
    private var paroles by mutableStateOf(LyricsUiState(mediaId = "remote:a"))

    /** Ce que l'écran a dit de la présence de la zone des paroles, dans l'ordre. */
    private val enVue = mutableListOf<Boolean>()

    private fun afficher() {
        compose.setContent {
            WaveFlowTheme {
                NowPlayingScreen(
                    state = PlayerUiState(track = piste, durationMs = 60_000L),
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
                    lyrics = paroles,
                    onToggleLyrics = {
                        paroles = paroles.copy(
                            shown = !paroles.shown,
                            result = LyricsResult.Found(Lyrics.Synced(listOf(SyncedLine(1_000, "Une ligne")))),
                        )
                    },
                    onRetryLyrics = {},
                    onLyricsOnScreen = { enVue += it },
                )
            }
        }
    }

    @Test
    fun `un appui sur la pochette la remplace par les paroles`() {
        afficher()

        compose.onNodeWithContentDescription("Pochette").performClick()

        compose.onNodeWithText("Une ligne").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pochette").assertDoesNotExist()
    }

    @Test
    fun `revenir a la pochette depuis les paroles passe par la meme bascule`() {
        afficher()
        compose.onNodeWithContentDescription("Pochette").performClick()

        // L'appui hors d'une ligne est éprouvé dans `LyricsPaneTest` ; ici, que
        // la zone des paroles rende la main à la même bascule que la pochette.
        val retour = compose.onNode(porteLAction(RETOUR)).fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
            .single { it.label == RETOUR }
        compose.runOnIdle { retour.action() }

        compose.onNodeWithContentDescription("Pochette").assertIsDisplayed()
        compose.onNodeWithText("Une ligne").assertDoesNotExist()
    }

    private fun porteLAction(libelle: String) = SemanticsMatcher("porte l'action « $libelle »") { noeud ->
        noeud.config.getOrNull(SemanticsActions.CustomActions).orEmpty().any { it.label == libelle }
    }

    @Test
    fun `l'en-tete garde ses quatre boutons`() {
        afficher()
        compose.onNodeWithContentDescription("Pochette").performClick()

        compose.onNodeWithContentDescription("Réduire le lecteur").assertIsDisplayed()
        compose.onNodeWithContentDescription("Vitesse de lecture : ×1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Minuterie de veille").assertIsDisplayed()
        compose.onNodeWithContentDescription("Voir la file — 0 morceau ensuite").assertIsDisplayed()
        assertEquals(true, paroles.shown)
    }

    @Test
    fun `la file qui prend la place des paroles les retire de l'ecran, sans changer le choix`() {
        afficher()
        compose.onNodeWithContentDescription("Pochette").performClick()
        compose.waitForIdle()
        assertEquals(listOf(true), enVue)

        compose.onNodeWithContentDescription("Voir la file — 0 morceau ensuite").performClick()
        compose.waitForIdle()

        assertEquals(listOf(true, false), enVue)
        assertEquals(true, paroles.shown)
    }

    private companion object {
        const val RETOUR = "Revenir à la pochette"
    }
}
