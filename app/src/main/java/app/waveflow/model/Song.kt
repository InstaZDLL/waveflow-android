package app.waveflow.model

import android.net.Uri

/**
 * Un morceau audio, indépendant de sa provenance (MediaStore aujourd'hui,
 * serveur WaveFlow demain).
 *
 * @property id identifiant stable de la source (MediaStore pour le local).
 * @property uri URI de contenu jouable, passé tel quel à ExoPlayer.
 * @property title titre affiché (retombe sur le nom de fichier si le tag manque).
 * @property artist artiste, `null` si le tag est absent.
 * @property artistId identifiant de l'artiste, utilisé pour regrouper.
 * @property album nom de l'album, `null` si absent.
 * @property albumId identifiant de l'album, utilisé pour regrouper.
 * @property durationMs durée en millisecondes (0 si inconnue).
 * @property artworkUri URI de la pochette d'album (peut ne rien résoudre), chargé par Coil.
 */
data class Song(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String?,
    val artistId: Long,
    val album: String?,
    val albumId: Long,
    val durationMs: Long,
    val artworkUri: Uri?,
) {
    val displayArtist: String
        get() = artist.orUnknownArtist()

    val displayAlbum: String
        get() = album?.takeIf { it.isNotBlank() } ?: "Album inconnu"
}

/** Nom d'artiste affichable : MediaStore écrit `<unknown>` quand le tag manque. */
internal fun String?.orUnknownArtist(): String =
    this?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Artiste inconnu"
