package app.waveflow.model

import android.net.Uri

/**
 * Un artiste, tel que déduit des morceaux de la bibliothèque.
 *
 * @property artworkUri pochette d'un de ses albums, faute d'image d'artiste
 *   dans le MediaStore.
 */
data class Artist(
    val id: Long,
    val name: String,
    val albumCount: Int,
    val trackCount: Int,
    val artworkUri: Uri?,
)
