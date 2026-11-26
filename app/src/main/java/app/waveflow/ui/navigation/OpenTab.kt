package app.waveflow.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/**
 * L'onglet ouvert : la destination de la barre du bas, et la route où sa pile
 * commence.
 *
 * @property root la racine de la pile, telle qu'elle y figure : pour la
 *   bibliothèque, la vue d'où l'on est parti — un album ouvert depuis les
 *   artistes vit dans la pile des artistes.
 */
data class OpenTab(val destination: TopLevelDestination, val root: String) {
    /** La vue de la bibliothèque dont la pile est ouverte, s'il y en a une. */
    val libraryTab: LibraryTab? get() = LibraryTab.entries.firstOrNull { it.route == root }
}

/**
 * L'onglet dont la pile commence à [root], si c'en est une racine.
 */
fun openTabOfRoot(root: String?): OpenTab? =
    TopLevelDestination.ofRoot(root)?.let { OpenTab(it, root!!) }

/**
 * L'onglet où vit [entry] : la racine que l'entrée a retenue en s'ouvrant
 * ([rememberTabNavController]), sinon sa propre route si elle commence une pile.
 *
 * La pile et non la route ouverte : une route de détail ne dit pas d'où l'on
 * vient. Un album ouvert depuis l'accueil vit dans la pile de l'accueil ; le
 * lire d'après sa route l'aurait rangé dans la bibliothèque, qui allumait alors
 * son onglet. Les réglages, ouverts d'où qu'on vienne, laissent de même allumé
 * l'onglet d'où on les a ouverts.
 */
fun openTabOf(entry: NavBackStackEntry?): OpenTab? = entry?.let { openTabOfRoot(rootOf(it)) }

private fun rootOf(entry: NavBackStackEntry): String? = entry.savedStateHandle[TAB_ROOT_KEY]
    ?: entry.destination.route?.takeIf { TopLevelDestination.ofRoot(it) != null }

/**
 * La clé sous laquelle une page qui ne commence aucune pile retient la racine
 * de la sienne.
 */
private const val TAB_ROOT_KEY = "waveflow.racineDeLaPile"

/**
 * Le contrôleur de navigation de l'application : celui de `rememberNavController`,
 * dont chaque page retient en s'ouvrant la racine de la pile où elle vit.
 *
 * La pile entière n'est pas lisible de dehors : `NavController.currentBackStack`
 * est réservé à la bibliothèque de navigation. Chaque page hérite donc la
 * racine de celle sur laquelle elle s'ouvre (`previousBackStackEntry`), de
 * proche en proche depuis la racine. Elle la garde dans son `SavedStateHandle`,
 * que la navigation sauve avec la pile quand on change d'onglet et restaure au
 * retour, comme le reste de l'état sauvé de la pile.
 *
 * Une racine ne commence sa pile qu'ouverte juste au-dessus de l'accueil, comme
 * le fait [switchTo]. Ailleurs, elle vit dans la pile où elle s'ouvre : l'écran
 * de connexion, ouvert depuis les réglages, est la route de l'onglet Serveur
 * mais reste dans la pile des réglages, et le retour y ramène. Compté comme
 * une seconde racine, il rendait la bascule vers l'accueil inopérante : elle
 * sauvait puis restaurait la pile même.
 *
 * La vue de la bibliothèque se retient aussi, dès qu'elle est ouverte, sur
 * l'entrée de l'accueil, toujours au fond de la pile ([selectTab]).
 */
