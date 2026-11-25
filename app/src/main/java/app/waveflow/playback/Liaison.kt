package app.waveflow.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import app.waveflow.model.StreamNetwork
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Vrai si le réseau actif n'est pas facturé au volume : Wi-Fi ou Ethernet
 * ordinaires, pas un Wi-Fi déclaré payant ni les données mobiles.
 *
 * Faux quand on ne peut pas le savoir — aucun réseau, ou l'accès à son état
 * refusé : dans le doute, on ne dépense pas le forfait de l'utilisateur.
 *
 * La seule lecture du réseau de l'application : le repli sur l'original (R22 de
 * `docs/deplacement-dans-un-transcodage.md`) et la qualité automatique (Q2 de
 * `docs/qualite-automatique.md`) en décident de la même façon.
 */
internal fun Context.surLiaisonNonFacturee(): Boolean {
    val connectivite = getSystemService(ConnectivityManager::class.java) ?: return false
    val capacites = try {
        connectivite.getNetworkCapabilities(connectivite.activeNetwork)
    } catch (_: SecurityException) {
        // `ACCESS_NETWORK_STATE` est déclarée au manifeste ; un système qui la
        // refuserait quand même ne doit pas faire tomber la lecture.
        null
    }
    return capacites.nonFacturee()
}

private fun NetworkCapabilities?.nonFacturee(): Boolean =
    this?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true

/**
 * Le réseau du moment, au sens de la qualité automatique, puis chacun de ses
 * changements.
 *
 * Le premier est lu tout de suite, par [surLiaisonNonFacturee] : sans réseau
 * lisible, c'est [StreamNetwork.Metered]. Les suivants viennent du réseau par
 * défaut, et de lui seul — le réseau qu'Android donne à l'application, celui
 * que les flux emprunteront. Un Wi-Fi qu'on déclare payant sans le quitter
 * change ses capacités, et compte donc comme un changement.
 *
 * **La perte du réseau n'est pas un changement** (Q3) : elle ne dit rien de
 * celui qui viendra, et une coupure passagère ne doit pas réécrire la file deux
 * fois. On garde le dernier réseau connu jusqu'au suivant.
 *
 * Le rappel arrive sur un fil du système ; `trySend` y suffit, le canal ne
 * bornant rien et le flux ne gardant que des changements.
 */
internal fun Context.reseauxSuccessifs(): Flow<StreamNetwork> = callbackFlow {
    trySend(StreamNetwork.of(surLiaisonNonFacturee()))

    val connectivite: ConnectivityManager? = getSystemService(ConnectivityManager::class.java)
    val rappel = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            trySend(StreamNetwork.of(networkCapabilities.nonFacturee()))
        }
    }
    // Sans service ou sans droit, on s'en tient à la première lecture : la file
    // gardera le rendu de départ, comme avant la qualité automatique.
    val inscrite = try {
        connectivite?.also { it.registerDefaultNetworkCallback(rappel) }
    } catch (_: SecurityException) {
        null
    }

    awaitClose { inscrite?.unregisterNetworkCallback(rappel) }
}.distinctUntilChanged()
