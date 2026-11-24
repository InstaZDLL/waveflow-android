package app.waveflow.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * La liaison qui porte l'original, lue comme Android la décrit (R22 de
 * `docs/deplacement-dans-un-transcodage.md`) : non facturée, et non « Wi-Fi ».
 */
@RunWith(RobolectricTestRunner::class)
class UnmeteredLinkTest {

    private val app: Context = ApplicationProvider.getApplicationContext()
    private val connectivite = app.getSystemService(ConnectivityManager::class.java)

    private fun reseauActif(vararg capacites: Int) {
        val description = ShadowNetworkCapabilities.newInstance()
        capacites.forEach { shadowOf(description).addCapability(it) }
        shadowOf(connectivite).setNetworkCapabilities(connectivite.activeNetwork, description)
    }

    @Test
    fun `un reseau non facture porte l'original`() {
        reseauActif(NetworkCapabilities.NET_CAPABILITY_INTERNET, NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        assertTrue(app.surLiaisonNonFacturee())
    }

    @Test
    fun `un reseau facture ne le porte pas, Wi-Fi ou non`() {
        // Un Wi-Fi déclaré payant — un partage de connexion — se facture
        // comme les données mobiles.
        reseauActif(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        assertFalse(app.surLiaisonNonFacturee())
    }

    @Test
    fun `sans reseau actif, dans le doute, non`() {
        shadowOf(connectivite).setDefaultNetworkActive(false)
        shadowOf(connectivite).setActiveNetworkInfo(null)

        assertFalse(app.surLiaisonNonFacturee())
    }
}
