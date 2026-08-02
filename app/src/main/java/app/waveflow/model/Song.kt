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
 * @property album nom de l'album, `null` si absent.
 * @property durationMs durée en millisecondes (0 si inconnue).
 * @property artworkUri URI de la pochette d'album (peut ne rien résoudre), chargé par Coil.
 */
data class Song(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val artworkUri: Uri?,
) {
    val displayArtist: String
        get() = artist?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Artiste inconnu"
}
