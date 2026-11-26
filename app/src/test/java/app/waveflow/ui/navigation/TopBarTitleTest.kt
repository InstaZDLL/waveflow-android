package app.waveflow.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Le titre de la barre du haut.
 *
 * Une page de détail porte son titre en grand dans son en-tête : le répéter
 * dans la barre le disait deux fois l'un sous l'autre. Le bureau n'en met
 * jamais dans la sienne.
 */
class TopBarTitleTest {

    @Test
    fun `une page de detail ne repete pas son titre dans la barre`() {
        listOf(
            Routes.ALBUM_DETAIL,
            Routes.ARTIST_DETAIL,
            Routes.PLAYLIST_DETAIL,
            Routes.SERVER_ALBUM_DETAIL,
            Routes.SERVER_ARTIST_DETAIL,
        ).forEach { route ->
            assertNull("$route ne devrait pas avoir de titre", topBarTitle(route))
        }
    }

    @Test
    fun `une page sans en-tete garde son titre`() {
        // Ni les réglages ni le compte n'ont d'en-tête de page : sans titre, la
        // barre ne dirait plus où l'on est.
        assertEquals("Réglages", topBarTitle(Routes.SETTINGS))
        assertEquals("Compte", topBarTitle(Routes.SERVER_ACCOUNT))
        assertEquals("Albums", topBarTitle(Routes.ALBUMS))
    }
}
