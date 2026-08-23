package app.waveflow.playback

import androidx.core.net.toUri
import app.waveflow.model.Library
import app.waveflow.model.Playlist
import app.waveflow.model.PlaylistEntry
import app.waveflow.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * L'arbre que voit Android Auto.
 *
 * Ce qui se joue ici n'est pas l'affichage mais le **contrat** : un hôte
 * extérieur pose des questions par identifiant et n'a aucun moyen de deviner ce
 * qu'on a voulu dire. Un identifiant qui ne se retrouve pas, une feuille sans
 * source, et la voiture reste muette sans rien signaler.
 *
 * Robolectric parce que `Uri` et `MediaItem` viennent du cadre Android.
 */
@RunWith(RobolectricTestRunner::class)
class BrowseTreeTest {

    private fun song(
        id: Long,
        title: String,
        album: String = "Album",
        albumId: Long = 10L,
        artist: String = "Artiste",
        artistId: Long = 100L,
    ) = Song(
        id = id,
        uri = "content://media/external/audio/media/$id".toUri(),
        title = title,
        artist = artist,
        artistId = artistId,
        album = album,
        albumId = albumId,
        durationMs = 180_000L,
        artworkUri = null,
    )

    private fun tree(
        songs: List<Song> = emptyList(),
        playlists: List<Playlist> = emptyList(),
        entries: List<PlaylistEntry> = emptyList(),
    ) = BrowseTree {
        BrowseSnapshot(
            library = Library(isLoading = false, songs = songs),
            playlists = playlists,
            playlistEntries = entries,
        )
    }

    private fun titlesOf(items: List<androidx.media3.common.MediaItem>) =
        items.map { it.mediaMetadata.title.toString() }

    @Test
    fun `la racine repond meme sans bibliotheque`() {
        // Android Auto demande la racine avant tout le reste et n'attend pas.
        // Rendre un échec ou rien tant que le chargement n'est pas fini
        // laisserait la voiture sans musique jusqu'au prochain balayage.
        val root = tree().root()

        assertEquals(BrowseTree.ROOT_ID, root.mediaId)
        assertTrue(root.mediaMetadata.isBrowsable == true)
        assertFalse(root.mediaMetadata.isPlayable == true)
    }

    @Test
    fun `la racine offre les sections de la bibliotheque`() {
        val items = tree(songs = listOf(song(1, "Un"))).children(BrowseTree.ROOT_ID)

        assertEquals(listOf("Albums", "Artistes", "Toutes les pistes"), titlesOf(items))
    }

    @Test
    fun `la section des playlists n'apparait que s'il y en a`() {
        // Une section vide n'apprend rien et occupe une place que la voiture
        // n'a pas.
        val avec = tree(
            songs = listOf(song(1, "Un")),
            playlists = listOf(Playlist(id = 7L, name = "Route")),
        ).children(BrowseTree.ROOT_ID)

        assertTrue("Playlists" in titlesOf(avec))
    }

    @Test
    fun `un album ne rend que ses propres pistes`() {
        val songs = listOf(
            song(1, "Une", album = "Bleu", albumId = 10L),
            song(2, "Deux", album = "Bleu", albumId = 10L),
            song(3, "Trois", album = "Rouge", albumId = 20L),
        )
        val arbre = tree(songs)

        val albums = arbre.children("${BrowseTree.BROWSE_PREFIX}albums")
        val bleu = albums.first { it.mediaMetadata.title.toString() == "Bleu" }

        assertEquals(listOf("Une", "Deux"), titlesOf(arbre.children(bleu.mediaId)))
    }

    @Test
    fun `une playlist rend ses pistes dans l'ordre voulu`() {
        // L'ordre d'une playlist est le sien, pas celui de la bibliothèque :
        // les entrées portent une position, et c'est elle qui décide.
        //
        // Les entrées sont volontairement données **à rebours de leur
        // position** : rangées dans le bon ordre, elles rendraient le tri
        // invisible et ce test passerait sans lui.
        val songs = listOf(song(1, "Une"), song(2, "Deux"), song(3, "Trois"))
        val arbre = tree(
            songs = songs,
            playlists = listOf(Playlist(id = 7L, name = "Route")),
            entries = listOf(
                PlaylistEntry(playlistId = 7L, songId = 1L, position = 1),
                PlaylistEntry(playlistId = 7L, songId = 3L, position = 0),
            ),
        )

        val pistes = arbre.children("${BrowseTree.BROWSE_PREFIX}playlist/7")

        assertEquals(listOf("Trois", "Une"), titlesOf(pistes))
    }

