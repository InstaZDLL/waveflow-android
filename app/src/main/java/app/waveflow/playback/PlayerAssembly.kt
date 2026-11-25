package app.waveflow.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlin.random.Random

/**
 * Le lecteur que tient [PlaybackService], de l'ExoPlayer à l'enveloppe.
 *
 * Sorti du service pour que les tests éprouvent **le même assemblage** face à un
 * `MockWebServer`, sans session serveur : ce qui se décide ici — la politique
 * de relance, l'enveloppe, le repli — ne dépend pas de la provenance des URL.
 *
 * @param dataSourceFactory la chaîne de lecture, cache et résolveur compris.
 * @param liaisonNonFacturee voir [TranscodeRefusalFallback].
 * @param annoncerRepli voir [TranscodeRefusalFallback].
 * @param hasard la source de la gigue des relances, voir [TranscodeRefusalPolicy].
 */
internal fun assemblerLeLecteur(
    context: Context,
    dataSourceFactory: DataSource.Factory,
    liaisonNonFacturee: () -> Boolean,
    annoncerRepli: () -> Unit,
    hasard: Random = Random.Default,
): TranscodeSeekingPlayer {
    val exoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(dataSourceFactory)
                // Un transcodage refusé se retente quand le serveur le dit, et
                // pas avant. Voir `docs/deplacement-dans-un-transcodage.md`, R17.
                .setLoadErrorHandlingPolicy(TranscodeRefusalPolicy(hasard)),
        )
        // Route audio "musique" + gestion du focus audio (pause si un appel
        // arrive, etc.).
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true,
        )
        // Met en pause quand le casque est débranché.
        .setHandleAudioBecomingNoisy(true)
        .build()

    // Tout ce qui suit parle à l'enveloppe, jamais à l'ExoPlayer : dans un
    // morceau transcodé relancé à 2:13, lui compte depuis zéro, et la
    // notification, la voiture, l'historique ou la boucle A-B s'y
    // tromperaient. Voir R1.
    val enveloppe = TranscodeSeekingPlayer(exoPlayer)

    // Sur l'ExoPlayer, et non sur l'enveloppe : l'erreur doit être réparée
    // avant que l'enveloppe ne la relise et ne l'annonce à la session (R23).
    exoPlayer.addListener(TranscodeRefusalFallback(enveloppe, liaisonNonFacturee, annoncerRepli))

    return enveloppe
}
