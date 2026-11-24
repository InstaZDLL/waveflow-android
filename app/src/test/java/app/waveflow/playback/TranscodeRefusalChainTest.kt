package app.waveflow.playback

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.ResolvingDataSource
import androidx.test.core.app.ApplicationProvider
import app.waveflow.model.StreamRendering
import app.waveflow.testing.remoteSong
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowSystemClock
import java.io.File
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Le 429 sur la vraie chaîne de lecture : ExoPlayer, cache, résolveur, enveloppe
 * et repli, tels que [PlaybackService] les assemble, face à un `MockWebServer`
 * qui refuse comme le serveur — `429` et `Retry-After: 1`, avant le premier
 * octet.
 *
 * Le chargement et le réseau tournent sur de vrais fils ; le lecteur vit sur des
 * boucles que Robolectric fige, et dont l'horloge n'avance que quand le test la
 * pousse. Chaque demande reçue note l'heure **de cette horloge** : c'est elle
 * qui dit si le lecteur a attendu ce que le serveur annonçait.
 *
 * Robolectric ne décode pas l'Opus : aucune piste ne joue. Ce qui s'éprouve ici,
 * c'est ce que la chaîne **demande** au serveur, et quand. Les renvois R9 et
 * R17 à R24 pointent vers `docs/deplacement-dans-un-transcodage.md`.
 */
@RunWith(RobolectricTestRunner::class)
class TranscodeRefusalChainTest {

    private val app: Context = ApplicationProvider.getApplicationContext()

    private lateinit var server: MockWebServer
    private lateinit var mediaCache: RemoteMediaCache
    private lateinit var lecteur: TranscodeSeekingPlayer

    /** Une demande de flux reçue, à l'heure de l'horloge du lecteur. */
    private data class Demande(val format: String?, val decalageMs: Long, val heureMs: Long)

    private val demandes: MutableList<Demande> = CopyOnWriteArrayList()

    /** Ce que le serveur répond à une demande de flux ; refuse tout par défaut. */
    @Volatile
    private var repondre: (Demande) -> MockResponse = { refus() }

    @Volatile
    private var nonFacturee = true

