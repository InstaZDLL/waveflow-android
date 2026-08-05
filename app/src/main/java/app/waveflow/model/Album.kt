package app.waveflow.model

import android.net.Uri

/**
 * Un album, tel que déduit des morceaux de la bibliothèque.
 *
 * @property id identifiant MediaStore de l'album.
 * @property artist artiste principal — celui du premier morceau ; les albums
 *   de compilation afficheront donc l'artiste de leur première piste.
 * @property trackCount nombre de morceaux présents sur l'appareil, pas le
 *   nombre de pistes de l'album original.
 */
data class Album(
    val id: Long,
    val title: String,
    val artist: String?,
    val artworkUri: Uri?,
    val trackCount: Int,
    val durationMs: Long,
) {
    val displayArtist: String
        get() = artist.orUnknownArtist()
}
