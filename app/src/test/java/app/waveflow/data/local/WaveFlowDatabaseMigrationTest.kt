package app.waveflow.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Ce que devient une base déjà remplie quand l'application se met à jour.
 *
 * Une migration ratée ne se voit pas en développement — la base y est toujours
 * neuve — mais efface les playlists de qui utilisait la version précédente.
 * C'est le seul chemin du dépôt où une erreur détruit des données que personne
 * ne peut reconstruire.
 *
 * Le test ne passe pas par `MigrationTestHelper` : celui-ci cherche les schémas
 * dans les ressources d'un APK de test instrumenté, qui n'existe pas ici. Il
 * fait la même chose autrement — bâtir une base à l'ancienne version, puis
 * laisser Room migrer. **Room valide le schéma qu'il trouve à l'ouverture** :
 * une migration qui créerait une table même légèrement différente échouerait
 * ici, exactement comme elle échouerait chez l'utilisateur.
 */
@RunWith(RobolectricTestRunner::class)
class WaveFlowDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val fichier: File get() = context.getDatabasePath(BASE)

    @Before
    fun setUp() {
        listOf("", "-shm", "-wal").forEach { File(fichier.path + it).delete() }
    }

    @After
    fun tearDown() = setUp()

    @Test
    fun `la migration vers l'historique garde les playlists`() = runTest {
        creerBaseVersion1()

        val base = Room.databaseBuilder(context, WaveFlowDatabase::class.java, BASE)
            .addMigrations(WaveFlowDatabase.MIGRATION_1_2)
            .build()

        try {
            // La playlist d'avant la mise à jour est toujours là...
            assertEquals(NOM, base.playlistDao().observePlaylists().first().single().name)
            // ...et la table neuve répond, donc la migration est bien passée.
            base.playHistoryDao().record("local:1", playedAtMs = 10L)
            assertEquals(1, base.playHistoryDao().observeRecent(10).first().size)
        } finally {
            base.close()
        }
    }

    /**
     * Bâtit une base à la version 1, d'après le schéma que Room a exporté.
     *
     * Recopier ce schéma à la main dans le test le ferait diverger le jour où
     * la version 1 serait relue de travers ; le lire là où le compilateur
     * l'écrit garantit qu'on part bien de ce que l'utilisateur a sur son
     * appareil.
     */
    private fun creerBaseVersion1() {
        val schema = JSONObject(File(dossierDesSchemas(), "1.json").readText())
            .getJSONObject("database")
        val entites = schema.getJSONArray("entities")

        fichier.parentFile?.mkdirs()
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(fichier, null).use { base ->
            for (i in 0 until entites.length()) {
                val entite = entites.getJSONObject(i)
                val table = entite.getString("tableName")
                base.execSQL(entite.getString("createSql").pour(table))

                // Les index sont décrits à part du `createSql`. Les oublier
                // produit une base que Room refuse après migration : il compare
                // ce qu'il trouve à ce qu'il attend, index compris.
                val index = entite.optJSONArray("indices") ?: continue
                for (j in 0 until index.length()) {
                    base.execSQL(index.getJSONObject(j).getString("createSql").pour(table))
                }
            }
            // La table dont Room se sert pour reconnaître la version d'une base.
            // Sans elle, il croirait ouvrir une base neuve et ne migrerait rien.
            base.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            base.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)",
                arrayOf(schema.getString("identityHash")),
            )
            base.execSQL(
                "INSERT INTO playlists (id, name, createdAt, updatedAt) VALUES (1, ?, 10, 10)",
                arrayOf(NOM),
            )
            base.version = 1
        }
    }

    private fun String.pour(table: String): String = replace("\${TABLE_NAME}", table)

    private fun dossierDesSchemas(): File {
        val chemin = checkNotNull(System.getProperty("waveflow.schemas")) {
            "Le chemin des schémas Room manque : voir testOptions dans build.gradle.kts"
        }
        return File(chemin, WaveFlowDatabase::class.java.canonicalName!!)
    }

    private companion object {
        const val BASE = "migration-test.db"
        const val NOM = "Sur la route"
    }
}