    private var replis = 0

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                val demande = Demande(
                    format = url.queryParameter("format"),
                    decalageMs = url.queryParameter("offset_ms")?.toLong() ?: 0L,
                    heureMs = SystemClock.uptimeMillis(),
                )
                demandes += demande
                return repondre(demande)
            }
        }
        server.start()

        File(app.cacheDir, "media-serveur").deleteRecursively()
        mediaCache = RemoteMediaCache(app)
        lecteur = assemblerLeLecteur(
            context = app,
            dataSourceFactory = mediaCache.dataSourceFactory(resolveur),
            liaisonNonFacturee = { nonFacturee },
            annoncerRepli = { replis++ },
            hasard = Random(SEMENCE),
        )
    }

    @After
    fun tearDown() {
        lecteur.release()
        ecouler()
        mediaCache.release()
        server.shutdown()
    }

    /** Le marqueur vers le serveur factice, rendu et décalage compris, comme le vrai résolveur. */
    private val resolveur = ResolvingDataSource.Resolver { dataSpec ->
        val trackId = trackIdOfRemoteUri(dataSpec.uri) ?: return@Resolver dataSpec
        val rendu = dataSpec.uri.query?.let { "?$it" }.orEmpty()
        dataSpec.withUri(server.url("/api/v2/stream/ticket-$trackId$rendu").toString().toUri())
    }

    private fun refus() = MockResponse().setResponseCode(429).setHeader("Retry-After", "1")

    private fun transcode(): MediaItem =
        remoteSong(id = "c07f8d98", durationMs = DUREE_MS).toMediaItem().withRendering(StreamRendering("opus", 96))

    private fun jouer() {
        lecteur.setMediaItems(listOf(transcode()))
        lecteur.prepare()
        lecteur.play()
    }

    private val transcodees get() = demandes.toList().filter { it.format != null }

    @Test
    fun `un refus se relance apres le delai annonce, et pas avant`() {
        // R18 : la politique de Media3 retentait aussitôt.
        repondre = { if (demandes.size == 1) refus() else MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) }
        jouer()

        attendre("la seconde demande") { demandes.size >= 2 }

        val (premiere, seconde) = demandes.toList()
        assertTrue(
            "relancée ${seconde.heureMs - premiere.heureMs} ms après le refus",
            seconde.heureMs - premiere.heureMs >= 1_000L,
        )
        assertEquals("la même qualité", "opus", seconde.format)
        assertNull("aucune erreur tant qu'on attend", lecteur.playerError)
    }

    @Test
    fun `sur une liaison facturee, l'abandon arrive apres trois relances et dit le serveur occupe`() {
        // R20, R22 et R24 : jamais l'original en données mobiles.
        nonFacturee = false
        jouer()

        attendre("l'abandon") { lecteur.playerError != null }

        assertEquals(TranscodeRefusalPolicy.RELANCES_MAX + 1, demandes.size)
        assertTrue("aucune demande de l'original", demandes.all { it.format == "opus" })
        assertEquals(PlaybackFailure.Busy, lecteur.playerError!!.toPlaybackFailure())
        assertEquals(0, replis)
    }

    @Test
    fun `sur une liaison non facturee, l'original vient apres les essais et se dit`() {
        // R22 et R24.
        repondre = { if (it.format == null) MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) else refus() }
        jouer()

        attendre("la demande de l'original") { demandes.any { it.format == null } }

        assertEquals(
            "quatre refus, puis l'original",
            List(TranscodeRefusalPolicy.RELANCES_MAX + 1) { "opus" } + listOf(null),
            demandes.map { it.format },
        )
        assertEquals(1, replis)
    }

    @Test
    fun `le repli ne passe ni par une erreur annoncee ni par un changement de piste`() {
        // R23 : sinon l'écran dirait « injoignable » avant de jouer. R5 :
        // l'historique compterait deux fois le morceau.
        repondre = { if (it.format == null) MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) else refus() }
        jouer()
        attendre("la piste chargée") { lecteur.mediaItemCount == 1 && demandes.isNotEmpty() }
        val erreurs = mutableListOf<PlaybackException>()
        val transitions = mutableListOf<Int>()
        lecteur.addListener(
            object : Player.Listener {
                override fun onPlayerErrorChanged(error: PlaybackException?) {
                    error?.let(erreurs::add)
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    transitions += reason
                }
            },
        )

        attendre("la demande de l'original") { demandes.any { it.format == null } }
        ecouler()

        assertEquals(emptyList<PlaybackException>(), erreurs)
        assertEquals(emptyList<Int>(), transitions)
    }

    @Test
    fun `le repli reprend a la position logique du saut refuse`() {
        // R22 : un saut à 2:13 refusé jusqu'au bout reprend l'original à 2:13,
        // pas au début du morceau.
        repondre = { if (it.format == null) MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) else refus() }
        jouer()
        attendre("la première demande") { demandes.isNotEmpty() }

        lecteur.seekTo(133_000L)
        attendre("la demande de l'original") { demandes.any { it.format == null } }
        ecouler()

        assertTrue("le saut a bien été refusé", transcodees.any { it.decalageMs == 133_000L })
        assertEquals(133_000L, lecteur.currentPosition)
    }

    @Test
    fun `des sauts rapproches ne demandent qu'un transcodage, a la cible du dernier`() {
        // R9. Entre deux sauts, on laisse au réseau le temps de partir — sans
        // avancer l'horloge du lecteur : sans coalescence, chaque saut aurait
        // sa demande.
        jouer()
        attendre("la première demande") { demandes.isNotEmpty() }

        for (cible in listOf(60_000L, 90_000L, 133_000L)) {
            lecteur.seekTo(cible)
            laisserPartir()
        }
        avancer(TranscodeSeekingPlayer.COALESCENCE_MS)
        attendre("la demande du saut") { transcodees.any { it.decalageMs > 0L } }
        laisserPartir()

        assertEquals(listOf(133_000L), transcodees.map { it.decalageMs }.filter { it > 0L })
    }

    @Test
    fun `avancer plusieurs fois de suite part une seule fois, cumule`() {
        // R9 : pendant l'attente, la position est déjà la cible, et chaque
        // « avancer » se compte depuis elle.
        jouer()
        attendre("la première demande") { demandes.isNotEmpty() }
        val pas = lecteur.seekForwardIncrement

        repeat(3) {
            lecteur.seekForward()
            ecouler()
        }
        avancer(TranscodeSeekingPlayer.COALESCENCE_MS)
        attendre("la demande du saut") { transcodees.any { it.decalageMs > 0L } }
        laisserPartir()

        assertEquals(listOf(3 * pas), transcodees.map { it.decalageMs }.filter { it > 0L })
    }

    @Test
    fun `un saut natif annule la relance en attente`() {
        // R9 : revenir au tout début d'un transcodage se fait nativement ; la
        // relance demandée juste avant n'a plus d'objet.
        jouer()
        attendre("la première demande") { demandes.isNotEmpty() }

        lecteur.seekTo(133_000L)
        ecouler()
        lecteur.seekTo(0L)
        avancer(TranscodeSeekingPlayer.COALESCENCE_MS)
        laisserPartir()

        assertEquals(emptyList<Long>(), transcodees.map { it.decalageMs }.filter { it > 0L })
    }

    @Test
    fun `une file reposee pendant l'attente rend la relance caduque`() {
        // R9 : poser une autre file n'est pas un saut, et n'annule rien en
        // passant ; c'est au départ que l'attente doit voir que sa piste n'est
        // plus là. Sinon la nouvelle piste partirait au milieu.
        jouer()
        attendre("la première demande") { demandes.isNotEmpty() }

        lecteur.seekTo(133_000L)
        ecouler()
        lecteur.setMediaItems(
            listOf(
                remoteSong(
                    id = "autre",
                    durationMs = DUREE_MS,
                ).toMediaItem().withRendering(StreamRendering("opus", 96)),
            ),
        )
        lecteur.prepare()
        avancer(TranscodeSeekingPlayer.COALESCENCE_MS)
        laisserPartir()

        assertEquals(emptyList<Long>(), transcodees.map { it.decalageMs }.filter { it > 0L })
    }

    @Test
    fun `un flux quitte pendant l'attente n'est jamais relance`() {
        // R21 : le début de la piste, refusé, attendait sa relance quand le
        // saut l'a remplacé.
        jouer()
        attendre("la première demande") { demandes.isNotEmpty() }

        lecteur.seekTo(133_000L)
        avancer(TranscodeSeekingPlayer.COALESCENCE_MS)
        attendre("la relance du saut") { transcodees.count { it.decalageMs == 133_000L } >= 2 }

        assertEquals("le début n'a été demandé qu'une fois", 1, transcodees.count { it.decalageMs == 0L })
    }

    @Test
    fun `un saut apres l'abandon relance le lecteur arrete sur son erreur`() {
        // R9 et R20 : sur une liaison facturée, le dernier refus arrête le
        // lecteur. Le saut qui suit — ou qui attendait déjà — doit repartir,
        // et non remplacer la piste d'un lecteur qui ne charge plus rien.
        nonFacturee = false
        jouer()
        attendre("l'abandon") { lecteur.playerError != null }

        lecteur.seekTo(133_000L)
        avancer(TranscodeSeekingPlayer.COALESCENCE_MS)
        attendre("la demande du saut") { transcodees.any { it.decalageMs == 133_000L } }
    }

    @Test
    fun `un saut apres un arret demande ne relance rien`() {
        // Le pendant du précédent : `stop` n'est pas une panne, le lecteur
        // reste arrêté.
        jouer()
        attendre("la première demande") { demandes.isNotEmpty() }
        lecteur.stop()
        ecouler()

        lecteur.seekTo(133_000L)
        avancer(TranscodeSeekingPlayer.COALESCENCE_MS)
        laisserPartir()

        assertEquals(emptyList<Long>(), transcodees.map { it.decalageMs }.filter { it > 0L })
    }

    /**
     * Écoule les messages de toutes les boucles encore vivantes, sans avancer
     * l'horloge : ExoPlayer tient sa machine à états sur un fil à lui.
     */
    private fun ecouler() {
        shadowOf(Looper.getMainLooper()).idle()
        ShadowLooper.getAllLoopers()
            .filter { it.thread.isAlive }
            .forEach { boucle -> runCatching { shadowOf(boucle).idle() } }
    }

    /** Avance l'horloge du lecteur, puis écoule ce qu'elle a rendu dû. */
    private fun avancer(ms: Long) {
        ShadowSystemClock.advanceBy(Duration.ofMillis(ms))
        ecouler()
    }

    /** Laisse aux fils réels — chargement, réseau — le temps d'agir, l'horloge du lecteur arrêtée. */
    private fun laisserPartir() {
        repeat(LAISSER_PARTIR_TOURS) {
            ecouler()
            Thread.sleep(PAUSE_MS)
        }
    }

    /**
     * Fait tourner le lecteur, son horloge par petits pas, jusqu'à [condition].
     * Bornée en temps réel : un état qui n'arriverait plus fait échouer le test.
     */
    private fun attendre(quoi: String, condition: () -> Boolean) {
        val echeance = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_S)
        while (System.nanoTime() < echeance) {
            avancer(PAS_MS)
            if (condition()) return
            Thread.sleep(PAUSE_MS)
        }
        fail("Délai dépassé en attendant : $quoi (demandes : ${demandes.toList()})")
    }

    private companion object {
        const val DUREE_MS = 245_000L
        const val SEMENCE = 57
        const val TIMEOUT_S = 20L
        const val PAS_MS = 50L
        const val PAUSE_MS = 5L
        const val LAISSER_PARTIR_TOURS = 20
    }
}
