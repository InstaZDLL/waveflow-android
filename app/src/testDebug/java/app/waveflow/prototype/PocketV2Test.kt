package app.waveflow.prototype

import android.net.Uri
import androidx.compose.ui.unit.dp
import app.waveflow.model.Album
import app.waveflow.model.Artist
import app.waveflow.model.Song
import app.waveflow.playback.PlayingTrack
import app.waveflow.playback.RepeatMode
import app.waveflow.playback.TrackSource
import app.waveflow.suggestion.SorteDeSuggestion
import app.waveflow.ui.home.SuggestionBand
import app.waveflow.ui.player.PlayerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Les règles de la seconde version de Pocket : la composition choisie par la
 * largeur de la fenêtre, le contexte de lecture de Now Playing, la raison
 * courte de la suggestion et les tris de la bibliothèque.
 */
@RunWith(RobolectricTestRunner::class)
class PocketV2Test {

    @Test
    fun `la largeur de la fenetre choisit la composition aux seuils de Material`() {
        assertEquals(WindowClass.Compact, windowClassOf(599.dp))
        assertEquals(WindowClass.Medium, windowClassOf(600.dp))
        assertEquals(WindowClass.Medium, windowClassOf(839.dp))
        assertEquals(WindowClass.Expanded, windowClassOf(840.dp))
    }

    @Test
    fun `le panneau de lecture demande une fenetre etendue et assez haute`() {
        // Tablette en paysage.
        assertTrue(showsPlaybackPanel(1280.dp, 800.dp))
        // Grand téléphone en paysage : large, mais trop bas — il garde le dock.
        assertFalse(showsPlaybackPanel(915.dp, 412.dp))
        // Tablette en portrait : moyenne.
        assertFalse(showsPlaybackPanel(800.dp, 1280.dp))
    }

    @Test
    fun `dans une fenetre basse la pochette du panneau cede la hauteur aux commandes`() {
        // Tablette en paysage : toute la largeur du panneau.
        assertEquals(312f, panelCoverSide(312.dp, 720.dp).value, 0.01f)
        // Fenêtre étendue basse : la pochette rétrécit.
        assertEquals(108f, panelCoverSide(312.dp, 488.dp).value, 0.01f)
        // Jamais moins qu'une vignette lisible.
        assertEquals(96f, panelCoverSide(312.dp, 300.dp).value, 0.01f)
    }

    @Test
    fun `rouvrir la page du sommet n'empile rien`() {
        val pile = listOf("album:1", "artiste:2")

        assertEquals(pile, pile.pushedOnce("artiste:2"))
        assertEquals(pile + "album:3", pile.pushedOnce("album:3"))
        // Une page plus bas dans la pile se rouvre au sommet.
        assertEquals(pile + "album:1", pile.pushedOnce("album:1"))
    }

    @Test
    fun `sur un telephone le pont reste au pied de l'ecran`() {
        // Hauteur 2 000, pochette 800, écart 50, bloc 700 : le bloc touche le bas.
        val (haut, bloc) = coverAndDetailsTop(2_000, 800, 50, 700, margin = 20, floating = false)
        assertEquals(2_000 - 700, bloc)
        assertEquals((2_000 - 800 - 50 - 700) / 2, haut)
    }

    @Test
    fun `dans une colonne bornee la pochette et le pont se centrent ensemble`() {
        val (haut, bloc) = coverAndDetailsTop(2_000, 800, 50, 700, margin = 20, floating = true)
        // Le bloc suit la pochette au lieu d'attendre au pied de l'écran…
        assertEquals(haut + 800 + 50, bloc)
        // … et l'ensemble laisse autant d'air au-dessus qu'au-dessous.
        assertEquals(haut, 2_000 - (bloc + 700))
    }

    @Test
    fun `la file ne dit ce qui suit que sans aleatoire ni repetition`() {
        assertTrue(followsQueueOrder(PlayerUiState()))
        assertFalse(followsQueueOrder(PlayerUiState(shuffleEnabled = true)))
        assertFalse(followsQueueOrder(PlayerUiState(repeatMode = RepeatMode.One)))
        assertFalse(followsQueueOrder(PlayerUiState(repeatMode = RepeatMode.All)))
    }

    @Test
    fun `en aleatoire la place dans la file n'est pas annoncee`() {
        val pistes = (1..7).map { piste("p$it", album = "Doughnut") }
        val etat = PlayerUiState(track = pistes[5], queue = pistes, queueIndex = 5, shuffleEnabled = true)

        assertEquals("Doughnut", playbackContext(etat, pistes[5]))
    }

