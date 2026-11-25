package app.waveflow.ui.server.catalog

import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import app.waveflow.model.RemoteAlbum
import app.waveflow.model.RemoteArtist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Le catalogue en tuiles : la grille doit encore demander la page suivante à
 * l'approche de sa fin, comme le faisait la liste qu'elle remplace.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h800dp-xhdpi")
class ServerCatalogScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var pagesAlbums = 0
    private var pagesArtistes = 0
    private val ouverts = mutableListOf<String>()

    private fun album(i: Int) = RemoteAlbum(
        id = "album-$i",
        title = "Album $i",
        artist = "Artiste $i",
        artistId = null,
        year = null,
        artworkUri = null,
    )

    private fun afficher(albums: List<RemoteAlbum>) {
        compose.setContent {
            ServerCatalogScreen(
                albums = PagedList(items = albums),
                artists = PagedList<RemoteArtist>(),
                search = RemoteSearchState(),
                onSearchQueryChange = {},
                onSongClick = {},
                onAlbumClick = { ouverts += it.id },
                onArtistClick = {},
                onLoadMoreAlbums = { pagesAlbums++ },
                onLoadMoreArtists = { pagesArtistes++ },
                onRetryAlbums = {},
                onRetryArtists = {},
            )
        }
    }

    @Test
    fun `la grille demande la page suivante en approchant de sa fin`() {
        afficher((1..40).map(::album))

        // Le haut de la grille est loin de la fin : rien ne part.
        assertEquals(0, pagesAlbums)

        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(39)
        compose.waitForIdle()

        assertTrue("la page suivante n'a pas été demandée", pagesAlbums >= 1)
        assertEquals("l'onglet masqué ne charge rien", 0, pagesArtistes)
    }

    @Test
    fun `toucher une tuile ouvre son album`() {
        afficher((1..4).map(::album))

        compose.onNodeWithText("Album 3").performClick()

        assertEquals(listOf("album-3"), ouverts)
    }
}
