package app.waveflow.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player

/**
 * Passe à l'original quand le serveur a refusé le transcodage jusqu'au bout.
 *
 * [TranscodeRefusalPolicy] a déjà attendu et retenté ; l'erreur qui arrive ici
 * est un refus dont les essais sont épuisés. On ne redescend vers l'original
 * que si la liaison le porte (R22) : il pèse plusieurs fois le transcodage
 * refusé, et le servir en données mobiles coûterait plus cher que d'échouer.
 * Sinon l'erreur suit son cours, et l'écran dit « serveur occupé ».
 *
 * **Écouté sur l'ExoPlayer, pas sur l'enveloppe** (R23). L'erreur y arrive par
 * un rappel individuel, et l'enveloppe ne relit l'état qu'au `onEvents` qui
 * suit, livré par un message posté. Réparée dans ce rappel, l'erreur n'atteint
 * donc jamais la session : l'écran ne dit pas « injoignable » avant de jouer.
 *
 * @param liaisonNonFacturee lu au moment du refus, non au démarrage : on change
 *   de réseau en cours d'écoute.
 * @param annoncerRepli dit à l'écran que la qualité a changé (R24).
 */
internal class TranscodeRefusalFallback(
    private val lecteur: TranscodeSeekingPlayer,
    private val liaisonNonFacturee: () -> Boolean,
    private val annoncerRepli: () -> Unit,
) : Player.Listener {

    override fun onPlayerError(error: PlaybackException) {
        if (error.refusDeTranscodage() == null) return
        if (!liaisonNonFacturee()) return
        if (lecteur.revenirAlOriginal()) annoncerRepli()
    }
}

/**
 * Vrai si le réseau actif n'est pas facturé au volume : Wi-Fi ou Ethernet
 * ordinaires, pas un Wi-Fi déclaré payant ni les données mobiles.
 *
 * Faux quand on ne peut pas le savoir — aucun réseau, ou l'accès à son état
 * refusé : dans le doute, on ne dépense pas le forfait de l'utilisateur.
 */
internal fun Context.surLiaisonNonFacturee(): Boolean {
    val connectivite = getSystemService(ConnectivityManager::class.java) ?: return false
    val capacites = try {
        connectivite.getNetworkCapabilities(connectivite.activeNetwork)
    } catch (_: SecurityException) {
        // `ACCESS_NETWORK_STATE` n'arrive aujourd'hui que par le manifeste de
        // Media3, fusionné au nôtre.
        null
    }
    return capacites?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
}
