package app.waveflow.ui.quality

import app.waveflow.model.AppPreferences
import app.waveflow.model.StreamQuality
import app.waveflow.testing.FakePreferencesStore
import app.waveflow.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** La qualité de lecture vue depuis le compte serveur. */
class StreamQualityViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `l'ecran s'ouvre sur le choix enregistre`() = runTest {
        val magasin = FakePreferencesStore(AppPreferences(streamQuality = StreamQuality.Haute))

        val vm = StreamQualityViewModel(magasin)

        assertEquals(StreamQuality.Haute, vm.state.value.current)
    }

    @Test
    fun `choisir un profil l'enregistre`() = runTest {
        val magasin = FakePreferencesStore()
        val vm = StreamQualityViewModel(magasin)

        vm.choose(StreamQuality.Economie)

        assertEquals(StreamQuality.Economie, magasin.streamQuality)
    }
}
