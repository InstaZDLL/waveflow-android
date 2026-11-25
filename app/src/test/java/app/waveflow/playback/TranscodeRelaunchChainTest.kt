package app.waveflow.playback

import android.content.Context
import android.os.Looper
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
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
import okio.Buffer
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
 * La coupure réseau s'y joue pour de bon. Robolectric ne décode pas l'Opus, mais
 * le moteur audio lit du PCM sans décodeur : le serveur factice sert donc ses
 * « transcodages » en WAV, que la chaîne aiguille comme un transcodage puisque
 * c'est le marqueur qui en décide. La lecture y avance, le tampon s'y vide, et
 * l'erreur d'une plage refusée tombe là où elle tomberait sur un appareil.
 * Seule différence : un WAV annonce sa longueur, si bien que l'ExoPlayer tient
 * pour déplaçable le flux d'un transcodage commencé au début — ce qui ne touche
 * pas à la coupure, mais fait qu'un saut y serait natif (R7).
 *
 * Les renvois R1 à R30 pointent vers `docs/deplacement-dans-un-transcodage.md`.
 */
@RunWith(RobolectricTestRunner::class)
class TranscodeRelaunchChainTest {

    private val app: Context = ApplicationProvider.getApplicationContext()

    private lateinit var server: MockWebServer
    private lateinit var mediaCache: RemoteMediaCache
    private lateinit var lecteur: TranscodeSeekingPlayer

    /** Une demande de flux reçue par le serveur factice, avec la plage demandée s'il y en a une. */
    private data class Demande(val piste: String, val format: String?, val decalageMs: Long, val plage: String?)

    private val demandes: MutableList<Demande> = CopyOnWriteArrayList()

