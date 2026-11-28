package app.waveflow.prototype

import android.net.Uri
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import app.waveflow.model.Library
import app.waveflow.model.Song
import app.waveflow.ui.player.PlayerUiState
import app.waveflow.ui.playlists.PlaylistsUiState
import app.waveflow.ui.theme.WaveFlowTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ce que la relecture indépendante a relevé : une page dont la source est
 * illisible ne se titre pas « introuvable », et un simple bouton n'a pas
 * d'état « sélectionné » à annoncer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class PocketRelectureTest {

    @get:Rule
    val compose = createComposeRule()

    private val verbes = PocketVerbs({ _, _ -> }, {}, {}, { _, _ -> }, { _, _ -> })

    @Test
    fun `une page dont la bibliotheque est illisible ne se titre pas introuvable`() {
        compose.setContent {
            WaveFlowTheme {
                PocketTheme(dark = true) {
                    PageDeCollection(
                        page = Page.Album(12L),
                        library = Library(isLoading = false, errorMessage = "Accès refusé au stockage."),
                        playlists = PlaylistsUiState(isLoading = false),
                        player = PlayerUiState(),
                        verbs = verbes,
                        menu = { emptyList() },
                        onBack = {},
                    )
                }
            }
        }

        compose.onNodeWithText("Cette page n'a pas pu être lue.").assertExists()
        compose.onAllNodesWithText("Album introuvable").assertCountEquals(0)
    }

    @Test
    fun `une page lue sans resultat se dit introuvable`() {
        val morceau = Song(1L, Uri.parse("content://media/1"), "Titre", "Artiste", 2L, "Album", 7L, 180_000L, null)
        compose.setContent {
            WaveFlowTheme {
                PocketTheme(dark = true) {
                    PageDeCollection(
                        page = Page.Album(12L),
                        library = Library(isLoading = false, songs = listOf(morceau)),
                        playlists = PlaylistsUiState(isLoading = false),
                        player = PlayerUiState(),
                        verbs = verbes,
                        menu = { emptyList() },
                        onBack = {},
                    )
                }
            }
        }

        // L'en-tête et la barre du haut (transparente tant que l'en-tête est
        // déployé) portent tous deux le titre.
        compose.onAllNodesWithText("Album introuvable").assertCountEquals(2)
    }

    @Test
    fun `un simple bouton n'annonce pas d'etat selectionne, une bascule si`() {
        compose.setContent {
            WaveFlowTheme {
                PocketTheme(dark = true) {
                    androidx.compose.foundation.layout.Column {
                        PocketPill(text = "Tout voir", onClick = {}, description = "Voir tous les albums")
                        PocketPill(text = "Paroles", onClick = {
                        }, selected = false, description = "Afficher les paroles")
                    }
                }
            }
        }

        compose.onNodeWithContentDescription("Voir tous les albums")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
        compose.onNodeWithContentDescription("Afficher les paroles")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
    }
}
