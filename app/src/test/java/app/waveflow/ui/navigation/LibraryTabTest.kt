package app.waveflow.ui.navigation

import org.junit.Assert.assertFalse
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
        assertFalse(TopLevelDestination.Library.owns(Routes.SERVER_ACCOUNT))
    }

    @Test
    fun `un onglet ne reclame pas la route d'un autre`() {
        assertTrue(LibraryTab.Songs.owns(Routes.SONGS))
        assertFalse(LibraryTab.Songs.owns(Routes.ALBUMS))
        assertFalse(LibraryTab.Albums.owns(Routes.ARTISTS))
    }

    @Test
    fun `l'accueil ne se confond pas avec la bibliotheque`() {
        assertTrue(TopLevelDestination.Home.owns(Routes.HOME))
        assertFalse(TopLevelDestination.Home.owns(Routes.SONGS))
        assertFalse(TopLevelDestination.Library.owns(Routes.HOME))
        // Les réglages n'appartiennent à personne : ils s'ouvrent de partout.
        assertFalse(TopLevelDestination.Home.owns(Routes.SETTINGS))
        assertFalse(TopLevelDestination.Library.owns(Routes.SETTINGS))
    }
}
