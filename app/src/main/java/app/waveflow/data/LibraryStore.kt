package app.waveflow.data

import app.waveflow.model.Library
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * La bibliothèque chargée, partagée par toute l'application.
 *
 * Porté au niveau application plutôt que par un ViewModel : plusieurs écrans
 * en dépendent (titres, albums, artistes, playlists, et la recherche à venir),
 * et chacun d'eux instanciant son propre flux relancerait une requête
 * MediaStore complète. Ici, la lecture n'a lieu qu'une fois et survit à la
 * recréation des ViewModels.
 *
 * [scope] vit aussi longtemps que le processus : l'observation du MediaStore
 * n'est pas censée s'arrêter tant que l'application tourne.
 */
class LibraryStore(
    private val musicRepository: MusicRepository,
    private val scope: CoroutineScope,
) {

    private val _library = MutableStateFlow(Library())
    val library: StateFlow<Library> = _library.asStateFlow()

    /**
     * Garde [job].
     *
     * Les appels viennent aujourd'hui du thread principal, mais la fin d'une
     * observation est notifiée depuis la coroutine : tester, remplacer et
     * effacer doivent former un tout, sans quoi deux collectes pourraient
     * coexister.
     */
    private val lock = Any()

    private var job: Job? = null

    /**
     * Starts observing the library when no active observation is running.
     */
    fun load() {
        synchronized(lock) {
            // L'état du job fait foi, pas sa présence : entre sa complétion et
            // la prise du verrou par son gestionnaire de fin, il est non nul
            // mais n'observe plus rien.
            if (job?.isActive == true) return
            observe()
        }
    }

    /**
 * Starts a new library observation.
 */
    fun retry() = synchronized(lock) { observe() }

    /**
     * Starts observing songs and replaces any previous observation.
     *
     * This function must be called while holding [lock].
     */
    private fun observe() {
        val previous = job
        val started = scope.launch {
            // Attendre la fin de la précédente : deux collectes concurrentes
            // pourraient écrire dans l'état l'une après l'autre et laisser la
            // plus ancienne avoir le dernier mot.
            previous?.cancelAndJoin()
            musicRepository.observeSongs()
                .onStart { _library.update { it.copy(isLoading = true, errorMessage = null) } }
                .catch { error ->
                    _library.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Impossible de lire la bibliothèque.",
                        )
                    }
                }
                .collect { songs ->
                    _library.value = Library(isLoading = false, songs = songs)
                }
        }

        job = started
        started.invokeOnCompletion {
            // Hygiène : `load()` se fie à `isActive`, mais garder la référence
            // d'une observation terminée n'a pas d'intérêt. On n'efface que si
            // personne ne l'a déjà remplacée. Le moniteur est réentrant : une
            // complétion immédiate, notifiée sur place, ne bloque pas.
            synchronized(lock) {
                if (job === started) job = null
            }
        }
    }
}
