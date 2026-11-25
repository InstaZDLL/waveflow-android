package app.waveflow.playback.audio

import app.waveflow.model.ReglagesEgaliseur
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * L'égaliseur à six bandes, porté du bureau (`src-tauri/crates/app/src/audio/eq.rs`).
 * Voir E1 à E5 de `docs/audio-avance.md`.
 *
 * Six cloches en série aux fréquences de [ReglagesEgaliseur.FREQUENCES_HZ],
 * d'après le *Audio EQ Cookbook* de RBJ, en forme directe II transposée : assez
 * stable en simple précision aux fréquences audio, et un registre de moins que la
 * forme directe I.
 *
 * **La marge.** Une amplification pousse un master fort au-delà de la pleine
 * échelle, et la sortie l'écrête : +12 dB de graves sur un titre moderne
 * s'entendaient comme une saturation franche. La cascade est donc précédée d'une
 * atténuation égale au pic de sa propre réponse — lue sur la cascade et non sur
 * le plus grand curseur, puisque des bandes voisines additionnent leurs gains —
 * et d'1 dB de plus pour les transitoires. Un réglage sans amplification a une
 * atténuation de 1 exactement, et laisse le signal intact.
 *
 * Une instance par fil de lecture : elle tient la mémoire des filtres, que rien
 * d'autre ne doit toucher.
 */
internal class Egaliseur {

    /** Six jeux de cinq coefficients normalisés, `b0 b1 b2 a1 a2` à la suite. */
    private val coefficients = FloatArray(NOMBRE_DE_BANDES * COEFFS_PAR_BANDE).also(::identite)

    /**
     * La mémoire des filtres, par canal : deux valeurs par bande. Les canaux
     * s'ajoutent à la demande.
     */
    private var memoires: Array<FloatArray> = emptyArray()

    /** La fréquence pour laquelle [coefficients] ont été calculés. */
    private var frequenceCalculee = 0f

    /** Les bandes pour lesquelles [coefficients] ont été calculés. */
    private var bandesCalculees: List<Float>? = null

    /**
     * Le gain posé avant les filtres pour garder la cascade sous l'unité : celui
     * sur lequel le dernier tampon s'est terminé.
     */
    internal var preampli = 1f
        private set

    /**
     * Le gain que demande le réglage courant. Atteint tout de suite quand il
     * baisse, en un tampon quand il remonte (E4).
     */
    private var preampliCible = 1f

    /**
     * Applique la cascade en place à un tampon entrelacé de [trames] trames.
     *
     * Rien quand l'égaliseur est inactif : le tampon passe tel quel (E2).
     */
    fun traiter(tampon: FloatArray, trames: Int, canaux: Int, frequence: Float, reglages: ReglagesEgaliseur) {
        if (!reglages.actif || canaux <= 0 || trames <= 0) return
        if (reglages.bandesDb != bandesCalculees || frequence != frequenceCalculee) {
            recalculer(reglages.bandesDb, frequence)
        }
        if (memoires.size < canaux) {
            memoires = Array(canaux) { canal -> memoires.getOrNull(canal) ?: FloatArray(NOMBRE_DE_BANDES * 2) }
        }

        appliquerPreampli(tampon, trames, canaux, min(preampli, preampliCible), preampliCible)
        preampli = preampliCible

        for (trame in 0 until trames) {
            for (canal in 0 until canaux) {
                val i = trame * canaux + canal
                tampon[i] = cascade(tampon[i], memoires[canal])
            }
        }
    }

    /**
     * Oublie la mémoire des filtres, à un changement de format ou à la libération
     * du lecteur. **Pas** à une nouvelle piste ni à un saut (E5).
     */
    fun oublier() {
        memoires.forEach { it.fill(0f) }
    }

    private fun recalculer(bandesDb: List<Float>, frequence: Float) {
        for (bande in 0 until NOMBRE_DE_BANDES) {
            cloche(ReglagesEgaliseur.FREQUENCES_HZ[bande], frequence, bandesDb[bande], Q)
                .copyInto(coefficients, bande * COEFFS_PAR_BANDE)
        }
        preampliCible = preampliDeMarge(coefficients, frequence)
        frequenceCalculee = frequence
        bandesCalculees = bandesDb
    }

    /** Un échantillon à travers les six cloches. */
    private fun cascade(entree: Float, memoire: FloatArray): Float {
        var x = entree
        for (bande in 0 until NOMBRE_DE_BANDES) {
            val c = bande * COEFFS_PAR_BANDE
            val m = bande * 2
            val y = coefficients[c] * x + memoire[m]
            memoire[m] = coefficients[c + 1] * x - coefficients[c + 3] * y + memoire[m + 1]
            memoire[m + 1] = coefficients[c + 2] * x - coefficients[c + 4] * y
            x = y
        }
        return x
    }

