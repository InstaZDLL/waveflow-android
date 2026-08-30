package app.waveflow.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.waveflow.model.ThemeChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Ce que l'application retrouve au démarrage suivant.
 *
 * Le vrai DataStore, sur un vrai fichier : c'est l'écriture puis la relecture
 * qui se jouent ici, et un faux ne prouverait que sa propre cohérence.
 */
class PreferencesStoreTest {

    @get:Rule
    val dossier = TemporaryFolder()

    private val fichier: File get() = File(dossier.root, "preferences.preferences_pb")

    /**
     * Ouvre un magasin sur le fichier du test, puis le referme.
     *
     * DataStore refuse deux instances actives sur un même fichier ; la portée
     * doit donc être annulée **et terminée** avant la suivante. C'est aussi ce
     * qui rend le second appel fidèle à un relancement de l'application :
     * rien ne subsiste en mémoire d'une fois sur l'autre.
     */
    private suspend fun <T> avecUnMagasin(bloc: suspend (PreferencesStore) -> T): T {
        val portee = CoroutineScope(Job() + Dispatchers.IO)
        try {
            val magasin = DataStorePreferencesStore(
                PreferenceDataStoreFactory.create(scope = portee) { fichier },
            )
            return bloc(magasin)
        } finally {
            portee.coroutineContext[Job]!!.cancelAndJoin()
        }
    }

    @Test
    fun `sans rien de choisi l'application suit le systeme`() = runTest {
        // Le défaut n'est pas un troisième choix de politesse : tant que
        // l'utilisateur n'a rien dit, suivre l'appareil est ce qu'il attend.
        val theme = avecUnMagasin { it.preferences.first().theme }

        assertEquals(ThemeChoice.System, theme)
    }

    @Test
    fun `le theme choisi survit a la relecture`() = runTest {
        avecUnMagasin { it.setTheme(ThemeChoice.Dark) }

        // Un second magasin, comme au lancement suivant : c'est le fichier qui
        // doit porter le choix, et non l'objet qui l'a reçu.
        val theme = avecUnMagasin { it.preferences.first().theme }

        assertEquals(ThemeChoice.Dark, theme)
    }

    @Test
    fun `changer de theme se voit sans rouvrir le fichier`() = runTest {
        // Un flux, et non une lecture ponctuelle : l'écran qui change le thème
        // et celui qui l'applique ne se parlent pas, ils observent le même
        // flux. Sans cette propagation il faudrait redémarrer pour voir l'effet
        // de son propre choix.
        val vus = avecUnMagasin { magasin ->
            buildList {
                magasin.setTheme(ThemeChoice.Light)
                add(magasin.preferences.first().theme)
                magasin.setTheme(ThemeChoice.Dark)
                add(magasin.preferences.first().theme)
            }
        }

        assertEquals(listOf(ThemeChoice.Light, ThemeChoice.Dark), vus)
    }
}
