package app.waveflow.ui.quality

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.waveflow.WaveFlowApp
import app.waveflow.data.PreferencesStore
import app.waveflow.model.StreamQuality
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Ce que l'écran sait de la qualité de lecture.
 *
 * Tous les profils se choisissent, sans demander au serveur s'il sait
 * transcoder : `waveflow-server` refuse de démarrer sans ffmpeg, et un serveur
 * joignable le sait donc toujours.
 */
data class StreamQualityUiState(
    val current: StreamQuality = StreamQuality.Original,
)

/** La qualité de lecture, vue et choisie depuis le compte serveur. */
class StreamQualityViewModel(
    private val store: PreferencesStore,
) : ViewModel() {

    /**
     * `Eagerly`, comme les réglages : l'écran doit s'ouvrir sur le choix
     * enregistré, pas sur le défaut qu'il remplacerait sous les yeux.
     */
    val state: StateFlow<StreamQualityUiState> = store.preferences
        .map { prefs -> StreamQualityUiState(current = prefs.streamQuality) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StreamQualityUiState())

    fun choose(quality: StreamQuality) {
        viewModelScope.launch { store.setStreamQuality(quality) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as WaveFlowApp
                StreamQualityViewModel(store = app.container.preferencesStore)
            }
        }
    }
}
