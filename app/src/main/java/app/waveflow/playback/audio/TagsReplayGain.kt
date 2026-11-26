package app.waveflow.playback.audio

import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import kotlin.math.abs

/**
 * Le ReplayGain que les tags d'un fichier déclarent, tels que l'extracteur de
 * Media3 les range dans le `Format.metadata` de la piste audio. Voir G1 et G3
 * de `docs/audio-avance.md`.
 *
 * La lecture des valeurs est celle du scanner du bureau
 * (`waveflow-core`, `scanner/replay_gain.rs`) ; seul change ce qui les porte :
 * le bureau passe par `lofty`, Android par les entrées de Media3.
 */
internal object TagsReplayGain {

    /**
     * Le gain déclaré par ces métadonnées, ou un [GainDePiste] vide.
     *
     * Trois porteurs, selon le conteneur : `TXXX` en ID3, commentaire Vorbis en
     * FLAC, Ogg et Opus, atome iTunes `----` en MP4. Les clés ne tiennent pas
     * compte de la casse.
     */
    fun lire(metadata: Metadata?): GainDePiste {
        if (metadata == null) return GainDePiste()
        val valeurs = HashMap<String, String>()
        for (index in 0 until metadata.length()) {
            val (cle, valeur) = cleEtValeur(metadata[index]) ?: continue
            // La première l'emporte, comme un lecteur de tags le fait d'un
            // champ répété.
            valeurs.putIfAbsent(cle.uppercase(), valeur)
        }
        return deValeurs(valeurs)
    }

    /**
     * Le gain que disent ces paires clé-valeur, clés en majuscules.
     *
     * `REPLAYGAIN_*` d'abord : c'est le cas courant, et un fichier qui porte les
     * deux a été marqué pour les lecteurs d'Opus **et** pour les autres. La
     * valeur textuelle est déjà sur notre échelle.
     */
    fun deValeurs(valeurs: Map<String, String>): GainDePiste = GainDePiste(
        gainDb = valeurs[TRACK_GAIN]?.let(::lireGainDb) ?: valeurs[R128_TRACK_GAIN]?.let(::lireGainR128Db),
        pic = valeurs[TRACK_PEAK]?.let(::lirePic),
        gainAlbumDb = valeurs[ALBUM_GAIN]?.let(::lireGainDb) ?: valeurs[R128_ALBUM_GAIN]?.let(::lireGainR128Db),
        picAlbum = valeurs[ALBUM_PEAK]?.let(::lirePic),
    )

    /**
     * Un gain textuel : un décimal signé, suivi ou non de `dB` dans n'importe
     * quelle casse, entouré ou non d'espaces. Écarté au-delà de
     * ±[GAIN_PLAUSIBLE_MAX_DB], ou plus long que [LONGUEUR_MAX].
     */
    fun lireGainDb(brut: String): Double? {
        val nettoye = brut.courte()?.trim() ?: return null
        val nombre = if (nettoye.endsWith("db", ignoreCase = true)) {
            nettoye.dropLast(2).trimEnd()
        } else {
            nettoye
        }
        return nombre.toDoubleDecimal()?.let(::gainPlausible)
    }

    /**
     * Un gain R128 : un entier Q7.8 en LU par rapport à −23 LUFS, rendu en dB par
     * rapport à −18 LUFS pour s'aligner sur le reste.
     */
    fun lireGainR128Db(brut: String): Double? {
        val q78 = brut.courte()?.trim()?.toIntOrNull() ?: return null
        val valeur = q78 / Q78 + R128_VERS_REPLAYGAIN_LU
        return valeur.takeIf { abs(it) <= GAIN_PLAUSIBLE_MAX_DB }
    }

    /**
     * Un pic linéaire. Zéro est écarté avec les négatifs : il donnerait une marge
     * infinie, et un fichier silencieux n'a de toute façon rien à limiter.
     */
    fun lirePic(brut: String): Double? = brut.courte()?.trim()?.toDoubleDecimal()?.let(::picPlausible)

