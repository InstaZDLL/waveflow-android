package app.waveflow.model

import java.text.Normalizer

/**
 * Recherche dans la bibliothèque déjà chargée.
 *
 * Tout se fait en mémoire, contre le [Library] du store partagé : aucune
 * requête MediaStore supplémentaire, et les résultats suivent d'eux-mêmes un
 * rechargement de la bibliothèque.
 */

/** Résultats groupés par type, dans l'ordre où l'écran les présente. */
data class SearchResults(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
) {
    val isEmpty: Boolean
        get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty()
}

/**
 * Filtre la bibliothèque sur [query].
 *
 * Une requête vide ne renvoie rien plutôt que tout : l'écran affiche alors son
 * invite, il n'a pas à recopier l'onglet Titres.
 *
 * Un morceau ressort par son titre, son artiste ou son album, pour que
 * chercher « nuit » trouve aussi les pistes de l'album Nuit.
 */
fun Library.search(query: String): SearchResults {
    val key = query.searchKey()
    if (key.isEmpty()) return SearchResults()

    return SearchResults(
        songs = songs.rankedBy(key) { listOf(it.title, it.displayArtist, it.displayAlbum) },
        albums = albums.rankedBy(key) { listOf(it.title, it.displayArtist) },
        artists = artists.rankedBy(key) { listOf(it.name) },
    )
}

/**
 * Garde les éléments dont l'un des [fields] contient [key], les préfixes
 * d'abord.
 *
 * `sortedBy` est stable : à rang égal, l'ordre alphabétique de la liste
 * d'origine est conservé.
 */
private fun <T> List<T>.rankedBy(key: String, fields: (T) -> List<String>): List<T> =
    mapNotNull { item ->
        val rank = fields(item).minOfOrNull { it.searchKey().matchRank(key) } ?: NO_MATCH
        if (rank == NO_MATCH) null else item to rank
    }
        .sortedBy { (_, rank) -> rank }
        .map { (item, _) -> item }

/** Un préfixe est un meilleur résultat qu'une occurrence au milieu du texte. */
private fun String.matchRank(key: String): Int = when {
    startsWith(key) -> 0
    contains(key) -> 1
    else -> NO_MATCH
}

/**
 * Forme comparable d'un texte : sans casse ni accents, pour que « bebe »
 * trouve « Bébé » et l'inverse.
 *
 * La décomposition NFD sépare la lettre de son accent, qui devient alors un
 * caractère combinant retirable.
 */
private fun String.searchKey(): String =
    Normalizer.normalize(trim(), Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase()

private val COMBINING_MARKS = Regex("\\p{InCombiningDiacriticalMarks}+")

private const val NO_MATCH = Int.MAX_VALUE
