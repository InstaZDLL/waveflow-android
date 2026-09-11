package app.waveflow.playback

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.waveflow.model.RemoteSong
import app.waveflow.model.Song
import app.waveflow.model.StreamRendering

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
        .setMediaId(mediaId)
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
 *
 * La durée du catalogue suit la piste : un transcodage en direct n'en annonce
 * aucune, et c'est elle que la timeline logique expose à sa place.
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
                .setArtworkUri(artworkUri)
                .setDurationMs(durationMs.takeIf { it > 0L })
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

/** Identité de ce morceau local, sans passer par [toMediaItem]. */
val Song.mediaId: String
    get() = "$LOCAL_PREFIX$id"

/** Identifiant MediaStore porté par ce [MediaItem], ou `null` s'il vient d'ailleurs. */
val MediaItem.localSongId: Long?
    get() = localSongIdOf(mediaId)

/**
 * Identifiant MediaStore que porte ce `mediaId`, ou `null` s'il vient d'ailleurs.
 *
 * La même lecture que [MediaItem.localSongId], mais sur la chaîne seule : un
 * hôte extérieur redemande une piste par son identifiant, sans le [MediaItem]
 * qui l'accompagnait. Retrouver le morceau en reconstruisant un [MediaItem] par
 * candidat coûterait un balayage de la bibliothèque là où un index suffit.
 */
internal fun localSongIdOf(mediaId: String): Long? =
    mediaId.takeIf { it.startsWith(LOCAL_PREFIX) }?.removePrefix(LOCAL_PREFIX)?.toLongOrNull()

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
 * Les deux sont posés ensemble par [withRendering], jamais séparément.
 *
 * @param bitrate omis de la clé quand il ne décrit aucun rendu — absent, nul ou
 *   négatif — pour que le cas courant reste lisible : `remote:<id>:raw`. Aucun
 *   appelant ne passe zéro aujourd'hui, mais un réglage qui coderait ainsi
 *   « qualité d'origine » ouvrirait sinon une seconde entrée de cache pour le
 *   même rendu.
 */
internal fun cacheKeyOf(
    trackId: String,
    format: String = DEFAULT_FORMAT,
    bitrate: Int? = null,
): String = buildString {
    append(REMOTE_PREFIX).append(trackId).append(':').append(format)
    bitrate?.takeIf { it > 0 }?.let { append(':').append(it) }
}

/** Le défaut du serveur : le fichier tel quel, sans transcodage. */
internal const val DEFAULT_FORMAT = StreamRendering.FORMAT_ORIGINAL

/**
 * Pose sur une piste distante le rendu à demander au serveur.
 *
 * L'URI **et** la clé de cache sont réécrites ensemble, depuis le même rendu.
 * Le cache calcule sa clé avant que [RemoteStreamResolver] ne construise l'URL :
 * deux lectures du réglage, une à chaque bout, laisseraient un changement
 * s'intercaler entre elles. Une version Opus se rangerait alors sous le nom de
 * l'original, et serait resservie à qui demande l'original.
 *
 * L'original laisse le marqueur nu, et retrouve ainsi la clé qu'il portait avant
 * que la qualité ne se choisisse : le cache déjà constitué reste valable.
 *
 * Une piste locale est rendue telle quelle : elle ne passe pas par le serveur.
 * Un décalage déjà posé est effacé : une piste qui entre dans la file part du
 * début.
 */
internal fun MediaItem.withRendering(rendering: StreamRendering): MediaItem {
    val uri = localConfiguration?.uri ?: return this
    val trackId = trackIdOfRemoteUri(uri) ?: return this

    return buildUpon()
        .setUri(uri.marqueur(rendering, offsetMs = 0L))
        .setCustomCacheKey(cacheKeyOf(trackId, rendering.format, rendering.bitrate))
        .build()
}

/**
 * Pose sur une piste distante transcodée l'instant où son flux doit commencer.
 *
 * Le décalage voyage dans le marqueur, comme le rendu : [RemoteStreamResolver]
 * le relit, et la chaîne de lecture aiguille un segment hors du cache d'après ce
 * même marqueur. Aucun état partagé ne s'intercale entre les deux. Voir
 * `docs/deplacement-dans-un-transcodage.md`, R10 et R11.
 *
 * La clé de cache ne change pas, et `0` rend exactement le marqueur que
 * [withRendering] avait posé : la piste revenue au début retrouve son cache.
 *
 * Sans effet sur une piste locale, ni sur l'original : le serveur refuse un
 * décalage sur `raw` (R8), et l'original se déplace par plages.
 */
internal fun MediaItem.withStreamOffset(offsetMs: Long): MediaItem {
    val uri = localConfiguration?.uri ?: return this
    trackIdOfRemoteUri(uri) ?: return this

    return buildUpon().setUri(uri.marqueur(renderingOfRemoteUri(uri), offsetMs)).build()
}

/** Le marqueur de cette piste pour ce rendu, à partir de cet instant. */
private fun android.net.Uri.marqueur(rendering: StreamRendering, offsetMs: Long): android.net.Uri =
    buildUpon().clearQuery().apply {
        if (!rendering.isOriginal) {
            appendQueryParameter(PARAM_FORMAT, rendering.format)
            rendering.bitrate?.let { appendQueryParameter(PARAM_BITRATE, it.toString()) }
            if (offsetMs > 0L) appendQueryParameter(PARAM_OFFSET, offsetMs.toString())
        }
    }.build()

/** Le rendu que porte le marqueur d'une piste distante ; l'original s'il n'en dit rien. */
internal fun renderingOfRemoteUri(uri: android.net.Uri): StreamRendering {
    val format = uri.getQueryParameter(PARAM_FORMAT) ?: return StreamRendering.ORIGINAL
    return StreamRendering(format, uri.getQueryParameter(PARAM_BITRATE)?.toIntOrNull())
}

/** L'instant du morceau où commence le flux de ce marqueur ; `0` hors segment. */
internal fun streamOffsetOfRemoteUri(uri: android.net.Uri): Long {
    if (trackIdOfRemoteUri(uri) == null) return 0L
    return uri.getQueryParameter(PARAM_OFFSET)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
}

private const val PARAM_FORMAT = "format"
private const val PARAM_BITRATE = "bitrate"
private const val PARAM_OFFSET = "offset_ms"

/** Identifiant de piste serveur, ou `null` si la piste est locale. */
internal fun trackIdOfRemoteUri(uri: android.net.Uri): String? =
    uri.lastPathSegment?.takeIf { uri.scheme == REMOTE_SCHEME }

private const val LOCAL_PREFIX = "local:"
private const val REMOTE_PREFIX = "remote:"

/** Schéma interne : aucune pile réseau ne sait le résoudre, et c'est voulu. */
internal const val REMOTE_SCHEME = "waveflow"
