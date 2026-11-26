package app.waveflow.suggestion

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Les mots de la bande, ceux du bureau en français (`home.suggestion` de
 * `fr.json`) : trois tournures par sorte, deux ou trois par raison, choisies
 * pour que la suggestion garde ses mots toute la journée (S15).
 */

/**
 * La tournure à prendre parmi [nombre], numérotées à partir de 1 (S15) : liée au
 * tirage et à la piste, positive même pour un identifiant négatif.
 */
internal fun tournure(jour: Long, essais: Int, songId: Long, sel: Int, nombre: Int): Int =
    Math.floorMod((jour + essais) * FACTEUR_TIRAGE + songId * FACTEUR_PISTE + sel, nombre.toLong()).toInt() + 1

/** « Vous l'adoriez » : la sorte, dans la voix éditoriale. */
fun libelleDeSorte(suggestion: Suggestion, jour: Long, essais: Int): String {
    val tournures = when (suggestion.sorte) {
        SorteDeSuggestion.Oubliee -> SORTE_OUBLIEE
        SorteDeSuggestion.DansLEsprit -> SORTE_DANS_L_ESPRIT
        SorteDeSuggestion.JamaisEcoutee -> SORTE_JAMAIS_ECOUTEE
    }
    return tournures[tournure(jour, essais, suggestion.song.id, SEL_SORTE, tournures.size) - 1]
}

/**
 * La ligne qui dit pourquoi, ou nulle quand la sorte n'a pas de quoi la dire :
 * un fichier que le MediaStore ne date pas, par exemple (S7).
 *
 * @param fuseau celui de l'utilisateur, pour le mois et la date affichés.
 */
fun raisonDe(suggestion: Suggestion, jour: Long, essais: Int, fuseau: ZoneId): String? {
    val choix = Choix(jour, essais, suggestion.song.id)
    val raison = suggestion.raison
    return when (suggestion.sorte) {
        SorteDeSuggestion.Oubliee -> raison.ecoutes?.let { ecoutes ->
            val quand = raison.derniereEcouteMs?.let { MOIS_ANNEE.enDate(it, fuseau) }.orEmpty()
            choix.parmi(RAISONS_OUBLIEE, SEL_OUBLIEE)(ecoutes, quand)
        }
        SorteDeSuggestion.DansLEsprit -> raison.artisteAncre?.let {
            choix.parmi(RAISONS_DANS_L_ESPRIT, SEL_DANS_L_ESPRIT)(it)
        }
        SorteDeSuggestion.JamaisEcoutee -> {
            val artiste = suggestion.song.artist.takeIf { suggestion.song.artisteConnu != null }
            val ajoutee = raison.ajouteeMs?.takeIf { it > 0L }
            when {
                (raison.ecoutesArtiste ?: 0L) > 0L && artiste != null ->
                    choix.parmi(RAISONS_ARTISTE_ECOUTE, SEL_JAMAIS_ARTISTE)(artiste)
                ajoutee != null -> choix.parmi(RAISONS_AJOUTEE, SEL_JAMAIS_AJOUT)(DATE_LONGUE.enDate(ajoutee, fuseau))
                else -> null
            }
        }
    }
}

/** Les tournures d'une même suggestion, prises toutes par la même formule (S15). */
private class Choix(private val jour: Long, private val essais: Int, private val songId: Long) {
    fun <T> parmi(tournures: List<T>, sel: Int): T = tournures[tournure(jour, essais, songId, sel, tournures.size) - 1]
}

private fun DateTimeFormatter.enDate(ms: Long, fuseau: ZoneId): String = format(Instant.ofEpochMilli(ms).atZone(fuseau))

/** Les deux raisons d'une oubliée : le nombre d'écoutes, puis le mois de la dernière. */
private val RAISONS_OUBLIEE: List<(Int, String) -> String> = listOf(
    { n, quand -> "$n ${if (n <= 1) "écoute" else "écoutes"} · dernière écoute en $quand" },
    { n, quand -> "Écouté $n fois, la dernière en $quand" },
)

private val RAISONS_DANS_L_ESPRIT: List<(String) -> String> = listOf(
    { artiste -> "Parce que vous écoutez $artiste en ce moment" },
    { artiste -> "Si $artiste vous plaît en ce moment, essayez ceci" },
    { artiste -> "Dans l'esprit de $artiste" },
)

private val RAISONS_ARTISTE_ECOUTE: List<(String) -> String> = listOf(
    { artiste -> "De $artiste, que vous écoutez souvent" },
    { artiste -> "Vous aimez $artiste, mais n'avez pas encore écouté ce titre" },
)

private val RAISONS_AJOUTEE: List<(String) -> String> = listOf(
    { le -> "Ajouté le $le, jamais lancé" },
    { le -> "Dans votre bibliothèque depuis le $le, toujours pas écouté" },
)

private val SORTE_OUBLIEE = listOf("Vous l'adoriez", "Un favori un peu oublié", "Ça vous revient ?")
private val SORTE_DANS_L_ESPRIT =
    listOf("Dans la lignée de vos écoutes", "Pile dans votre humeur", "Dans le ton du moment")
private val SORTE_JAMAIS_ECOUTEE = listOf("Jamais écouté", "Il vous attend dans la bibliothèque", "Une première écoute")

/** « novembre 2026 », comme le mois long et l'année du bureau. */
private val MOIS_ANNEE = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH)

/** « 26 novembre 2026 », la date longue du bureau. */
private val DATE_LONGUE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)

private const val FACTEUR_TIRAGE = 7L
private const val FACTEUR_PISTE = 13L
private const val SEL_SORTE = 0
private const val SEL_OUBLIEE = 1
private const val SEL_DANS_L_ESPRIT = 2
private const val SEL_JAMAIS_ARTISTE = 3
private const val SEL_JAMAIS_AJOUT = 4
