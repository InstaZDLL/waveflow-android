package app.waveflow.playback.audio

import android.content.Context
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import app.waveflow.model.ReglagesEgaliseur
import app.waveflow.model.ReglagesReplayGain

/**
 * Ce qui relie les réglages du son aux deux traitements du `DefaultAudioSink`.
 * Voir `docs/audio-avance.md`.
 *
 * Le service y pose les réglages et l'écoute, sur le fil principal ; les
 * traitements les relisent à chaque tampon, sur le fil de lecture. Chaque champ
 * est remplacé d'un bloc, jamais modifié en place : un tampon voit toujours un
 * réglage entier, fût-il d'un instant plus tôt.
 *
 * Les défauts sont ceux des préférences, pour qu'un son joué avant leur première
 * lecture soit déjà le bon (A2).
 */
internal class ChaineAudio {

    @Volatile
    var replayGain: ReglagesReplayGain = ReglagesReplayGain()

    @Volatile
    var egaliseur: ReglagesEgaliseur = ReglagesEgaliseur.PAR_DEFAUT

    /** Voir G7 et [suivreLEcoute]. */
    @Volatile
    var ecoute: Ecoute = Ecoute.UnTitre

    /** Le gain de chaque piste, retenu au `configure` et repris au `flush` (G4). */
    val gains = GainsDesPistes()

    val traitementReplayGain = TraitementReplayGain(this)
    val traitementEgaliseur = TraitementEgaliseur(this)

    /** Les deux traitements, dans l'ordre du bureau : le gain, puis l'égaliseur (C1). */
    fun traitements(): Array<AudioProcessor> = arrayOf(traitementReplayGain, traitementEgaliseur)

    /**
     * Tient [ecoute] à jour d'après la file et l'aléatoire de [player].
     *
     * Sur l'ExoPlayer, dont la timeline porte les `MediaItem` tels qu'ils ont
     * été posés. Réévaluée à chaque changement de l'une ou de l'autre (G7).
     */
    fun suivreLEcoute(player: Player) {
        player.addListener(
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
                        ecoute = ecouteDuLecteur(player)
                    }
                }
            },
        )
    }
}

/** L'écoute que décrivent la file et l'aléatoire de ce lecteur. Voir [ecouteDeLaFile]. */
internal fun ecouteDuLecteur(player: Player): Ecoute {
    val timeline = player.currentTimeline
    val fenetre = Timeline.Window()
    val albums = (0 until timeline.windowCount).map { timeline.getWindow(it, fenetre).mediaItem.albumDeLaFile }
    return ecouteDeLaFile(albums, player.shuffleModeEnabled)
}

/**
 * Le gain de chaque piste, sous l'identifiant de sa période dans la timeline.
 *
 * Écrit au `configure` du sink, lu au `flush` des traitements : les deux sur le
 * fil de lecture, mais synchronisé quand même, la garantie ne coûtant rien. Les
 * plus anciennes entrées tombent : une file de mille titres n'a pas à garder
 * mille gains, seulement ceux des pistes en train de passer dans le tampon.
 */
internal class GainsDesPistes {

    private val parPeriode = object : LinkedHashMap<Any, GainDePiste>(CAPACITE, CHARGE, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, GainDePiste>?): Boolean =
            size > CAPACITE
    }

    @Synchronized
    fun retenir(periode: Any, gain: GainDePiste) {
        parPeriode[periode] = gain
    }

    /** Le gain de cette période ; vide si on l'ignore, et le gain par défaut s'appliquera (G4). */
    @Synchronized
    fun de(periode: Any?): GainDePiste = periode?.let { parPeriode[it] } ?: GainDePiste()

    private companion object {
        const val CAPACITE = 16
        const val CHARGE = 0.75f
    }
}

/**
 * Le `DefaultAudioSink`, qui retient au passage le gain de chaque piste qu'on lui
 * configure (G4).
 *
 * C'est le seul endroit qui voit à la fois les tags de la piste
 * (`Format.metadata`) et la période à laquelle ils appartiennent. Le traitement,
 * lui, ne reçoit au `flush` que l'identifiant de la période.
 */
