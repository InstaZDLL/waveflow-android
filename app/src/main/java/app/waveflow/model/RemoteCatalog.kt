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
 * `artworkUri` est nul quand l'entité n'a pas de pochette. Sa construction
 * appartient à la couche de données ; l'affichage n'a qu'à la charger.
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
    val replayGain: RemoteReplayGain = RemoteReplayGain(),
)

/**
 * Les quatre mesures `replay_gain_*` que le serveur publie pour une piste, en dB
 * sur l'échelle ReplayGain 2.0 pour les gains, linéaires pour les pics. Chacune
 * peut manquer : le serveur ne les remplit qu'au scan qui suit leur apparition.
 *
 * Le serveur ne met de gain dans aucun flux de l'API v2 : c'est au client de les
 * appliquer. Voir `docs/audio-avance.md`, G2.
 */
data class RemoteReplayGain(
    val trackGainDb: Double? = null,
    val trackPeak: Double? = null,
    val albumGainDb: Double? = null,
    val albumPeak: Double? = null,
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

/**
 * Résultats d'une recherche sur le serveur.
 *
 * Le serveur les rend groupés par type et déjà classés : contrairement à la
 * recherche locale, rien n'est trié ici — c'est son index qui décide de la
 * pertinence.
 */
data class RemoteSearchResults(
    val songs: List<RemoteSong> = emptyList(),
    val albums: List<RemoteAlbum> = emptyList(),
    val artists: List<RemoteArtist> = emptyList(),
) {
    val isEmpty: Boolean
        get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty()
}