    /**
     * Un gain, s'il est vraisemblable : fini et d'au plus ±[GAIN_PLAUSIBLE_MAX_DB].
     * Vaut aussi pour les champs du serveur, qui ne filtre que les valeurs non
     * finies (G2).
     */
    fun gainPlausible(db: Double): Double? = db.takeIf { it.isFinite() && abs(it) <= GAIN_PLAUSIBLE_MAX_DB }

    /** Un pic, s'il est vraisemblable : fini, strictement positif et au plus [PIC_PLAUSIBLE_MAX]. */
    fun picPlausible(pic: Double): Double? = pic.takeIf { it.isFinite() && it > 0.0 && it <= PIC_PLAUSIBLE_MAX }

    /**
     * La clé et la valeur d'une entrée qui peut porter un ReplayGain, `null`
     * pour toute autre.
     */
    private fun cleEtValeur(entree: Metadata.Entry): Pair<String, String>? = when (entree) {
        is TextInformationFrame ->
            entree.description
                ?.takeIf { entree.id == "TXXX" }
                ?.let { cle -> entree.values.firstOrNull()?.let { cle to it } }
        is VorbisComment -> entree.key to entree.value
        is InternalFrame -> entree.description to entree.text
        else -> null
    }

    /**
     * `toDoubleOrNull` ramené à ce qu'accepte le `f64::from_str` du bureau : un
     * signe, des chiffres, un point décimal, un exposant. Le Kotlin accepterait
     * aussi l'hexadécimal (`0x1p3`) et un suffixe de type (`2.5d`), que le bureau
     * refuse. `NaN` et `inf`, que les deux acceptent, ne passent pas ici ; le
     * bureau les écarte plus loin comme non finis, l'issue est la même.
     */
    private fun String.toDoubleDecimal(): Double? = takeIf(::estUnDecimal)?.toDoubleOrNull()

    /**
     * Ce texte s'écrit-il comme un décimal du bureau ? La partie entière et la
     * partie fractionnaire ne se disputent aucun chiffre : `\d+(\.\d*)?`, pas
     * `\d+\.?\d*`, où chaque découpage d'une suite de chiffres serait essayé
     * avant le refus — un temps quadratique en sa longueur.
     */
    internal fun estUnDecimal(texte: String): Boolean = DECIMAL.matches(texte)

    private val DECIMAL = Regex("""[+-]?(\d+(\.\d*)?|\.\d+)([eE][+-]?\d+)?""")

    /**
     * Ce texte, s'il est assez court pour être une valeur de ReplayGain. Un tag
     * n'a pas de longueur bornée et se lit sur le fil de lecture : un texte plus
     * long n'est pas un nombre qu'un outil aurait écrit, on ne le lit pas.
     */
    private fun String.courte(): String? = takeIf { it.length <= LONGUEUR_MAX }

    /**
     * La plus longue valeur lue, espaces et unité compris. Un double écrit en
     * entier, `-6.123456789012345 dB`, en compte 21.
     */
    internal const val LONGUEUR_MAX = 64

    /** Au-delà, c'est un tag cassé ou mal lu : on l'écarte plutôt que de le confier au mixeur. */
    private const val GAIN_PLAUSIBLE_MAX_DB = 60.0

    /**
     * Au-dessus, un pic n'est pas fort, il est dans une autre unité. Les pics
     * entre échantillons d'un master saturé dépassent légitimement 1.
     */
    private const val PIC_PLAUSIBLE_MAX = 4.0

    /** L'écart entre la référence ReplayGain 2.0 (−18 LUFS) et celle d'EBU R128 (−23 LUFS). */
    private const val R128_VERS_REPLAYGAIN_LU = 5.0

    /** Un LU en Q7.8. */
    private const val Q78 = 256.0

    private const val TRACK_GAIN = "REPLAYGAIN_TRACK_GAIN"
    private const val TRACK_PEAK = "REPLAYGAIN_TRACK_PEAK"
    private const val ALBUM_GAIN = "REPLAYGAIN_ALBUM_GAIN"
    private const val ALBUM_PEAK = "REPLAYGAIN_ALBUM_PEAK"
    private const val R128_TRACK_GAIN = "R128_TRACK_GAIN"
    private const val R128_ALBUM_GAIN = "R128_ALBUM_GAIN"
}
