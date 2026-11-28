package app.waveflow.prototype

import android.net.Uri
import androidx.compose.ui.unit.dp
import app.waveflow.model.Library
import app.waveflow.model.Song
import app.waveflow.ui.theme.contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Les règles de l'accueil, de la bibliothèque et de la page d'un album qui ne
 * se voient pas sur une capture : un artiste sans pochette garde un visage
 * lisible, une durée se dit en minutes, un album écouté deux fois ne paraît
 * qu'une fois, un carrousel montre deux tuiles et le bord d'une troisième.
 *
 * Robolectric pour `Uri`, que portent les morceaux.
 */
@RunWith(RobolectricTestRunner::class)
class PocketPartsTest {

    @Test
    fun `les initiales d'un artiste, et un point d'interrogation pour l'inconnu`() {
        assertEquals("TD", initialsOf("Tom Doolie"))
        assertEquals("N", initialsOf("NAYEON"))
        assertEquals("LL", initialsOf("Lenny Loops, 96 Zeus"))
        assertEquals("?", initialsOf("Artiste inconnu"))
        assertEquals("?", initialsOf(" "))
    }

    @Test
    fun `l'encre se lit sur le disque de tout artiste dans les deux apparences`() {
        val noms = (0 until 200).map { "Artiste numéro $it" } + listOf("TWICE", "Purrple Cat", "Tom Doolie")
        for (colors in listOf(PocketDark, PocketLight)) {
            for (nom in noms) {
                val fond = artistTint(nom, colors.dark)
                assertTrue("$nom : ${contrast(colors.ink, fond)}", contrast(colors.ink, fond) >= 4.5f)
            }
        }
        // La même teinte à chaque fois.
        assertEquals(artistTint("TWICE", dark = true), artistTint("TWICE", dark = true))
    }

    @Test
    fun `une duree totale se dit en minutes, puis en heures`() {
        assertEquals("0 min", formatTotal(0L))
        assertEquals("1 min", formatTotal(20_000L))
        assertEquals("42 min", formatTotal(42 * 60_000L + 10_000L))
        assertEquals("1 h", formatTotal(60 * 60_000L))
        assertEquals("1 h 12 min", formatTotal(72 * 60_000L))
    }

    @Test
    fun `les albums ecoutes recemment, chacun une fois, dans l'ordre de l'ecoute`() {
        val a1 = morceau(1, album = 10)
        val a2 = morceau(2, album = 10)
        val b1 = morceau(3, album = 20)
        val c1 = morceau(4, album = 30)
        val bibliotheque = Library(isLoading = false, songs = listOf(a1, a2, b1, c1))

        val albums = recentAlbums(listOf(b1, a1, a2, b1, c1), bibliotheque)

        assertEquals(listOf(20L, 10L, 30L), albums.map { it.id })
    }

    @Test
    fun `un album qui n'est plus sur l'appareil n'est pas propose`() {
        val bibliotheque = Library(isLoading = false, songs = listOf(morceau(1, album = 10)))

        val albums = recentAlbums(listOf(morceau(9, album = 99), morceau(1, album = 10)), bibliotheque)

        assertEquals(listOf(10L), albums.map { it.id })
    }

    @Test
    fun `un carrousel montre deux tuiles et le bord d'une troisieme, sans geantes sur tablette`() {
        val telephone = carouselTileWidth(available = 412.dp, gutter = 24.dp)
        val visibles = (412.dp - 24.dp) / (telephone + TILE_SPACING)
        assertTrue("$visibles", visibles > 2.2f && visibles < 2.6f)

        assertEquals(200.dp, carouselTileWidth(available = 800.dp, gutter = 24.dp))
    }

    @Test
    fun `l'en-tete se replie avec le defilement et reste replie une fois sorti`() {
        assertEquals(0f, collapseProgress(index = 0, offset = 0, range = 800f))
        assertEquals(0.5f, collapseProgress(index = 0, offset = 400, range = 800f))
        assertEquals(1f, collapseProgress(index = 0, offset = 1_200, range = 800f))
        assertEquals(1f, collapseProgress(index = 3, offset = 0, range = 800f))
    }

    @Test
    fun `la pochette d'un album laisse voir les premieres pistes`() {
        // Pixel 8 : 62 % de la largeur, et non plus toute la largeur.
        assertEquals(412f * 0.62f, coverSideFor(412.dp, 900.dp, sideBySide = false).value, 0.01f)
        // Une fenêtre basse : le tiers de la hauteur.
        assertEquals(128f, coverSideFor(412.dp, 400.dp, sideBySide = false).value, 0.01f)
        // Un grand téléphone : plafonnée à 260 dp.
        assertEquals(260f, coverSideFor(480.dp, 1000.dp, sideBySide = false).value, 0.01f)
        // Tablette : à côté du texte, plafonnée.
        assertEquals(360f, coverSideFor(1280.dp, 1200.dp, sideBySide = true).value, 0.01f)
    }

    @Test
    fun `le nom ne parait dans la barre que sur la fin du repli`() {
        assertEquals(0f, barTitleAlpha(0.5f), 0f)
        assertEquals(0f, barTitleAlpha(0.65f), 1e-4f)
        assertEquals(1f, barTitleAlpha(1f), 0f)
        assertTrue(barTitleAlpha(0.8f) in 0.1f..0.9f)
    }

    @Test
    fun `une page se relit depuis sa route, et une route abimee n'ouvre rien`() {
        for (page in listOf(Page.Album(12), Page.Artiste(5), Page.Playlist(3))) {
            assertEquals(page, Page.of(page.route))
        }
        assertNull(Page.of("album:"))
        assertNull(Page.of("inconnu:4"))
        assertNull(Page.of("album"))
    }

    private fun morceau(id: Long, album: Long) = Song(
        id = id,
        uri = Uri.parse("content://media/$id"),
        title = "Titre $id",
        artist = "Artiste",
        artistId = 1L,
        album = "Album $album",
        albumId = album,
        durationMs = 180_000L,
        artworkUri = null,
    )
}
