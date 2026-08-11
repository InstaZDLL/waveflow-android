package app.waveflow.playback

import android.net.Uri

/**
 * Le morceau que le lecteur a réellement en main.
 *
 * Décrit par ce que Media3 en sait, et non par un identifiant à résoudre dans
 * la bibliothèque locale : depuis que le serveur peut alimenter la file, un
 * morceau en cours n'est plus forcément un fichier de l'appareil.
 *
 * @property mediaId identité stable dans la file, quelle que soit la source.
 * @property localSongId identifiant MediaStore quand la piste est locale, `null`
 *   sinon. Sert à souligner la ligne en cours dans les listes locales.
 * @property source d'où vient la piste, à afficher quand l'album est inconnu.
 */
data class PlayingTrack(
    val mediaId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val artworkUri: Uri?,
    val localSongId: Long?,
    val source: TrackSource,
)

enum class TrackSource(val label: String) {
    Local("Bibliothèque locale"),
    Remote("Serveur WaveFlow"),
}
