package app.waveflow.ui.cache

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.waveflow.WaveFlowApp
import app.waveflow.playback.PlaybackCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Ce que l'écran sait du cache de lecture.
 *
 * @property usedBytes place occupée, `null` tant que la première mesure n'est
 *   pas revenue — l'écran affiche alors une attente plutôt qu'un zéro trompeur.
 * @property maxBytes plafond au-delà duquel les pistes les plus anciennes sont
 *   évincées.
 */
data class CacheUiState(
    val usedBytes: Long? = null,
    val maxBytes: Long = 0L,
    val isClearing: Boolean = false,
    val errorMessage: String? = null,
) {
    /** Rien à vider : le bouton n'a alors aucun effet à proposer. */
    val isEmpty: Boolean get() = usedBytes == 0L
}

/** Le cache des pistes distantes, vu depuis les réglages. */
class CacheViewModel(
    private val cache: PlaybackCache,
) : ViewModel() {

    private val _state = MutableStateFlow(CacheUiState(maxBytes = cache.maxBytes))
    val state: StateFlow<CacheUiState> = _state.asStateFlow()

    /** À l'ouverture de l'écran : la taille bouge à chaque piste lue. */
    fun refresh() {
        viewModelScope.launch {
            // Mesurer d'abord, publier ensuite. `update` rejoue sa lambda quand
            // l'état a bougé entre-temps, et la mesure suspend le temps d'un
            // accès disque : la laisser dedans la ferait repartir pour rien.
            val mesure = mesurer()
            _state.update { it.copy(usedBytes = mesure) }
        }
    }

    fun clear() {
        if (_state.value.isClearing) return
        _state.update { it.copy(isClearing = true, errorMessage = null) }

        viewModelScope.launch {
            val echec = try {
                cache.clear()
                null
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                // Un fichier verrouillé ou un index abîmé ne doit pas faire
                // tomber l'écran. La mesure qui suit dira ce qui reste.
                Log.w(TAG, "Vidage du cache incomplet", error)
                "Le cache n'a pas pu être entièrement vidé."
            }

            val mesure = mesurer()
            _state.update {
                it.copy(usedBytes = mesure, isClearing = false, errorMessage = echec)
            }
        }
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    /** Une mesure qui échoue laisse l'ancienne valeur plutôt qu'un chiffre faux. */
    private suspend fun mesurer(): Long? = try {
        cache.usedBytes()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        Log.w(TAG, "Taille du cache illisible", error)
        _state.value.usedBytes
    }

    companion object {
        private const val TAG = "CacheViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as WaveFlowApp
                CacheViewModel(cache = app.container.remoteMediaCache)
            }
        }
    }
}
