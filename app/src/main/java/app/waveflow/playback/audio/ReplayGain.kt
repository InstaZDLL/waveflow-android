package app.waveflow.playback.audio

import app.waveflow.model.ModeDeGain
import app.waveflow.model.ReglagesReplayGain
import kotlin.math.log10
import kotlin.math.pow

/**
 * Ce qui transforme ce qu'on sait du volume d'une piste en un facteur à
 * appliquer à ses échantillons.
 *
 * Porté de `waveflow-core`, `playback/replay_gain.rs` (`effective_gain_db`,
 * `effective_linear`), pour que le son soit celui du bureau. Les règles sont G5
 * et G6 de `docs/audio-avance.md`.
 *
 * Du calcul pur, sur le fil de lecture, une fois par tampon : bouger la
 * pré-amplification s'entend tout de suite, pas à la piste suivante.
 */
internal object ReplayGain {

    /**
     * Plafond du gain appliqué, en dB. La protection contre la saturation garde
     * d'ordinaire les gains positifs bien en dessous ; ce plafond est ce qui se
     * tient entre un tag corrompu et une paire d'enceintes.
     */
    const val GAIN_TOTAL_MAX_DB = 12.0

    /** Plancher, en dB : en deçà, c'est un tag cassé, pas une piste calme. */
    const val GAIN_TOTAL_MIN_DB = -30.0

    /**
     * Le gain à appliquer, en dB.
     *
     * Séparé de [lineaire] pour que la décision s'éprouve en dB, l'unité de tout
     * ce qui touche au ReplayGain.
     */
    fun gainEffectifDb(piste: GainDePiste, reglages: ReglagesReplayGain, ecoute: Ecoute): Double {
        if (!reglages.actif) return 0.0

        // La paire voulue par le mode, choisie d'abord : rien en dessous ne
        // peut mélanger un gain d'album et un pic de titre.
        val (gainDb, pic) = piste.paire(reglages.mode, ecoute)

        // Une piste sans gain prend le gain par défaut plutôt que l'unité : une
        // bibliothèque à moitié normalisée sonne pire qu'une qui l'est toute
        // également mal. Un `NaN` est filtré ici et non borné ensuite :
        // `coerceIn` le laisserait passer.
        val base = gainDb?.takeIf { it.isFinite() } ?: reglages.gainParDefautDb.toDouble()

        // Borner d'abord, plafonner ensuite. L'ordre inverse laisserait le
        // plancher défaire le plafond : un pic demandant plus de 30 dB
        // d'atténuation serait remonté au-dessus de sa propre marge.
        var gain = (base + reglages.preampliDb).coerceIn(GAIN_TOTAL_MIN_DB, GAIN_TOTAL_MAX_DB)

        if (reglages.eviterSaturation) {
            pic?.takeIf { it.isFinite() && it > 0.0 }?.let { valide ->
                // Le gain qui amène l'échantillon le plus fort à pleine échelle :
                // négatif pour une piste qui sature déjà. `min` ne fait que
                // baisser, un pic minuscule ne peut donc pas devenir une
                // amplification.
                gain = minOf(gain, -VINGT * log10(valide))
            }
        }

        return gain
    }

    /**
     * Le facteur linéaire de la multiplication. Exactement `1` quand il n'y a
     * rien à faire, ce qui permet à l'appelant de ne pas parcourir le tampon.
     */
    fun lineaire(piste: GainDePiste, reglages: ReglagesReplayGain, ecoute: Ecoute): Float {
        val db = gainEffectifDb(piste, reglages, ecoute)
        if (db == 0.0) return 1f
        val facteur = DIX.pow(db / VINGT)
        return if (facteur.isFinite()) facteur.toFloat() else 1f
    }

    private const val VINGT = 20.0
    private const val DIX = 10.0
}

