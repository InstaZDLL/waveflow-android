package app.waveflow.ui.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.waveflow.model.Album
import app.waveflow.model.Song
import app.waveflow.suggestion.SorteDeSuggestion
import app.waveflow.testing.song
import app.waveflow.ui.theme.WaveFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/** Le canevas de l'accueil : le salut, les compteurs, la reprise. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp-xhdpi")
class HomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `le salut suit l'heure comme sur le bureau`() {
        assertEquals("Bonne nuit", greetingFor(4))
        assertEquals("Bonjour", greetingFor(5))
        assertEquals("Bonjour", greetingFor(17))
        assertEquals("Bonsoir", greetingFor(18))
        assertEquals("Bonsoir", greetingFor(22))
        assertEquals("Bonne nuit", greetingFor(23))
    }

    @Test
    fun `la prochaine frontiere du salut est 5 h, 18 h, 23 h ou minuit`() {
        fun le(jour: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, jour, h, m)

        assertEquals(le(25, 5), nextGreetingChange(le(25, 1, 30)))
        assertEquals(le(25, 18), nextGreetingChange(le(25, 5)))
        assertEquals(le(25, 18), nextGreetingChange(le(25, 17, 59)))
        assertEquals(le(25, 23), nextGreetingChange(le(25, 18)))
        // Après 23 h, c'est la date qui change ensuite.
        assertEquals(le(26, 0), nextGreetingChange(le(25, 23, 30)))
    }

    @Test
    fun `une page restee ouverte change de salut au passage de 18 h`() {
        var maintenant = LocalDateTime.of(2026, 11, 25, 17, 59, 30)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            WaveFlowTheme {
                HomeScreen(
                    state = HomeUiState(isLoading = false, resume = song(1)),
                    onSongClick = {},
                    onAlbumClick = {},
                    onAnotherSuggestion = {},
                    now = { maintenant },
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Bonjour").assertExists()

        // Trente secondes plus tard, sans quitter la page ni la mettre au fond.
        maintenant = LocalDateTime.of(2026, 11, 25, 18, 0)
        compose.mainClock.advanceTimeBy(HALF_MINUTE_MS)

        compose.onNodeWithText("Bonsoir").assertExists()
    }

    @Test
    fun `la ligne de compteurs donne la date puis la bibliotheque`() {
        assertEquals(
            "mercredi 25 novembre · 1 titre · 12 albums",
            countersLine(LocalDateTime.of(2026, 11, 25, 10, 0), songCount = 1, albumCount = 12),
        )
    }

    @Test
    fun `reprendre et voir l'album renvoient a la derniere ecoute`() {
        val reprise = song(3, title = "Nocturne", albumId = 20)
        val album = Album(
            id = 20,
            title = "Album 3",
            artist = "Artiste 3",
            artworkUri = null,
            trackCount = 1,
            durationMs = 60_000L,
        )
        val joues = mutableListOf<Song>()
        val ouverts = mutableListOf<Album>()

        compose.setContent {
            WaveFlowTheme {
                HomeScreen(
                    state = HomeUiState(isLoading = false, resume = reprise, resumeAlbum = album, songCount = 1),
                    onSongClick = { joues += it },
                    onAlbumClick = { ouverts += it },
                    onAnotherSuggestion = {},
                    now = { LocalDateTime.of(2026, 11, 25, 21, 0) },
                )
            }
        }

        compose.onNodeWithText("Bonsoir").assertExists()
        compose.onNodeWithText("Nocturne").assertExists()
        compose.onNodeWithText("Reprendre").performClick()
        compose.onNodeWithText("Voir l'album").performClick()

        assertEquals(listOf(reprise), joues)
        assertEquals(listOf(album), ouverts)
    }

    @Test
    fun `la bande dit la suggestion et ses deux actions repondent`() {
        val piste = song(7, title = "Strange Fruit", artist = "Billie Holiday", album = "Lady Day")
        val joues = mutableListOf<Song>()
        var autres = 0
        compose.setContent {
            WaveFlowTheme {
                HomeScreen(
                    state = HomeUiState(
                        isLoading = false,
                        suggestion = SuggestionBand(
                            song = piste,
                            kind = SorteDeSuggestion.Oubliee,
                            kindLabel = "Vous l'adoriez",
                            reason = "4 écoutes · dernière écoute en août 2026",
                        ),
                    ),
                    onSongClick = { joues += it },
                    onAlbumClick = {},
                    onAnotherSuggestion = { autres++ },
                )
            }
        }

        compose.onNodeWithText("SUGGESTION DU JOUR").assertExists()
        compose.onNodeWithText("Vous l'adoriez").assertExists()
        compose.onNodeWithText("Strange Fruit").assertExists()
        compose.onNodeWithText("4 écoutes · dernière écoute en août 2026").assertExists()
        compose.onNodeWithText("Écouter").performClick()
        compose.onNodeWithText("Autre suggestion").performClick()

        // S16 : « Écouter » joue comme un appui sur un morceau de l'accueil.
        assertEquals(listOf(piste), joues)
        assertEquals(1, autres)
    }

    @Test
    fun `sans suggestion il n'y a pas de bande`() {
        compose.setContent {
            WaveFlowTheme {
                HomeScreen(
                    state = HomeUiState(isLoading = false, resume = song(1)),
                    onSongClick = {},
                    onAlbumClick = {},
                    onAnotherSuggestion = {},
                    now = { LocalDateTime.of(2026, 11, 25, 10, 0) },
                )
            }
        }

        // La page est bien là ; la bande seule manque.
        compose.onNodeWithText("Bonjour").assertExists()
        compose.onNodeWithText("SUGGESTION DU JOUR").assertDoesNotExist()
    }

    /** Un cycle de vie qu'un test mène à la main, comme une activité qui passe au fond. */
    private class CycleDeVie : LifecycleOwner {
        val registre = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registre
    }

    @Test
    fun `revenir au premier plan relit l'heure du salut`() {
        var heure = 21
        val cycle = CycleDeVie()
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides cycle) {
                WaveFlowTheme {
                    HomeScreen(
                        state = HomeUiState(isLoading = false, resume = song(1)),
                        onSongClick = {},
                        onAlbumClick = {},
                        onAnotherSuggestion = {},
                        now = { LocalDateTime.of(2026, 11, 25, heure, 0) },
                    )
                }
            }
        }
        compose.onNodeWithText("Bonsoir").assertExists()

        // L'application passe la nuit au fond, et revient au matin.
        heure = 9
        compose.runOnIdle { cycle.registre.currentState = Lifecycle.State.CREATED }
        compose.runOnIdle { cycle.registre.currentState = Lifecycle.State.RESUMED }

        compose.onNodeWithText("Bonjour").assertExists()
    }

    @Test
    @Config(qualifiers = "w1000dp-h2000dp-mdpi")
    fun `sur un grand ecran la mosaique ne grandit pas au dela de son plafond`() {
        val albums = (1L..6L).map { i ->
            Album(id = i, title = "Album $i", artist = null, artworkUri = null, trackCount = 1, durationMs = 0L)
        }
        compose.setContent {
            WaveFlowTheme {
                HomeScreen(
                    state = HomeUiState(isLoading = false, recentlyAdded = albums),
                    onSongClick = {},
                    onAlbumClick = {},
                    onAnotherSuggestion = {},
                )
            }
        }

        // Une tuile de côté : un tiers plafonné, et non un tiers de 1 000 dp.
        compose.onNodeWithText("Album 2").assertWidthIsEqualTo(160.dp)
    }
}

/** De 17 h 59 min 30 s à 18 h, plus une image pour recomposer. */
private const val HALF_MINUTE_MS = 30_100L
