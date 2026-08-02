package app.waveflow.ui.library

import app.waveflow.model.Song

/**
 * État complet de l'écran bibliothèque : tout ce que la vue a besoin de
 * connaître, et rien d'autre.
 *
 * @property isLoading premier chargement de la bibliothèque en cours.
 * @property songs morceaux à afficher.
 * @property errorMessage message d'erreur si la lecture du MediaStore a échoué.
 * @property nowPlayingId identifiant du morceau en cours, `null` si rien n'est chargé.
 * @property isPlaying lecture en cours.
 */
data class LibraryUiState(
    val isLoading: Boolean = true,
    val songs: List<Song> = emptyList(),
    val errorMessage: String? = null,
    val nowPlayingId: Long? = null,
    val isPlaying: Boolean = false,
) {
    /** Bibliothèque vide alors que le chargement s'est bien terminé. */
    val isEmpty: Boolean
        get() = !isLoading && errorMessage == null && songs.isEmpty()

    /** Morceau en cours, s'il fait partie de la bibliothèque affichée. */
    val nowPlaying: Song?
        get() = nowPlayingId?.let { id -> songs.firstOrNull { it.id == id } }
}
