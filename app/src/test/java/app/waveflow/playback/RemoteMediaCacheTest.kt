package app.waveflow.playback

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.test.core.app.ApplicationProvider
import app.waveflow.model.StreamRendering
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    /**
     * Un résolveur qui rend une URL différente à chaque appel, comme le vrai.
     *
     * Le rendu et le décalage du marqueur suivent jusqu'au serveur, comme dans
     * [RemoteStreamResolver].
     */
    private val resolver = ResolvingDataSource.Resolver { dataSpec ->
        val trackId = trackIdOfRemoteUri(dataSpec.uri) ?: return@Resolver dataSpec
        ticketsDemandes++
        val rendu = dataSpec.uri.query?.let { "?$it" }.orEmpty()
        dataSpec.withUri(server.url("/api/v2/stream/ticket-$ticketsDemandes-$trackId$rendu").toString().toUri())
    }

    private fun lire(source: DataSource, spec: DataSpec): ByteArray {
        source.open(spec)
        return try {
            val tampon = ByteArray(1024)
            val sortie = java.io.ByteArrayOutputStream()
            while (true) {
                val lus = source.read(tampon, 0, tampon.size)
                if (lus == C.RESULT_END_OF_INPUT) break
                sortie.write(tampon, 0, lus)
            }
            sortie.toByteArray()
        } finally {
            source.close()
        }
    }

    /** Lit [octets] puis referme, comme le lecteur qui passe au morceau suivant. */
    private fun lireLeDebut(source: DataSource, spec: DataSpec, octets: Int) {
        source.open(spec)
        try {
            val tampon = ByteArray(octets)
            var lus = 0
            while (lus < octets) {
                val n = source.read(tampon, lus, octets - lus)
                if (n == C.RESULT_END_OF_INPUT) break
                lus += n
            }
        } finally {
            source.close()
        }
    }

    /** Le marqueur et la clé qu'une piste distante porte réellement dans la file. */
    private fun specDe(
        trackId: String,
        format: String = DEFAULT_FORMAT,
        bitrate: Int? = null,
        offsetMs: Long = 0L,
    ): DataSpec {
        val piste = MediaItem.Builder()
            .setUri("waveflow://track/$trackId".toUri())
            .build()
            .withRendering(StreamRendering(format, bitrate))
            .withStreamOffset(offsetMs)
        val configuration = checkNotNull(piste.localConfiguration)
        return DataSpec.Builder()
            .setUri(configuration.uri)
            .setKey(configuration.customCacheKey)
            .build()
    }

    /**
     * Répond comme `waveflow-server` (`src/media.rs`) : l'original se sert par
     * plages ; un transcodage en direct arrive par morceaux, sans longueur, et
     * refuse toute plage qui ne part pas du premier octet ; un segment est un
     * autre flux, qui commence à l'instant demandé.
     */
    private fun servirCommeLeServeur(contenu: ByteArray) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val debut = request.getHeader("Range")
                    ?.removePrefix("bytes=")
                    ?.substringBefore('-')
                    ?.toInt()
                    ?: 0
                val transcode = request.requestUrl?.queryParameter("format") != null
                val decalage = request.requestUrl?.queryParameter("offset_ms")?.toLong() ?: 0L
                return when {
                    transcode && debut > 0 -> MockResponse()
                        .setResponseCode(416)
                        .setHeader("Content-Range", "bytes */0")
                        .setHeader("Accept-Ranges", "none")

                    transcode && decalage > 0 -> MockResponse()
                        .setHeader("Accept-Ranges", "none")
                        .setChunkedBody(Buffer().write(segmentDe(decalage)), 4096)

                    transcode -> MockResponse()
                        .setHeader("Accept-Ranges", "none")
                        .setChunkedBody(Buffer().write(contenu), 4096)

                    debut > 0 -> MockResponse()
                        .setResponseCode(206)
                        .setHeader("Content-Range", "bytes $debut-${contenu.size - 1}/${contenu.size}")
                        .setBody(Buffer().write(contenu, debut, contenu.size - debut))

                    else -> MockResponse().setBody(Buffer().write(contenu))
                }
            }
        }
    }

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
    fun `un debit nul reutilise l'entree sans debit`() {
        // Le pendant du test précédent : deux façons d'exprimer le même rendu
        // ne doivent pas le télécharger deux fois.
        server.enqueue(MockResponse().setBody("original"))

        val factory = mediaCache.dataSourceFactory(resolver)

        val sansDebit = lire(factory.createDataSource(), specDe("piste-1"))
        val debitNul = lire(factory.createDataSource(), specDe("piste-1", bitrate = 0))

        assertEquals("original", String(sansDebit))
        assertEquals("original", String(debitNul))
        assertEquals("une seule requête réseau", 1, server.requestCount)
        assertEquals("un seul ticket réclamé", 1, ticketsDemandes)
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
    fun `un transcodage quitte en route se relit en entier`() {
        // On passe au morceau suivant avant la fin : le cache garde le début.
        // Le relire puis demander la suite, c'est demander une plage à un
        // transcodage en direct, qui la refuse — et Media3 ne retente pas un
        // 416. La piste tombait en erreur là où le cache s'arrêtait.
        val contenu = octetsAudio()
        servirCommeLeServeur(contenu)
        val factory = mediaCache.dataSourceFactory(resolver)

        lireLeDebut(factory.createDataSource(), specDe("piste-1", "opus", 96), OCTETS_ECOUTES)
        val relu = lire(factory.createDataSource(), specDe("piste-1", "opus", 96))

        assertArrayEquals(contenu, relu)
    }

    @Test
    fun `un transcodage lu jusqu'au bout reste en cache`() {
        // Le pendant du précédent. Sa longueur n'arrive qu'avec la fin du flux,
        // et c'est elle qui distingue le morceau entier d'un début abandonné.
        val contenu = octetsAudio()
        servirCommeLeServeur(contenu)
        val factory = mediaCache.dataSourceFactory(resolver)

        lire(factory.createDataSource(), specDe("piste-1", "opus", 96))
        val relu = lire(factory.createDataSource(), specDe("piste-1", "opus", 96))

        assertArrayEquals(contenu, relu)
        assertEquals("une seule requête réseau", 1, server.requestCount)
    }

    @Test
    fun `un original quitte en route reprend la ou le cache s'arrete`() {
        // L'original, lui, se sert par plages : son début en cache reste utile,
        // et le jeter ferait retélécharger ce qu'on a déjà.
        val contenu = octetsAudio()
        servirCommeLeServeur(contenu)
        val factory = mediaCache.dataSourceFactory(resolver)

        lireLeDebut(factory.createDataSource(), specDe("piste-1"), OCTETS_ECOUTES)
        val relu = lire(factory.createDataSource(), specDe("piste-1"))

        assertArrayEquals(contenu, relu)
        server.takeRequest()
        assertEquals(
            "seule la suite est redemandée",
            "bytes=$OCTETS_ECOUTES-${contenu.size - 1}",
            server.takeRequest().getHeader("Range"),
        )
    }

    @Test
    fun `un segment ne se sert pas du morceau deja en cache`() {
        // Le cache est posé avant le résolveur. Sous la clé du morceau, un
        // segment se verrait servir le morceau depuis 0:00 pendant que l'écran
        // afficherait l'instant demandé.
        val contenu = octetsAudio()
        servirCommeLeServeur(contenu)
        val factory = mediaCache.dataSourceFactory(resolver)
        lire(factory.createDataSource(), specDe("piste-1", "opus", 96))

        val segment = lire(factory.createDataSource(), specDe("piste-1", "opus", 96, offsetMs = 133_000L))

        assertArrayEquals(segmentDe(133_000L), segment)
        assertEquals("le segment est allé au serveur", 2, server.requestCount)
    }

    @Test
    fun `un segment ne s'ecrit pas dans le cache`() = runTest {
        // Lu jusqu'au bout, son reste rangé sous la clé du morceau serait
        // resservi à qui demande le morceau entier.
        servirCommeLeServeur(octetsAudio())
        val factory = mediaCache.dataSourceFactory(resolver)

        lire(factory.createDataSource(), specDe("piste-1", "opus", 96, offsetMs = 133_000L))

        assertEquals(0L, mediaCache.usedBytes())
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
        val spec = DataSpec.Builder().setUri("content://$AUTORITE/audio/42".toUri()).build()

        val lu = lire(factory.createDataSource(), spec)
        assertEquals("depuis le MediaStore", String(lu))

        // Le fichier change sous la même URI — un réencodage, une réécriture de
        // tags. Une lecture qui rendrait encore l'ancien contenu trahirait un
        // cache posé au-dessus des sources locales : l'arrangement Media3 le
        // plus courant, et celui qu'un « nettoyage » de cette fabrique
        // produirait le plus naturellement.
        fichier.writeBytes("réencodé depuis".toByteArray())
        val relu = lire(factory.createDataSource(), spec)

        assertEquals("réencodé depuis", String(relu))
        assertEquals("aucun ticket pour une piste locale", 0, ticketsDemandes)
        assertEquals("aucune requête réseau", 0, server.requestCount)
        fichier.delete()
    }

    @Test
    fun `la place annoncee est celle qu'occupent les pistes mises en cache`() = runTest {
        // Le chiffre que l'écran des réglages affiche : s'il ne suivait pas ce
        // qui entre réellement, il n'aiderait personne à décider de vider.
        val contenu = "des octets audio".toByteArray()
        server.enqueue(MockResponse().setBody(String(contenu)))
        assertEquals("un cache neuf n'occupe rien", 0L, mediaCache.usedBytes())

        lire(mediaCache.dataSourceFactory(resolver).createDataSource(), specDe("piste-1"))

        assertEquals(contenu.size.toLong(), mediaCache.usedBytes())
    }

    @Test
    fun `vider le cache renvoie la piste au reseau`() = runTest {
        // Le vidage se prouve par le comportement, pas par un compteur remis à
        // zéro : la même piste doit repartir chercher ses octets et son ticket.
        val contenu = "des octets audio"
        repeat(2) { server.enqueue(MockResponse().setBody(contenu)) }
        val factory = mediaCache.dataSourceFactory(resolver)

        lire(factory.createDataSource(), specDe("piste-1"))
        assertTrue("rien n'a été mis en cache", mediaCache.usedBytes() > 0L)

        mediaCache.clear()

        assertEquals(0L, mediaCache.usedBytes())
        val relu = lire(factory.createDataSource(), specDe("piste-1"))
        assertEquals(contenu, String(relu))
        assertEquals("la seconde lecture doit repartir au réseau", 2, server.requestCount)
        assertEquals("et redemander un ticket", 2, ticketsDemandes)
    }
}

private const val AUTORITE = "app.waveflow.test.audio"

/** Assez pour qu'un début lu laisse une vraie suite à demander. */
private const val OCTETS_ECOUTES = 8_000

private fun octetsAudio() = ByteArray(64_000) { (it % 251).toByte() }

/** Ce que le serveur rend d'un flux décalé : reconnaissable, et autre que le morceau. */
private fun segmentDe(offsetMs: Long) = "segment à partir de $offsetMs ms".toByteArray()

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
