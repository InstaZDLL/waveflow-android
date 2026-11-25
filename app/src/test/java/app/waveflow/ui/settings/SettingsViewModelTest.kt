package app.waveflow.ui.settings

import app.waveflow.model.AppPreferences
import app.waveflow.model.ModeDeGain
import app.waveflow.model.Prereglage
import app.waveflow.model.ThemeChoice
import app.waveflow.testing.FakePreferencesStore
import app.waveflow.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Les préférences telles que l'application les lit au lancement.
 *
 * Ce qui se joue ici n'est pas la valeur mais le **moment** où elle est prête :
 * le thème habille la première image affichée, et une préférence qui arrive
 * après coup se voit — l'écran s'ouvre en clair puis bascule en sombre.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `le theme enregistre est pret sans que personne n'ait collecte`() =
        runTest(mainDispatcherRule.dispatcher) {
            val magasin = FakePreferencesStore(AppPreferences(theme = ThemeChoice.Dark))

            val vue = SettingsViewModel(magasin)
            advanceUntilIdle()

            // Personne n'a souscrit à `vue.preferences` : c'est tout l'enjeu.
            // Un partage qui attendrait un abonné rendrait ici le défaut, et
            // l'application démarrerait en clair avant de virer au sombre.
            assertEquals(ThemeChoice.Dark, vue.preferences.value.theme)
        }

    @Test
    fun `choisir un theme le confie au magasin`() =
        runTest(mainDispatcherRule.dispatcher) {
            // L'assertion porte sur le magasin et non sur le ViewModel : un
            // état gardé pour soi afficherait le bon thème jusqu'à la
            // fermeture, puis l'oublierait.
            val magasin = FakePreferencesStore(AppPreferences())

            SettingsViewModel(magasin).setTheme(ThemeChoice.Light)
            advanceUntilIdle()

            assertEquals(ThemeChoice.Light, magasin.theme)
        }

    /**
     * Les gestes de la section *Son* arrivent au magasin. L'assertion porte sur
     * le magasin : c'est lui que le service observe, pas le ViewModel.
     */
    @Test
    fun `les gestes du son sont confies au magasin`() =
        runTest(mainDispatcherRule.dispatcher) {
            val magasin = FakePreferencesStore(AppPreferences())
            val vue = SettingsViewModel(magasin)

            vue.regler(GesteSon.Mode(ModeDeGain.ParTitre))
            vue.regler(GesteSon.Preampli(20f))
            vue.regler(GesteSon.EgaliseurActif(true))
            vue.regler(GesteSon.Choisir(Prereglage.Pop))
            vue.regler(GesteSon.Bande(0, 2.5f))
            advanceUntilIdle()

            val lues = magasin.preferences.first()
            assertEquals(ModeDeGain.ParTitre, lues.replayGain.mode)
            // Borné comme le vrai magasin : ±15 dB.
            assertEquals(15f, lues.replayGain.preampliDb, 0f)
            assertEquals(true, lues.equalizer.actif)
            // Un curseur bougé quitte le préréglage, et garde ses autres bandes.
            assertEquals(null, lues.equalizer.prereglage)
            assertEquals(listOf(2.5f) + Prereglage.Pop.bandesDb.drop(1), lues.equalizer.bandesDb)

            vue.regler(GesteSon.Reinitialiser)
            advanceUntilIdle()
            assertEquals(Prereglage.Plat, magasin.preferences.first().equalizer.prereglage)
        }
}
