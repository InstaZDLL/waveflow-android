package app.waveflow.ui.server

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.waveflow.model.ServerSession
import app.waveflow.ui.cache.CacheUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** L'écran de connexion, du point de vue de ce qui est affiché et cliquable. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp-xhdpi")
class ServerScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val connexions = mutableListOf<Triple<String, String, String>>()
    private var deconnexions = 0
    private var vidages = 0

    private val session = ServerSession.Connected(
        serverUrl = "https://musique.test",
        username = "admin",
        accessToken = "wfa_1",
        refreshToken = "wfr_1",
        deviceId = "appareil-1",
        accessExpiresAtMs = 0L,
    )

    /** Formulaire de connexion : l'écran affiché tant qu'aucune session n'existe. */
    private fun afficher(state: ServerUiState) {
        compose.setContent {
            ServerSignInScreen(
                state = state,
                onConnect = { url, user, password -> connexions += Triple(url, user, password) },
            )
        }
    }

    /** Écran de compte : atteint depuis la barre du haut, une fois connecté. */
    private fun afficherCompte(cache: CacheUiState = CacheUiState(usedBytes = 0L)) {
        compose.setContent {
            ServerAccountScreen(
                session = session,
                cache = cache,
                onDisconnect = { deconnexions++ },
                onClearCache = { vidages++ },
                onDismissCacheError = {},
            )
        }
    }

    /**
     * Nombre de nœuds dont un texte contient [text].
     *
     * En sous-chaîne : un jeton glissé au milieu d'une phrase échapperait à une
     * comparaison stricte, et c'est justement ce qu'on cherche à exclure.
     */
    private fun ComposeContentTestRule.occurrencesDe(text: String): Int =
        onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().size

    @Test
    fun `sans session l'ecran propose le formulaire`() {
        afficher(ServerUiState())

        compose.onNodeWithText("Se connecter à un serveur").assertIsDisplayed()
        compose.onNodeWithText("Se connecter").assertIsDisplayed()
    }

    @Test
    fun `la saisie est transmise telle quelle`() {
        afficher(ServerUiState())

        compose.onNodeWithText("Adresse du serveur").performTextInput("musique.test")
        compose.onNodeWithText("Identifiant").performTextInput("admin")
        compose.onNodeWithText("Mot de passe").performTextInput("secret")
        compose.onNodeWithText("Se connecter").performClick()

        // Le rognage est du ressort du ViewModel, pas de l'écran.
        assertEquals(listOf(Triple("musique.test", "admin", "secret")), connexions)
    }

    @Test
    fun `l'affichage du mot de passe se demande et se reprend`() {
        // Le masquage lui-même est une transformation visuelle : l'arbre de
        // sémantique porte le texte brut dans les deux cas, et ne peut donc pas
        // en témoigner. Ce qui s'y voit, c'est l'état de la bascule.
        afficher(ServerUiState())
        compose.onNodeWithText("Mot de passe").performTextInput("secret")

        compose.onNodeWithContentDescription("Afficher le mot de passe").performClick()
        compose.onNodeWithContentDescription("Masquer le mot de passe").assertIsDisplayed()

        compose.onNodeWithContentDescription("Masquer le mot de passe").performClick()
        compose.onNodeWithContentDescription("Afficher le mot de passe").assertIsDisplayed()
    }

    @Test
    fun `pendant la connexion le bouton est inactif`() {
        afficher(ServerUiState(isConnecting = true))

        compose.onNodeWithText("Connexion…").assertIsNotEnabled()
    }

    @Test
    fun `hors connexion le bouton est actif`() {
        afficher(ServerUiState())

        compose.onNodeWithText("Se connecter").assertIsEnabled()
    }

    @Test
    fun `le message d'erreur est affiche`() {
        afficher(ServerUiState(errorMessage = "Identifiant ou mot de passe refusé."))

        compose.onNodeWithText("Identifiant ou mot de passe refusé.").assertIsDisplayed()
    }

    @Test
    fun `l'ecran de compte montre l'identifiant et l'adresse`() {
        afficherCompte()

        compose.onNodeWithText("admin").assertIsDisplayed()
        compose.onNodeWithText("https://musique.test").assertIsDisplayed()
        compose.onNodeWithText("Se déconnecter").assertIsDisplayed()
        // Le formulaire ne doit pas cohabiter avec le compte.
        assertEquals(0, compose.occurrencesDe("Adresse du serveur"))
    }

    @Test
    fun `aucun jeton n'est affiche a l'ecran`() {
        // Ils passent par l'état, ils ne doivent pas se retrouver lisibles.
        afficherCompte()

        assertEquals(0, compose.occurrencesDe("wfa_1"))
        assertEquals(0, compose.occurrencesDe("wfr_1"))
    }

    @Test
    fun `le bouton de deconnexion previent l'appelant`() {
        afficherCompte()

        compose.onNodeWithText("Se déconnecter").performClick()

        assertEquals(1, deconnexions)
        assertTrue(connexions.isEmpty())
    }

    @Test
    fun `tant que la taille n'est pas connue l'ecran le dit`() {
        // Zéro serait un mensonge : le cache peut être plein et la mesure en
        // cours. L'attente s'annonce, et le bouton reste inactif.
        afficherCompte(cache = CacheUiState(usedBytes = null))

        compose.onNodeWithText("Calcul de la place occupée…").assertIsDisplayed()
        compose.onNodeWithText("Vider le cache").assertIsNotEnabled()
    }

    @Test
    fun `un cache vide ne propose pas de le vider`() {
        afficherCompte(cache = CacheUiState(usedBytes = 0L, maxBytes = 1_024L))

        assertEquals(0, compose.occurrencesDe("Calcul de la place occupée"))
        compose.onNodeWithText("Vider le cache").assertIsNotEnabled()
    }

    @Test
    fun `vider le cache demande confirmation avant d'agir`() {
        // Vider coûte du réseau à la prochaine écoute : le geste ne doit pas
        // partir d'un seul appui.
        afficherCompte(cache = CacheUiState(usedBytes = 5_000L, maxBytes = 1_024L * 1_024L))

        compose.onNodeWithText("Vider le cache").performClick()
        assertEquals("le vidage ne doit pas partir tout seul", 0, vidages)

        compose.onNodeWithText("Vider le cache ?").assertIsDisplayed()
        compose.onNodeWithText("Annuler").performClick()
        assertEquals("annuler ne vide rien", 0, vidages)

        compose.onNodeWithText("Vider le cache").performClick()
        compose.onNodeWithText("Vider").performClick()

        assertEquals(1, vidages)
    }
}
