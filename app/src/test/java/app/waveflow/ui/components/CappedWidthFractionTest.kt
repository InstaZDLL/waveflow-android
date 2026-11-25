package app.waveflow.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La largeur de la grande pochette : une fraction de la place sur un
 * téléphone, jamais plus que son plafond sur une tablette.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1200dp-h2000dp-mdpi")
class CappedWidthFractionTest {

    @get:Rule
    val compose = createComposeRule()

    private fun poser(place: Dp) {
        compose.setContent {
            Box(Modifier.width(place)) {
                Box(
                    Modifier
                        .testTag("pochette")
                        .cappedWidthFraction(0.5f, 280.dp)
                        .aspectRatio(1f),
                )
            }
        }
    }

    @Test
    fun `sur un telephone la pochette garde sa fraction`() {
        poser(400.dp)

        compose.onNodeWithTag("pochette").assertWidthIsEqualTo(200.dp)
    }

    @Test
    fun `sur une tablette la pochette s'arrete a son plafond`() {
        poser(1_000.dp)

        compose.onNodeWithTag("pochette")
            .assertWidthIsEqualTo(280.dp)
            .assertHeightIsEqualTo(280.dp)
    }
}