/**
 * Comment la piste qu'on traite est écoutée : la seule entrée du mode
 * [ModeDeGain.Automatique]. Voir G7, et [ecouteDeLaFile].
 *
 * Une énumération plutôt qu'un booléen, comme `Listening` sur le bureau :
 * `lineaire(piste, reglages, true)` ne dirait pas ce qui est vrai.
 */
internal enum class Ecoute {
    /** Un disque joué en entier, dans son ordre. */
    UnDisque,

    /** Un titre parmi d'autres sans rapport. */
    UnTitre,
}

/**
 * Ce qu'on sait du volume d'une piste, d'où que ça vienne : tags du fichier ou
 * champs du serveur (G1, G2). Les quatre valeurs sont indépendantes.
 *
 * @property gainDb le gain du titre, en dB sur l'échelle ReplayGain 2.0
 *   (−18 LUFS).
 * @property pic le pic du titre, linéaire. Au-dessus de 1 sur un master saturé.
 * @property gainAlbumDb le gain du **disque entier**, même échelle.
 * @property picAlbum le pic du disque, linéaire : l'échantillon le plus fort de
 *   sa piste la plus forte.
 */
internal data class GainDePiste(
    val gainDb: Double? = null,
    val pic: Double? = null,
    val gainAlbumDb: Double? = null,
    val picAlbum: Double? = null,
) {

    /**
     * Chaque valeur de celle-ci, et à défaut celle de [autre].
     *
     * Champ par champ (G2) : un serveur qui connaît le gain d'une piste mais pas
     * son pic laisse la protection contre la saturation au tag du flux reçu.
     */
    fun ouSinon(autre: GainDePiste): GainDePiste = GainDePiste(
        gainDb = gainDb ?: autre.gainDb,
        pic = pic ?: autre.pic,
        gainAlbumDb = gainAlbumDb ?: autre.gainAlbumDb,
        picAlbum = picAlbum ?: autre.picAlbum,
    )

    /**
     * La paire gain et pic que demande le mode, selon l'écoute (G6).
     *
     * Deux replis, chacun pour sa raison : sans gain d'album, celui du titre —
     * sans quoi le mode album ne ferait rien sur un fichier non marqué ; sans
     * pic d'album, celui du titre — un plafond inégal est un défaut d'aspect,
     * une saturation s'entend.
     */
    fun paire(mode: ModeDeGain, ecoute: Ecoute): Pair<Double?, Double?> {
        val veutAlbum = when (mode) {
            ModeDeGain.ParTitre -> false
            ModeDeGain.ParAlbum -> true
            ModeDeGain.Automatique -> ecoute == Ecoute.UnDisque
        }
        // Un gain d'album non fini compte pour absent : le choisir ferait
        // tomber sur le gain par défaut, et non sur le gain de titre, valable,
        // de ce fichier.
        val album = gainAlbumDb?.takeIf { veutAlbum && it.isFinite() } ?: return gainDb to pic
        // Le pic **d'album**, le même pour toutes les pistes du disque. Plafonner
        // chacune par le sien réintroduirait les écarts de piste à piste que le
        // mode album existe pour supprimer.
        return album to (picAlbum ?: pic)
    }
}

/**
 * Écoute-t-on un disque en entier ? Voir G7.
 *
 * Le bureau le lit sur la source de sa file ; Android ne la garde pas, la file
 * le dit d'elle-même : l'aléatoire coupé, au moins deux pistes, toutes du même
 * album.
 *
 * @param albums l'album de chaque piste de la file, `null` quand on l'ignore.
 *   Un identifiant, jamais un titre : deux disques peuvent s'appeler *Greatest
 *   Hits*.
 */
internal fun ecouteDeLaFile(albums: List<String?>, aleatoire: Boolean): Ecoute {
    if (aleatoire || albums.size < 2) return Ecoute.UnTitre
    val premier = albums.first() ?: return Ecoute.UnTitre
    return if (albums.all { it == premier }) Ecoute.UnDisque else Ecoute.UnTitre
}
