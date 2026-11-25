package app.waveflow.model

import kotlin.math.roundToInt

/**
 * Lequel des deux gains d'un fichier appliquer. Voir `docs/audio-avance.md`, G6.
 *
 * Écrit par son nom, comme le thème : renommer une entrée perdrait le choix de
 * qui l'avait fait. Un nom inconnu relit [Automatique] (A3), comme
 * `GainMode::from_setting` sur le bureau : un réglage que personne ne sait lire
 * reste un réglage qu'on écoute.
 */
enum class ModeDeGain(val label: String, val resume: String) {
    Automatique("Automatique", "Gain d'album quand un disque s'écoute en entier, gain du titre sinon"),
    ParTitre("Par titre", "Normalise le volume de chaque titre par rapport aux autres"),
    ParAlbum("Par album", "Un seul gain par disque, qui préserve ses écarts de niveau internes"),
}

/**
 * Les réglages du ReplayGain, tels que l'utilisateur les a choisis.
 *
 * Les défauts sont ceux du bureau, sauf [actif] : la décision du 25/11 le veut
 * actif d'emblée, en mode [ModeDeGain.Automatique] (A2).
 *
 * @property preampliDb ajoutée au gain de chaque titre : la référence
 *   ReplayGain (−18 LUFS) est plus basse que la plupart des systèmes.
 * @property gainParDefautDb appliqué aux titres dont on ne connaît aucun gain.
 * @property eviterSaturation plafonne le gain à la marge que laisse le pic.
 */
data class ReglagesReplayGain(
    val actif: Boolean = true,
    val mode: ModeDeGain = ModeDeGain.Automatique,
    val preampliDb: Float = 0f,
    val gainParDefautDb: Float = 0f,
    val eviterSaturation: Boolean = true,
) {

    /** Les mêmes réglages, ajustements ramenés dans leurs bornes. */
    fun bornes(): ReglagesReplayGain = copy(
        preampliDb = bornerAjustement(preampliDb),
        gainParDefautDb = bornerAjustement(gainParDefautDb),
    )

    companion object {
        /** La plage des deux ajustements, celle du bureau (`REPLAYGAIN_ADJUST_LIMIT_DB`). */
        const val AJUSTEMENT_MAX_DB = 15f

        /** Le pas des deux curseurs, celui du bureau. */
        const val PAS_DB = 0.5f

        /**
         * Ramène un ajustement dans ±[AJUSTEMENT_MAX_DB], et rend 0 de ce qui
         * n'est pas un nombre (A3) : `coerceIn` laisserait passer un `NaN`, qui
         * empoisonnerait chaque échantillon. Le bureau fait de même
         * (`clamp_replaygain_adjust`).
         */
        fun bornerAjustement(db: Float): Float =
            if (db.isFinite()) db.coerceIn(-AJUSTEMENT_MAX_DB, AJUSTEMENT_MAX_DB) else 0f
    }
}

/**
 * Les réglages de l'égaliseur : actif ou non, et le gain de chacune des six
 * bandes. Voir `docs/audio-avance.md`, E1 à E7.
 *
 * Inactif et plat par défaut (E2), comme sur le bureau.
 *
 * @property bandesDb un gain par bande, dans l'ordre de [FREQUENCES_HZ], au
 *   dixième de dB et borné à ±[GAIN_MAX_DB]. Toujours six : la construction le
 *   garantit, voir [de].
 * @property prereglage le préréglage dont viennent les bandes, `null` pour un
 *   réglage personnalisé.
 */
