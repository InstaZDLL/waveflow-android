package app.waveflow.ui.quality

import app.waveflow.data.remote.CatalogRepository
import app.waveflow.data.remote.ServerException
import app.waveflow.data.remote.ServerSessionRepository
import app.waveflow.model.AppPreferences
import app.waveflow.model.ServerSession
import app.waveflow.model.StreamQuality
import app.waveflow.testing.FakeCatalogApi
import app.waveflow.testing.FakePreferencesStore
import app.waveflow.testing.FakeServerApi
import app.waveflow.testing.FakeSessionStore
import app.waveflow.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * La qualité de lecture vue depuis le compte serveur.
 *
 * Robolectric parce que le ViewModel journalise un relevé en échec : sans lui,
 * `android.util.Log` lève, et l'erreur se déguise en panne du code testé.
 */
@RunWith(RobolectricTestRunner::class)
class StreamQualityViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val session = ServerSession.Connected(
        serverUrl = "https://musique.test",
        username = "admin",
        accessToken = "wfa_1",
        refreshToken = "wfr_1",
        deviceId = "appareil-1",
        accessExpiresAtMs = Long.MAX_VALUE,
    )

    private suspend fun catalogue(api: FakeCatalogApi): CatalogRepository {
        val sessions = ServerSessionRepository(
            api = FakeServerApi(),
            store = FakeSessionStore(stored = session),
            deviceName = "Pixel de test",
            now = { 0L },
        )
        sessions.restore()
        return CatalogRepository(api, sessions)
    }

    @Test
    fun `l'ecran s'ouvre sur le choix enregistre`() = runTest {
        val magasin = FakePreferencesStore(AppPreferences(streamQuality = StreamQuality.Haute))

        val vm = StreamQualityViewModel(magasin, catalogue(FakeCatalogApi()))

        assertEquals(StreamQuality.Haute, vm.state.value.current)
    }

    @Test
    fun `choisir un profil l'enregistre`() = runTest {
        val magasin = FakePreferencesStore()
        val vm = StreamQualityViewModel(magasin, catalogue(FakeCatalogApi()))

        vm.choose(StreamQuality.Economie)

        assertEquals(StreamQuality.Economie, magasin.streamQuality)
    }

    @Test
    fun `un serveur sans ffmpeg le fait savoir a l'ecran`() = runTest {
        val api = FakeCatalogApi().apply { transcoding = false }
        val vm = StreamQualityViewModel(FakePreferencesStore(), catalogue(api))

        vm.refresh()

        assertEquals(false, vm.state.value.transcodingAvailable)
    }

    @Test
    fun `un profil que le serveur ne sert pas n'est pas enregistre`() = runTest {
        // L'écran le grise déjà. La garde tient aussi pour l'appui qui arrive
        // avant que l'écran ne se soit repeint.
        val magasin = FakePreferencesStore()
        val api = FakeCatalogApi().apply { transcoding = false }
        val vm = StreamQualityViewModel(magasin, catalogue(api))
        vm.refresh()

        vm.choose(StreamQuality.Economie)

        assertEquals(StreamQuality.Original, magasin.streamQuality)
    }

    @Test
    fun `sans reponse du serveur, rien n'est ferme`() = runTest {
        // Une coupure n'apprend rien de la capacité du serveur : l'inconnu
        // reste l'inconnu, et ne grise aucun profil.
        val api = FakeCatalogApi(failure = ServerException.Unreachable("coupure"))
        val vm = StreamQualityViewModel(FakePreferencesStore(), catalogue(api))

        vm.refresh()

        assertNull(vm.state.value.transcodingAvailable)
    }

    @Test
    fun `un releve en echec garde ce qu'on savait`() = runTest {
        // Un serveur qui a dit ne pas transcoder, puis devient injoignable :
        // la coupure n'a pas installé ffmpeg. Oublier sa réponse rouvrirait des
        // profils qui feraient échouer chaque piste.
        val api = FakeCatalogApi().apply { transcoding = false }
        val vm = StreamQualityViewModel(FakePreferencesStore(), catalogue(api))
        vm.refresh()

        api.pendantLAppel = { throw ServerException.Unreachable("coupure") }
        vm.refresh()

        assertEquals(false, vm.state.value.transcodingAvailable)
    }
}