internal class SortieQuiRetientLesGains(
    sortie: AudioSink,
    private val gains: GainsDesPistes,
) : ForwardingAudioSink(sortie) {

    override fun configure(audioSinkConfig: AudioSink.AudioSinkConfig) {
        audioSinkConfig.mediaPeriodId?.periodUid?.let { periode ->
            val piste = mediaItemDe(audioSinkConfig.timeline, periode)
            // Le serveur d'abord, les tags du flux ensuite, champ par champ (G2).
            // Une piste locale n'a rien du serveur : ses tags seuls comptent (G1).
            val gain = (piste?.gainDuServeur ?: GainDePiste()).ouSinon(
                TagsReplayGain.lire(audioSinkConfig.format.metadata),
            )
            gains.retenir(periode, gain)
        }
        super.configure(audioSinkConfig)
    }

    private fun mediaItemDe(timeline: Timeline, periode: Any): MediaItem? {
        val index = timeline.getIndexOfPeriod(periode)
        if (index == C.INDEX_UNSET) return null
        val fenetre = timeline.getPeriod(index, Timeline.Period()).windowIndex
        return timeline.getWindow(fenetre, Timeline.Window()).mediaItem
    }
}

/**
 * Les renderers par défaut, dont la sortie audio porte les deux traitements.
 *
 * `enableFloatOutput` reste celui de Media3, faux : avec lui, Media3 contournerait
 * les traitements ajoutés pour les formats haute résolution.
 */
internal class RenderersAvecTraitements(
    context: Context,
    private val chaine: ChaineAudio,
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParams: Boolean,
    ): AudioSink = SortieQuiRetientLesGains(
        DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
            .setAudioProcessors(chaine.traitements())
            .build(),
        chaine.gains,
    )
}

/**
 * Ce qu'une piste emporte dans la file pour le son : son album, pour l'écoute
 * (G7), et le gain que le serveur publie (G2).
 *
 * Dans les `extras` de ses métadonnées : elles voyagent avec la piste du
 * contrôleur au service, et à travers les réécritures du rendu ou du décalage,
 * qui partent toutes de `buildUpon`.
 */
internal object ExtrasAudio {
    const val ALBUM = "app.waveflow.audio.album"
    const val GAIN_TITRE_DB = "app.waveflow.audio.gain_titre_db"
    const val PIC_TITRE = "app.waveflow.audio.pic_titre"
    const val GAIN_ALBUM_DB = "app.waveflow.audio.gain_album_db"
    const val PIC_ALBUM = "app.waveflow.audio.pic_album"

    /**
     * Les extras d'une piste : la clé de son album (préfixée par sa source, pour
     * qu'un album local et un album du serveur ne se confondent pas) et les
     * valeurs du serveur, chacune seulement si elle est connue.
     */
    fun de(
        album: String?,
        gainTitreDb: Double? = null,
        picTitre: Double? = null,
        gainAlbumDb: Double? = null,
        picAlbum: Double? = null,
    ): Bundle = Bundle().apply {
        album?.let { putString(ALBUM, it) }
        gainTitreDb?.let { putDouble(GAIN_TITRE_DB, it) }
        picTitre?.let { putDouble(PIC_TITRE, it) }
        gainAlbumDb?.let { putDouble(GAIN_ALBUM_DB, it) }
        picAlbum?.let { putDouble(PIC_ALBUM, it) }
    }
}

/** L'album de cette piste pour l'écoute, `null` si elle n'en dit rien. */
internal val MediaItem.albumDeLaFile: String?
    get() = mediaMetadata.extras?.getString(ExtrasAudio.ALBUM)

/** Le gain que le serveur publie pour cette piste ; vide pour une piste locale. */
internal val MediaItem.gainDuServeur: GainDePiste
    get() {
        val extras = mediaMetadata.extras ?: return GainDePiste()
        fun valeur(cle: String): Double? = if (extras.containsKey(cle)) extras.getDouble(cle) else null
        return GainDePiste(
            gainDb = valeur(ExtrasAudio.GAIN_TITRE_DB),
            pic = valeur(ExtrasAudio.PIC_TITRE),
            gainAlbumDb = valeur(ExtrasAudio.GAIN_ALBUM_DB),
            picAlbum = valeur(ExtrasAudio.PIC_ALBUM),
        )
    }