    internal companion object {

        val NOMBRE_DE_BANDES = ReglagesEgaliseur.NOMBRE_DE_BANDES

        /**
         * Le Q de toutes les bandes. Environ 1/√2, soit une octave de large au
         * centre : assez pour que les bandes voisines se recouvrent, assez peu
         * pour qu'une coupe reste précise.
         */
        const val Q = 0.707f

        /**
         * L'atténuation de plus, en facteur linéaire (−1 dB), pour un réglage qui
         * amplifie.
         *
         * Le pic de la réponse borne un signal stable, pas un transitoire : un
         * filtre peut dépasser sur un motif net à pleine échelle. Le bureau a
         * mesuré le pire cas, +12 dB à 15 kHz sous un motif `1, −1, −1, 1…`, à
         * 0,9 dB au-dessus du pic de la réponse ; la marge couvre cette mesure
         * plutôt que la borne stricte, qui coûterait 2 à 4 dB à tout réglage.
         */
        const val MARGE_TRANSITOIRES = 0.891_250_9f

        private const val COEFFS_PAR_BANDE = 5
        private const val POINTS_DE_MESURE = 1024
        private const val MESURE_BASSE_HZ = 20.0
        private const val MESURE_HAUTE_HZ = 20_000.0
        private const val PART_DE_LA_FREQUENCE = 0.49
        private const val SEUIL_D_AMPLIFICATION = 1.001f
        private const val DIX = 10f
        private const val DB_PAR_DECADE_DE_A = 40f

        /**
         * Les coefficients d'une cloche selon RBJ, déjà divisés par `a0` pour que
         * la boucle par échantillon n'ait pas à diviser : `b0 b1 b2 a1 a2`.
         *
         * Une bande dont le centre n'est pas sous Nyquist rend l'identité : c'est
         * l'écart assumé de E1. Au-delà, `alpha` change de signe et le filtre
         * pourrait diverger.
         */
        fun cloche(frequenceHz: Float, frequence: Float, gainDb: Float, q: Float): FloatArray {
            if (frequence <= 0f || frequenceHz >= frequence / 2f) return floatArrayOf(1f, 0f, 0f, 0f, 0f)
            // RBJ : A = 10^(dB/40), la racine du gain linéaire au centre.
            val a = DIX.pow(gainDb / DB_PAR_DECADE_DE_A)
            val omega = 2f * PI.toFloat() * (frequenceHz / frequence)
            val sinW = sin(omega)
            val cosW = cos(omega)
            val alpha = sinW / (2f * q)

            val b0 = 1f + alpha * a
            val b1 = -2f * cosW
            val b2 = 1f - alpha * a
            val a0 = 1f + alpha / a
            val a1 = -2f * cosW
            val a2 = 1f - alpha / a
            return floatArrayOf(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
        }

        /**
         * Le gain maximal, linéaire, de toute la cascade sur la bande audible.
         *
         * La réponse de chaque cloche est évaluée sur le cercle unité et
         * multipliée, en 1024 points espacés logarithmiquement de 20 Hz à 20 kHz
         * (ou juste sous Nyquist), plus le centre de chaque bande, là où culmine
         * une amplification isolée. En double précision : le calcul se fait une
         * fois par changement de réglage ou de fréquence, jamais par échantillon.
         */
        fun picDeLaCascade(coefficients: FloatArray, frequence: Float): Float {
            val taux = frequence.toDouble()
            if (taux <= 0.0) return 1f
            val haut = min(MESURE_HAUTE_HZ, taux * PART_DE_LA_FREQUENCE)
            val bas = min(MESURE_BASSE_HZ, haut)
            val balayage = (0 until POINTS_DE_MESURE).map { i ->
                bas * (haut / bas).pow(i.toDouble() / (POINTS_DE_MESURE - 1))
            }
            val centres = ReglagesEgaliseur.FREQUENCES_HZ.map { it.toDouble() }.filter { it < taux / 2 }

            var pic = 0.0
            for (f in balayage + centres) {
                val w = 2.0 * PI * f / taux
                val c1 = cos(w)
                val s1 = sin(w)
                val c2 = cos(2.0 * w)
                val s2 = sin(2.0 * w)
                var gain = 1.0
                for (bande in 0 until NOMBRE_DE_BANDES) {
                    val o = bande * COEFFS_PAR_BANDE
                    val b0 = coefficients[o].toDouble()
                    val b1 = coefficients[o + 1].toDouble()
                    val b2 = coefficients[o + 2].toDouble()
                    val a1 = coefficients[o + 3].toDouble()
                    val a2 = coefficients[o + 4].toDouble()
                    val numRe = b0 + b1 * c1 + b2 * c2
                    val numIm = -(b1 * s1 + b2 * s2)
                    val denRe = 1.0 + a1 * c1 + a2 * c2
                    val denIm = -(a1 * s1 + a2 * s2)
                    gain *= sqrt((numRe * numRe + numIm * numIm) / (denRe * denRe + denIm * denIm))
                }
                pic = max(pic, gain)
            }
            return pic.toFloat()
        }

        /**
         * L'atténuation qui garde la cascade sous l'unité : `1 / pic`, moins la
         * marge des transitoires, pour un réglage qui amplifie ; exactement 1 pour
         * un réglage plat ou qui ne fait que couper.
         */
        fun preampliDeMarge(coefficients: FloatArray, frequence: Float): Float {
            val pic = picDeLaCascade(coefficients, frequence)
            // Une cascade plate calcule un cheveu au-dessus de 1 en flottant :
            // à 0,01 dB de l'unité, ce n'est pas une amplification.
            return if (pic > SEUIL_D_AMPLIFICATION) MARGE_TRANSITOIRES / pic else 1f
        }

        /**
         * Multiplie un tampon entrelacé par un gain qui va linéairement de
         * [debut] à [cible] au fil de ses trames, tous les canaux d'une trame
         * ensemble. Avec `debut == cible`, le gain est constant ; à 1, le tampon
         * n'est pas touché.
         */
        fun appliquerPreampli(tampon: FloatArray, trames: Int, canaux: Int, debut: Float, cible: Float) {
            if (trames <= 0 || (debut == 1f && cible == 1f)) return
            val pas = (cible - debut) / trames
            for (trame in 0 until trames) {
                val gain = if (debut == cible) cible else debut + pas * (trame + 1)
                for (canal in 0 until canaux) {
                    tampon[trame * canaux + canal] *= gain
                }
            }
        }

        private fun identite(coefficients: FloatArray) {
            for (bande in 0 until NOMBRE_DE_BANDES) coefficients[bande * COEFFS_PAR_BANDE] = 1f
        }
    }
}
