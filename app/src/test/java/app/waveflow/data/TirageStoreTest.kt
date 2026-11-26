package app.waveflow.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.waveflow.suggestion.Raison
import app.waveflow.suggestion.SorteDeSuggestion
import app.waveflow.suggestion.SuggestionEpinglee
import app.waveflow.suggestion.TirageDuJour
import app.waveflow.suggestion.apresUnAppui
import app.waveflow.suggestion.epingler
import app.waveflow.suggestion.pour
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Le tirage de la suggestion du jour, tel qu'il survit au redémarrage (S9 à S11
 * de `docs/suggestion-du-jour.md`).
 *
 * Le vrai DataStore sur un vrai fichier, comme pour les préférences ; Robolectric
 * pour que le journal des échecs n'y lève pas.
 */
@RunWith(RobolectricTestRunner::class)
class TirageStoreTest {

    @get:Rule
    val dossier = TemporaryFolder()

    private val fichier: File get() = File(dossier.root, "suggestion_du_jour.preferences_pb")

    private val epinglee = SuggestionEpinglee(
        sorte = SorteDeSuggestion.Oubliee,
        songId = 42,
        raison = Raison(ecoutes = 7, derniereEcouteMs = 1_700_000_000_000, artisteAncre = "Nina Simone"),
    )

    /** Un magasin ouvert sur le fichier du test, refermé ensuite : un redémarrage entre deux appels. */
    private suspend fun <T> avecUnMagasin(bloc: suspend (DataStoreTirageStore, Fichier) -> T): T {
        val portee = CoroutineScope(Job() + Dispatchers.IO)
        try {
            val dataStore = PreferenceDataStoreFactory.create(scope = portee) { fichier }
            return bloc(DataStoreTirageStore(dataStore), Fichier { dataStore.edit(it) })
        } finally {
            portee.coroutineContext[Job]!!.cancelAndJoin()
        }
    }

    /** Écrire dans le fichier par-dessous le magasin, comme une autre version l'aurait fait. */
    private fun interface Fichier {
        suspend fun ecrire(transform: suspend (MutablePreferences) -> Unit)
    }

    @Test
    fun `sans rien d'enregistre il n'y a pas de tirage`() = runTest {
        assertNull(avecUnMagasin { magasin, _ -> magasin.tirage.first() })
    }

    @Test
    fun `le tirage et sa raison survivent au redemarrage`() = runTest {
        val tirage = TirageDuJour(jour = 20_418, essais = 2, epinglee = epinglee)
        avecUnMagasin { magasin, _ -> magasin.modifier { tirage } }

        assertEquals(tirage, avecUnMagasin { magasin, _ -> magasin.tirage.first() })
    }

    @Test
    fun `un nouvel enregistrement ne garde rien de l'ancienne raison`() = runTest {
        // Les champs de la raison sont facultatifs : une épinglée qui n'en a pas
        // ne doit pas hériter de ceux de la précédente.
        val sansRaison = TirageDuJour(20_419, 0, SuggestionEpinglee(SorteDeSuggestion.JamaisEcoutee, 7, Raison()))
        avecUnMagasin { magasin, _ ->
            magasin.modifier { TirageDuJour(20_418, 2, epinglee) }
            magasin.modifier { sansRaison }
        }

        assertEquals(sansRaison, avecUnMagasin { magasin, _ -> magasin.tirage.first() })
    }

    @Test
    fun `une sorte inconnue ou un nom demesure n'epinglent rien`() = runTest {
        val relu = avecUnMagasin { magasin, fichier ->
            magasin.modifier { TirageDuJour(20_418, 1, epinglee) }
            // Une version future aurait ajouté une sorte.
            fichier.ecrire { it[stringPreferencesKey("sorte")] = "Radio" }
            magasin.tirage.first()
        }
        assertEquals(TirageDuJour(20_418, 1, null), relu)

        val demesure = avecUnMagasin { magasin, fichier ->
            magasin.modifier { TirageDuJour(20_418, 1, epinglee) }
            fichier.ecrire { it[stringPreferencesKey("artiste_ancre")] = "x".repeat(10_000) }
            magasin.tirage.first()
        }
        assertNull(demesure?.epinglee?.raison?.artisteAncre)
        assertEquals(42L, demesure?.epinglee?.songId)
    }

