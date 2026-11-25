package app.waveflow.ui.quality

import app.waveflow.model.AppPreferences
import app.waveflow.model.StreamNetwork
import app.waveflow.model.StreamQuality
import app.waveflow.testing.FakePreferencesStore
import app.waveflow.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `l'ecran s'ouvre sur la qualite automatique et ses deux profils`() = runTest {
        val magasin = FakePreferencesStore(
            AppPreferences(automaticQuality = true, unmeteredQuality = StreamQuality.Haute),
        )

        val etat = StreamQualityViewModel(magasin).state.value

        assertTrue(etat.automatic)
        assertEquals(StreamQuality.Haute, etat.on(StreamNetwork.Unmetered))
        assertEquals(StreamQuality.Economie, etat.on(StreamNetwork.Metered))
    }

    @Test
    fun `choisir l'automatique puis un profil par reseau l'enregistre`() = runTest {
        val magasin = FakePreferencesStore()
        val vm = StreamQualityViewModel(magasin)

        vm.chooseAutomatic()
        vm.chooseOn(StreamNetwork.Metered, StreamQuality.Haute)

        assertTrue(vm.state.value.automatic)
        assertEquals(StreamQuality.Haute, vm.state.value.metered)
    }

    @Test
    fun `choisir un profil fixe quitte l'automatique`() = runTest {
        val vm = StreamQualityViewModel(FakePreferencesStore(AppPreferences(automaticQuality = true)))

        vm.choose(StreamQuality.Haute)

        assertFalse(vm.state.value.automatic)
        assertEquals(StreamQuality.Haute, vm.state.value.current)
    }
}
