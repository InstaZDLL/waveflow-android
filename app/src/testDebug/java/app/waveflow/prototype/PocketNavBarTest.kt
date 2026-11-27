package app.waveflow.prototype

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.waveflow.ui.theme.WaveFlowTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La barre de navigation sous une page : elle prend sa hauteur et laisse le
 * reste à la page, à police normale comme doublée.
 */
@RunWith(RobolectricTestRunner::class)
class PocketNavBarTest {

    @get:Rule
    val compose = createComposeRule()

    private fun afficher() {
        compose.setContent {
            WaveFlowTheme {
                PocketTheme(dark = true) {
                    Column(Modifier.fillMaxSize()) {
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .testTag("page"),
                        )
                        PocketNavBar(selected = PocketTab.Bibliotheque, onSelect = {})
                    }
                }
            }
        }
    }

    private fun hauteurPage() =
        compose.onNodeWithTag("page").getUnclippedBoundsInRoot().let { it.bottom - it.top }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun `la barre laisse l'ecran a la page`() {
        afficher()

        assertTrue("${hauteurPage()}", hauteurPage() > 780.dp)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f)
    fun `a police doublee la barre grandit sans prendre l'ecran`() {
        afficher()

        assertTrue("${hauteurPage()}", hauteurPage() > 520.dp)
    }
}
