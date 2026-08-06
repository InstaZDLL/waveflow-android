package app.waveflow.model

import app.waveflow.testing.song
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GroupingTest {

    private val library = listOf(
        song(id = 1L, title = "B", artist = "Zoe", artistId = 10L, album = "Nuit", albumId = 100L, durationMs = 1_000L),
        song(id = 2L, title = "A", artist = "Zoe", artistId = 10L, album = "Nuit", albumId = 100L, durationMs = 2_000L),
        song(id = 3L, title = "C", artist = "Zoe", artistId = 10L, album = "Aube", albumId = 200L, durationMs = 3_000L),
        song(id = 4L, title = "D", artist = "Alba", artistId = 20L, album = "Mer", albumId = 300L, durationMs = 4_000L),
    )

    @Test
    fun `les albums agregent titres et duree`() {
        val albums = library.toAlbums()

        assertEquals(3, albums.size)
        val nuit = albums.first { it.id == 100L }
        assertEquals("Nuit", nuit.title)
        assertEquals(2, nuit.trackCount)
        assertEquals(3_000L, nuit.durationMs)
    }

    @Test
    fun `les albums sont tries par titre sans tenir compte de la casse`() {
        val titles = library.toAlbums().map { it.title }

        assertEquals(listOf("Aube", "Mer", "Nuit"), titles)
    }

    @Test
    fun `les artistes comptent leurs albums distincts`() {
        val artists = library.toArtists()

        assertEquals(2, artists.size)
        val zoe = artists.first { it.id == 10L }
        assertEquals(2, zoe.albumCount)
        assertEquals(3, zoe.trackCount)
    }

    @Test
    fun `les artistes sont tries par nom`() {
        assertEquals(listOf("Alba", "Zoe"), library.toArtists().map { it.name })
    }

    @Test
    fun `un artiste sans tag retombe sur le libelle inconnu`() {
        val artists = listOf(song(id = 1L, artist = "<unknown>", artistId = 0L)).toArtists()

        assertEquals("Artiste inconnu", artists.single().name)
    }

    @Test
    fun `une bibliotheque vide ne produit aucun regroupement`() {
        assertEquals(emptyList<Album>(), emptyList<Song>().toAlbums())
        assertEquals(emptyList<Artist>(), emptyList<Song>().toArtists())
    }
}
