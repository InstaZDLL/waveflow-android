package app.waveflow.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.waveflow.WaveFlowApp
import app.waveflow.data.PreferencesStore
import app.waveflow.model.AppPreferences
import app.waveflow.model.Prereglage
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

    /**
     * Applique un geste de la section *Son*.
     *
     * Chaque geste devient une transformation que le magasin applique à ce qu'il
     * contient au moment d'écrire : deux gestes rapprochés ne s'écrasent pas.
     */
    fun regler(geste: GesteSon) {
        viewModelScope.launch {
            when (geste) {
                is GesteSon.ReplayGainActif -> store.updateReplayGain { it.copy(actif = geste.actif) }
                is GesteSon.Mode -> store.updateReplayGain { it.copy(mode = geste.mode) }
                is GesteSon.Preampli -> store.updateReplayGain { it.copy(preampliDb = geste.db) }
                is GesteSon.GainParDefaut -> store.updateReplayGain { it.copy(gainParDefautDb = geste.db) }
                is GesteSon.EviterSaturation -> store.updateReplayGain { it.copy(eviterSaturation = geste.actif) }
                is GesteSon.EgaliseurActif -> store.updateEqualizer { it.avecActif(geste.actif) }
                is GesteSon.Choisir -> store.updateEqualizer { it.avecPrereglage(geste.prereglage) }
                is GesteSon.Bande -> store.updateEqualizer { it.avecBande(geste.index, geste.db) }
                GesteSon.Reinitialiser -> store.updateEqualizer { it.avecPrereglage(Prereglage.Plat) }
            }
        }
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
