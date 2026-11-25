package app.waveflow.ui.player

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.waveflow.model.ThemeChoice
import app.waveflow.ui.theme.WaveFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Ce que la progression dessine aux limites : une ligne plus étroite que sa
 * tête, et les lettres d'une boucle de quelques secondes posée contre un bord.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class ProgressLineTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `une ligne de lumiere plus etroite que sa tete se dessine`() {
        compose.setContent {
            WaveFlowTheme(theme = ThemeChoice.Light) {
                ProgressLine(
                    progress = { 0.5f },
                    playing = false,
                    scrubbing = true,
                    modifier = Modifier.width(4.dp).height(40.dp),
                )
            }
        }

        // Le dessin a lieu à la capture : sans le rayon borné, il lève.
        compose.onRoot().captureToImage()
    }

    @Test
    fun `loin des bords, chaque lettre reste contre son trait`() {
        assertEquals(90f to 110f, loopLetterLefts(aX = 100f, aWidth = 10f, bX = 110f, bWidth = 10f, width = 400f))
    }

    @Test
    fun `contre un bord, les lettres d'une boucle courte ne se chevauchent pas`() {
        // A au tout début du morceau, B trois pixels plus loin.
        assertEquals(0f to 10f, loopLetterLefts(aX = 0f, aWidth = 10f, bX = 3f, bWidth = 10f, width = 400f))
        // Les deux tout au bout : B bute sur le bord, A se range avant elle.
        assertEquals(380f to 390f, loopLetterLefts(aX = 397f, aWidth = 10f, bX = 399f, bWidth = 10f, width = 400f))
    }
}
