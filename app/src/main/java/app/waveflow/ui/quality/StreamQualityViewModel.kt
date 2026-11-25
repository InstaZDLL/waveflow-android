package app.waveflow.ui.quality

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.waveflow.WaveFlowApp
import app.waveflow.data.PreferencesStore
import app.waveflow.model.AppPreferences
import app.waveflow.model.StreamNetwork
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
    val automatic: Boolean = false,
    val unmetered: StreamQuality = AppPreferences().unmeteredQuality,
    val metered: StreamQuality = AppPreferences().meteredQuality,
) {
    /** Le profil que la qualité automatique prend sur ce réseau. */
    fun on(network: StreamNetwork): StreamQuality = when (network) {
        StreamNetwork.Unmetered -> unmetered
        StreamNetwork.Metered -> metered
    }
}

/** La qualité de lecture, vue et choisie depuis le compte serveur. */
class StreamQualityViewModel(
    private val store: PreferencesStore,
) : ViewModel() {

    /**
     * `Eagerly`, comme les réglages : l'écran doit s'ouvrir sur le choix
     * enregistré, pas sur le défaut qu'il remplacerait sous les yeux.
     */
    val state: StateFlow<StreamQualityUiState> = store.preferences
        .map { prefs ->
            StreamQualityUiState(
                current = prefs.streamQuality,
                automatic = prefs.automaticQuality,
                unmetered = prefs.unmeteredQuality,
                metered = prefs.meteredQuality,
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StreamQualityUiState())

    /** Un profil fixe, qui quitte la qualité automatique. */
    fun choose(quality: StreamQuality) {
        viewModelScope.launch { store.setStreamQuality(quality) }
    }

    fun chooseAutomatic() {
        viewModelScope.launch { store.setAutomaticQuality() }
    }

    fun chooseOn(network: StreamNetwork, quality: StreamQuality) {
        viewModelScope.launch { store.setQualityOn(network, quality) }
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
