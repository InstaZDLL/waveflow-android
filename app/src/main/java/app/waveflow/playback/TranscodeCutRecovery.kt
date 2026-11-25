package app.waveflow.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSourceException

/**
 * Reprend un transcodage coupé en route, là où le son s'est tu.
 *
 * Coupé, un flux se rouvre à l'octet atteint. Le serveur refuse toute plage
 * d'un transcodage en cours (`416`), et Media3 tient ce refus pour définitif :
 * la piste tombait pour de bon. La reprise est une relance au décalage de la
 * position logique, voir [TranscodeSeekingPlayer.reprendreApresCoupure] et
 * R25 à R30 de `docs/deplacement-dans-un-transcodage.md`.
 *
 * **Écouté sur l'ExoPlayer, pas sur l'enveloppe**, comme le repli du 429
 * (R23, R27) : réparée dans ce rappel, l'erreur n'atteint jamais la session.
 */
internal class TranscodeCutRecovery(
    private val lecteur: TranscodeSeekingPlayer,
) : Player.Listener {

    override fun onPlayerError(error: PlaybackException) {
        if (error.estUnePlageRefusee()) lecteur.reprendreApresCoupure()
    }
}

/**
 * Vrai si cette erreur vient d'une plage que la source a refusée.
 *
 * Le lecteur porte le code de l'en-tête HTTP (`ERROR_CODE_IO_BAD_HTTP_STATUS`) ;
 * la plage refusée est la cause qu'il enveloppe, celle-là même qui fait juger
 * l'erreur définitive à Media3 (`DataSourceException.isCausedByPositionOutOfRange`).
 */
internal fun Throwable.estUnePlageRefusee(): Boolean =
    generateSequence(this) { it.cause.takeIf { suivante -> suivante !== it } }
        .take(PROFONDEUR_MAX)
        .any {
            it is DataSourceException &&
                it.reason == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
        }

/** Garde-fou : une chaîne de causes circulaire ne doit pas figer le lecteur. */
private const val PROFONDEUR_MAX = 10
