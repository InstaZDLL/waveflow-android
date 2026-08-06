package app.waveflow.model

/**
 * La bibliothèque locale, telle que chargée à un instant donné.
 *
 * Une seule source : [songs]. Albums, artistes et index sont des vues
 * dérivées, calculées à la première lecture puis mémorisées — l'onglet Albums
 * ne paie son regroupement que s'il est ouvert, et le lecteur retrouve le
 * morceau courant en temps constant plutôt qu'en parcourant la liste à chaque
 * tic de position.
 */
data class Library(
    val isLoading: Boolean = true,
    val songs: List<Song> = emptyList(),
    val errorMessage: String? = null,
) {
    val albums: List<Album> by lazy { songs.toAlbums() }

    val artists: List<Artist> by lazy { songs.toArtists() }

    val songsById: Map<Long, Song> by lazy { songs.associateBy { it.id } }

    /** Bibliothèque vide alors que le chargement s'est bien terminé. */
    val isEmpty: Boolean
        get() = !isLoading && errorMessage == null && songs.isEmpty()

    fun album(albumId: Long): Album? = albums.firstOrNull { it.id == albumId }

    fun artist(artistId: Long): Artist? = artists.firstOrNull { it.id == artistId }

    /** Morceaux d'un album, dans l'ordre de la bibliothèque. */
    fun songsOfAlbum(albumId: Long): List<Song> = songs.filter { it.albumId == albumId }

    fun songsOfArtist(artistId: Long): List<Song> = songs.filter { it.artistId == artistId }
}
