package app.waveflow.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource.HttpDataSourceException

/**
 * Reprend un transcodage coupé en route, là où le son s'est tu.
 *
 * Laissé à Media3, un transcodage coupé ne repartait jamais du bon endroit : il
 * le rouvre à l'octet atteint quand il en connaît la longueur ou la durée — et
 * le serveur refuse la plage —, du début sinon, ce qui est le cas de tout
 * transcodage en direct. [TranscodeRefusalPolicy] rend donc la coupure
 * définitive, et elle arrive ici, une fois joué ce qui avait été reçu. La
 * reprise est une relance au décalage de la position logique, voir
 * [TranscodeSeekingPlayer.reprendreApresCoupure] et R25 à R30 de
 * `docs/deplacement-dans-un-transcodage.md`.
 *
 * **Écouté sur l'ExoPlayer, pas sur l'enveloppe**, comme le repli du 429
 * (R23, R27) : réparée dans ce rappel, l'erreur n'atteint jamais la session.
 */
internal class TranscodeCutRecovery(
    private val lecteur: TranscodeSeekingPlayer,
) : Player.Listener {

    override fun onPlayerError(error: PlaybackException) {
        if (error.estUneLectureCoupee()) lecteur.reprendreApresCoupure()
    }
}

/**
 * Vrai si cette erreur vient d'un flux HTTP interrompu **pendant sa lecture** :
 * la liaison a cédé une fois des octets reçus. Une demande qui n'aboutit pas —
 * aucun réseau, un serveur muet — échoue à l'ouverture, et n'en est pas une.
 *
 * Cherché sous les couches d'emballage : le lecteur range l'erreur de la source
 * dans une `PlaybackException`.
 */
internal fun Throwable.estUneLectureCoupee(): Boolean =
    generateSequence(this) { it.cause.takeIf { suivante -> suivante !== it } }
        .take(PROFONDEUR_MAX)
        .any { it is HttpDataSourceException && it.type == HttpDataSourceException.TYPE_READ }

/** Garde-fou : une chaîne de causes circulaire ne doit pas figer le lecteur. */
private const val PROFONDEUR_MAX = 10
