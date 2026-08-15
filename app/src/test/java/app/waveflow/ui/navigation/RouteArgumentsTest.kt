package app.waveflow.ui.navigation

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * La lecture des identifiants portés par une route.
 *
 * Robolectric pour disposer d'un vrai [Bundle] : c'est son comportement en cas
 * de mauvais type qui est en cause, et un faux le masquerait.
 */
@RunWith(RobolectricTestRunner::class)
class RouteArgumentsTest {

    @Test
    fun `un identifiant local est lu sur sa propre route`() {
        val args = Bundle().apply { putLong(Routes.ARG_ALBUM_ID, 42L) }

        val albumId = args.longArgOf(Routes.ALBUM_DETAIL, Routes.ALBUM_DETAIL, Routes.ARG_ALBUM_ID)

        assertEquals(42L, albumId)
    }

    @Test
    fun `un UUID distant n'est pas lu comme un entier`() {
        // Le cœur du correctif. `Bundle.getLong` ne lève pas sur une chaîne : il
        // attrape la ClassCastException et rend `0`. Sans le filtre par route,
        // la barre de titre obtenait donc un identifiant d'album parfaitement
        // plausible — et faux — à chaque recomposition.
        val args = Bundle().apply {
            putString(Routes.ARG_ALBUM_ID, "2ce78e18-1390-43e1-a7b9-de371debb728")
        }

        val albumId = args.longArgOf(
            currentRoute = Routes.SERVER_ALBUM_DETAIL,
            route = Routes.ALBUM_DETAIL,
            key = Routes.ARG_ALBUM_ID,
        )

        assertNull(albumId)
    }

    @Test
    fun `un UUID purement numerique ne passe pas davantage`() {
        // Celui-ci se lirait en Long sans que rien ne proteste : c'est la route,
        // et elle seule, qui doit trancher.
        val args = Bundle().apply { putString(Routes.ARG_ALBUM_ID, "1234") }

        val albumId = args.longArgOf(
            currentRoute = Routes.SERVER_ALBUM_DETAIL,
            route = Routes.ALBUM_DETAIL,
            key = Routes.ARG_ALBUM_ID,
        )

        assertNull(albumId)
    }

    @Test
    fun `l'argument d'artiste obeit a la meme regle`() {
        val args = Bundle().apply {
            putString(Routes.ARG_ARTIST_ID, "fc8396af-c0f2-4322-b136-b210004cda78")
        }

        val artistId = args.longArgOf(
            currentRoute = Routes.SERVER_ARTIST_DETAIL,
            route = Routes.ARTIST_DETAIL,
            key = Routes.ARG_ARTIST_ID,
        )

        assertNull(artistId)
    }

    @Test
    fun `sans arguments il n'y a rien a lire`() {
        val args: Bundle? = null

        assertNull(args.longArgOf(Routes.ALBUM_DETAIL, Routes.ALBUM_DETAIL, Routes.ARG_ALBUM_ID))
    }
}
