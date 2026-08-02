package app.waveflow.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.waveflow.model.Song

/**
 * Traduction [Song] <-> [MediaItem].
 *
 * Le `mediaId` porte l'identifiant du morceau : c'est le seul lien entre ce
 * que joue Media3 et le modèle de l'application, ce qui permet de retrouver
 * la piste courante sans garder de référence côté lecteur.
 */
fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
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

/** Identifiant [Song] porté par ce [MediaItem], ou `null` s'il vient d'ailleurs. */
val MediaItem.songId: Long?
    get() = mediaId.toLongOrNull()