class ReglagesEgaliseur private constructor(
    val actif: Boolean,
    val bandesDb: List<Float>,
    val prereglage: Prereglage?,
) {

    /** Le même réglage, actif ou non. */
    fun avecActif(actif: Boolean): ReglagesEgaliseur = ReglagesEgaliseur(actif, bandesDb, prereglage)

    /** Une bande déplacée à la main : le réglage devient personnalisé (E6). */
    fun avecBande(index: Int, gainDb: Float): ReglagesEgaliseur {
        if (index !in bandesDb.indices) return this
        val bandes = bandesDb.toMutableList().apply { this[index] = bornerBande(gainDb) }
        return ReglagesEgaliseur(actif, bandes, prereglage = null)
    }

    /** Les six valeurs d'un préréglage, qui devient le courant (E6). */
    fun avecPrereglage(choisi: Prereglage): ReglagesEgaliseur =
        ReglagesEgaliseur(actif, choisi.bandesDb.map(::bornerBande), choisi)

    override fun equals(other: Any?): Boolean =
        other is ReglagesEgaliseur &&
            other.actif == actif &&
            other.bandesDb == bandesDb &&
            other.prereglage == prereglage

    override fun hashCode(): Int = (actif.hashCode() * 31 + bandesDb.hashCode()) * 31 + prereglage.hashCode()

    override fun toString(): String = "ReglagesEgaliseur(actif=$actif, bandesDb=$bandesDb, prereglage=$prereglage)"

    companion object {
        /** Les centres des six bandes, ceux du bureau (`BAND_FREQS`). */
        val FREQUENCES_HZ = listOf(60f, 150f, 400f, 1000f, 2400f, 15000f)

        /** Le nombre de bandes. */
        val NOMBRE_DE_BANDES = FREQUENCES_HZ.size

        /** La course de chaque curseur, en valeur absolue, celle du bureau (`MAX_GAIN_DB`). */
        const val GAIN_MAX_DB = 12f

        /** L'égaliseur par défaut : inactif et plat. */
        val PAR_DEFAUT = ReglagesEgaliseur(false, List(NOMBRE_DE_BANDES) { 0f }, Prereglage.Plat)

        /**
         * Un réglage depuis ses valeurs, bornées et arrondies au dixième de dB.
         *
         * Un nombre de bandes qui n'est pas six rend l'égaliseur plat (A3) : c'est
         * le cas d'un fichier écrit par une version qui en aurait d'autres, et
         * deviner quelle valeur va à quelle fréquence serait pire que n'en
         * appliquer aucune.
         */
        fun de(actif: Boolean, bandesDb: List<Float>, prereglage: Prereglage?): ReglagesEgaliseur =
            if (bandesDb.size != NOMBRE_DE_BANDES) {
                ReglagesEgaliseur(actif, PAR_DEFAUT.bandesDb, Prereglage.Plat)
            } else {
                ReglagesEgaliseur(actif, bandesDb.map(::bornerBande), prereglage)
            }

        /**
         * Ramène un gain de bande dans ±[GAIN_MAX_DB], au dixième de dB, et rend
         * 0 de ce qui n'est pas un nombre.
         *
         * Le dixième est la précision du bureau, qui range ses bandes en dixièmes
         * de dB dans un entier.
         */
        fun bornerBande(db: Float): Float {
            if (!db.isFinite()) return 0f
            return (db.coerceIn(-GAIN_MAX_DB, GAIN_MAX_DB) * DIXIEMES).roundToInt() / DIXIEMES
        }

        private const val DIXIEMES = 10f
    }
}

/**
 * Les préréglages du bureau (`PRESETS`), mêmes valeurs et mêmes noms (E6).
 *
 * Écrits par leur [cle], celle du bureau : un renommage de l'entrée ne perd pas
 * le choix enregistré.
 */
enum class Prereglage(val cle: String, val label: String, val bandesDb: List<Float>) {
    Plat("flat", "Plat", listOf(0f, 0f, 0f, 0f, 0f, 0f)),
    Acoustique("acoustic", "Acoustique", listOf(5f, 4.5f, 3.5f, 1f, 1.5f, 3f)),
    AmplificationDesGraves("bass_booster", "Amplification des graves", listOf(6f, 5f, 3f, 0f, 0f, 0f)),
    ReductionDesGraves("bass_reducer", "Réduction des graves", listOf(-6f, -5f, -3f, 0f, 0f, 0f)),
    Classique("classical", "Classique", listOf(5f, 4f, -2f, -2f, 0f, 3f)),
    Dance("dance", "Dance", listOf(6f, 5f, 1f, 2f, 4f, 5f)),
    Profond("deep", "Profond", listOf(5f, 3f, 1f, 2f, -1f, -3f)),
    Electronique("electronic", "Électronique", listOf(4f, 3f, -2f, 2f, 1f, 5f)),
    HipHop("hip_hop", "Hip-Hop", listOf(5f, 4f, 1f, 1f, -1f, 2f)),
    Jazz("jazz", "Jazz", listOf(4f, 3f, 1f, 2f, 3f, 4f)),
    Latin("latin", "Latin", listOf(5f, 3f, 0f, 0f, 3f, 4f)),
    IntensiteSonore("loudness", "Intensité sonore", listOf(6f, 3f, 0f, 3f, 3f, -2f)),
    Lounge("lounge", "Lounge", listOf(-3f, 1f, 4f, 5f, 2f, -1f)),
    Piano("piano", "Piano", listOf(3f, 2f, 0f, 3f, 4f, 3f)),
    Pop("pop", "Pop", listOf(-1f, 2f, 5f, 4f, 1f, -2f)),
    RnB("rnb", "R&B", listOf(3f, 5f, 4f, 1f, 2f, 3f)),
    Rock("rock", "Rock", listOf(5f, 4f, 3f, -1f, 1f, 5f)),
    PetitsHautParleurs("small_speakers", "Petits haut-parleurs", listOf(5f, 4f, 3f, 2f, 1f, 0f)),
    VoixParlee("spoken_word", "Voix parlée", listOf(-3f, -2f, 0f, 5f, 4f, 0f)),
    AmplificationDesAigus("treble_booster", "Amplification des aigus", listOf(0f, 0f, 0f, 3f, 5f, 6f)),
    ;

    companion object {
        /** Le préréglage de cette clé, `null` si elle n'en désigne aucun. */
        fun deCle(cle: String?): Prereglage? = entries.firstOrNull { it.cle == cle }
    }
}
