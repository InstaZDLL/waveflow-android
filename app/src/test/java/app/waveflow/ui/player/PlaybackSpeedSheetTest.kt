package app.waveflow.ui.player

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Ce que la feuille de vitesse montre, et ce qu'elle rend en retour. */
@RunWith(RobolectricTestRunner::class)
class PlaybackSpeedSheetTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `la vitesse en vigueur est celle qui apparait selectionnee`() {
        // Sans elle, la feuille serait une liste de sept lignes identiques :
        // impossible d'y lire ce qu'on écoute, donc impossible de savoir de
        // combien on s'en écarte en choisissant.
        compose.setContent {
            PlaybackSpeedSheet(speed = 1.5f, onPick = {}, onDismiss = {})
        }

        compose.onNodeWithText("×1,5").assertIsSelected()
    }

    @Test
    fun `choisir une vitesse la remonte telle quelle`() {
        var choisie: Float? = null
        compose.setContent {
            PlaybackSpeedSheet(speed = 1f, onPick = { choisie = it }, onDismiss = {})
        }

        compose.onNodeWithText("×1,75").performClick()

        assertEquals(1.75f, choisie!!, 0f)
    }

    @Test
    fun `une vitesse absente des propositions apparait quand meme`() {
        // Le format persisté ne se limite pas aux sept propositions : une
        // version future peut offrir un réglage plus fin. La feuille s'ouvrirait
        // alors sans rien de coché, à contredire le bouton qui l'a ouverte.
        compose.setContent {
            PlaybackSpeedSheet(speed = 1.1f, onPick = {}, onDismiss = {})
        }

        compose.onNodeWithText("×1,1").assertIsSelected()
    }

    @Test
    fun `la vitesse normale se nomme, pour qu'on sache ou revenir`() {
        // « ×1 » seul, au milieu de six autres nombres, ne signale pas qu'il est
        // le point de départ — et c'est celui qu'on cherche pour tout annuler.
        compose.setContent {
            PlaybackSpeedSheet(speed = 1.5f, onPick = {}, onDismiss = {})
        }

        compose.onNodeWithText("×1 (normale)").assertIsNotSelected()
    }
}
