package app.waveflow.playback

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.net.toUri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.test.core.app.ApplicationProvider
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Le cache de lecture, éprouvé sur la vraie chaîne Media3.
 *
 * Le comportement à démontrer n'est pas « ça met en cache » mais « ça ne
 * redemande rien » : ni octets, ni ticket. Un ticket est à usage unique et
 * change à chaque ouverture — c'est précisément ce qui rendrait un cache clé
 * sur l'URL parfaitement inutile.
 */
@RunWith(RobolectricTestRunner::class)
class RemoteMediaCacheTest {

    private lateinit var server: MockWebServer
    private lateinit var mediaCache: RemoteMediaCache

    /** Compte les tickets réclamés : un par ouverture réellement partie au réseau. */
    private var ticketsDemandes = 0

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        // Le cache applicatif de Robolectric est propre à chaque test.
        File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "media-serveur")
            .deleteRecursively()
        mediaCache = RemoteMediaCache(ApplicationProvider.getApplicationContext())
        ticketsDemandes = 0
    }

    @After
    fun tearDown() {
        mediaCache.release()
        server.shutdown()
    }

    /** Un résolveur qui rend une URL différente à chaque appel, comme le vrai. */
    private val resolver = ResolvingDataSource.Resolver { dataSpec ->
        val trackId = trackIdOfRemoteUri(dataSpec.uri) ?: return@Resolver dataSpec
        ticketsDemandes++
        dataSpec.withUri(server.url("/api/v2/stream/ticket-$ticketsDemandes-$trackId").toString().toUri())
    }

    private fun lire(source: DataSource, spec: DataSpec): ByteArray {
        source.open(spec)
        return try {
            val tampon = ByteArray(1024)
            val sortie = java.io.ByteArrayOutputStream()
            while (true) {
                val lus = source.read(tampon, 0, tampon.size)
                if (lus == androidx.media3.common.C.RESULT_END_OF_INPUT) break
                sortie.write(tampon, 0, lus)
            }
            sortie.toByteArray()
        } finally {
            source.close()
        }
    }

    private fun specDe(
        trackId: String,
        format: String = DEFAULT_FORMAT,
        bitrate: Int? = null,
    ) = DataSpec.Builder()
        .setUri("waveflow://track/$trackId".toUri())
        .setKey(cacheKeyOf(trackId, format, bitrate))
        .build()

    @Test
    fun `une seconde lecture ne redemande ni octets ni ticket`() {
        val contenu = "des octets audio".toByteArray()
        server.enqueue(MockResponse().setBody(String(contenu)))

        val factory = mediaCache.dataSourceFactory(resolver)

        val premier = lire(factory.createDataSource(), specDe("piste-1"))
        val second = lire(factory.createDataSource(), specDe("piste-1"))

        assertArrayEquals(contenu, premier)
        assertArrayEquals("le cache doit rendre les mêmes octets", contenu, second)
        assertEquals("une seule requête réseau", 1, server.requestCount)
        assertEquals("un seul ticket réclamé", 1, ticketsDemandes)
    }

    @Test
    fun `deux pistes distinctes ne se confondent pas`() {
        server.enqueue(MockResponse().setBody("piste une"))
        server.enqueue(MockResponse().setBody("piste deux"))

        val factory = mediaCache.dataSourceFactory(resolver)

        val une = lire(factory.createDataSource(), specDe("piste-1"))
        val deux = lire(factory.createDataSource(), specDe("piste-2"))

        assertEquals("piste une", String(une))
        assertEquals("piste deux", String(deux))
        assertEquals(2, ticketsDemandes)
    }

    @Test
    fun `deux debits d'une meme piste ne se recouvrent pas`() {
        // Le serveur sert la même piste en plusieurs rendus. Une clé qui les
        // confondrait servirait le premier téléchargé à qui demande l'autre.
        server.enqueue(MockResponse().setBody("opus 64"))
        server.enqueue(MockResponse().setBody("opus 128"))

        val factory = mediaCache.dataSourceFactory(resolver)

        val bas = lire(factory.createDataSource(), specDe("piste-1", "opus", 64))
        val haut = lire(factory.createDataSource(), specDe("piste-1", "opus", 128))

        assertEquals("opus 64", String(bas))
        assertEquals("opus 128", String(haut))
        assertEquals("deux rendus, deux tickets", 2, ticketsDemandes)
    }

    @Test
    fun `deux formats d'une meme piste ne se recouvrent pas`() {
        server.enqueue(MockResponse().setBody("original"))
        server.enqueue(MockResponse().setBody("transcode"))

        val factory = mediaCache.dataSourceFactory(resolver)

        val brut = lire(factory.createDataSource(), specDe("piste-1"))
        val transcode = lire(factory.createDataSource(), specDe("piste-1", "mp3"))

        assertEquals("original", String(brut))
        assertEquals("transcode", String(transcode))
    }

    @Test
    fun `un fichier local ne passe pas par le cache`() {
        // Il est déjà sur le disque : le recopier doublerait sa place.
        val fichier = File.createTempFile("local", ".bin").apply { writeBytes("local".toByteArray()) }
        val factory = mediaCache.dataSourceFactory(resolver)

        val lu = lire(
            factory.createDataSource(),
            DataSpec.Builder().setUri(fichier.toURI().toString().toUri()).build(),
        )

        assertEquals("local", String(lu))
        assertEquals("aucun ticket pour un fichier local", 0, ticketsDemandes)
        assertEquals("aucune requête réseau", 0, server.requestCount)
        fichier.delete()
    }

    @Test
    fun `un content ne passe pas non plus par le cache`() {
        // C'est le schéma réel des pistes de l'appareil : le `file://` du test
        // précédent n'apparaît nulle part dans l'application.
        val fichier = File.createTempFile("mediastore", ".bin")
            .apply { writeBytes("depuis le MediaStore".toByteArray()) }
        FichierFournisseur.fichier = fichier
        Robolectric.setupContentProvider(FichierFournisseur::class.java, AUTORITE)

        val factory = mediaCache.dataSourceFactory(resolver)
        val lu = lire(
            factory.createDataSource(),
            DataSpec.Builder().setUri("content://$AUTORITE/audio/42".toUri()).build(),
        )

        assertEquals("depuis le MediaStore", String(lu))
        assertEquals("aucun ticket pour une piste locale", 0, ticketsDemandes)
        assertEquals("aucune requête réseau", 0, server.requestCount)
        fichier.delete()
    }
}

private const val AUTORITE = "app.waveflow.test.audio"

/**
 * Sert un fichier temporaire derrière une URI `content://`.
 *
 * `ContentDataSource` ouvre un descripteur, pas un flux : un simple flux
 * enregistré dans le résolveur de Robolectric ne suffirait pas.
 */
class FichierFournisseur : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(
            requireNotNull(fichier) { "aucun fichier posé" },
            ParcelFileDescriptor.MODE_READ_ONLY,
        )

    override fun getType(uri: Uri): String = "application/octet-stream"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ) = null

    override fun insert(uri: Uri, values: ContentValues?) = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0

    companion object {
        var fichier: File? = null
    }
}
