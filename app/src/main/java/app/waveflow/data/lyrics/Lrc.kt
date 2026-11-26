package app.waveflow.data.lyrics

import app.waveflow.model.Lyrics
import app.waveflow.model.SyncedLine

/**
 * Lecture d'un texte de paroles : LRC s'il porte des horodatages, texte simple
 * sinon (`docs/paroles.md`, P9 et P10).
 *
 * Le même lecteur sert au `.lrc` voisin, aux paroles embarquées — un tag
 * `USLT` ou `LYRICS` contient souvent un LRC entier — et à tout texte venu
 * d'ailleurs : la forme du texte décide, pas sa provenance.
 */
object Lrc {

    /**
     * Les paroles que porte [text], ou `null` s'il n'en porte aucune — vide,
     * blanc, ou fait de balises seulement.
     *
     * Un texte de plus de [TEXTE_MAX_CARACTERES] n'en porte pas non plus : un
     * tag embarqué n'a pas de taille plafonnée, et le `.lrc` voisin s'arrête
     * déjà à 1 Mio (P3).
     */
    fun parse(text: String): Lyrics? {
        if (text.length > TEXTE_MAX_CARACTERES) return null
        var decalageMs = 0L
        val synchronisees = mutableListOf<SyncedLine>()
        val simples = mutableListOf<String>()

        for (brute in text.removePrefix(BOM).lines()) {
            when (val ligne = lire(brute.trim())) {
                is Ligne.Horodatee -> ligne.instants.forEach { synchronisees += SyncedLine(it, ligne.texte) }
                is Ligne.Balise -> ligne.decalageMs?.let { decalageMs = it }
                is Ligne.Texte -> simples += ligne.texte
            }
        }

        if (synchronisees.any { it.text.isNotEmpty() }) {
            // Un décalage positif avance les paroles : chaque ligne commence
            // plus tôt. `sortedBy` est stable, l'ordre du texte départage.
            return Lyrics.Synced(
                synchronisees
                    .map { it.copy(startMs = (it.startMs - decalageMs).coerceAtLeast(0L)) }
                    .sortedBy { it.startMs },
            )
        }

        val texte = simples.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }
        return if (texte.isEmpty()) null else Lyrics.Plain(texte)
    }

    /** Ce qu'est une ligne du texte, une fois ses horodatages de tête lus. */
    private sealed interface Ligne {
        class Horodatee(val instants: List<Long>, val texte: String) : Ligne

        /** Une balise d'en-tête ; seul `offset` compte. */
        class Balise(val decalageMs: Long?) : Ligne

        class Texte(val texte: String) : Ligne
    }

    /**
     * Les horodatages de tête se lisent en avançant un index dans la ligne,
     * sans jamais recopier ce qui reste : une ligne de milliers d'horodatages
     * se lit en un passage.
     */
    private fun lire(ligne: String): Ligne {
        val instants = mutableListOf<Long>()
        var debut = 0
        var horodatage = HORODATAGE.matchAt(ligne, debut)
        while (horodatage != null) {
            instants += horodatage.enMillisecondes()
            debut = horodatage.range.last + 1
            while (debut < ligne.length && ligne[debut].isWhitespace()) debut++
            horodatage = HORODATAGE.matchAt(ligne, debut)
        }
        if (instants.isNotEmpty()) {
            return Ligne.Horodatee(instants, ligne.substring(debut).replace(HORODATAGE_DE_MOT, "").trim())
        }

        val balise = BALISE.matchEntire(ligne)
            ?.takeIf { it.groupValues[1].lowercase() in BALISES_LRC }
            ?: return Ligne.Texte(ligne)
        val (nom, valeur) = balise.destructured
        return Ligne.Balise(valeur.trim().toLongOrNull()?.takeIf { nom.equals("offset", ignoreCase = true) })
    }

    private fun MatchResult.enMillisecondes(): Long {
        val (minutes, secondes, fraction) = destructured
        // La fraction se lit en dixièmes, centièmes ou millièmes selon ses chiffres.
        val millis = fraction.padEnd(MILLIS_DIGITS, '0').toLongOrNull() ?: 0L
        return (minutes.toLong() * SECONDS_PER_MINUTE + secondes.toLong()) * MILLIS_PER_SECOND + millis
    }

    private val BOM = Char(BOM_CODE).toString()

    /**
     * `[mm:ss]`, `[mm:ss.x]` à `[mm:ss.xxx]`, `:` au lieu du point. Sans ancre :
     * `matchAt` le cherche exactement là où la lecture en est.
     */
    private val HORODATAGE = Regex("""\[(\d{1,4}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    /** Le mot chanté du LRC enrichi : retiré, la ligne seule est suivie. */
    private val HORODATAGE_DE_MOT = Regex("""<\d{1,4}:\d{1,2}(?:[.:]\d{1,3})?>""")

    /**
     * Une balise d'en-tête, seule sur sa ligne : `[ar:…]`, `[offset:+250]`…
     * Seuls les noms du format comptent ([BALISES_LRC]) : `[Chorus: Kanye West]`,
     * qu'on trouve dans des paroles recopiées de Genius, est du texte, et reste.
     */
    private val BALISE = Regex("""^\[([A-Za-z#]+):([^\]]*)]$""")

    /** Les balises d'en-tête du format LRC, en minuscules. */
    private val BALISES_LRC =
        setOf("ar", "ti", "al", "au", "by", "re", "ve", "la", "length", "offset", "tool", "id", "#")
}

/** La marque d'ordre d'octets, que certains éditeurs laissent en tête de texte. */
internal const val BOM_CODE = 0xFEFF

/** La taille au-delà de laquelle un texte n'est pas lu : 1 Mi caractères, comme le `.lrc` voisin. */
internal const val TEXTE_MAX_CARACTERES = 1 shl 20

private const val MILLIS_DIGITS = 3
private const val SECONDS_PER_MINUTE = 60L
private const val MILLIS_PER_SECOND = 1_000L
