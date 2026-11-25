package app.waveflow.ui.components

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Les pilules s'annoncent comme des boutons : le `Surface` cliquable de
 * Material ne pose aucun rôle, c'est `Button` qui l'ajoute d'ordinaire.
 */
@RunWith(RobolectricTestRunner::class)
class PagePartsTest {

    @get:Rule
    val compose = createComposeRule()

    private val estUnBouton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    @Test
    fun `la pilule d'accent s'annonce comme un bouton`() {
        compose.setContent { AccentPill(text = "Lecture", onClick = {}) }

        compose.onNodeWithText("Lecture").assert(estUnBouton)
    }

    @Test
    fun `la pilule fantome s'annonce comme un bouton`() {
        compose.setContent { GhostPill(text = "Aléatoire", onClick = {}) }

        compose.onNodeWithText("Aléatoire").assert(estUnBouton)
    }
}
