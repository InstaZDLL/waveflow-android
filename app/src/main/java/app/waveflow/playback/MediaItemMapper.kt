package app.waveflow.playback

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.waveflow.model.RemoteSong
import app.waveflow.model.Song

/**
 * Traduction des morceaux vers [MediaItem], et retour.
 *
 * Le `mediaId` est préfixé par sa source. C'est le seul lien entre ce que joue
 * Media3 et le modèle de l'application ; le préfixe évite qu'un identifiant
 * MediaStore et un UUID distant se confondent, et permet de reconnaître une
 * piste locale sans consulter la bibliothèque.
 */
fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId("$LOCAL_PREFIX$id")
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri)
                .build(),
        )
        .build()

/**
 * Piste distante, dont l'URI n'est **pas** joignable telle quelle.
 *
 * Le schéma `waveflow` est un marqueur : [RemoteStreamResolver] l'échange
 * contre une URL de diffusion au moment où le lecteur ouvre la piste. Frapper
 * le serveur ici, à la construction de la file, périmerait les tickets des
 * derniers morceaux avant qu'on ne les atteigne.
 */
fun RemoteSong.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId("$REMOTE_PREFIX$id")
        .setUri("$REMOTE_SCHEME://track/$id".toUri())
        .setCustomCacheKey(cacheKeyOf(id))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .build(),
        )
        .build()

/** Ce que le lecteur donne à voir de sa piste courante. */
fun MediaItem.toPlayingTrack(): PlayingTrack = PlayingTrack(
    mediaId = mediaId,
    title = mediaMetadata.title?.toString().orEmpty(),
    artist = mediaMetadata.artist?.toString(),
    album = mediaMetadata.albumTitle?.toString(),
    artworkUri = mediaMetadata.artworkUri,
    localSongId = localSongId,
    source = if (mediaId.startsWith(REMOTE_PREFIX)) TrackSource.Remote else TrackSource.Local,
)

/**
 * Identité de cette piste distante dans la file de lecture.
 *
 * Permet à un écran de reconnaître la ligne en cours sans construire de
 * [MediaItem] : c'est la même clé que [PlayingTrack.mediaId].
 */
val RemoteSong.mediaId: String
    get() = "$REMOTE_PREFIX$id"

/** Identifiant MediaStore porté par ce [MediaItem], ou `null` s'il vient d'ailleurs. */
val MediaItem.localSongId: Long?
    get() = mediaId.removePrefix(LOCAL_PREFIX).takeIf { mediaId.startsWith(LOCAL_PREFIX) }?.toLongOrNull()

/**
 * Clé de cache d'une piste distante.
 *
 * Explicite plutôt que déduite de l'URL : celle-ci porte un ticket qui change à
 * chaque ouverture, et ne coïnciderait donc jamais avec elle-même.
 *
 * Le **rendu** entier fait partie de la clé, format et débit : le serveur sert
 * la même piste en plusieurs versions, et les confondre rendrait un Opus à
 * 64 kbit/s à qui demande l'original. C'est d'ailleurs ainsi que le serveur
 * nomme ses propres entrées de cache.
 *
 * Le client ne demande aujourd'hui que [DEFAULT_FORMAT], sans débit — le
 * transcodage n'est pas branché. Mettre les deux dès maintenant évite qu'un
 * ajout futur du format oublie le débit, et fasse se recouvrir deux versions.
 *
 * @param bitrate omis de la clé quand il est nul, pour que le cas courant reste
 *   lisible : `remote:<id>:raw`.
 */
internal fun cacheKeyOf(
    trackId: String,
    format: String = DEFAULT_FORMAT,
    bitrate: Int? = null,
): String = buildString {
    append(REMOTE_PREFIX).append(trackId).append(':').append(format)
    bitrate?.let { append(':').append(it) }
}

/** Le défaut du serveur : le fichier tel quel, sans transcodage. */
internal const val DEFAULT_FORMAT = "raw"

/** Identifiant de piste serveur, ou `null` si la piste est locale. */
internal fun trackIdOfRemoteUri(uri: android.net.Uri): String? =
    uri.lastPathSegment?.takeIf { uri.scheme == REMOTE_SCHEME }

private const val LOCAL_PREFIX = "local:"
private const val REMOTE_PREFIX = "remote:"

/** Schéma interne : aucune pile réseau ne sait le résoudre, et c'est voulu. */
internal const val REMOTE_SCHEME = "waveflow"
