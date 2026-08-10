package app.waveflow.ui.playlists

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.waveflow.model.Playlist
import app.waveflow.model.Song
import app.waveflow.testing.song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ce que le glisser-déposer ne peut pas prouver ailleurs.
 *
 * `DragStateTest` couvre l'arithmétique du geste, mais deux comportements
 * n'existent que dans la composition : les actions d'accessibilité, seul accès
 * au déplacement pour TalkBack et le clavier, et la restauration de l'ordre
 * affiché quand l'écriture échoue.
 */
@RunWith(RobolectricTestRunner::class)
// L'écran par défaut de Robolectric est trop court : l'en-tête et sa pochette
// le remplissent, et la `LazyColumn` ne compose alors qu'une partie des lignes
// — celles qui manquent sont absentes de l'arbre, pas seulement invisibles.
@Config(qualifiers = "w411dp-h2000dp-xhdpi")
class PlaylistDetailScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val playlist = Playlist(id = 1L, name = "Ma playlist")

    private val songs = listOf(
        song(id = 1L, title = "Alpha"),
        song(id = 2L, title = "Bravo"),
        song(id = 3L, title = "Charlie"),
    )

    /** Ordres transmis à `onReorder`, et le rappel de restauration du dernier. */
    private val ordersSent = mutableListOf<List<Song>>()
    private var lastFailureCallback: (() -> Unit)? = null

    private fun afficher(songs: List<Song> = this.songs) {
        compose.setContent {
            PlaylistDetailScreen(
                playlist = playlist,
                songs = songs,
                nowPlayingId = null,
                onSongClick = {},
                onRemoveSong = {},
                onReorder = { order, onFailure ->
                    ordersSent += order
                    lastFailureCallback = onFailure
                },
                onPlay = {},
                onShuffle = {},
            )
        }
    }

    @Test
    fun `descendre une ligne change l'ordre affiche et transmet le nouvel ordre`() {
        afficher()

        compose.deplacer("Alpha", "Descendre")

        assertEquals(listOf("Bravo", "Alpha", "Charlie"), compose.titresAffiches())
        assertEquals(
            listOf(listOf(2L, 1L, 3L)),
            ordersSent.map { order -> order.map { it.id } },
        )
    }

    @Test
    fun `monter une ligne change l'ordre affiche et transmet le nouvel ordre`() {
        afficher()

        compose.deplacer("Charlie", "Monter")

        assertEquals(listOf("Alpha", "Charlie", "Bravo"), compose.titresAffiches())
        assertEquals(
            listOf(listOf(1L, 3L, 2L)),
            ordersSent.map { order -> order.map { it.id } },
        )
    }

    @Test
    fun `les bords de la liste n'offrent que le deplacement possible`() {
        afficher()

        assertEquals(listOf("Descendre"), compose.actionsDe("Alpha"))
        assertEquals(listOf("Monter", "Descendre"), compose.actionsDe("Bravo"))
        assertEquals(listOf("Monter"), compose.actionsDe("Charlie"))
    }

    @Test
    fun `un morceau seul n'offre aucun deplacement`() {
        afficher(songs = songs.take(1))

        assertEquals(emptyList<String>(), compose.actionsDe("Alpha"))
    }

    @Test
    fun `une ecriture qui echoue ramene l'ordre affiche a celui de la base`() {
        afficher()

        compose.deplacer("Alpha", "Descendre")
        assertEquals(listOf("Bravo", "Alpha", "Charlie"), compose.titresAffiches())

        // Room n'émet rien quand l'écriture échoue : sans ce rappel, l'écran
        // resterait sur un ordre que la base ignore.
        val restaurer = requireNotNull(lastFailureCallback)
        compose.runOnUiThread(restaurer)
        compose.waitForIdle()

        assertEquals(listOf("Alpha", "Bravo", "Charlie"), compose.titresAffiches())
    }

    @Test
    fun `deux deplacements de suite s'enchainent depuis l'ordre courant`() {
        afficher()

        compose.deplacer("Alpha", "Descendre")
        compose.deplacer("Alpha", "Descendre")

        assertEquals(listOf("Bravo", "Charlie", "Alpha"), compose.titresAffiches())
        assertEquals(
            listOf(listOf(2L, 1L, 3L), listOf(2L, 3L, 1L)),
            ordersSent.map { order -> order.map { it.id } },
        )
    }

    // --- Lecture de l'arbre de sémantique -----------------------------------
    //
    // Les titres et les actions sont relus depuis l'arbre plutôt que par des
    // marqueurs posés dans l'écran : le test s'appuie sur ce que le système
    // d'accessibilité voit réellement, ce qui est justement l'objet du test.

    private val titresConnus get() = songs.map { it.title }.toSet()

    private fun ComposeContentTestRule.titresAffiches(): List<String> =
        onRoot().fetchSemanticsNode().collecter { node ->
            node.textes().filter { it in titresConnus }
        }

    private fun ComposeContentTestRule.actionsDe(titre: String): List<String> {
        val ligne = onRoot().fetchSemanticsNode()
            .collecter { node -> if (titre in node.textes()) listOf(node) else emptyList() }
            .lastOrNull()
            ?: error("ligne « $titre » absente de l'arbre")

        return ligne.config
            .getOrNull(SemanticsActions.CustomActions)
            .orEmpty()
            .map { it.label }
    }

    /** Déclenche l'action nommée [action] portée par la ligne [titre]. */
    private fun ComposeContentTestRule.deplacer(titre: String, action: String) {
        val ligne = onRoot().fetchSemanticsNode()
            .collecter { node -> if (titre in node.textes()) listOf(node) else emptyList() }
            .lastOrNull()
            ?: error("ligne « $titre » absente de l'arbre")

        val cible = ligne.config
            .getOrNull(SemanticsActions.CustomActions)
            .orEmpty()
            .firstOrNull { it.label == action }
            ?: error("action « $action » absente de la ligne « $titre »")

        assertTrue("l'action « $action » a échoué", runOnUiThread { cible.action() })
        waitForIdle()
    }

    private fun SemanticsNode.textes(): List<String> =
        config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    /** Parcourt l'arbre dans l'ordre d'affichage en concaténant [extraire]. */
    private fun <T> SemanticsNode.collecter(extraire: (SemanticsNode) -> List<T>): List<T> =
        extraire(this) + children.flatMap { it.collecter(extraire) }
}
