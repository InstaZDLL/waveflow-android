package app.waveflow.ui.player

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.waveflow.playback.PlayingTrack
import app.waveflow.playback.TrackSource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ce que la file dit du morceau en cours.
 *
 * Elle le distinguait par la couleur seule. Un lecteur d'écran lit les mêmes
 * mots sur les cinq lignes, et rien n'y désigne celle qui sonne.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class QueueScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun piste(id: String, titre: String) = PlayingTrack(
        mediaId = id,
        title = titre,
        artist = "Bruit de Fond",
        album = null,
        artworkUri = null,
        localSongId = null,
        source = TrackSource.Local,
    )

    private fun afficher(index: Int) {
        compose.setContent {
            QueueScreen(
                state = PlayerUiState(
                    queue = listOf(
                        piste("local:1", "Première"),
                        piste("local:2", "Deuxième"),
                        piste("local:3", "Troisième"),
                    ),
                    queueIndex = index,
                ),
                onPlayAt = {},
                onMove = { _, _ -> },
                onRemove = {},
            )
        }
    }

    private fun etatAnnonce(valeur: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, valeur)

    @Test
    fun `le morceau en cours s'annonce comme tel`() {
        afficher(index = 1)

        compose.onNodeWithText("Deuxième").assert(etatAnnonce("En cours de lecture"))
    }

    @Test
    fun `les autres lignes n'annoncent aucun etat`() {
        // Sans cette réserve, toutes les lignes se diraient en lecture et
        // l'annonce ne distinguerait plus rien.
        afficher(index = 1)

        compose.onNodeWithText("Première")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        compose.onNodeWithText("Troisième")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }
}
