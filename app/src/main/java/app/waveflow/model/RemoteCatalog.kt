package app.waveflow.model

/**
 * Le catalogue d'un serveur WaveFlow.
 *
 * Volontairement distinct de [Song], [Album] et [Artist], qui décrivent la
 * bibliothèque de l'appareil : les identifiants sont des UUID et non des
 * entiers MediaStore, et rien ne permet aujourd'hui de dire qu'une piste
 * distante est la même qu'une piste locale — la RFC-003 du serveur renvoie
 * explicitement cette réconciliation à un jalon ultérieur. Fusionner les deux
 * modèles maintenant reviendrait à préjuger de ce travail.
 */
data class RemoteAlbum(
    val id: String,
    val title: String,
    val artist: String?,
    val artistId: String?,
    val year: Int?,
)

data class RemoteArtist(
    val id: String,
    val name: String,
    /** Connu depuis la liste, absent du détail : le serveur ne le renvoie pas. */
    val albumCount: Int?,
)

data class RemoteSong(
    val id: String,
    val title: String,
    val album: String?,
    val albumId: String?,
    val artist: String?,
    val trackNumber: Int?,
    val durationMs: Long,
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
