package app.waveflow.model

import app.waveflow.testing.song
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Ce qu'une demande vocale fait jouer. Les règles R4 à R9 sont celles de
 * `docs/recherche-vocale.md`.
 *
 * La bibliothèque est bâtie pour que chaque règle se départage de la suivante :
 * un album et un morceau qui s'appellent tous deux « Nevermind », un artiste
 * et un morceau qui s'appellent « Nirvana », un morceau « Yesterday » et un
 * album « Yesterday and Today ». Elle est dans l'ordre du MediaStore, par
 * titre : c'est ce qui rend observable un classement qui le corrige.
 */
@RunWith(RobolectricTestRunner::class)
class VoiceSearchTest {

    private val library = Library(
        isLoading = false,
        songs = listOf(
            song(1L, "Come As You Are", artist = "Nirvana", artistId = 10L, album = "Nevermind", albumId = 100L),
            song(6L, "Day Tripper", artist = "Beatles", artistId = 40L, album = "Yesterday and Today", albumId = 500L),
            song(2L, "Lithium", artist = "Nirvana", artistId = 10L, album = "Nevermind", albumId = 100L),
            song(3L, "Nevermind", artist = "Tribute Band", artistId = 20L, album = "Covers", albumId = 200L),
            song(4L, "Nirvana", artist = "Lana", artistId = 30L, album = "Ultraviolence", albumId = 300L),
            song(5L, "Yesterday", artist = "Beatles", artistId = 40L, album = "Help", albumId = 400L),
        ),
    )

    private fun joue(
        query: String,
        focus: VoiceFocus = VoiceFocus.Unspecified,
        album: String? = null,
        artist: String? = null,
        title: String? = null,
    ): List<Long> = library.voiceSearch(VoiceRequest(query, focus, album, artist, title)).map { it.id }

    @Test
    fun `R4 une phrase vide joue toute la bibliotheque`() {
        assertEquals(listOf(1L, 6L, 2L, 3L, 4L, 5L), joue(""))
        assertEquals(listOf(1L, 6L, 2L, 3L, 4L, 5L), joue("   "))
    }

    @Test
    fun `R5 un album demande joue l'album nomme par l'hote`() {
        // La phrase entière ne trouve rien : seul le nom isolé par l'hôte mène
        // à l'album.
        assertEquals(listOf(5L), joue("joue Help des Beatles", VoiceFocus.Album, album = "Help"))
    }

    @Test
    fun `R6 un artiste demande joue toutes ses pistes`() {
        assertEquals(listOf(1L, 2L), joue("mets du Nirvana", VoiceFocus.Artist, artist = "Nirvana"))
    }

    @Test
    fun `R7 un morceau demande passe avant ceux de l'album du meme nom`() {
        // Le moteur seul rendrait 1, 2, 3 : les pistes de l'album Nevermind
        // commencent elles aussi par la phrase, et viennent avant dans la
        // bibliothèque.
        assertEquals(
            listOf(3L, 1L, 2L),
            joue("joue la chanson Nevermind", VoiceFocus.Song, title = "Nevermind"),
        )
    }

    @Test
    fun `R5 un album eponyme passe avant les autres albums de son artiste`() {
        // Le moteur compare aussi l'artiste d'un album : « Pinkerton » et
        // « Weezer » y sortent au même rang, « Pinkerton » d'abord.
        val weezer = Library(
            isLoading = false,
            songs = listOf(
                song(11L, "Buddy Holly", artist = "Weezer", artistId = 110L, album = "Weezer", albumId = 1100L),
                song(12L, "El Scorcho", artist = "Weezer", artistId = 110L, album = "Pinkerton", albumId = 1200L),
            ),
        )

        assertEquals(
            listOf(11L),
            weezer.voiceSearch(VoiceRequest("joue Weezer", VoiceFocus.Album, album = "Weezer")).map { it.id },
        )
    }

    @Test
    fun `R6 l'artiste nomme exactement passe avant ceux qui commencent pareil`() {
        // L'onglet Artistes trie sans ôter les accents : « Edith Crash » y
        // précède « Édith ».
        val ediths = Library(
            isLoading = false,
            songs = listOf(
                song(21L, "Padam", artist = "Édith", artistId = 210L, album = "Récital", albumId = 2100L),
                song(22L, "Riff", artist = "Edith Crash", artistId = 220L, album = "Bruit", albumId = 2200L),
            ),
        )

        assertEquals(
            listOf(21L),
            ediths.voiceSearch(VoiceRequest("mets Édith", VoiceFocus.Artist, artist = "Édith")).map { it.id },
        )
    }

    @Test
    fun `R12 l'artiste isole departage deux albums homonymes`() {
        val homonymes = Library(
            isLoading = false,
            songs = listOf(
                song(
                    8L,
                    "Bohemian Rhapsody",
                    artist = "Queen",
                    artistId = 80L,
                    album = "Greatest Hits",
                    albumId = 800L,
                ),
                song(9L, "Everlong", artist = "Foo Fighters", artistId = 90L, album = "Greatest Hits", albumId = 900L),
            ),
        )
        fun joue(artist: String?) = homonymes
            .voiceSearch(VoiceRequest("Greatest Hits", VoiceFocus.Album, album = "Greatest Hits", artist = artist))
            .map { it.id }

        assertEquals(listOf(9L), joue("Foo Fighters"))
        // Un indice qui écarte tout est ignoré.
        assertEquals(listOf(8L), joue("Nirvana"))
        assertEquals(listOf(8L), joue(null))
    }

    @Test
    fun `R12 l'artiste isole departage deux morceaux homonymes`() {
        assertEquals(
            listOf(4L),
            joue("Nirvana de Lana", VoiceFocus.Song, title = "Nirvana", artist = "Lana"),
        )
    }

    @Test
    fun `un focus sans nom isole cherche la phrase entiere`() {
        assertEquals(listOf(5L), joue("Help", VoiceFocus.Album))
    }

    @Test
    fun `un focus que rien ne satisfait retombe sur le meilleur resultat`() {
        assertEquals(listOf(5L, 6L), joue("Yesterday", VoiceFocus.Album, album = "Inconnu"))
    }

    @Test
    fun `R8-1 un album nomme exactement passe avant un morceau du meme nom`() {
        assertEquals(listOf(1L, 2L), joue("Nevermind"))
    }

    @Test
    fun `R8-2 un artiste nomme exactement passe avant un morceau du meme nom`() {
        assertEquals(listOf(1L, 2L), joue("nirvana"))
    }

    @Test
    fun `R8-3 un titre exact passe avant un album qui commence pareil`() {
        assertEquals(listOf(5L, 6L), joue("Yesterday"))
    }

    @Test
    fun `R8-4 un album qui commence par la phrase passe avant les morceaux`() {
        // « Yesterday » commence aussi par « yest » : sans la règle, il serait
        // joué.
        assertEquals(listOf(6L), joue("yest"))
    }

    @Test
    fun `R8-5 un artiste qui commence par la phrase passe avant les morceaux`() {
        assertEquals(listOf(6L, 5L), joue("beat"))
    }

    @Test
    fun `R8-6 sinon les morceaux que trouve le moteur`() {
        assertEquals(listOf(6L), joue("tripper"))
    }

    @Test
    fun `la comparaison ignore casse et accents comme l'ecran de recherche`() {
        assertEquals(listOf(1L, 2L), joue("NÉVERMIND"))
    }

    @Test
    fun `R9 rien ne correspond, rien n'est rendu`() {
        assertEquals(emptyList<Long>(), joue("Bohemian Rhapsody"))
    }
}
