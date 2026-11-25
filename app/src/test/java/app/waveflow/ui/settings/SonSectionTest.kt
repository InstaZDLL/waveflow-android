package app.waveflow.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import app.waveflow.model.ModeDeGain
import app.waveflow.model.Prereglage
import app.waveflow.model.ReglagesEgaliseur
import app.waveflow.model.ReglagesReplayGain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Ce que la section *Son* montre, et ce qu'elle remonte. Voir A1, A2, E6 et E7 de `docs/audio-avance.md`. */
@RunWith(RobolectricTestRunner::class)
class SonSectionTest {

    @get:Rule
    val compose = createComposeRule()

    private val gestes = mutableListOf<GesteSon>()

    private fun afficher(
        replayGain: ReglagesReplayGain = ReglagesReplayGain(),
        egaliseur: ReglagesEgaliseur = ReglagesEgaliseur.PAR_DEFAUT,
    ) {
        compose.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SonSection(replayGain = replayGain, egaliseur = egaliseur, onGeste = { gestes += it })
            }
        }
    }

    @Test
    fun `par defaut le ReplayGain est actif en automatique et l'egaliseur coupe`() {
        afficher()

        compose.onNodeWithText("Normaliser le volume").assertIsOn()
        compose.onNodeWithText("Automatique").assertIsSelected()
        compose.onNodeWithText("Égaliseur").assertIsOff()
        // Coupé, l'égaliseur ne montre rien qui n'agirait pas.
        assertEquals(0, compose.onAllNodesWithText("60 Hz").fetchSemanticsNodes().size)
    }

    @Test
    fun `le ReplayGain coupe cache ses sous-reglages`() {
        afficher(replayGain = ReglagesReplayGain(actif = false))

        compose.onNodeWithText("Normaliser le volume").assertIsOff()
        assertEquals(0, compose.onAllNodesWithText("Pré-amplification").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("Par album").fetchSemanticsNodes().size)
    }

    @Test
    fun `les interrupteurs et le mode remontent leur geste`() {
        afficher()

        compose.onNodeWithText("Normaliser le volume").performClick()
        compose.onNodeWithText("Par album").performScrollTo().performClick()
        compose.onNodeWithText("Éviter la saturation").performScrollTo().performClick()
        compose.onNodeWithText("Égaliseur").performScrollTo().performClick()

        assertEquals(
            listOf(
                GesteSon.ReplayGainActif(false),
                GesteSon.Mode(ModeDeGain.ParAlbum),
                GesteSon.EviterSaturation(false),
                GesteSon.EgaliseurActif(true),
            ),
            gestes,
        )
    }

    /** E7 : la valeur part au relâchement du curseur. */
    @Test
    fun `un curseur remonte sa valeur au relachement`() {
        afficher()

        compose.onNodeWithContentDescription("Pré-amplification")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(3f) }

        assertEquals(listOf<GesteSon>(GesteSon.Preampli(3f)), gestes)
    }

    /**
     * E7, au doigt : rien ne part pendant le glissement, une seule valeur au
     * relâchement. Un enregistrement déplacé dans `onValueChange` écrirait le
     * fichier à chaque image du geste.
     */
    @Test
    fun `un glissement ne remonte rien avant d'etre relache`() {
        afficher()
        val curseur = compose.onNodeWithContentDescription("Pré-amplification").performScrollTo()

        curseur.performTouchInput {
            down(center)
            moveBy(Offset(width / 10f, 0f))
            moveBy(Offset(width / 10f, 0f))
            moveBy(Offset(width / 10f, 0f))
        }
        compose.waitForIdle()
        assertEquals(emptyList<GesteSon>(), gestes)

        curseur.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(1, gestes.size)
        assertTrue(gestes.single().toString(), (gestes.single() as GesteSon.Preampli).db > 0f)
    }

    /** Le curseur est continu, sans crans dessinés : le demi-dB est tenu par l'arrondi. */
    @Test
    fun `un curseur s'arrondit au demi-decibel`() {
        afficher()

        compose.onNodeWithContentDescription("Gain par défaut")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(-3.3f) }

        assertEquals(listOf<GesteSon>(GesteSon.GainParDefaut(-3.5f)), gestes)
    }

    @Test
    fun `l'egaliseur actif montre ses six bandes et son prereglage`() {
        afficher(egaliseur = ReglagesEgaliseur.PAR_DEFAUT.avecActif(true).avecPrereglage(Prereglage.Rock))

        compose.onNodeWithText("Rock").performScrollTo()
        for (bande in listOf("60 Hz", "150 Hz", "400 Hz", "1 kHz", "2,4 kHz", "15 kHz")) {
            compose.onNodeWithText(bande).performScrollTo()
        }
        // La valeur de la deuxième bande du préréglage Rock, la seule à +4 dB.
        compose.onNodeWithText("+4,0 dB").performScrollTo()
    }

    @Test
    fun `choisir un prereglage le remonte`() {
        afficher(egaliseur = ReglagesEgaliseur.PAR_DEFAUT.avecActif(true))

        compose.onNodeWithText("Préréglage").performScrollTo().performClick()
        compose.onNodeWithText("Voix parlée").performScrollTo().performClick()

        assertEquals(listOf<GesteSon>(GesteSon.Choisir(Prereglage.VoixParlee)), gestes)
    }

    @Test
    fun `une bande bougee a la main se dit personnalisee`() {
        afficher(egaliseur = ReglagesEgaliseur.PAR_DEFAUT.avecActif(true).avecBande(1, 2f))

        compose.onNodeWithText("Personnalisé").performScrollTo()
    }

    @Test
    fun `un gain s'ecrit en francais avec son signe`() {
        assertEquals("0 dB", formatDb(0f))
        assertEquals("+1,5 dB", formatDb(1.5f))
        assertEquals("−3,0 dB", formatDb(-3f))
        assertEquals("+12,0 dB", formatDb(12f))
        assertEquals("0 dB", formatDb(-0.04f))
    }

    @Test
    fun `une bande se nomme en hertz ou en kilohertz`() {
        assertEquals(
            listOf("60 Hz", "150 Hz", "400 Hz", "1 kHz", "2,4 kHz", "15 kHz"),
            ReglagesEgaliseur.FREQUENCES_HZ.map(::nomDeBande),
        )
    }
}
