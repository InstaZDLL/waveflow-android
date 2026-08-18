package app.waveflow.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import app.waveflow.playback.PlayingTrack
import app.waveflow.playback.TrackSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La place que les listes laissent au mini-lecteur.
 *
 * Elle était écrite en dur : 76 dp, pour une carte qui en occupe 82 aux
 * réglages par défaut et 103,5 à polices doublées. Le bas de la dernière ligne
 * disparaissait donc sous la carte — un peu pour tout le monde, beaucoup pour
 * qui agrandit les caractères.
 *
 * Ces tests ne fixent aucun nombre : ils comparent la réserve annoncée à la
 * hauteur que la carte occupe vraiment, dans le même environnement. Un nombre
 * écrit en dur ne peut satisfaire les deux réglages de police à la fois.
 */
@RunWith(RobolectricTestRunner::class)
class MiniPlayerHostTest {

    @get:Rule
    val compose = createComposeRule()

    private val track = PlayingTrack(
        mediaId = "remote:a",
        title = "Résonance",
        artist = "Bruit de Fond",
        album = "Écho",
        artworkUri = null,
        localSongId = null,
        source = TrackSource.Remote,
    )

    private val state = PlayerUiState(track = track, isPlaying = true)

    /** Ce que l'hôte annonce aux listes, et ce que la carte occupe réellement. */
    private class Mesures {
        var reserve: Dp = Dp.Unspecified
        var carte: Dp = Dp.Unspecified
    }

    private fun poser(): Mesures {
        val mesures = Mesures()

        compose.setContent {
            val density = LocalDensity.current

            Box(Modifier.fillMaxSize()) {
                MiniPlayerHost(
                    state = state,
                    showMiniPlayer = true,
                    onExpand = {},
                    onTogglePlayPause = {},
                    onSkipNext = {},
                    modifier = Modifier.fillMaxSize(),
                ) { reserve ->
                    mesures.reserve = reserve
                    ListeTemoin(reserve)
                }

                // La même carte, mesurée à part : c'est elle l'étalon, et non un
                // nombre que ce test se donnerait à lui-même. Sa hauteur ne
                // dépend pas de la largeur, les deux lignes de texte restant
                // sur une ligne chacune.
                MiniPlayer(
                    state = state,
                    onExpand = {},
                    onTogglePlayPause = {},
                    onSkipNext = {},
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .onSizeChanged { mesures.carte = with(density) { it.height.toDp() } },
                )
            }
        }

        compose.waitForIdle()
        return mesures
    }

    @Composable
    private fun ListeTemoin(reserve: Dp) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = reserve),
            modifier = Modifier
                .fillMaxSize()
                .testTag(LISTE),
        ) {
            items((0 until LIGNES).toList()) { index ->
                Text(
                    text = "Ligne $index",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("ligne-$index"),
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun `la reserve sous les listes vaut la hauteur de la carte`() {
        val mesures = poser()

        assertEquals(mesures.carte, mesures.reserve)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi", fontScale = 2.0f)
    fun `a polices doublees la carte grandit et la reserve avec elle`() {
        // Le cas qu'aucune constante ne peut couvrir : la carte grandit avec les
        // réglages d'accessibilité du système, la réserve doit suivre.
        val mesures = poser()

        assertEquals(mesures.carte, mesures.reserve)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun `la derniere ligne reste entierement visible sous la carte`() {
        // La propriété que l'utilisateur constate : arrivé au bas de la liste,
        // rien ne se cache derrière le mini-lecteur.
        val mesures = poser()

        compose.onNodeWithTag(LISTE).performScrollToIndex(LIGNES - 1)
        compose.waitForIdle()

        val racine = compose.onRoot().getUnclippedBoundsInRoot()
        val derniere = compose.onNodeWithTag("ligne-${LIGNES - 1}").getUnclippedBoundsInRoot()
        val hautDeLaCarte = racine.height - mesures.carte

        assertTrue(
            "La dernière ligne finit à ${derniere.bottom}, la carte commence à $hautDeLaCarte",
            derniere.bottom <= hautDeLaCarte,
        )
    }

    private companion object {
        const val LISTE = "liste-temoin"
        const val LIGNES = 30
    }
}
