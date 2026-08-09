package app.waveflow.model

import app.waveflow.testing.song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchTest {

    /**
     * Le morceau dont le titre ne contient « nuit » qu'au milieu vient en
     * premier dans la bibliothèque : c'est ce qui rend le classement par
     * préfixe observable, plutôt que confondu avec l'ordre d'origine.
     */
    private val library = Library(
        isLoading = false,
        songs = listOf(
            song(id = 3L, title = "Blanche nuit", artist = "Alba", artistId = 20L, album = "Mer", albumId = 300L),
            song(id = 1L, title = "Nuit blanche", artist = "Zoé Blanc", artistId = 10L, album = "Été", albumId = 100L),
            song(id = 2L, title = "Aube", artist = "Alba", artistId = 20L, album = "Nuit", albumId = 200L),
        ),
    )

    @Test
    fun `une requete vide ne renvoie rien plutot que tout`() {
        val results = library.search("")

        assertTrue(results.isEmpty)
    }

    @Test
    fun `une requete faite d'espaces ne renvoie rien`() {
        assertTrue(library.search("   ").isEmpty)
    }

    @Test
    fun `un morceau ressort par son titre`() {
        val results = library.search("aube")

        assertEquals(listOf(2L), results.songs.map { it.id })
    }

    @Test
    fun `un morceau ressort par son album`() {
        // Le morceau 2 s'appelle « Aube » : seul son album porte « nuit ».
        val ids = library.search("nuit").songs.map { it.id }

        assertTrue("l'album Nuit doit ramener son morceau", 2L in ids)
    }

    @Test
    fun `un morceau ressort par son artiste`() {
        val results = library.search("zoé")

        assertEquals(listOf(1L), results.songs.map { it.id })
    }

    @Test
    fun `la recherche ignore les accents et la casse`() {
        // La requête doit dépasser l'accent : « zoe » seul correspondrait déjà
        // au préfixe de « zoé » sans qu'aucun accent ait été retiré, et le
        // test passerait pour la mauvaise raison.
        val results = library.search("ZOE BLANC")

        assertEquals(listOf(1L), results.songs.map { it.id })
    }

    @Test
    fun `une requete accentuee trouve un texte sans accent`() {
        val sansAccent = Library(isLoading = false, songs = listOf(song(id = 1L, title = "Ete")))

        assertEquals(listOf(1L), sansAccent.search("été").songs.map { it.id })
    }

    @Test
    fun `les prefixes passent devant les occurrences au milieu du texte`() {
        val ids = library.search("nuit").songs.map { it.id }

        // 1 « Nuit blanche » et 2 (album « Nuit ») commencent par la requête ;
        // 3 « Blanche nuit » ne la contient qu'au milieu, et passe donc après
        // alors qu'il ouvre la bibliothèque.
        assertEquals(listOf(1L, 2L, 3L), ids)
    }

    @Test
    fun `albums et artistes sont filtres eux aussi`() {
        val results = library.search("alba")

        assertEquals(listOf("Alba"), results.artists.map { it.name })
        // Un album ressort aussi par son artiste : ce sont les deux d'Alba,
        // dans l'ordre alphabétique de la bibliothèque.
        assertEquals(listOf("Mer", "Nuit"), results.albums.map { it.title })
    }

    @Test
    fun `une requete sans correspondance ne renvoie aucune section`() {
        val results = library.search("xylophone")

        assertTrue(results.isEmpty)
    }
}