    @Test
    fun `une cle d'un autre type ne leve pas`() = runTest {
        val relu = avecUnMagasin { magasin, fichier ->
            fichier.ecrire { it[stringPreferencesKey("jour")] = "hier" }
            magasin.tirage.first()
        }
        assertNull(relu)

        val autre = avecUnMagasin { magasin, fichier ->
            fichier.ecrire { it.clear() }
            fichier.ecrire { it[longPreferencesKey("jour")] = 20_418 }
            magasin.tirage.first()
        }
        // Le jour sans les appuis : illisible, le jour reprend à zéro.
        assertNull(autre)
    }

    @Test
    fun `un tirage d'un autre jour est perime`() {
        // S10
        val hier = TirageDuJour(20_417, 3, epinglee)
        assertEquals(TirageDuJour(20_418), hier.pour(20_418))
        assertEquals(hier, hier.pour(20_417))
        assertEquals(TirageDuJour(20_418), null.pour(20_418))
    }

    @Test
    fun `un appui ajoute un essai et efface l'epinglee, meme le lendemain`() {
        // S11
        assertEquals(TirageDuJour(20_418, 4), TirageDuJour(20_418, 3, epinglee).apresUnAppui(20_418))
        assertEquals(TirageDuJour(20_418, 1), TirageDuJour(20_417, 3, epinglee).apresUnAppui(20_418))
        assertEquals(TirageDuJour(20_418, 1), null.apresUnAppui(20_418))
    }

    @Test
    fun `un tirage ne s'epingle que sur l'etat d'ou il vient`() {
        // S11 : le tirage a été fait sur (20 418, 0, rien) ; un appui l'a précédé
        // dans le fichier. L'écriture du tirage ne doit pas le défaire.
        val depuis = TirageDuJour(20_418, 0)
        val apresLAppui = TirageDuJour(20_418, 1)

        assertEquals(apresLAppui, apresLAppui.epingler(depuis, epinglee, aujourdhui = 20_418))
        assertEquals(TirageDuJour(20_418, 0, epinglee), depuis.epingler(depuis, epinglee, aujourdhui = 20_418))
        // Rien d'enregistré, ou un tirage d'hier : c'est bien le premier du jour.
        assertEquals(TirageDuJour(20_418, 0, epinglee), null.epingler(depuis, epinglee, aujourdhui = 20_418))
        assertEquals(
            TirageDuJour(20_418, 0, epinglee),
            TirageDuJour(20_417, 5).epingler(depuis, epinglee, aujourdhui = 20_418),
        )
    }

    @Test
    fun `le tirage de la veille n'ecrase pas un appui du lendemain`() {
        // Tiré à 23 h 59 min 59 s, écrit après un appui fait à minuit passé.
        val lendemain = TirageDuJour(20_419, 1)

        assertEquals(lendemain, lendemain.epingler(TirageDuJour(20_418), epinglee, aujourdhui = 20_419))
    }

    @Test
    fun `un tirage date d'un jour pas encore arrive est perime`() {
        // L'horloge avançait d'un jour, puis a été remise à l'heure : croire ce
        // tirage bloquerait toute épinglée jusqu'à demain.
        val enAvance = TirageDuJour(20_419, 3)

        assertEquals(
            TirageDuJour(20_418, 0, epinglee),
            enAvance.epingler(TirageDuJour(20_418), epinglee, aujourdhui = 20_418),
        )
    }

    @Test
    fun `l'appui suivi de l'ecriture du tirage precedent garde l'appui, sur le vrai fichier`() = runTest {
        val relu = avecUnMagasin { magasin, _ ->
            val depuis = TirageDuJour(20_418, 0)
            magasin.modifier { it.apresUnAppui(20_418) }
            magasin.modifier { it.epingler(depuis, epinglee, aujourdhui = 20_418) }
            magasin.tirage.first()
        }

        assertEquals(TirageDuJour(20_418, 1), relu)
    }
}
