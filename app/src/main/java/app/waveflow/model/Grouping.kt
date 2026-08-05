package app.waveflow.model

/**
 * Regroupements dérivés de la bibliothèque.
 *
 * Albums et artistes sont calculés à partir de la liste de morceaux plutôt
 * qu'interrogés séparément dans le MediaStore : la bibliothèque complète est
 * déjà chargée en mémoire (elle sert de file de lecture), une seconde source
 * n'apporterait qu'un observateur de plus à tenir synchronisé.
 *
 * Le tri est insensible à la casse, comme celui de la liste des morceaux.
 */

fun List<Song>.toAlbums(): List<Album> =
    groupBy { it.albumId }
        .map { (albumId, songs) ->
            val first = songs.first()
            Album(
                id = albumId,
                title = first.displayAlbum,
                artist = first.artist,
                artworkUri = songs.firstNotNullOfOrNull { it.artworkUri },
                trackCount = songs.size,
                durationMs = songs.sumOf { it.durationMs },
            )
        }
        .sortedBy { it.title.lowercase() }

fun List<Song>.toArtists(): List<Artist> =
    groupBy { it.artistId }
        .map { (artistId, songs) ->
            Artist(
                id = artistId,
                name = songs.first().displayArtist,
                albumCount = songs.distinctBy { it.albumId }.size,
                trackCount = songs.size,
                artworkUri = songs.firstNotNullOfOrNull { it.artworkUri },
            )
        }
        .sortedBy { it.name.lowercase() }