@Composable
fun rememberTabNavController(): NavHostController {
    val navController = rememberNavController()
    DisposableEffect(navController) {
        val retenir = NavController.OnDestinationChangedListener { controller, _, _ ->
            val entry = controller.currentBackStackEntry ?: return@OnDestinationChangedListener
            val previous = controller.previousBackStackEntry
            val commenceSaPile = TopLevelDestination.ofRoot(entry.destination.route) != null &&
                (previous == null || previous.destination.route == Routes.HOME)
            if (!commenceSaPile && !entry.savedStateHandle.contains(TAB_ROOT_KEY)) {
                previous?.let(::rootOf)?.let { entry.savedStateHandle[TAB_ROOT_KEY] = it }
            }
            openTabOf(entry)?.takeIf { it.destination == TopLevelDestination.Library }?.let {
                controller.getBackStackEntry(Routes.HOME).savedStateHandle[LAST_LIBRARY_ROOT_KEY] = it.root
            }
        }
        navController.addOnDestinationChangedListener(retenir)
        onDispose { navController.removeOnDestinationChangedListener(retenir) }
    }
    return navController
}

/** L'onglet ouvert de ce contrôleur, relu à chaque changement de page. */
@Composable
fun NavHostController.openTabAsState(): State<OpenTab?> {
    val entry = currentBackStackEntryAsState()
    return remember(this) { derivedStateOf { openTabOf(entry.value) } }
}

/**
 * Toucher un onglet de la barre du bas.
 *
 * L'onglet déjà ouvert revient à la racine de sa pile ; un autre se rouvre
 * là où on l'avait laissé ([switchTo]).
 *
 * La bibliothèque a cinq racines, une par vue : elle se rouvre sur la dernière
 * ouverte, et non sur les titres, dont la pile n'était pas la sienne. Cette vue
 * se retient sur l'entrée de l'accueil, toujours au fond de la pile et sauvée
 * avec elle ([rememberTabNavController]).
 */
fun NavHostController.selectTab(destination: TopLevelDestination) {
    val open = currentOpenTab()
    when {
        open?.destination == destination -> popToRoot()
        destination == TopLevelDestination.Library ->
            switchTo(getBackStackEntry(Routes.HOME).savedStateHandle[LAST_LIBRARY_ROOT_KEY] ?: destination.route)
        else -> switchTo(destination.route)
    }
}

/** La clé sous laquelle l'accueil retient la dernière vue ouverte de la bibliothèque. */
private const val LAST_LIBRARY_ROOT_KEY = "waveflow.derniereVueDeLaBibliotheque"

/**
 * Toucher une vue de la bibliothèque, ou y mener d'ailleurs (« chercher sur le
 * serveur » depuis la recherche).
 *
 * La vue déjà ouverte revient à sa racine, comme un onglet de la barre du bas.
 */
fun NavHostController.selectLibraryTab(tab: LibraryTab) {
    if (currentOpenTab()?.root == tab.route) {
        popToRoot()
    } else {
        switchTo(tab.route)
    }
}

private fun NavHostController.currentOpenTab(): OpenTab? = openTabOf(currentBackStackEntry)

/**
 * Revient à la racine de la pile ouverte, page par page.
 *
 * Et non par `popBackStack(route)`, qui s'arrête à la dernière entrée de cette
 * route : l'écran de connexion, rouvert depuis les réglages au-dessus de
 * l'onglet Serveur, porte la même route que sa racine sans en être une. La
 * racine est la seule entrée de sa pile à n'avoir rien retenu, et elle est
 * toujours dessous.
 */
private fun NavHostController.popToRoot() {
    while (currentBackStackEntry?.savedStateHandle?.contains(TAB_ROOT_KEY) == true) {
        if (!popBackStack()) return
    }
}

/**
 * Bascule vers la pile qui commence à [root] : une seule racine d'onglet dans
 * la pile, au-dessus de l'accueil, et la pile quittée est sauvée pour être
 * restaurée au retour.
 *
 * Jamais vers la pile déjà ouverte : sauvée puis restaurée d'un même geste,
 * elle reviendrait telle quelle. L'accueil, destination de départ, le montrait
 * — toucher « Accueil » sur un album ouvert depuis l'accueil rouvrait l'album.
 * [selectTab] et [selectLibraryTab] traitent ce cas avant.
 */
private fun NavHostController.switchTo(root: String) {
    navigate(root) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
