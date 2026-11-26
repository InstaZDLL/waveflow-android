package app.waveflow.ui.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipeUp
import app.waveflow.data.lyrics.LyricsResult
import app.waveflow.model.Lyrics
import app.waveflow.model.SyncedLine
import app.waveflow.playback.TrackSource
import app.waveflow.ui.theme.WaveFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Les paroles dans le lecteur (`docs/paroles.md`, P11 à P19) : la ligne chantée
 * suit la position, un appui sur une ligne s'y déplace, un appui ailleurs
 * ramène la pochette.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class LyricsPaneTest {

    @get:Rule
    val compose = createComposeRule()

    private val lignes = listOf(
        SyncedLine(1_000, "Une"),
        SyncedLine(1_600, "Deux"),
        SyncedLine(9_000, "Trois"),
    )

    private val sauts = mutableListOf<Long>()
    private var masquages = 0
    private var essais = 0

    /** La position publiée par le lecteur, que les tests font bouger. */
    private var position by mutableLongStateOf(0L)
    private var lecture by mutableStateOf(false)

    /** Pour passer en mode clavier, où une ligne peut prendre le focus. */
    private lateinit var modes: InputModeManager

    private fun afficher(
        result: LyricsResult? = LyricsResult.Found(Lyrics.Synced(lignes)),
        mediaId: String = PISTE,
        source: TrackSource = TrackSource.Remote,
        // Un faux qui se comporte comme le vrai : un déplacement, même en
        // pause, publie sa position (P13).
        surDeplacement: (Long) -> Unit = {
            sauts += it
            position = it
        },
    ) {
        compose.setContent {
            modes = LocalInputModeManager.current
            WaveFlowTheme {
                LyricsPane(
                    state = LyricsUiState(shown = true, mediaId = mediaId, result = result),
                    trackKey = PISTE,
                    source = source,
                    positionMs = position,
                    playing = lecture,
                    playbackSpeed = 1f,
                    glow = Color.Gray,
                    onSeek = surDeplacement,
                    onHide = { masquages++ },
                    onRetry = { essais++ },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(PANNEAU),
                )
            }
        }
    }

    private fun chantee() = compose.onNode(isSelected())

    @Test
    fun `avant la premiere ligne, aucune n'est chantee`() {
        afficher()

        chantee().assertDoesNotExist()
    }

    @Test
    fun `en pause, la ligne chantee suit la position publiee par un deplacement`() {
        afficher()

        position = 1_700L
        compose.waitForIdle()
        chantee().assert(hasText("Deux"))

        position = 1_000L
        compose.waitForIdle()
        chantee().assert(hasText("Une"))
    }

    @Test
    fun `un appui sur une ligne s'y deplace, et elle devient la ligne chantee meme en pause`() {
        afficher()

        compose.onNodeWithText("Trois").performClick()

        assertEquals(listOf(9_000L), sauts)
        chantee().assert(hasText("Trois"))
        assertEquals(0, masquages)
    }

    @Test
    fun `pendant la lecture, la ligne avance entre deux tics, sans depasser d'un tic`() {
        compose.mainClock.autoAdvance = false
        position = 900L
        lecture = true
        afficher()
        compose.mainClock.advanceTimeBy(50)
        chantee().assertDoesNotExist()

        // 900 ms publiées, 200 ms écoulées : « Une » (1 000 ms) est chantée sans
        // attendre le tic suivant.
        compose.mainClock.advanceTimeBy(200)
        chantee().assert(hasText("Une"))

        // Plus aucun tic : la position s'arrête à 1 400 ms, avant « Deux ».
        compose.mainClock.advanceTimeBy(3_000)
        chantee().assert(hasText("Une"))
    }

    @Test
    fun `en pause, la position ne se prolonge pas`() {
        compose.mainClock.autoAdvance = false
        position = 900L
        lecture = false
        afficher()

        compose.mainClock.advanceTimeBy(1_000)

        chantee().assertDoesNotExist()
    }

    @Test
    fun `un autre morceau aux paroles egales suit sa propre position`() {
        // Deux versions d'un même morceau, mêmes paroles : la recherche du
        // second peut arriver sans passer par l'attente.
        var piste by mutableStateOf(PISTE)
        compose.setContent {
            WaveFlowTheme {
                LyricsPane(
                    state = LyricsUiState(
                        shown = true,
                        mediaId = piste,
                        result = LyricsResult.Found(Lyrics.Synced(lignes)),
                    ),
                    trackKey = piste,
                    source = TrackSource.Remote,
                    positionMs = position,
                    playing = false,
                    playbackSpeed = 1f,
                    glow = Color.Gray,
                    onSeek = {},
                    onHide = {},
                    onRetry = {},
                )
            }
        }
        position = 1_700L
        compose.waitForIdle()
        chantee().assert(hasText("Deux"))

        piste = "remote:autre-version"
        position = 9_500L
        compose.waitForIdle()

        chantee().assert(hasText("Trois"))
    }

    @Test
    fun `des paroles simples d'un autre morceau repartent de leur premiere ligne`() {
        var piste by mutableStateOf(PISTE)
        var paroles by mutableStateOf(Lyrics.Plain((0 until 80).map { "Vers $it de A" }))
        compose.setContent {
            WaveFlowTheme {
                LyricsPane(
                    state = LyricsUiState(shown = true, mediaId = piste, result = LyricsResult.Found(paroles)),
                    trackKey = piste,
                    source = TrackSource.Remote,
                    positionMs = 0L,
                    playing = false,
                    playbackSpeed = 1f,
                    glow = Color.Gray,
                    onSeek = {},
                    onHide = {},
                    onRetry = {},
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(PANNEAU),
                )
            }
        }
        compose.onNodeWithTag(PANNEAU).performTouchInput { swipeUp() }
        compose.onNodeWithText("Vers 0 de A").assertDoesNotExist()

        // Le texte de B arrive directement, sans l'attente entre les deux.
        piste = "remote:b"
        paroles = Lyrics.Plain((0 until 80).map { "Vers $it de B" })

        compose.onNodeWithText("Vers 0 de B").assertIsDisplayed()
    }

    @Test
    fun `un appui hors d'une ligne ramene la pochette`() {
        afficher()

        // Le haut de la zone : la marge qui pose la ligne chantée au tiers.
        compose.onNodeWithTag(PANNEAU).performTouchInput { click(Offset(width - 2f, 4f)) }

        assertEquals(1, masquages)
        assertTrue(sauts.isEmpty())
    }

    @Test
    fun `le lecteur d'ecran peut revenir a la pochette`() {
        afficher()

        compose.onNodeWithTag(PANNEAU).performSemanticsActionCustom("Revenir à la pochette")

        assertEquals(1, masquages)
    }

    @Test
    fun `des paroles cherchees pour un autre morceau ne se dessinent pas`() {
        afficher(mediaId = "remote:autre")

        compose.onNodeWithText("Une").assertDoesNotExist()
        compose.onNodeWithText("Recherche des paroles…").assertIsDisplayed()
    }

    @Test
    fun `sans paroles, l'ecran dit ou il a cherche`() {
        afficher(result = LyricsResult.None, source = TrackSource.Local)

        compose.onNodeWithText("Pas de paroles pour ce morceau.").assertIsDisplayed()
        compose.onNodeWithText("Ni dans ses tags, ni dans un fichier .lrc à côté de lui.").assertIsDisplayed()
    }

    @Test
    fun `un serveur muet se dit, et se reessaie`() {
        afficher(result = LyricsResult.Unavailable)

        compose.onNodeWithText("Paroles indisponibles.").assertIsDisplayed()
        compose.onNodeWithText("Réessayer").performClick()

        assertEquals(1, essais)
        assertEquals(0, masquages)
    }

    @Test
    fun `des paroles simples se lisent, et un appui ramene la pochette`() {
        afficher(result = LyricsResult.Found(Lyrics.Plain(listOf("Premier vers", "", "Second vers"))))

        compose.onNode(hasText("Premier vers") and hasClickAction()).assertDoesNotExist()
        compose.onNodeWithText("Second vers").performClick()

        assertEquals(1, masquages)
        assertTrue(sauts.isEmpty())
    }

    @Test
    fun `parcourir les paroles suspend le suivi, un bouton le retablit`() {
        afficher(result = LyricsResult.Found(Lyrics.Synced(beaucoupDeLignes)))

        compose.onNodeWithTag(PANNEAU).performTouchInput { swipeUp() }
        compose.onNodeWithText("Revenir à la ligne").assertIsDisplayed()

        compose.onNodeWithText("Revenir à la ligne").performClick()
        compose.onNodeWithText("Revenir à la ligne").assertDoesNotExist()
        assertEquals(0, masquages)
    }

    @Test
    fun `le suivi reprend de lui-meme cinq secondes apres le dernier defilement`() {
        afficher(result = LyricsResult.Found(Lyrics.Synced(beaucoupDeLignes)))
        compose.onNodeWithTag(PANNEAU).performTouchInput { swipeUp() }
        compose.onNodeWithText("Revenir à la ligne").assertIsDisplayed()

        compose.mainClock.advanceTimeBy(4_000)
        compose.onNodeWithText("Revenir à la ligne").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithText("Revenir à la ligne").assertDoesNotExist()
    }

    @Test
    fun `le lecteur d'ecran qui fait defiler les paroles suspend aussi le suivi`() {
        afficher(result = LyricsResult.Found(Lyrics.Synced(beaucoupDeLignes)))

        compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 600f) }

        compose.onNodeWithText("Revenir à la ligne").assertIsDisplayed()
    }

    @Test
    fun `un doigt pose et immobile tient le parcours ouvert`() {
        afficher(result = LyricsResult.Found(Lyrics.Synced(beaucoupDeLignes)))
        compose.onNodeWithTag(PANNEAU).performTouchInput {
            down(center)
            moveBy(Offset(0f, -300f))
        }

        compose.mainClock.advanceTimeBy(6_000)
        compose.onNodeWithText("Revenir à la ligne").assertIsDisplayed()

        compose.onNodeWithTag(PANNEAU).performTouchInput { up() }
    }

    @Test
    fun `page suivante au clavier suspend aussi le suivi`() {
        afficher(result = LyricsResult.Found(Lyrics.Synced(beaucoupDeLignes)))
        compose.runOnIdle { modes.requestInputMode(InputMode.Keyboard) }
        compose.onNodeWithText("Ligne 1").requestFocus()

        compose.onNodeWithText("Ligne 1").performKeyInput { pressKey(Key.PageDown) }

        compose.onNodeWithText("Revenir à la ligne").assertIsDisplayed()
    }

    @Test
    fun `suivre la ligne chantee n'est pas un parcours`() {
        afficher(result = LyricsResult.Found(Lyrics.Synced(beaucoupDeLignes)))

        // « Ligne 2 » est en vue : le suivi y glisse.
        position = 4_100L
        compose.waitForIdle()
        compose.onNodeWithText("Revenir à la ligne").assertDoesNotExist()

        // « Ligne 40 » est hors de vue : le suivi y saute d'un coup.
        position = 80_100L
        compose.waitForIdle()
        compose.onNodeWithText("Revenir à la ligne").assertDoesNotExist()
        chantee().assert(hasText("Ligne 40"))
    }

    private val beaucoupDeLignes = (0 until 60).map { SyncedLine(it * 2_000L, "Ligne $it") }

    private fun SemanticsNodeInteraction.performSemanticsActionCustom(libelle: String) {
        val actions = fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { actions.first { it.label == libelle }.action() }
    }

    private companion object {
        const val PISTE = "remote:a"
        const val PANNEAU = "paroles"
    }
}
