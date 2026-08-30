package app.waveflow.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.waveflow.WaveFlowApp
import app.waveflow.data.PreferencesStore
import app.waveflow.model.AppPreferences
import app.waveflow.model.ThemeChoice
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Les préférences, telles que les écrans les lisent et les changent.
 *
 * Porté au-dessus du `NavHost` plutôt que par l'écran de réglages : le thème
 * habille toute l'application, et non la page où on le choisit.
 */
class SettingsViewModel(
    private val store: PreferencesStore,
) : ViewModel() {

    /**
     * `Eagerly` et non `WhileSubscribed` : ce flux habille la première image
     * affichée. Attendre un abonné ferait démarrer l'application sur le défaut,
     * puis basculer sous les yeux de l'utilisateur — un clignotement à chaque
     * lancement pour quiconque a choisi autre chose que le thème du système.
     */
    val preferences: StateFlow<AppPreferences> = store.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences())

    fun setTheme(choice: ThemeChoice) {
        viewModelScope.launch { store.setTheme(choice) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as WaveFlowApp
                SettingsViewModel(store = app.container.preferencesStore)
            }
        }
    }
}