    @Test
    fun `now playing dit la place dans la file, ou a defaut l'album`() {
        val pistes = (1..7).map { piste("p$it", album = "Doughnut") }
        assertEquals(
            "Piste 2 sur 7",
            playbackContext(PlayerUiState(track = pistes[1], queue = pistes, queueIndex = 1), pistes[1]),
        )
        // Un morceau seul n'a pas de place à dire : son album.
        val seul = piste("s", album = "Ditto")
        assertEquals("Ditto", playbackContext(PlayerUiState(track = seul, queue = listOf(seul), queueIndex = 0), seul))
        // Sans album, d'où il vient.
        val anonyme = piste("a", album = null)
        assertEquals(
            TrackSource.Local.label,
            playbackContext(PlayerUiState(track = anonyme, queue = listOf(anonyme), queueIndex = 0), anonyme),
        )
    }

    @Test
    fun `la raison d'une piste jamais ecoutee est sa date d'ajout, insecable`() {
        val ajout = LocalDate.of(2026, 11, 28).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val bande = bande(SorteDeSuggestion.JamaisEcoutee, addedAtMs = ajout, reason = "phrase longue du bureau")

        assertEquals("ajouté le 28 nov.", shortReason(bande, ZoneOffset.UTC))
    }

    @Test
    fun `sans date d'ajout une piste jamais ecoutee n'a pas de raison, les autres gardent la leur`() {
        assertNull(shortReason(bande(SorteDeSuggestion.JamaisEcoutee, addedAtMs = 0L, reason = "x"), ZoneOffset.UTC))
        assertEquals(
            "12 écoutes",
            shortReason(bande(SorteDeSuggestion.Oubliee, addedAtMs = 5L, reason = "12 écoutes"), ZoneOffset.UTC),
        )
    }

    @Test
    fun `chaque categorie n'offre que les tris que ses donnees permettent`() {
        assertEquals(listOf(SortOrder.Titre, SortOrder.Artiste, SortOrder.Ajout), sortOrdersFor(LibraryCategory.Albums))
        assertEquals(listOf(SortOrder.Nom, SortOrder.Pistes), sortOrdersFor(LibraryCategory.Artistes))
        assertTrue(sortOrdersFor(LibraryCategory.Playlists).isEmpty())
    }

    @Test
    fun `les albums se trient par artiste sans tenir compte de la casse, ou par ajout`() {
        val albums = listOf(
            album(1, "Ditto", "NewJeans", ajout = 30),
            album(2, "Doughnut", "TWICE", ajout = 10),
            // En minuscules : avant « TWICE » sans la casse, après avec elle.
            album(3, "GUTS", "olivia", ajout = 20),
        )

        assertEquals(listOf(1L, 3L, 2L), albums.sortedFor(SortOrder.Artiste).map { it.id })
        assertEquals(listOf(1L, 3L, 2L), albums.sortedFor(SortOrder.Ajout).map { it.id })
        assertEquals(albums, albums.sortedFor(SortOrder.Titre))
    }

    @Test
    fun `les artistes se trient par nombre de titres, le plus fourni d'abord`() {
        val artistes = listOf(
            Artist(id = 1, name = "A", albumCount = 1, trackCount = 2, artworkUri = null),
            Artist(id = 2, name = "B", albumCount = 1, trackCount = 9, artworkUri = null),
        )

        assertEquals(listOf(2L, 1L), artistes.sortedFor(SortOrder.Pistes).map { it.id })
        assertEquals(artistes, artistes.sortedFor(SortOrder.Nom))
    }

    @Test
    fun `les titres se trient par artiste puis par titre`() {
        val titres = listOf(
            morceau(1, "Zèbre", "b"),
            morceau(2, "Alpha", "B"),
            morceau(3, "Milieu", "a"),
        )

        assertEquals(listOf(3L, 2L, 1L), titres.sortedFor(SortOrder.Artiste).map { it.id })
    }

    private fun piste(id: String, album: String?) = PlayingTrack(
        mediaId = id,
        title = id,
        artist = "TWICE",
        album = album,
        artworkUri = null,
        localSongId = null,
        source = TrackSource.Local,
    )

    private fun bande(sorte: SorteDeSuggestion, addedAtMs: Long, reason: String?) = SuggestionBand(
        song = morceau(1, "Rehab", "Tom Doolie").copy(addedAtMs = addedAtMs),
        kind = sorte,
        kindLabel = "Jamais écouté",
        reason = reason,
    )

    private fun album(id: Long, titre: String, artiste: String, ajout: Long) = Album(
        id = id,
        title = titre,
        artist = artiste,
        artworkUri = null,
        trackCount = 1,
        durationMs = 1_000L,
        addedAtMs = ajout,
    )

    private fun morceau(id: Long, titre: String, artiste: String) = Song(
        id = id,
        uri = Uri.parse("content://media/$id"),
        title = titre,
        artist = artiste,
        artistId = 1L,
        album = "Album",
        albumId = 1L,
        durationMs = 1_000L,
        artworkUri = null,
    )
}
