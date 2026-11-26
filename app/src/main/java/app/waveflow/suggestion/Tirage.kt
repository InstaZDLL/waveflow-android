package app.waveflow.suggestion

/*
 * L'arithmétique de la suggestion du jour, portée de `waveflow_core::home_suggestion`
 * du bureau : la rotation des sortes, le tirage, le plafond par artiste. À part
 * des données pour s'éprouver contre des valeurs — les mêmes que celles du
 * bureau. Les règles citées sont celles de `docs/suggestion-du-jour.md`.
 */

/**
 * Les sortes à essayer pour le jour [jour] après [essais] appuis sur « Une autre
 * suggestion », la première d'abord (S1). Les deux autres servent de repli quand
 * elle n'a rien à offrir.
 *
 * @param jour les jours depuis l'époque de la date locale (S2).
 * @param essais positif ou nul.
 */
internal fun ordreDesSortes(jour: Long, essais: Int): List<SorteDeSuggestion> {
    val sortes = SorteDeSuggestion.entries
    val debut = Math.floorMod(jour + essais, sortes.size.toLong()).toInt()
    return List(sortes.size) { decalage -> sortes[(debut + decalage) % sortes.size] }
}

/**
 * Le rang tiré parmi [taille] pistes de la courte liste (S3) : toujours le même
 * pour le même jour, le même nombre d'appuis et la même sorte. Zéro pour une
 * liste vide, comme le bureau.
 *
 * En non signé sur 64 bits, multiplications et additions comprises, pour tomber
 * au bit près sur `pick_index` : le jour s'y lit en complément à deux, comme le
 * `day as u64` de Rust.
 */
internal fun rangTire(taille: Int, jour: Long, essais: Int, sorte: SorteDeSuggestion): Int {
    if (taille <= 0) return 0
    val graine = jour.toULong() * MELANGE_JOUR +
        essais.toUInt().toULong() * MELANGE_ESSAI +
        sorte.ordinal.toULong()
    return (splitmix64(graine) % taille.toULong()).toInt()
}

/** Le mélangeur `splitmix64`, tel que le bureau l'écrit. */
internal fun splitmix64(entree: ULong): ULong {
    var z = entree + MELANGE_JOUR
    z = (z xor (z shr DECALAGE_1)) * MELANGE_ESSAI
    z = (z xor (z shr DECALAGE_2)) * MELANGE_FINAL
    return z xor (z shr DECALAGE_3)
}

/**
 * Les [limite] premiers éléments de [classes], les meilleurs d'abord, en gardant
 * au plus [parArtiste] par artiste (S4). Un élément sans artiste n'est jamais
 * plafonné.
 */
internal fun <T> courteListe(
    classes: Iterable<T>,
    artisteDe: (T) -> Long?,
    parArtiste: Int,
    limite: Int,
): List<T> {
    val pris = HashMap<Long, Int>()
    val retenus = ArrayList<T>(minOf(limite, TAILLE_INITIALE))
    for (element in classes) {
        if (retenus.size >= limite) break
        val artiste = artisteDe(element)
        val deja = artiste?.let { pris[it] } ?: 0
        if (artiste == null || deja < parArtiste) {
            if (artiste != null) pris[artiste] = deja + 1
            retenus += element
        }
    }
    return retenus
}

private const val MELANGE_JOUR = 0x9E3779B97F4A7C15uL
private const val MELANGE_ESSAI = 0xBF58476D1CE4E5B9uL
private const val MELANGE_FINAL = 0x94D049BB133111EBuL
private const val DECALAGE_1 = 30
private const val DECALAGE_2 = 27
private const val DECALAGE_3 = 31

/** Une courte liste en a vingt au plus : inutile de réserver davantage. */
private const val TAILLE_INITIALE = 32
