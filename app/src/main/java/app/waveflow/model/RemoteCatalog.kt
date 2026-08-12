package app.waveflow.model

import android.net.Uri

/**
 * Le catalogue d'un serveur WaveFlow.
 *
 * Volontairement distinct de [Song], [Album] et [Artist], qui décrivent la
 * bibliothèque de l'appareil : les identifiants sont des UUID et non des
 * entiers MediaStore, et rien ne permet aujourd'hui de dire qu'une piste
 * distante est la même qu'une piste locale — la RFC-003 du serveur renvoie
 * explicitement cette réconciliation à un jalon ultérieur. Fusionner les deux
 * modèles maintenant reviendrait à préjuger de ce travail.
 *
 * [RemoteAlbum.artworkUri] et ses homologues pointent vers `/api/v2/artwork/`,
 * qui exige le même jeton que le reste de l'API : voir `ServerImageLoader`.
 * Nuls quand l'entité n'a pas de pochette — inutile de demander au serveur une
 * image dont il vient de dire qu'elle n'existe pas.
 */
data class RemoteAlbum(
    val id: String,
    val title: String,
    val artist: String?,
    val artistId: String?,
    val year: Int?,
    val artworkUri: Uri?,
)

data class RemoteArtist(
    val id: String,
    val name: String,
    val albumCount: Int?,
    val artworkUri: Uri?,
)

data class RemoteSong(
    val id: String,
    val title: String,
    val album: String?,
    val albumId: String?,
    val artist: String?,
    val trackNumber: Int?,
    val durationMs: Long,
    val artworkUri: Uri?,
)

/** Un album et son contenu, tels que renvoyés d'un seul appel. */
data class RemoteAlbumDetail(
    val album: RemoteAlbum,
    val songs: List<RemoteSong>,
)

/** Un artiste et ses albums, tels que renvoyés d'un seul appel. */
data class RemoteArtistDetail(
    val artist: RemoteArtist,
    val albums: List<RemoteAlbum>,
)
