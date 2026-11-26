package app.waveflow.ui.settings

import app.waveflow.model.ModeDeGain
import app.waveflow.model.Prereglage

/**
 * Un geste sur la section *Son*, que l'écran remonte sans l'interpréter.
 *
 * Un seul rappel pour toute la section plutôt qu'un par réglage : l'écran n'a
 * qu'une porte à faire traverser, et c'est le ViewModel qui sait ce que chaque
 * geste change. Voir [SettingsViewModel.regler].
 */
sealed interface GesteSon {
    data class ReplayGainActif(val actif: Boolean) : GesteSon
    data class Mode(val mode: ModeDeGain) : GesteSon
    data class Preampli(val db: Float) : GesteSon
    data class GainParDefaut(val db: Float) : GesteSon
    data class EviterSaturation(val actif: Boolean) : GesteSon
    data class EgaliseurActif(val actif: Boolean) : GesteSon
    data class Choisir(val prereglage: Prereglage) : GesteSon
    data class Bande(val index: Int, val db: Float) : GesteSon
    data object Reinitialiser : GesteSon
}
