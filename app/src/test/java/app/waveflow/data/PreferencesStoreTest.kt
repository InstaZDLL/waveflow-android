package app.waveflow.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.waveflow.model.ThemeChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException

/**
 * Ce que l'application retrouve au démarrage suivant.
 *
 * Le vrai DataStore, sur un vrai fichier : c'est l'écriture puis la relecture
 * qui se jouent ici, et un faux ne prouverait que sa propre cohérence.
 *
 * Robolectric parce que le magasin journalise ses échecs de lecture : sans lui,
 * `android.util.Log` lève au lieu d'écrire, et l'erreur se déguise en panne du
 * code testé.
 */
@RunWith(RobolectricTestRunner::class)
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

    /**
     * Un magasin dont la lecture échoue [echecs] fois avant de rendre [theme].
     *
     * Le `DataStore` est une interface : le faux ici n'est pas un raccourci de
     * test, c'est la seule façon de provoquer une panne de lecture qu'un vrai
     * fichier ne produira pas sur commande.
     */
    private fun magasinDefaillant(echecs: Int, theme: ThemeChoice): PreferencesStore {
        var tentatives = 0
        val flux = flow {
            if (tentatives++ < echecs) throw IOException("disque indisponible")
            emit(preferencesOf(stringPreferencesKey("theme") to theme.name))
        }
        return DataStorePreferencesStore(
            object : DataStore<Preferences> {
                override val data: Flow<Preferences> = flux
                override suspend fun updateData(
                    transform: suspend (Preferences) -> Preferences,
                ): Preferences = throw UnsupportedOperationException("lecture seule")
            },
        )
    }

    @Test
    fun `une lecture en echec rend le defaut puis se rattrape`() = runTest {
        // Sans reprise, la collecte se termine sur l'erreur et le partage en
        // aval se fige : l'utilisateur pourrait changer de thème sans que rien
        // ne bouge, sa préférence écrite mais jamais relue.
        val magasin = magasinDefaillant(echecs = 1, theme = ThemeChoice.Dark)

        val vus = magasin.preferences.take(2).toList().map { it.theme }

        assertEquals(listOf(ThemeChoice.System, ThemeChoice.Dark), vus)
    }

    @Test
    fun `une lecture durablement en echec laisse l'application sur le defaut`() = runTest {
        // Et ne la fait pas tomber : la portée du ViewModel n'a pas de
        // gestionnaire d'exception, une erreur qui remonterait jusqu'à elle
        // emporterait l'application entière — pour un thème.
        val magasin = magasinDefaillant(echecs = Int.MAX_VALUE, theme = ThemeChoice.Dark)

        // `toList` rend la main : le flux se termine au lieu de reprendre sans
        // fin. Et il n'aura rien laissé passer d'autre que le défaut.
        val vus = magasin.preferences.toList().map { it.theme }

        assertEquals(setOf(ThemeChoice.System), vus.toSet())
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
