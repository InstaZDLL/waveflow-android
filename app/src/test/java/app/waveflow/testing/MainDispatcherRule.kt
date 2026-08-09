package app.waveflow.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Remplace `Dispatchers.Main` le temps d'un test.
 *
 * `viewModelScope` s'y rattache : sans ce remplacement, tout lancement de
 * coroutine dans un ViewModel échoue faute de Looper principal.
 *
 * [dispatcher] est exposé pour être passé à `runTest` : les deux partagent
 * alors le même ordonnanceur, et `advanceUntilIdle()` fait aussi progresser
 * ce que le ViewModel a lancé. Sans ce partage, un test qui modifie une
 * source *après* avoir souscrit au ViewModel observe un état périmé.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
