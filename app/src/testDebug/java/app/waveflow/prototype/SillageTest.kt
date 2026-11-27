package app.waveflow.prototype

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import app.waveflow.ui.theme.WaveFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Le sillage, la progression de Pocket : il va où l'on touche, dit le temps
 * restant, et un geste commencé sur un morceau ne finit pas dans le suivant —
 * le piège que la barre de production a payé (`PlayerSeekBarTest`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class SillageTest {

    @get:Rule
    val compose = createComposeRule()

    private val sauts = mutableListOf<Long>()
    private var piste by mutableStateOf("local:1")

    private fun afficher() {
        compose.setContent {
            WaveFlowTheme {
                PocketTheme(dark = true) {
                    Sillage(
                        positionMs = 30_000L,
                        durationMs = DUREE,
                        trackKey = piste,
                        playing = false,
                        onSeek = { sauts += it },
                    )
                }
            }
        }
    }

    private fun sillage() = compose.onNodeWithContentDescription("Position dans le morceau")

    @Test
    fun `un appui va ou l'on touche et le temps restant se lit en negatif`() {
        afficher()

        compose.onNodeWithText("−2:50").assertExists()
        sillage().performTouchInput { click(Offset(width / 2f, height / 2f)) }

        assertEquals(listOf(DUREE / 2), sauts)
    }

    @Test
    fun `un glissement commence sur un morceau ne saute pas dans le suivant`() {
        afficher()

        sillage().performTouchInput {
            down(Offset(width * 0.2f, height / 2f))
            moveTo(Offset(width * 0.5f, height / 2f))
        }
        piste = "local:2"
        compose.waitForIdle()
        sillage().performTouchInput { up() }

        assertTrue("$sauts", sauts.isEmpty())
    }

    private companion object {
        const val DUREE = 200_000L
    }
}
