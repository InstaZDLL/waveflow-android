package app.waveflow.ui.quality

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.waveflow.WaveFlowApp
import app.waveflow.data.PreferencesStore
import app.waveflow.data.remote.CatalogRepository
import app.waveflow.data.remote.ServerException
import app.waveflow.model.StreamQuality
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Ce que l'écran sait de la qualité de lecture.
 *
 * @property transcodingAvailable ce que le serveur a dit de sa capacité à
 *   transcoder, `null` tant qu'il n'a rien dit. L'inconnu ne ferme aucun choix :
 *   seul un refus explicite le fait, sans quoi une coupure réseau passagère
 *   grisait des profils que le serveur sert très bien.
 */
data class StreamQualityUiState(
    val current: StreamQuality = StreamQuality.Original,
    val transcodingAvailable: Boolean? = null,
) {
    /** L'original se sert toujours ; le reste suppose un serveur qui transcode. */
    fun isSelectable(quality: StreamQuality): Boolean =
        quality.rendering.isOriginal || transcodingAvailable != false

    /**
     * Le choix enregistré ne peut pas être servi par ce serveur.
     *
     * Le cas se présente : la préférence appartient à l'application, pas au
     * serveur, et elle survit à un changement de serveur.
     */
    val isCurrentUnavailable: Boolean get() = !isSelectable(current)
}

/** La qualité de lecture, vue et choisie depuis le compte serveur. */
class StreamQualityViewModel(
    private val store: PreferencesStore,
    private val catalog: CatalogRepository,
) : ViewModel() {

    private val disponible = MutableStateFlow<Boolean?>(null)

    /**
     * `Eagerly`, comme les réglages : l'écran doit s'ouvrir sur le choix
     * enregistré, pas sur le défaut qu'il remplacerait sous les yeux.
     */
    val state: StateFlow<StreamQualityUiState> = combine(store.preferences, disponible) { prefs, dispo ->
        StreamQualityUiState(current = prefs.streamQuality, transcodingAvailable = dispo)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StreamQualityUiState())

    private var releve: Job? = null

    /**
     * À l'ouverture de l'écran : ffmpeg peut avoir été installé, ou retiré,
     * depuis la dernière fois.
     *
     * Un relevé en échec laisse ce qu'on savait déjà. Un serveur injoignable
     * n'a rien appris de neuf sur sa capacité à transcoder.
     */
    fun refresh() {
        releve?.cancel()
        releve = viewModelScope.launch {
            try {
                disponible.value = catalog.transcodingAvailable()
            } catch (error: ServerException) {
                Log.w(TAG, "Capacité de transcodage inconnue", error)
            }
        }
    }

    /** Un profil que le serveur a déclaré ne pas servir n'est pas enregistré. */
    fun choose(quality: StreamQuality) {
        if (!state.value.isSelectable(quality)) return
        viewModelScope.launch { store.setStreamQuality(quality) }
    }

    companion object {
        private const val TAG = "StreamQualityViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as WaveFlowApp
                StreamQualityViewModel(
                    store = app.container.preferencesStore,
                    catalog = app.container.catalogRepository,
                )
            }
        }
    }
}
