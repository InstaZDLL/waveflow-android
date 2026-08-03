package app.waveflow.ui.library

import app.waveflow.model.Song

/**
 * État de la liste de la bibliothèque.
 *
 * Tout ce qui concerne la lecture elle-même vit dans
 * `PlayerUiState` : ici on ne garde que [nowPlayingId], nécessaire pour
 * mettre en évidence la ligne en cours.
 *
 * @property isLoading premier chargement de la bibliothèque en cours.
 * @property songs morceaux à afficher.
 * @property errorMessage message d'erreur si la lecture du MediaStore a échoué.
 * @property nowPlayingId identifiant du morceau en cours, `null` si rien n'est chargé.
 */
data class LibraryUiState(
    val isLoading: Boolean = true,
    val songs: List<Song> = emptyList(),
    val errorMessage: String? = null,
    val nowPlayingId: Long? = null,
) {
    /** Bibliothèque vide alors que le chargement s'est bien terminé. */
    val isEmpty: Boolean
        get() = !isLoading && errorMessage == null && songs.isEmpty()
}
