package app.waveflow.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import app.waveflow.model.StreamNetwork
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
 * Puis ses changements, que suit la qualité automatique (Q2 et Q3 de
 * `docs/qualite-automatique.md`).
 */
@RunWith(RobolectricTestRunner::class)
class UnmeteredLinkTest {

    private val app: Context = ApplicationProvider.getApplicationContext()
    private val connectivite = app.getSystemService(ConnectivityManager::class.java)

    private fun reseauActif(vararg capacites: Int) {
        shadowOf(connectivite).setNetworkCapabilities(connectivite.activeNetwork, description(*capacites))
    }

    private fun description(vararg capacites: Int, transport: Int = NetworkCapabilities.TRANSPORT_WIFI) =
        ShadowNetworkCapabilities.newInstance().also { description ->
            shadowOf(description).addTransportType(transport)
            capacites.forEach { shadowOf(description).addCapability(it) }
        }

    /** Le réseau par défaut annonce ses capacités, comme Android au rappel inscrit. */
    private fun annoncer(description: NetworkCapabilities) {
        shadowOf(connectivite).networkCallbacks.forEach {
            it.onCapabilitiesChanged(connectivite.activeNetwork!!, description)
        }
    }

    /** Les réseaux successifs vus pendant [geste]. */
    private fun suivre(geste: () -> Unit): List<StreamNetwork> = runBlocking {
        val vus = mutableListOf<StreamNetwork>()
        val suivi = launch(Dispatchers.Unconfined) { app.reseauxSuccessifs().toList(vus) }
        geste()
        suivi.cancelAndJoin()
        vus
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

    @Test
    fun `le premier reseau se lit tout de suite`() {
        reseauActif(NetworkCapabilities.NET_CAPABILITY_INTERNET, NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        assertEquals(listOf(StreamNetwork.Unmetered), suivre {})
    }

    @Test
    fun `sans reseau au depart, les donnees mobiles`() {
        // Q3 : dans le doute, on ne dépense pas le forfait.
        shadowOf(connectivite).setDefaultNetworkActive(false)
        shadowOf(connectivite).setActiveNetworkInfo(null)

        assertEquals(listOf(StreamNetwork.Metered), suivre {})
    }

    @Test
    fun `un Wi-Fi declare payant suit les donnees mobiles, et revient`() {
        // Q2 : le même Wi-Fi, que l'utilisateur déclare payant puis non.
        reseauActif(NetworkCapabilities.NET_CAPABILITY_INTERNET, NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        val vus = suivre {
            annoncer(description(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            annoncer(
                description(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET,
                    NetworkCapabilities.NET_CAPABILITY_NOT_METERED,
                ),
            )
        }

        assertEquals(listOf(StreamNetwork.Unmetered, StreamNetwork.Metered, StreamNetwork.Unmetered), vus)
    }

    @Test
    fun `un Ethernet non facture suit le Wi-Fi`() {
        reseauActif(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        val vus = suivre {
            annoncer(
                description(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET,
                    NetworkCapabilities.NET_CAPABILITY_NOT_METERED,
                    transport = NetworkCapabilities.TRANSPORT_ETHERNET,
                ),
            )
        }

        assertEquals(listOf(StreamNetwork.Metered, StreamNetwork.Unmetered), vus)
    }

    @Test
    fun `perdre le reseau ne change rien`() {
        // Q3 : la perte ne dit rien du réseau qui viendra.
        reseauActif(NetworkCapabilities.NET_CAPABILITY_INTERNET, NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        val vus = suivre {
            shadowOf(connectivite).networkCallbacks.forEach { it.onLost(connectivite.activeNetwork!!) }
        }

        assertEquals(listOf(StreamNetwork.Unmetered), vus)
    }

    @Test
    fun `un collecteur en retard recoit quand meme le dernier reseau`() {
        // Le rappel ne peut pas attendre le collecteur : passé la contenance
        // d'un canal borné, le dernier changement se perdrait sans bruit.
        reseauActif(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val payant = description(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val gratuit =
            description(NetworkCapabilities.NET_CAPABILITY_INTERNET, NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        val vus = runBlocking {
            val vus = mutableListOf<StreamNetwork>()
            val retard = CompletableDeferred<Unit>()
            val suivi = launch(Dispatchers.Unconfined) {
                app.reseauxSuccessifs().collect {
                    vus += it
                    retard.await()
                }
            }
            repeat(ANNONCES_EN_RETARD) { annoncer(payant) }
            annoncer(gratuit)
            retard.complete(Unit)
            suivi.cancelAndJoin()
            vus
        }

        assertEquals(StreamNetwork.Unmetered, vus.last())
    }

    @Test
    fun `le suivi termine, le rappel est desinscrit`() {
        reseauActif(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        suivre { assertEquals(1, shadowOf(connectivite).networkCallbacks.size) }

        assertEquals(0, shadowOf(connectivite).networkCallbacks.size)
    }

    private companion object {
        /** De quoi déborder un canal `BUFFERED` et ses 64 places. */
        const val ANNONCES_EN_RETARD = 100
    }
}
