package app.waveflow.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Le ReplayGain, premier des deux traitements de la chaîne (C1 de
 * `docs/audio-avance.md`).
 *
 * Il reçoit du PCM 16 bits — ce que Media3 lui passe, après sa propre
 * conversion — ou flottant, et rend du **flottant** : le signal ne repasse en
 * 16 bits qu'après l'égaliseur (C2).
 *
 * Le gain est celui de la piste dont arrivent les échantillons, repris au
 * `flush` qui marque son premier échantillon (G4), et le facteur est recalculé à
 * chaque tampon depuis les réglages du moment (A4).
 */
internal class TraitementReplayGain(private val chaine: ChaineAudio) : BaseAudioProcessor() {

    /** Ce qu'on sait de la piste en cours de traitement. */
    private var piste = GainDePiste()

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (!estGere(inputAudioFormat.encoding)) throw UnhandledAudioFormatException(inputAudioFormat)
        return AudioFormat(inputAudioFormat.sampleRate, inputAudioFormat.channelCount, C.ENCODING_PCM_FLOAT)
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        piste = chaine.gains.de(streamMetadata.periodUid)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val facteur = ReplayGain.lineaire(piste, chaine.replayGain, chaine.ecoute)
        val seize = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
        val echantillons = inputBuffer.remaining() / if (seize) OCTETS_16 else OCTETS_FLOTTANT
        val sortie = replaceOutputBuffer(echantillons * OCTETS_FLOTTANT)
        repeat(echantillons) {
            val x = if (seize) inputBuffer.getShort() / PLEINE_ECHELLE else inputBuffer.getFloat()
            sortie.putFloat(x * facteur)
        }
        // Un reste d'octets qui ne fait pas un échantillon n'existe pas en PCM
        // bien formé ; on le consomme pour ne pas boucler dessus.
        inputBuffer.position(inputBuffer.limit())
        sortie.flip()
    }

    override fun onReset() {
        piste = GainDePiste()
    }
}

/**
 * L'égaliseur, second traitement de la chaîne (C1).
 *
 * Il reçoit le flottant du ReplayGain (ou du 16 bits) et rend du **16 bits**,
 * arrondi au plus proche et borné à pleine échelle : le saut des silences de
 * Media3, qui le suit, n'accepte rien d'autre (C2).
 *
 * Inactif, il ne fait que cette conversion, qui rend à l'identique un signal
 * venu du 16 bits sans gain : diviser puis multiplier par 32 768, une puissance
 * de deux, est exact.
 */
internal class TraitementEgaliseur(private val chaine: ChaineAudio) : BaseAudioProcessor() {

    private val egaliseur = Egaliseur()

    /** Le tampon de calcul, réutilisé d'un appel à l'autre. */
    private var calcul = FloatArray(0)

    /** Le format pour lequel la mémoire des filtres vaut. Voir [onFlush]. */
    private var formatDeLaMemoire: AudioFormat? = null

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (!estGere(inputAudioFormat.encoding)) throw UnhandledAudioFormatException(inputAudioFormat)
        return AudioFormat(inputAudioFormat.sampleRate, inputAudioFormat.channelCount, C.ENCODING_PCM_16BIT)
    }

    /**
     * La mémoire des filtres n'est oubliée qu'au changement de format (E5).
     *
     * Ici et non dans [onConfigure] : la configuration arrive pendant que la
     * piste précédente s'écoule encore, dans l'ancien format ; le `flush` marque
     * le moment où le nouveau commence.
     */
    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        val format = inputAudioFormat
        val precedent = formatDeLaMemoire
        if (precedent != null &&
            (precedent.sampleRate != format.sampleRate || precedent.channelCount != format.channelCount)
        ) {
            egaliseur.oublier()
        }
        formatDeLaMemoire = format
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val format = inputAudioFormat
        val seize = format.encoding == C.ENCODING_PCM_16BIT
        val canaux = format.channelCount
        val echantillons = inputBuffer.remaining() / if (seize) OCTETS_16 else OCTETS_FLOTTANT
        val trames = echantillons / canaux
        if (calcul.size < echantillons) calcul = FloatArray(echantillons)

        repeat(echantillons) { i ->
            calcul[i] = if (seize) inputBuffer.getShort() / PLEINE_ECHELLE else inputBuffer.getFloat()
        }
        inputBuffer.position(inputBuffer.limit())

        egaliseur.traiter(calcul, trames, canaux, format.sampleRate.toFloat(), chaine.egaliseur)

        val sortie = replaceOutputBuffer(echantillons * OCTETS_16)
        repeat(echantillons) { i -> sortie.putShort(versSeizeBits(calcul[i])) }
        sortie.flip()
    }

    override fun onReset() {
        egaliseur.oublier()
        formatDeLaMemoire = null
    }

    /** Le préampli courant de l'égaliseur, pour les tests de la marge. */
    internal val preampli: Float get() = egaliseur.preampli
}

/** Les deux formats que la chaîne sait traiter : ce que Media3 lui passe, et le flottant. */
private fun estGere(encodage: Int): Boolean = encodage == C.ENCODING_PCM_16BIT || encodage == C.ENCODING_PCM_FLOAT

/** Un échantillon flottant en 16 bits, arrondi au plus proche et borné à pleine échelle. */
internal fun versSeizeBits(x: Float): Short {
    if (x.isNaN()) return 0
    return (x * PLEINE_ECHELLE).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
}

/** 2¹⁵ : un échantillon 16 bits divisé par ce nombre tient dans [−1, 1[. */
private const val PLEINE_ECHELLE = 32_768f
private const val OCTETS_16 = 2
private const val OCTETS_FLOTTANT = 4