    /** Ce que le serveur répond à une demande de flux ; ne répond jamais par défaut. */
    @Volatile
    private var repondre: (Demande) -> MockResponse = { MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) }

    @Volatile
    private var nonFacturee = false

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                val demande = Demande(
                    piste = url.pathSegments.last().removePrefix("ticket-"),
                    format = url.queryParameter("format"),
                    decalageMs = url.queryParameter("offset_ms")?.toLong() ?: 0L,
                    plage = request.getHeader("Range"),
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

    private fun original(id: String = PISTE): MediaItem = remoteSong(id = id, durationMs = DUREE_MS).toMediaItem()

    private val transcodees get() = demandes.toList().filter { it.format != null }

    /** Les demandes de reprise, sans les réouvertures à l'octet atteint que Media3 y ajoute. */
    private val reprises get() = transcodees.filter { it.decalageMs > 0L && it.plage == null }

    // --- L'ordre aléatoire (#86) ---

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

    // --- La coupure réseau (#60) ---

    /** Un transcodage en direct : ni longueur ni plages, comme le serveur. */
    private fun flux(secondes: Int, coupe: Boolean): MockResponse =
        MockResponse()
            .setHeader("Accept-Ranges", "none")
            .setChunkedBody(Buffer().write(wav(secondes)), TAILLE_DES_MORCEAUX)
            .apply { if (coupe) setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY) }

    /** Ce que le serveur répond à une plage sur un transcodage en cours (`src/media/mod.rs`). */
    private fun plageRefusee(): MockResponse = MockResponse().setResponseCode(416).setHeader("Accept-Ranges", "none")

    private fun Demande.rouvreEnRoute(): Boolean = plage != null && plage != "bytes=0-"

    /**
     * Le premier flux de la piste se coupe en son milieu, à 30 s ; Media3 le
     * rouvre à l'octet atteint, et le serveur refuse la plage d'un transcodage.
     * [reprise] dit ce que reçoivent les demandes suivantes, l'original compris.
     */
    private fun couperPuis(reprise: (Demande) -> MockResponse) {
        repondre = { demande ->
            when {
                demande.format != null && demande.rouvreEnRoute() -> plageRefusee()
                demande.decalageMs == 0L && demande.format != null -> flux(PREMIER_FLUX_S, coupe = true)
                else -> reprise(demande)
            }
        }
    }

    private fun jouer(item: MediaItem = transcode()) {
        lecteur.setMediaItems(listOf(item))
        lecteur.prepare()
        lecteur.play()
    }

    @Test
    fun `une coupure dans un transcodage reprend la ou le son s'est tu`() {
        // R25 : sans reprise, le lecteur s'arrêtait sur la plage refusée, pour
        // de bon, à la fin de ce qui avait été reçu.
        couperPuis { flux(PREMIER_FLUX_S, coupe = false) }
        jouer()

        attendre("la reprise") { reprises.isNotEmpty() }
        val reprise = reprises.single()
        assertTrue(
            "reprise à ${reprise.decalageMs} ms, la coupure est à 30 s",
            reprise.decalageMs in (COUPURE_MS - 1_000L)..COUPURE_MS,
        )
        assertEquals("même rendu", "opus", reprise.format)

        attendre("la lecture au-delà de la reprise") { lecteur.currentPosition > reprise.decalageMs + 5_000L }
        assertNull(lecteur.playerError)
        assertEquals(
            "le début n'a été demandé qu'une fois",
            1,
            transcodees.count {
                it.decalageMs == 0L &&
                    it.plage == null
            },
        )
    }

    @Test
    fun `une reprise ne passe ni par une erreur annoncee ni par un changement de piste`() {
        // R27 : l'écran dirait « injoignable » avant de reprendre, et
        // l'historique compterait deux fois le morceau (R5).
        couperPuis { flux(PREMIER_FLUX_S, coupe = false) }
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
        jouer()
        attendre("la piste posée") { transitions.isNotEmpty() }
        transitions.clear()

        attendre("la lecture au-delà de la reprise") { reprises.isNotEmpty() && lecteur.currentPosition > COUPURE_MS }
        ecouler()

        assertEquals(emptyList<PlaybackException>(), erreurs)
        assertEquals(emptyList<Int>(), transitions)
    }

    @Test
    fun `une coupure dans l'original se rouvre a l'octet atteint, comme avant`() {
        // R26 : l'original accepte les plages. Rien à relancer, et rien ne doit
        // l'être.
        //
        // Servi en morceaux, comme le transcodage : un corps à longueur fixe
        // coupé en son milieu n'a pas fait rouvrir le flux sur ce banc — une
        // seule demande, la lecture menée jusqu'à la fin —, cause non élucidée.
        val octets = wav(PREMIER_FLUX_S)
        repondre = { demande ->
            val debut = demande.plage?.removePrefix("bytes=")?.substringBefore('-')?.toInt() ?: 0
            val reponse = MockResponse()
                .setHeader("Accept-Ranges", "bytes")
                .setChunkedBody(Buffer().write(octets, debut, octets.size - debut), TAILLE_DES_MORCEAUX)
            if (debut == 0) {
                reponse.setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
            } else {
                reponse.setResponseCode(206)
                    .setHeader("Content-Range", "bytes $debut-${octets.size - 1}/${octets.size}")
            }
        }
        jouer(original())

        attendre("la lecture au-delà de la coupure") { lecteur.currentPosition > COUPURE_MS + 5_000L }

        assertTrue("rouvert par une plage", demandes.any { it.rouvreEnRoute() })
        assertEquals("aucune relance", emptyList<Demande>(), demandes.filter { it.decalageMs > 0L })
        assertNull(lecteur.playerError)
    }

    @Test
    fun `une coupure qui dure finit en panne du reseau, sans boucler`() {
        // R30 : la demande de reprise reste sans réponse — la liaison tombe
        // sitôt la demande partie. Media3 la retente jusqu'à son seuil, puis
        // rend une panne du réseau ; la reprise ne s'en mêle plus.
        couperPuis { MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST) }
        jouer()

        attendre("la panne") { lecteur.playerError != null }
        val apresLaPanne = demandes.size
        laisserPartir()

        assertEquals("une seule reprise", 1, reprises.map { it.decalageMs }.distinct().size)
        assertEquals("plus rien après la panne", apresLaPanne, demandes.size)
        assertEquals(PlaybackFailure.Unreachable, lecteur.playerError!!.toPlaybackFailure())
    }

    @Test
    fun `un flux qui coupe sans cesse s'arrete a la troisieme reprise`() {
        // R30 : chaque reprise ne rend qu'une seconde avant de couper de
        // nouveau ; trois reprises sans écoute, et la quatrième coupure remonte.
        couperPuis { flux(2, coupe = true) }
        jouer()

        attendre("la panne") { lecteur.playerError != null }
        laisserPartir()

        assertEquals(TranscodeSeekingPlayer.REPRISES_SANS_ECOUTE_MAX, reprises.size)
        assertEquals(PlaybackFailure.Unreachable, lecteur.playerError!!.toPlaybackFailure())
    }

    @Test
    fun `une reprise refusee jusqu'au bout reprend l'original a la position de la coupure`() {
        // R29 : la reprise est une demande de transcodage ; refusée, elle suit
        // les règles du 429, repli compris, à la position où le son s'est tu.
        nonFacturee = true
        val octets = wav(DUREE_MS.toInt() / 1_000)
        couperPuis { demande ->
            if (demande.format == null) {
                // L'original, plages comprises, comme le serveur.
                val debut = demande.plage?.removePrefix("bytes=")?.substringBefore('-')?.toInt() ?: 0
                MockResponse()
                    .setResponseCode(if (debut == 0) 200 else 206)
                    .setHeader("Accept-Ranges", "bytes")
                    .setHeader("Content-Range", "bytes $debut-${octets.size - 1}/${octets.size}")
                    .setBody(Buffer().write(octets, debut, octets.size - debut))
            } else {
                MockResponse().setResponseCode(429).setHeader("Retry-After", "1")
            }
        }
        jouer()

        // L'original se déplace nativement : la plage demandée dit l'instant
        // d'où il repart, à l'octet près.
        attendre("l'original rouvert à la coupure") { demandes.any { it.format == null && it.plage != null } }
        val reprise = reprises.first()
        val debut = demandes.first { it.format == null && it.plage != null }.plage!!
            .removePrefix("bytes=").substringBefore('-').toLong()
        attendre("la lecture de l'original") { lecteur.currentPosition > reprise.decalageMs + 1_000L }

        assertEquals("quatre demandes de reprise refusées", TranscodeRefusalPolicy.RELANCES_MAX + 1, reprises.size)
        assertEquals(
            "l'original repart de la coupure",
            TAILLE_ENTETE + reprise.decalageMs * FREQUENCE * OCTETS_PAR_ECHANTILLON / 1_000L,
            debut,
        )
        assertNull(lecteur.playerError)
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
     * Laisse au lecteur et aux fils réels le temps d'agir encore : une demande
     * de trop arriverait là. Vingt secondes d'horloge du lecteur — au-delà du
     * plus long délai de relance de Media3 (5 s) — et deux secondes réelles,
     * pour que le réseau ait le temps de porter une demande partie au dernier
     * pas. Une borne de temps, pas un signal : une demande plus lente encore
     * échapperait à l'assertion.
     */
    private fun laisserPartir() {
        repeat(LAISSER_PARTIR_TOURS) {
            avancer(LAISSER_PARTIR_PAS_MS)
            Thread.sleep(LAISSER_PARTIR_PAUSE_MS)
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
        fail(
            "Délai dépassé en attendant : $quoi (position ${lecteur.currentPosition}, " +
                "erreur ${lecteur.playerError}, demandes : ${demandes.toList()})",
        )
    }

    private companion object {
        const val DUREE_MS = 245_000L
        const val PISTE = "c07f8d98"
        const val FILE = 30
        const val SEMENCE = 60
        const val TIMEOUT_S = 20L
        const val PAS_MS = 50L
        const val PAUSE_MS = 5L
        const val LAISSER_PARTIR_TOURS = 40
        const val LAISSER_PARTIR_PAS_MS = 500L
        const val LAISSER_PARTIR_PAUSE_MS = 50L

        /** Le premier flux dure une minute, et se coupe en son milieu. */
        const val PREMIER_FLUX_S = 60
        const val COUPURE_MS = PREMIER_FLUX_S * 1_000L / 2
        const val TAILLE_DES_MORCEAUX = 4_096
    }
}

/**
 * Un flux WAV PCM 16 bits mono à 8 kHz, muet, de [secondes] secondes : ce que
 * le moteur audio lit sous Robolectric sans décodeur.
 */
private fun wav(secondes: Int): ByteArray {
    val donnees = FREQUENCE * OCTETS_PAR_ECHANTILLON * secondes
    val entete = ByteBuffer.allocate(TAILLE_ENTETE).order(ByteOrder.LITTLE_ENDIAN)
        .put("RIFF".toByteArray()).putInt(TAILLE_ENTETE - 8 + donnees).put("WAVE".toByteArray())
        .put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
        .putInt(FREQUENCE).putInt(FREQUENCE * OCTETS_PAR_ECHANTILLON)
        .putShort(OCTETS_PAR_ECHANTILLON.toShort()).putShort(16)
        .put("data".toByteArray()).putInt(donnees)
    return entete.array() + ByteArray(donnees)
}

private const val FREQUENCE = 8_000
private const val OCTETS_PAR_ECHANTILLON = 2
private const val TAILLE_ENTETE = 44
