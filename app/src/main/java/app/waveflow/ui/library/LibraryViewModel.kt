package app.waveflow.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.waveflow.WaveFlowApp
import app.waveflow.data.LibraryStore
import app.waveflow.model.Library
import kotlinx.coroutines.flow.StateFlow

/**
 * Point d'accès des écrans de navigation à la bibliothèque.
 *
 * Volontairement mince : l'état vit dans le [LibraryStore], partagé au niveau
 * application. Ce ViewModel n'existe que pour l'exposer à Compose et relayer
 * les deux actions qui le pilotent.
 */
class LibraryViewModel(private val libraryStore: LibraryStore) : ViewModel() {

    val library: StateFlow<Library> = libraryStore.library

    /** Le MediaStore n'est lisible qu'une fois la permission audio accordée. */
    fun onAudioAccessGranted() = libraryStore.load()

    fun retry() = libraryStore.retry()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WaveFlowApp
                LibraryViewModel(app.container.libraryStore)
            }
        }
    }
}
