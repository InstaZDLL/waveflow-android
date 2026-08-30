package app.waveflow.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Ce que l'historique retient d'une écoute.
 *
 * Robolectric fournit SQLite côté JVM : ces tests tournent sans émulateur.
 */
@RunWith(RobolectricTestRunner::class)
class PlayHistoryDaoTest {

    private lateinit var database: WaveFlowDatabase
    private lateinit var dao: PlayHistoryDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WaveFlowDatabase::class.java,
        ).build()
        dao = database.playHistoryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `reecouter une piste ajoute au compte au lieu de repartir de un`() = runTest {
        // Une seule instruction fait l'insertion et l'incrément. Lire puis
        // réécrire perdrait les écoutes concurrentes : le service enregistre
        // depuis sa portée pendant que l'application peut en enregistrer
        // d'autres.
        dao.record("local:1", playedAtMs = 100L)
        dao.record("local:1", playedAtMs = 500L)
        dao.record("local:1", playedAtMs = 900L)

        val ligne = dao.observeRecent(limit = 10).first().single()

        assertEquals(3, ligne.playCount)
        assertEquals(900L, ligne.lastPlayedAtMs)
    }

    @Test
    fun `les recentes viennent de la plus fraiche a la plus ancienne`() = runTest {
        dao.record("local:1", playedAtMs = 100L)
        dao.record("local:2", playedAtMs = 300L)
        dao.record("local:3", playedAtMs = 200L)

        val ids = dao.observeRecent(limit = 10).first().map { it.mediaId }

        assertEquals(listOf("local:2", "local:3", "local:1"), ids)
    }

    @Test
    fun `les plus ecoutees se departagent par la date`() = runTest {
        // Deux pistes à égalité de compte : c'est la plus récemment écoutée qui
        // passe devant. Sans ce second critère, l'ordre dépendrait de SQLite et
        // la liste sauterait d'un affichage à l'autre.
        dao.record("local:1", playedAtMs = 100L)
        dao.record("local:1", playedAtMs = 200L)
        dao.record("local:2", playedAtMs = 300L)
        dao.record("local:2", playedAtMs = 400L)
        dao.record("local:3", playedAtMs = 900L)

        val ids = dao.observeMostPlayed(limit = 10).first().map { it.mediaId }

        assertEquals(listOf("local:2", "local:1", "local:3"), ids)
    }

    @Test
    fun `l'historique ne distingue pas l'appareil du serveur`() = runTest {
        // Le `mediaId` est la seule identité que l'application donne
        // indifféremment aux deux sources : l'accueil peut donc proposer de
        // reprendre une piste distante comme une piste locale.
        dao.record("local:1", playedAtMs = 100L)
        dao.record("remote:uuid-a", playedAtMs = 200L)

        val ids = dao.observeRecent(limit = 10).first().map { it.mediaId }

        assertEquals(listOf("remote:uuid-a", "local:1"), ids)
    }

    @Test
    fun `la limite borne ce que l'accueil aura a afficher`() = runTest {
        repeat(5) { dao.record("local:$it", playedAtMs = it.toLong()) }

        assertEquals(2, dao.observeRecent(limit = 2).first().size)
    }
}
