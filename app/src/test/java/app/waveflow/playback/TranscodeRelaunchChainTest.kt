package app.waveflow.playback

import android.content.Context
import android.os.Looper
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
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
 * Les relances sur la vraie chaîne de lecture, telle que [PlaybackService]
 * l'assemble (`assemblerLeLecteur`), face à un `MockWebServer`.
 *
 * Le lecteur factice de `TranscodeSeekingPlayerTest` remplace une piste « sur
 * place » ; l'ExoPlayer, lui, ajoute la nouvelle source puis retire l'ancienne,
 * et c'est ce geste qui dérange l'ordre aléatoire. Il ne se voit qu'ici.
 *
 * Les renvois R1 à R14 pointent vers `docs/deplacement-dans-un-transcodage.md`.
 */
@RunWith(RobolectricTestRunner::class)
class TranscodeRelaunchChainTest {

    private val app: Context = ApplicationProvider.getApplicationContext()

    private lateinit var server: MockWebServer
    private lateinit var mediaCache: RemoteMediaCache
    private lateinit var lecteur: TranscodeSeekingPlayer

    /** Une demande de flux reçue par le serveur factice. */
    private data class Demande(val piste: String, val format: String?, val decalageMs: Long)

    private val demandes: MutableList<Demande> = CopyOnWriteArrayList()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                demandes += Demande(
                    piste = url.pathSegments.last().removePrefix("ticket-"),
                    format = url.queryParameter("format"),
                    decalageMs = url.queryParameter("offset_ms")?.toLong() ?: 0L,
                )
                // Rien ne joue : seul compte ce que la chaîne demande.
                return MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
            }
        }
        server.start()

        File(app.cacheDir, "media-serveur").deleteRecursively()
        mediaCache = RemoteMediaCache(app)
        lecteur = assemblerLeLecteur(
            context = app,
            dataSourceFactory = mediaCache.dataSourceFactory(resolveur),
            liaisonNonFacturee = { false },
            annoncerRepli = {},
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

    private fun transcode(id: String = PISTE): MediaItem =
        remoteSong(id = id, durationMs = DUREE_MS).toMediaItem().withRendering(StreamRendering("opus", 96))

    /**
     * Une file assez longue pour qu'un ordre dérangé ne retombe pas sur l'ancien
     * par hasard : l'ExoPlayer insère la nouvelle source à une place tirée
     * parmi `n + 1`, et l'ordre ne survit que si elle tombe juste avant ou juste
     * après l'ancienne — 2 chances sur 31 par remplacement.
     */
    private fun jouerEnAleatoire() {
        lecteur.setMediaItems(listOf(transcode()) + List(FILE - 1) { transcode("autre-$it") })
        lecteur.shuffleModeEnabled = true
        lecteur.prepare()
        lecteur.play()
        attendre("la première demande") { demandes.isNotEmpty() }
    }

    /** L'ordre de lecture aléatoire tel que le voit la session, par identifiant de piste. */
    private fun ordreAleatoire(): List<String> {
        val timeline = lecteur.currentTimeline
        val ordre = mutableListOf<String>()
        var index = timeline.getFirstWindowIndex(true)
        while (index != C.INDEX_UNSET) {
            ordre += timeline.getWindow(index, Timeline.Window()).mediaItem.mediaId
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
        }
        return ordre
    }

    @Test
    fun `une relance en aleatoire garde l'ordre tire`() {
        // R1 et R12 : la relance reste sur la piste, et ce qui la suit reste
        // ce qui la suivait. Trois relances, pour qu'un tirage chanceux ne
        // fasse pas passer le test sans le correctif.
        jouerEnAleatoire()
        val avant = ordreAleatoire()

        for (cible in listOf(60_000L, 133_000L, 200_000L)) {
            lecteur.seekTo(cible)
            avancer(TranscodeSeekingPlayer.COALESCENCE_MS)
            attendre("la relance à $cible") { demandes.any { it.piste == PISTE && it.decalageMs == cible } }

            assertEquals("après la relance à $cible", avant, ordreAleatoire())
        }
    }

    @Test
    fun `une piste relancee qu'on quitte garde sa place dans l'ordre tire`() {
        // R14 : la piste quittée retrouve son marqueur nu par un remplacement,
        // qui dérangeait l'ordre comme une relance. Trois pistes quittées.
        jouerEnAleatoire()
        val avant = ordreAleatoire()

        repeat(3) { tour ->
            val piste = lecteur.currentMediaItem!!.mediaId.removePrefix("remote:")
            lecteur.seekTo(133_000L)
            avancer(TranscodeSeekingPlayer.COALESCENCE_MS)
            attendre("la relance de $piste") { demandes.any { it.piste == piste && it.decalageMs == 133_000L } }

            lecteur.seekToNextMediaItem()
            ecouler()

            assertEquals("après avoir quitté la piste n° ${tour + 1}", avant, ordreAleatoire())
        }
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
        const val PISTE = "c07f8d98"
        const val FILE = 30
        const val SEMENCE = 86
        const val TIMEOUT_S = 20L
        const val PAS_MS = 50L
        const val PAUSE_MS = 5L
    }
}
