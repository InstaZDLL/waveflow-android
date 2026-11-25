package app.waveflow.model

/*
 * Ce qu'une demande vocale — « joue Nevermind » — fait jouer.
 *
 * Les règles sont dans `docs/recherche-vocale.md` ; les renvois R4 à R9
 * ci-dessous y mènent. Tout passe par le moteur de l'écran de recherche
 * ([search]) et sa forme comparable ([searchKey]) : la voiture et le téléphone
 * trouvent ce que l'écran trouverait.
 */

/** Ce que l'utilisateur a dit chercher, quand l'hôte le précise. */
enum class VoiceFocus {
    Album,
    Artist,
    Song,

    /** Pas de précision, ou une que la recherche ne couvre pas (playlist, genre). */
    Unspecified,
}

/**
 * Une demande vocale, détachée de la forme sous laquelle elle arrive.
 *
 * @property query la phrase entière, seule garantie ; vide pour « mets de la
 *   musique ».
 * @property album le nom d'album prononcé, si l'hôte l'a isolé.
 * @property artist le nom d'artiste prononcé, si l'hôte l'a isolé.
 * @property title le titre de morceau prononcé, si l'hôte l'a isolé.
 */
data class VoiceRequest(
    val query: String,
    val focus: VoiceFocus = VoiceFocus.Unspecified,
    val album: String? = null,
    val artist: String? = null,
    val title: String? = null,
)

/**
 * Les morceaux à mettre dans le lecteur pour [request], dans l'ordre de
 * lecture ; une liste vide si rien ne correspond (R9).
 */
fun Library.voiceSearch(request: VoiceRequest): List<Song> {
    // R4 : « mets de la musique ».
    if (request.query.isBlank()) return songs

    val focused = when (request.focus) {
        // R5 et R6 : l'album ou l'artiste nommé exactement, à défaut le premier
        // que rend le moteur.
        VoiceFocus.Album -> request.album.orIfBlank(request.query).let { name ->
            search(name).albums
                .exactFirst(name) { it.title }
                .preferring(request.artist) { it.displayArtist }
                .firstOrNull()
                ?.let { songsOfAlbum(it.id) }
        }
        VoiceFocus.Artist -> request.artist.orIfBlank(request.query).let { name ->
            search(name).artists.exactFirst(name) { it.name }.firstOrNull()?.let { songsOfArtist(it.id) }
        }
        // R7 : les morceaux trouvés, le titre exact d'abord.
        VoiceFocus.Song -> songsBestFirst(request.title.orIfBlank(request.query))
            .preferring(request.artist) { it.displayArtist }
            .preferring(request.album) { it.displayAlbum }
        VoiceFocus.Unspecified -> null
    }

    // Un focus que rien ne satisfait retombe sur R8, avec la phrase entière.
    return focused?.takeIf { it.isNotEmpty() } ?: bestMatch(request.query)
}

/**
 * R8 : le meilleur résultat quand l'hôte ne dit pas ce qu'on cherche — la
 * première des six étapes qui trouve quelque chose, dans l'ordre de la règle.
 */
private fun Library.bestMatch(query: String): List<Song> {
    val key = query.searchKey()
    val results = search(query)
    val songs = songsBestFirst(query)

    return results.albums.firstOrNull { it.title.searchKey() == key }?.let { songsOfAlbum(it.id) }
        ?: results.artists.firstOrNull { it.name.searchKey() == key }?.let { songsOfArtist(it.id) }
        ?: songs.takeIf { it.firstOrNull()?.title?.searchKey() == key }
        ?: results.albums.firstOrNull { it.title.searchKey().startsWith(key) }?.let { songsOfAlbum(it.id) }
        ?: results.artists.firstOrNull { it.name.searchKey().startsWith(key) }?.let { songsOfArtist(it.id) }
        ?: songs
}

/**
 * Les morceaux que trouve le moteur, ceux dont le titre **est** la phrase en
 * tête.
 *
 * Le moteur classe ensemble tout ce qui commence par la phrase, titre, artiste
 * ou album confondus, dans l'ordre de la bibliothèque : « Nevermind » y
 * suivrait « Come As You Are », de l'album du même nom.
 */
private fun Library.songsBestFirst(query: String): List<Song> = search(query).songs.exactFirst(query) { it.title }

/**
 * Les éléments dont [field] **est** [query] en tête, les autres ensuite, chaque
 * groupe dans son ordre.
 *
 * Le moteur ne distingue pas « est » de « commence par », et compare aussi
 * l'artiste d'un album : « Pinkerton » et « Weezer », tous deux de Weezer, y
 * sortent au même rang, dans l'ordre alphabétique.
 */
private fun <T> List<T>.exactFirst(query: String, field: (T) -> String): List<T> {
    val key = query.searchKey()
    val (exact, others) = partition { field(it).searchKey() == key }
    return exact + others
}

/**
 * R12 : ne garde que les éléments dont [field] contient [hint] — l'artiste ou
 * l'album que l'hôte a isolé —, dans leur ordre. Sans indice, ou si aucun ne
 * correspond, la liste reste entière : l'hôte découpe parfois mal la phrase, et
 * mieux vaut un homonyme que rien.
 */
private fun <T> List<T>.preferring(hint: String?, field: (T) -> String): List<T> {
    val key = hint?.searchKey()?.takeIf { it.isNotEmpty() } ?: return this
    return filter { field(it).searchKey().contains(key) }.ifEmpty { this }
}

private fun String?.orIfBlank(fallback: String): String = this?.takeIf { it.isNotBlank() } ?: fallback
