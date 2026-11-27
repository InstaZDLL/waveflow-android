package app.waveflow.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.waveflow.ui.theme.WaveFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * L'onglet allumé et ce que fait un appui dessus, sur une vraie pile de
 * navigation.
 *
 * Le graphe reprend la forme de celui de `MainActivity` : à plat, l'accueil en
 * destination de départ, les détails ouverts par `navigate` depuis n'importe
 * quel onglet. C'est cette forme qui faisait défaut — un album ouvert depuis
 * l'accueil se disait dans la bibliothèque, et toucher « Accueil » le
 * rouvrait. La barre du bas, elle, est la vraie.
 */
@RunWith(RobolectricTestRunner::class)
class TabNavigationTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var nav: NavHostController

    private fun afficher() {
        compose.setContent { Application() }
    }

    @Composable
    private fun Application() {
        val controller = rememberTabNavController()
        nav = controller
        val openTab by controller.openTabAsState()
        WaveFlowTheme {
            Column(Modifier.fillMaxSize()) {
                NavHost(controller, startDestination = Routes.HOME, modifier = Modifier.weight(1f)) {
                    composable(Routes.HOME) { Text("Page Accueil") }
                    composable(Routes.SEARCH) { Text("Page Recherche") }
                    LibraryTab.entries.forEach { vue ->
                        composable(vue.route) { Text("Page ${vue.label}") }
                    }
                    composable(
                        route = Routes.ALBUM_DETAIL,
                        arguments = listOf(navArgument(Routes.ARG_ALBUM_ID) { type = NavType.LongType }),
                    ) { entry ->
                        Text("Album ${entry.arguments?.getLong(Routes.ARG_ALBUM_ID)}")
                    }
                    composable(
                        route = Routes.ARTIST_DETAIL,
                        arguments = listOf(navArgument(Routes.ARG_ARTIST_ID) { type = NavType.LongType }),
                    ) { entry ->
                        Text("Artiste ${entry.arguments?.getLong(Routes.ARG_ARTIST_ID)}")
                    }
                    composable(Routes.SETTINGS) { Text("Page Réglages") }
                }
                WaveFlowBottomBar(selected = openTab?.destination, onSelect = controller::selectTab)
            }
        }
    }

    /** Ce que fait `MainActivity` quand on touche une pochette, d'où qu'on soit. */
    private fun ouvrirAlbum(id: Long) {
        compose.runOnIdle { nav.navigate(Routes.albumDetail(id)) }
    }

    private fun toucher(libelle: String) {
        compose.onNodeWithText(libelle).performClick()
    }

    private fun pile(): List<String?> = compose.runOnIdle {
        nav.currentBackStack.value.map { it.destination.route }.filter { it != null }
    }

    @Test
    fun `un album ouvert depuis l'accueil laisse l'accueil allume`() {
        afficher()

        ouvrirAlbum(5)

        compose.onNodeWithText("Album 5").assertExists()
        compose.onNodeWithText("Accueil").assertIsSelected()
        compose.onNodeWithText("Bibliothèque").assertIsNotSelected()
    }

    @Test
    fun `toucher l'accueil deja ouvert revient a sa racine`() {
        afficher()
        ouvrirAlbum(5)

        toucher("Accueil")

        compose.onNodeWithText("Page Accueil").assertExists()
        compose.onNodeWithText("Album 5").assertDoesNotExist()
        assertEquals(listOf(Routes.HOME), pile())
    }

    @Test
    fun `un album ouvert depuis les artistes allume la bibliotheque et garde les artistes marques`() {
        afficher()
        toucher("Bibliothèque")
        compose.runOnIdle { nav.selectLibraryTab(LibraryTab.Artists) }
        compose.runOnIdle { nav.navigate(Routes.artistDetail(2)) }

        ouvrirAlbum(7)

        compose.onNodeWithText("Album 7").assertExists()
        compose.onNodeWithText("Bibliothèque").assertIsSelected()
        compose.onNodeWithText("Accueil").assertIsNotSelected()
        // La vue d'où l'on est parti, de proche en proche, et non celle que
        // la route de l'album désignerait.
        compose.runOnIdle { assertEquals(Routes.ARTISTS, openTabOf(nav.currentBackStackEntry)?.root) }
    }

    @Test
    fun `un album ouvert depuis la recherche reste dans la recherche`() {
        afficher()
        toucher("Recherche")

        ouvrirAlbum(3)

        compose.onNodeWithText("Recherche").assertIsSelected()
        compose.runOnIdle { assertNull(libraryTabShown(openTabOf(nav.currentBackStackEntry), Routes.ALBUM_DETAIL)) }
    }

    @Test
    fun `toucher la bibliotheque deja ouverte revient a la vue d'ou l'on est parti`() {
        afficher()
        toucher("Bibliothèque")
        compose.runOnIdle { nav.selectLibraryTab(LibraryTab.Albums) }
        ouvrirAlbum(7)

        toucher("Bibliothèque")

        compose.onNodeWithText("Page Albums").assertExists()
        assertEquals(listOf(Routes.HOME, Routes.ALBUMS), pile())
    }

    @Test
    fun `toucher la vue de la bibliotheque deja ouverte revient a sa racine`() {
        afficher()
        toucher("Bibliothèque")
        compose.runOnIdle { nav.selectLibraryTab(LibraryTab.Albums) }
        ouvrirAlbum(7)

        // Ce que fait le sous-onglet « Albums » touché au-dessus de l'album.
        compose.runOnIdle { nav.selectLibraryTab(LibraryTab.Albums) }

        compose.onNodeWithText("Page Albums").assertExists()
        assertEquals(listOf(Routes.HOME, Routes.ALBUMS), pile())
    }

    @Test
    fun `la bibliotheque se rouvre sur la vue d'ou on l'a quittee`() {
        afficher()
        toucher("Bibliothèque")
        compose.runOnIdle { nav.selectLibraryTab(LibraryTab.Artists) }
        compose.runOnIdle { nav.navigate(Routes.artistDetail(2)) }
        ouvrirAlbum(7)

        toucher("Recherche")
        toucher("Bibliothèque")

        // La pile des artistes, et non la vue des titres.
        compose.onNodeWithText("Album 7").assertExists()
        assertEquals(listOf(Routes.HOME, Routes.ARTISTS, Routes.ARTIST_DETAIL, Routes.ALBUM_DETAIL), pile())
    }

    @Test
    fun `la bibliotheque se rouvre sur la derniere vue ouverte, meme quittee par le retour`() {
        afficher()
        toucher("Bibliothèque")
        compose.runOnIdle { nav.selectLibraryTab(LibraryTab.Albums) }
        toucher("Accueil")
        toucher("Bibliothèque")
        compose.runOnIdle { nav.selectLibraryTab(LibraryTab.Artists) }

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Page Accueil").assertExists()
        toucher("Bibliothèque")

        // Les artistes, ouverts en dernier, et non les albums, quittés avant.
        compose.onNodeWithText("Page Artistes").assertExists()
    }

    @Test
    fun `l'ecran du serveur ouvert depuis les reglages reste dans leur pile`() {
        afficher()
        compose.runOnIdle { nav.navigate(Routes.SETTINGS) }
        // Ce que fait la ligne « Serveur » des réglages, sans compte connecté.
        compose.runOnIdle { nav.navigate(Routes.SERVER) }

        compose.onNodeWithText("Page Serveur").assertExists()
        compose.onNodeWithText("Accueil").assertIsSelected()
        compose.runOnIdle { assertNull(libraryTabShown(openTabOf(nav.currentBackStackEntry), Routes.SERVER)) }

        // Compté comme une seconde racine, il faisait sauver puis restaurer la
        // même pile : l'appui ne menait nulle part.
        toucher("Accueil")

        compose.onNodeWithText("Page Accueil").assertExists()
        assertEquals(listOf(Routes.HOME), pile())
    }

    @Test
    fun `toucher la bibliotheque revient a la racine du serveur sous l'ecran rouvert par les reglages`() {
        afficher()
        toucher("Bibliothèque")
        compose.runOnIdle { nav.selectLibraryTab(LibraryTab.Server) }
        compose.runOnIdle { nav.navigate(Routes.SETTINGS) }
        compose.runOnIdle { nav.navigate(Routes.SERVER) }

        // Deux entrées de la même route : la plus haute n'est pas la racine.
        toucher("Bibliothèque")

        assertEquals(listOf(Routes.HOME, Routes.SERVER), pile())
    }

    @Test
    fun `chaque onglet retrouve sa pile quand on y revient`() {
        afficher()
        ouvrirAlbum(5)

        toucher("Bibliothèque")
        compose.onNodeWithText("Page Titres").assertExists()
        compose.onNodeWithText("Bibliothèque").assertIsSelected()

        toucher("Accueil")
        // La pile de l'accueil, sauvée au départ, est restaurée telle quelle.
        compose.onNodeWithText("Album 5").assertExists()
        compose.onNodeWithText("Accueil").assertIsSelected()
        assertEquals(listOf(Routes.HOME, Routes.ALBUM_DETAIL), pile())
    }

    @Test
    fun `une pile profonde garde son onglet apres avoir ete sauvee puis restauree`() {
        afficher()
        toucher("Bibliothèque")
        compose.runOnIdle { nav.selectLibraryTab(LibraryTab.Artists) }
        compose.runOnIdle { nav.navigate(Routes.artistDetail(2)) }
        ouvrirAlbum(7)

        toucher("Accueil")
        compose.runOnIdle { nav.selectLibraryTab(LibraryTab.Artists) }

        // Restaurées, l'album et l'artiste ont gardé la racine retenue à
        // leur ouverture : rien d'autre ne la leur rendrait, l'artiste n'étant
        // pas lui-même une racine.
        compose.onNodeWithText("Album 7").assertExists()
        compose.onNodeWithText("Bibliothèque").assertIsSelected()
        assertEquals(listOf(Routes.HOME, Routes.ARTISTS, Routes.ARTIST_DETAIL, Routes.ALBUM_DETAIL), pile())
    }

    @Test
    fun `l'onglet allume survit a la restauration de l'etat sauve`() {
        // La composition seule est recréée, depuis l'état qu'elle avait sauvé :
        // ni l'activité ni le processus ne meurent ici.
        val restauration = StateRestorationTester(compose)
        restauration.setContent { Application() }
        toucher("Bibliothèque")
        compose.runOnIdle { nav.selectLibraryTab(LibraryTab.Artists) }
        compose.runOnIdle { nav.navigate(Routes.artistDetail(2)) }
        ouvrirAlbum(7)

        restauration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("Album 7").assertExists()
        compose.onNodeWithText("Bibliothèque").assertIsSelected()
    }

    @Test
    fun `le retour systeme depuis un onglet ramene a l'accueil, comme avant`() {
        afficher()
        toucher("Recherche")
        compose.onNodeWithText("Recherche").assertIsSelected()

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

        compose.onNodeWithText("Page Accueil").assertExists()
        compose.onNodeWithText("Accueil").assertIsSelected()
        assertEquals(listOf(Routes.HOME), pile())
    }

    @Test
    fun `le retour systeme depuis un album rend la page d'ou on l'a ouvert`() {
        afficher()
        toucher("Recherche")
        ouvrirAlbum(3)
        compose.onNodeWithText("Recherche").assertIsSelected()

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

        compose.onNodeWithText("Page Recherche").assertExists()
        compose.onNodeWithText("Recherche").assertIsSelected()
    }

    @Test
    fun `les reglages laissent allume l'onglet d'ou on les ouvre`() {
        afficher()
        toucher("Recherche")

        compose.runOnIdle { nav.navigate(Routes.SETTINGS) }

        compose.onNodeWithText("Page Réglages").assertExists()
        compose.onNodeWithText("Recherche").assertIsSelected()
    }
}
