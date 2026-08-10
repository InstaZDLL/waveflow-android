package app.waveflow.ui.server

import app.waveflow.data.remote.ServerException
import app.waveflow.data.remote.ServerSessionRepository
import app.waveflow.model.ServerSession
import app.waveflow.testing.FakeServerApi
import app.waveflow.testing.FakeSessionStore
import app.waveflow.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/**
 * Robolectric parce que le ViewModel journalise ses échecs par
 * `android.util.Log`, qui lève sur une JVM nue.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ServerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        api: FakeServerApi = FakeServerApi(),
        store: FakeSessionStore = FakeSessionStore(),
    ) = ServerViewModel(
        ServerSessionRepository(
            api = api,
            store = store,
            deviceName = "Pixel de test",
            now = { 0L },
        ),
    )

    @Test
    fun `une connexion reussie expose le compte et efface la progression`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = viewModel()

            viewModel.connect("https://musique.test", "admin", "secret")
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state.isConnected)
            assertEquals("admin", state.connected?.username)
            assertFalse(state.isConnecting)
            assertNull(state.errorMessage)
        }

    @Test
    fun `l'adresse et l'identifiant sont rognes avant l'appel`() =
        runTest(mainDispatcherRule.dispatcher) {
            val store = FakeSessionStore()
            val viewModel = viewModel(store = store)

            viewModel.connect("  https://musique.test  ", "  admin  ", "secret")
            advanceUntilIdle()

            val session = store.written as ServerSession.Connected
            assertEquals("https://musique.test", session.serverUrl)
        }

    @Test
    fun `un champ vide est refuse sans toucher au reseau`() =
        runTest(mainDispatcherRule.dispatcher) {
            val api = FakeServerApi()
            val viewModel = viewModel(api = api)

            viewModel.connect("https://musique.test", "  ", "secret")
            advanceUntilIdle()

            assertNull(api.lastDeviceName)
            assertEquals(
                "Adresse, identifiant et mot de passe sont requis.",
                viewModel.state.value.errorMessage,
            )
        }

    @Test
    fun `un refus d'identifiants devient un message comprehensible`() =
        runTest(mainDispatcherRule.dispatcher) {
            val api = FakeServerApi(loginFailure = ServerException.Unauthorized("Authentication failed"))
            val viewModel = viewModel(api = api)

            viewModel.connect("https://musique.test", "admin", "faux")
            advanceUntilIdle()

            // Le message du serveur est en anglais et technique : il ne sort pas.
            assertEquals("Identifiant ou mot de passe refusé.", viewModel.state.value.errorMessage)
            assertFalse(viewModel.state.value.isConnecting)
        }

    @Test
    fun `un serveur injoignable se distingue d'un refus`() =
        runTest(mainDispatcherRule.dispatcher) {
            val api = FakeServerApi(loginFailure = ServerException.Unreachable("connection reset"))
            val viewModel = viewModel(api = api)

            viewModel.connect("https://musique.test", "admin", "secret")
            advanceUntilIdle()

            assertEquals(
                "Serveur injoignable. Vérifiez l'adresse et le réseau.",
                viewModel.state.value.errorMessage,
            )
        }

    @Test
    fun `un echec n'empeche pas une nouvelle tentative`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Le même ViewModel : c'est justement la reprise après échec qu'on
            // vérifie, pas deux instances indépendantes.
            val api = FakeServerApi(loginFailure = ServerException.Unauthorized("non"))
            val viewModel = viewModel(api = api)

            viewModel.connect("https://musique.test", "admin", "faux")
            advanceUntilIdle()
            assertFalse("le bouton doit redevenir actif", viewModel.state.value.isConnecting)

            api.loginFailure = null
            viewModel.connect("https://musique.test", "admin", "secret")
            advanceUntilIdle()

            assertTrue(viewModel.state.value.isConnected)
            assertNull("l'échec précédent ne doit pas survivre", viewModel.state.value.errorMessage)
        }

    @Test
    fun `le message d'erreur se referme`() = runTest(mainDispatcherRule.dispatcher) {
        val api = FakeServerApi(loginFailure = ServerException.Unauthorized("non"))
        val viewModel = viewModel(api = api)

        viewModel.connect("https://musique.test", "admin", "faux")
        advanceUntilIdle()
        viewModel.dismissError()

        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun `la deconnexion ramene au formulaire`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = viewModel()
        viewModel.connect("https://musique.test", "admin", "secret")
        advanceUntilIdle()

        viewModel.disconnect()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isConnected)
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun `un stockage en echec pendant la deconnexion devient un message, pas un crash`() =
        runTest(mainDispatcherRule.dispatcher) {
            // L'exception quitterait `viewModelScope` et ferait tomber l'app.
            val store = FakeSessionStore(writeFailure = IOException("disque plein"))
            val viewModel = viewModel(store = store)

            viewModel.disconnect()
            advanceUntilIdle()

            assertEquals(
                "La déconnexion n'a pas pu être enregistrée.",
                viewModel.state.value.errorMessage,
            )
        }
}
