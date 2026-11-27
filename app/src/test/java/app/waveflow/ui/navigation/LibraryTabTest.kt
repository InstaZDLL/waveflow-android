package app.waveflow.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Quelle route appartient à quel onglet.
 *
 * Une correspondance par préfixe est commode et distraite : elle réclame tout
 * ce qui commence pareil. Un écran mal réclamé se dit dans une section où il
 * n'est pas, et l'interface se contredit — barre du bas sur un onglet, contenu
 * sur un autre.
 *
 * Robolectric parce que les routes de détail distantes encodent leur
 * identifiant avec `Uri.encode`, que la JVM seule ne fournit pas.
 */
@RunWith(RobolectricTestRunner::class)
class LibraryTabTest {

    @Test
    fun `le catalogue du serveur et ses details appartiennent a son onglet`() {
        assertTrue(LibraryTab.Server.owns(Routes.SERVER))
        assertTrue(LibraryTab.Server.owns(Routes.serverAlbumDetail("uuid-a")))
        assertTrue(LibraryTab.Server.owns(Routes.serverArtistDetail("uuid-b")))
    }

    @Test
    fun `le compte serveur n'appartient a aucun onglet de bibliotheque`() {
        // Il s'ouvre depuis les réglages et il y appartient. Tant qu'il vivait
        // sous `server/`, l'onglet Serveur le réclamait : l'écran Compte
        // affichait les sous-onglets de la bibliothèque et s'y disait.
        LibraryTab.entries.forEach { onglet ->
            assertFalse(
                "${onglet.name} ne devrait pas réclamer le compte",
                onglet.owns(Routes.SERVER_ACCOUNT),
            )
        }
    }

    @Test
    fun `un onglet ne reclame pas la route d'un autre`() {
        assertTrue(LibraryTab.Songs.owns(Routes.SONGS))
        assertFalse(LibraryTab.Songs.owns(Routes.ALBUMS))
        assertFalse(LibraryTab.Albums.owns(Routes.ARTISTS))
    }

    @Test
    fun `chaque racine ne commence que la pile de sa destination`() {
        assertEquals(TopLevelDestination.Home, TopLevelDestination.ofRoot(Routes.HOME))
        assertEquals(TopLevelDestination.Search, TopLevelDestination.ofRoot(Routes.SEARCH))
        LibraryTab.entries.forEach { vue ->
            assertEquals(TopLevelDestination.Library, TopLevelDestination.ofRoot(vue.route))
        }
        // Un détail ou les réglages ne commencent aucune pile : ils vivent dans
        // celle d'où on les ouvre.
        assertNull(TopLevelDestination.ofRoot(Routes.ALBUM_DETAIL))
        assertNull(TopLevelDestination.ofRoot(Routes.SERVER_ALBUM_DETAIL))
        assertNull(TopLevelDestination.ofRoot(Routes.SETTINGS))
        assertNull(TopLevelDestination.ofRoot(Routes.SERVER_ACCOUNT))
    }

    @Test
    fun `les sous-onglets ne se montrent que sur une page de la pile de la bibliotheque`() {
        // Un album dans la pile de la recherche : sa route a beau ressembler à
        // celles de la vue Albums, les sous-onglets ne le coiffent pas.
        assertNull(libraryTabShown(openTabOfRoot(Routes.SEARCH), Routes.ALBUM_DETAIL))
        assertNull(libraryTabShown(openTabOfRoot(Routes.HOME), Routes.ALBUM_DETAIL))

        // Dans la pile des artistes : la vue marquée reste « Artistes ».
        assertEquals(LibraryTab.Artists, libraryTabShown(openTabOfRoot(Routes.ARTISTS), Routes.ALBUM_DETAIL))

        // Les réglages et le compte vivent dans la pile d'où on les ouvre sans
        // en être une page : pas de sous-onglets au-dessus d'eux.
        assertNull(libraryTabShown(openTabOfRoot(Routes.SONGS), Routes.SETTINGS))
        assertNull(libraryTabShown(openTabOfRoot(Routes.SONGS), Routes.SERVER_ACCOUNT))

        assertNull(openTabOfRoot(Routes.ALBUM_DETAIL))
    }
}