    @Test
    fun `une entree de playlist dont le fichier a disparu est ignoree`() {
        // Une playlist garde la mémoire d'un morceau que le MediaStore ne
        // connaît plus. La sauter vaut mieux que rendre une entrée sans source,
        // que le lecteur ne saurait pas ouvrir.
        val arbre = tree(
            songs = listOf(song(1, "Une")),
            playlists = listOf(Playlist(id = 7L, name = "Route")),
            entries = listOf(
                PlaylistEntry(playlistId = 7L, songId = 1L, position = 0),
                PlaylistEntry(playlistId = 7L, songId = 404L, position = 1),
            ),
        )

        assertEquals(listOf("Une"), titlesOf(arbre.children("${BrowseTree.BROWSE_PREFIX}playlist/7")))
    }

    @Test
    fun `les feuilles listees n'ont pas de source`() {
        // L'hôte les affiche, il ne les ouvre pas. C'est `resolve` qui leur rend
        // une URI au moment de jouer — sinon le lecteur recevrait des éléments
        // sans source et ne jouerait rien.
        val items = tree(songs = listOf(song(1, "Une"))).children("${BrowseTree.BROWSE_PREFIX}songs")

        val feuille = items.single()
        assertNull("une feuille de navigation ne porte pas d'URI", feuille.localConfiguration)
        assertTrue(feuille.mediaMetadata.isPlayable == true)
    }

    @Test
    fun `resoudre une feuille lui rend sa source`() {
        val arbre = tree(songs = listOf(song(1, "Une")))
        val feuille = arbre.children("${BrowseTree.BROWSE_PREFIX}songs").single()

        val jouable = arbre.resolve(feuille.mediaId).single()

        assertEquals(feuille.mediaId, jouable.mediaId)
        assertNotNull("le lecteur doit recevoir une source", jouable.localConfiguration)
    }

    @Test
    fun `resoudre un album rend toutes ses pistes dans l'ordre`() {
        // Demander un album, c'est demander ses pistes : l'hôte propose de
        // jouer un nœud entier, et le lecteur doit recevoir la file complète.
        val songs = listOf(song(1, "Une"), song(2, "Deux"))
        val arbre = tree(songs)
        val album = arbre.children("${BrowseTree.BROWSE_PREFIX}albums").single()

        val file = arbre.resolve(album.mediaId)

        assertEquals(listOf("Une", "Deux"), titlesOf(file))
        assertTrue(file.all { it.localConfiguration != null })
    }

    @Test
    fun `un identifiant inconnu ne rend rien plutot que d'echouer`() {
        val arbre = tree(songs = listOf(song(1, "Une")))

        assertEquals(emptyList<Any>(), arbre.children("${BrowseTree.BROWSE_PREFIX}album/999"))
        assertEquals(emptyList<Any>(), arbre.children("n'importe quoi"))
        assertNull(arbre.item("n'importe quoi"))
    }

    @Test
    fun `un noeud de navigation ne peut pas etre pris pour une piste`() {
        // Les deux familles d'identifiants se croisent dans les mêmes appels.
        // Si un préfixe recouvrait l'autre, demander un album jouerait une
        // piste, ou l'inverse.
        val arbre = tree(songs = listOf(song(1, "Une")))
        val piste = arbre.children("${BrowseTree.BROWSE_PREFIX}songs").single()
        val album = arbre.children("${BrowseTree.BROWSE_PREFIX}albums").single()

        assertFalse(piste.mediaId.startsWith(BrowseTree.BROWSE_PREFIX))
        assertTrue(album.mediaId.startsWith(BrowseTree.BROWSE_PREFIX))
    }
}
