package app.waveflow.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import app.waveflow.playback.AbLoopState
import app.waveflow.ui.theme.WaveFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La barre de position du lecteur plein écran, qui remplace le `Slider` de
 * Material : ce qu'il faisait sans qu'on le demande — aller où l'on touche,
 * suivre le doigt et ne déplacer la lecture qu'au lâcher, se laisser lire et
 * régler par un lecteur d'écran — doit se faire encore.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class PlayerSeekBarTest {

    @get:Rule
    val compose = createComposeRule()

    private val sauts = mutableListOf<Long>()

    /** Le morceau à l'écran : un test peut le changer sous la barre. */
    private var piste by mutableStateOf(PISTE)

    /** La durée annoncée par le lecteur : elle aussi peut changer sous la barre. */
    private var duree by mutableStateOf(DUREE)

    private fun afficher(durationMs: Long = DUREE, abLoop: AbLoopState = AbLoopState.Off) {
        duree = durationMs
        compose.setContent {
            WaveFlowTheme {
                PlayerSeekBar(
                    positionMs = 30_000L,
                    durationMs = duree,
                    trackKey = piste,
                    playing = false,
                    abLoop = abLoop,
                    onSeek = { sauts += it },
                    onMarkAbLoop = {},
                )
            }
        }
    }

    private fun barre() = compose.onNodeWithContentDescription("Position dans le morceau")

    @Test
    fun `un lecteur d'ecran lit la position et la regle`() {
        afficher()

        barre()
            .assertIsEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "0:30 sur 3:20"))
        barre().performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }

        assertEquals(listOf(DUREE / 2), sauts)
    }

    @Test
    fun `un appui va a l'endroit touche`() {
        afficher()

        barre().performTouchInput { click(center) }

        val saut = sauts.single()
        assertTrue("saut à $saut", saut in (DUREE / 2 - TOLERANCE)..(DUREE / 2 + TOLERANCE))
    }

    @Test
    fun `un glissement ne deplace la lecture qu'au lacher`() {
        afficher()

        barre().performTouchInput {
            down(centerLeft + Offset(width * 0.1f, 0f))
            moveTo(centerLeft + Offset(width * 0.5f, 0f))
            moveTo(centerLeft + Offset(width * 0.75f, 0f))
        }
        // Rien tant que le doigt est posé : la lecture ne saute pas à chaque
        // pas du glissement.
        assertTrue(sauts.isEmpty())

        barre().performTouchInput { up() }

        val saut = sauts.single()
        assertTrue("saut à $saut", saut in (DUREE * 3 / 4 - TOLERANCE)..(DUREE * 3 / 4 + TOLERANCE))
    }

    @Test
    fun `sans duree, la barre ne se regle pas`() {
        afficher(durationMs = 0L)

        barre()
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetProgress))
        barre().performTouchInput { click(center) }

        assertTrue(sauts.isEmpty())
    }

    @Test
    fun `au clavier, les fleches avancent de cinq secondes et les pages de trente`() {
        afficher()
        barre().performSemanticsAction(SemanticsActions.RequestFocus)

        barre().performKeyInput { pressKey(Key.DirectionRight) }
        barre().performKeyInput { pressKey(Key.PageDown) }
        barre().performKeyInput { pressKey(Key.MoveEnd) }

        // Depuis 0:30, que la position affichée n'a pas quittée : le faux
        // lecteur ne bouge pas.
        assertEquals(listOf(35_000L, 0L, DUREE), sauts)
    }

    @Test
    fun `une touche etrangere au curseur ne deplace rien`() {
        assertNull(keyboardSeekTarget(Key.Spacebar, 30_000L, DUREE))
        assertEquals(DUREE, keyboardSeekTarget(Key.PageUp, DUREE - 1_000L, DUREE))
    }

    @Test
    fun `une boucle posee se dit avec ses deux bornes`() {
        afficher(abLoop = AbLoopState.Armed(PISTE, startMs = 10_000L, endMs = 20_000L))

        compose.onNodeWithContentDescription("Boucle active de 0:10 à 0:20 — appuyer pour l'effacer")
            .assertIsEnabled()
    }

    @Test
    fun `les bornes se placent en part de la duree`() {
        assertEquals(
            LoopMarks(0.05f, 0.1f),
            loopMarks(AbLoopState.Armed(PISTE, 10_000L, 20_000L), PISTE, DUREE),
        )
        assertEquals(LoopMarks(0.05f, null), loopMarks(AbLoopState.Started(PISTE, 10_000L), PISTE, DUREE))
    }

    @Test
    fun `une boucle d'un autre morceau ne se dessine pas`() {
        // L'écran peut recevoir la piste suivante un tic avant que le service
        // n'efface la boucle de la précédente.
        assertNull(loopMarks(AbLoopState.Armed("autre", 10_000L, 20_000L), PISTE, DUREE))
        assertNull(loopMarks(AbLoopState.Armed(PISTE, 10_000L, 20_000L), PISTE, 0L))
        assertNull(loopMarks(AbLoopState.Off, PISTE, DUREE))
    }

    @Test
    fun `un glissement commence avant un changement de morceau est oublie`() {
        afficher()
        barre().performTouchInput {
            down(centerLeft + Offset(width * 0.1f, 0f))
            moveTo(centerLeft + Offset(width * 0.5f, 0f))
        }

        // Le morceau enchaîne sous le doigt : lâcher ne doit pas sauter dans
        // le suivant à une position choisie pour le précédent.
        compose.runOnIdle { piste = "local:2" }
        barre().performTouchInput { up() }

        assertTrue("sauts : $sauts", sauts.isEmpty())
        barre().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "0:30 sur 3:20"))
    }

    @Test
    fun `un appui commence avant un changement de morceau est oublie`() {
        afficher()
        barre().performTouchInput { down(center) }

        compose.runOnIdle { piste = "local:2" }
        barre().performTouchInput { up() }

        assertTrue("sauts : $sauts", sauts.isEmpty())
    }

    @Test
    fun `un glissement interrompu par la perte de la duree est oublie`() {
        afficher()
        barre().performTouchInput {
            down(centerLeft + Offset(width * 0.1f, 0f))
            moveTo(centerLeft + Offset(width * 0.5f, 0f))
        }

        // Le même morceau perd sa durée sous le doigt, puis la retrouve : la
        // tête ne doit pas rester figée là où le doigt l'avait laissée.
        compose.runOnIdle { duree = 0L }
        barre().performTouchInput { up() }
        compose.runOnIdle { duree = DUREE }

        assertTrue("sauts : $sauts", sauts.isEmpty())
        barre().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "0:30 sur 3:20"))
    }

    @Test
    fun `une boucle d'un autre morceau n'allume pas le bouton`() {
        afficher(abLoop = AbLoopState.Armed("autre", startMs = 10_000L, endMs = 20_000L))

        compose.onNodeWithContentDescription("Boucle A-B : poser le début").assertIsEnabled()
    }

    private companion object {
        const val PISTE = "local:1"
        const val DUREE = 200_000L

        /** Un pixel de part et d'autre, sur 411 dp : moins d'une seconde. */
        const val TOLERANCE = 2_000L
    }
}
