package app.waveflow.playback

import android.content.Context
import android.os.Looper
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.test.core.app.ApplicationProvider
import app.waveflow.model.StreamQuality
import app.waveflow.model.StreamRendering
import app.waveflow.testing.remoteSong
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * La réécriture des pistes à venir, sur un **vrai ExoPlayer** sous l'enveloppe,
 * tels que [assemblerLaLecture] les assemble.
 *
 * Un faux lecteur remplacerait « sur place » ; l'ExoPlayer, lui, ajoute la
 * nouvelle source puis retire l'ancienne, et c'est là que se joue l'ordre
 * aléatoire (Q9). Les renvois Q5 à Q9 pointent vers
 * `docs/qualite-automatique.md`.
 *
 * Robolectric ne décode pas l'audio : rien ne joue. Ce qui s'éprouve, c'est la
 * file — marqueurs, clés, ordre — et, face à un `MockWebServer`, ce que la
 * chaîne redemande au serveur.
 */
@RunWith(RobolectricTestRunner::class)
class PistesAVenirTest {

    private val app: Context = ApplicationProvider.getApplicationContext()

    private var server: MockWebServer? = null
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var lecteur: TranscodeSeekingPlayer
    private lateinit var pistesAVenir: PistesAVenir

    /** Les pistes dont le flux a été demandé au serveur, dans l'ordre. */
    private val demandes: MutableList<String> = CopyOnWriteArrayList()

    @After
    fun tearDown() {
        lecteur.release()
        ecouler()
        server?.shutdown()
    }

    /**
     * L'ExoPlayer, l'enveloppe et la réécriture, branchés comme dans
     * [assemblerLaLecture]. Monté ici plutôt que par lui pour tenir l'ExoPlayer
     * et poser un ordre aléatoire connu.
     *
     * @param espion enveloppe, s'il le faut, le lecteur que la réécriture voit.
     */
    private fun assembler(
        chaine: ResolvingDataSource.Factory? = null,
        espion: (Player) -> Player = { it },
    ) {
        val builder = ExoPlayer.Builder(app)
        chaine?.let { builder.setMediaSourceFactory(DefaultMediaSourceFactory(it)) }
        exoPlayer = builder.build()
        lecteur = TranscodeSeekingPlayer(exoPlayer)
        pistesAVenir = PistesAVenir(exoPlayer, espion(lecteur))
    }

    private fun piste(id: String, rendu: StreamRendering = WIFI): MediaItem =
        remoteSong(id = id, durationMs = DUREE_MS).toMediaItem().withRendering(rendu)

    private fun file(taille: Int, debut: Int = 0) {
        lecteur.setMediaItems((0 until taille).map { piste("p$it") }, debut, 0L)
        ecouler()
    }

    private fun rendus(): List<StreamRendering> = (0 until lecteur.mediaItemCount).map {
        renderingOfRemoteUri(lecteur.getMediaItemAt(it).localConfiguration!!.uri)
    }

    /** L'ordre aléatoire tel que le lecteur le publie. */
    private fun ordreAleatoire(): List<Int> {
        val timeline = lecteur.currentTimeline
        val ordre = mutableListOf<Int>()
        var index = timeline.getFirstWindowIndex(true)
        while (index != C.INDEX_UNSET) {
            ordre += index
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
        }
        return ordre
    }

    @Test
    fun `les pistes a venir prennent le rendu du nouveau reseau, la courante garde le sien`() {
        assembler()
        file(4)

        pistesAVenir.reecrire(MOBILE)
        ecouler()

        assertEquals(listOf(WIFI, MOBILE, MOBILE, MOBILE), rendus())
        // Q5 : la clé suit le marqueur, piste par piste.
        (0 until lecteur.mediaItemCount).forEach { index ->
            val config = lecteur.getMediaItemAt(index).localConfiguration!!
            val rendu = renderingOfRemoteUri(config.uri)
            assertEquals(cacheKeyOf("p$index", rendu.format, rendu.bitrate), config.customCacheKey)
        }
    }

    @Test
    fun `l'ordre aleatoire ne bouge pas`() {
        assembler()
        file(TAILLE_MELANGE, debut = 3)
        val tire = DefaultShuffleOrder(intArrayOf(3, 0, 6, 1, 7, 2, 5, 4), SEMENCE)
        exoPlayer.setShuffleOrder(tire)
        lecteur.shuffleModeEnabled = true
        ecouler()
        val avant = ordreAleatoire()

        pistesAVenir.reecrire(MOBILE)
        ecouler()

        // Le test ne vaut que si des pistes ont bien été remplacées.
        assertEquals(TAILLE_MELANGE - 1, rendus().count { it == MOBILE })
        assertEquals(avant, ordreAleatoire())
        // L'ordre tiré revient lui-même : c'est lui qui placera au hasard les
        // pistes ajoutées ensuite, pas l'ordre figé des remplacements.
        assertSame(tire, exoPlayer.shuffleOrder)
    }

    @Test
    fun `a chaque etape des remplacements, la suivante protegee suit la courante`() {
        // Q8 et Q9 : entre l'ajout de la nouvelle source et le retrait de
        // l'ancienne, l'ExoPlayer envoie au fil de lecture un ordre
        // intermédiaire. Si la nouvelle s'y glisse entre la courante et la
        // suivante qu'il lit déjà, il retire celle-ci et relance la courante :
        // la coupure de Q6, que l'ordre rétabli à la fin ne rattrape pas.
        val intermediaires = mutableListOf<Pair<Int, ShuffleOrder>>()
        assembler(
            espion = { enveloppe ->
                object : ForwardingPlayer(enveloppe) {
                    override fun replaceMediaItem(index: Int, mediaItem: MediaItem) {
                        // Ce que l'ExoPlayer tirera de l'ordre en vigueur : la
                        // nouvelle source ajoutée juste derrière l'ancienne.
                        intermediaires += index to exoPlayer.shuffleOrder.cloneAndInsert(index + 1, 1)
                        super.replaceMediaItem(index, mediaItem)
                    }
                }
            },
        )
        file(TAILLE_MELANGE, debut = COURANTE)
        lecteur.seekTo(COURANTE, DUREE_MS - 3_000L)
        ecouler()
        // Posé après le saut, que l'enveloppe fait en relançant la courante.
        exoPlayer.setShuffleOrder(DefaultShuffleOrder(intArrayOf(3, 0, 6, 1, 7, 2, 5, 4), SEMENCE_COUPURE))
        lecteur.shuffleModeEnabled = true
        ecouler()

        pistesAVenir.reecrire(MOBILE)
        ecouler()

        // Toutes, sauf la courante et la suivante protégée (la première).
        assertEquals(listOf(1, 2, 4, 5, 6, 7), intermediaires.map { it.first })
        intermediaires.forEach { (index, ordre) ->
            val courante = if (COURANTE > index) COURANTE + 1 else COURANTE
            assertEquals("en remplaçant la piste $index", 0, ordre.getNextIndex(courante))
        }
    }

    @Test
    fun `une piste commencee puis quittee garde son rendu`() {
        assembler()
        file(4)
        lecteur.seekTo(1, 0L)
        ecouler()
        lecteur.seekTo(2, 0L)
        ecouler()

        pistesAVenir.reecrire(MOBILE)
        ecouler()

        // Q7 : la première et la deuxième ont été jouées, la troisième joue.
        assertEquals(listOf(WIFI, WIFI, WIFI, MOBILE), rendus())
    }

    @Test
    fun `une piste relancee par l'enveloppe reste reconnue comme commencee`() {
        // Q7 : sauter au milieu d'un transcodage qui n'est pas la courante le
        // relance avant d'y aller — l'ExoPlayer n'a jamais joué la piste sous
        // son identifiant d'origine. La quitter la relance encore, au début
        // (R14). Seule l'identité que l'enveloppe lui garde la reconnaît.
        assembler()
        file(4)
        lecteur.seekTo(1, 30_000L)
        ecouler()
        // Lu sur l'ExoPlayer : l'enveloppe présente la piste sans son segment.
        val relancee = exoPlayer.currentTimeline.getWindow(1, Timeline.Window()).uid
        assertEquals(30_000L, streamOffsetOfRemoteUri(exoPlayer.getMediaItemAt(1).localConfiguration!!.uri))
        lecteur.seekTo(2, 0L)
        ecouler()
        assertEquals(0L, streamOffsetOfRemoteUri(exoPlayer.getMediaItemAt(1).localConfiguration!!.uri))
        assertNotEquals(relancee, exoPlayer.currentTimeline.getWindow(1, Timeline.Window()).uid)

        pistesAVenir.reecrire(MOBILE)
        ecouler()

        assertEquals(listOf(WIFI, WIFI, WIFI, MOBILE), rendus())
    }

    @Test
    fun `la suivante est protegee dans les dernieres secondes de la courante`() {
        // Q8 : le lecteur peut déjà la lire pour l'enchaîner.
        assembler()
        file(3)
        lecteur.seekTo(0, DUREE_MS - 3_000L)
        ecouler()

        pistesAVenir.reecrire(MOBILE)
        ecouler()

        assertEquals(listOf(WIFI, WIFI, MOBILE), rendus())
    }

    @Test
    fun `une suivante protegee une fois le reste, meme apres un retour en arriere`() {
        // Q8 : tenue pour commencée, elle ne se réécrit plus, quand bien même
        // la courante, ramenée au début, ne la protégerait plus.
        assembler()
        file(3)
        lecteur.seekTo(0, DUREE_MS - 3_000L)
        ecouler()
        pistesAVenir.reecrire(MOBILE)
        ecouler()
        lecteur.seekTo(0, 0L)
        ecouler()

        pistesAVenir.reecrire(WIFI)
        ecouler()
        pistesAVenir.reecrire(MOBILE)
        ecouler()

        // La troisième, jamais protégée, suit chaque bascule.
        assertEquals(listOf(WIFI, WIFI, MOBILE), rendus())
    }

    @Test
    fun `la courante ne s'interrompt pas et rien n'est redemande pour elle`() {
        // Q6, sur la vraie chaîne : le serveur reçoit la demande de la
        // courante, et ne répond jamais — elle charge encore.
        demarrerLeServeur()
        file(3)
        lecteur.prepare()
        lecteur.play()
        attendre("la demande de la courante") { demandes.isNotEmpty() }
        val courante = lecteur.currentMediaItem
        val evenements = mutableListOf<String>()
        lecteur.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    evenements += "transition"
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    evenements += "discontinuité $reason"
                }
            },
        )

        pistesAVenir.reecrire(MOBILE)
        ecouler()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        ecouler()

        assertEquals(listOf(WIFI, MOBILE, MOBILE), rendus())
        assertEquals(courante, lecteur.currentMediaItem)
        assertEquals(emptyList<String>(), evenements)
        assertEquals(listOf("p0"), demandes.toList())
        assertNotEquals(Player.STATE_IDLE, lecteur.playbackState)
    }

    private fun demarrerLeServeur() {
        val serveur = MockWebServer()
        serveur.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                demandes += request.requestUrl!!.pathSegments.last().removePrefix("ticket-")
                return MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
            }
        }
        serveur.start()
        server = serveur

        val resolveur = ResolvingDataSource.Resolver { dataSpec ->
            val trackId = trackIdOfRemoteUri(dataSpec.uri) ?: return@Resolver dataSpec
            val rendu = dataSpec.uri.query?.let { "?$it" }.orEmpty()
            dataSpec.withUri(serveur.url("/api/v2/stream/ticket-$trackId$rendu").toString().toUri())
        }
        assembler(ResolvingDataSource.Factory(DefaultHttpDataSource.Factory(), resolveur))
    }

    private fun attendre(quoi: String, condition: () -> Boolean) {
        val echeance = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_S)
        while (true) {
            ecouler()
            if (condition()) return
            if (System.nanoTime() >= echeance) break
            Thread.sleep(PAUSE_MS)
        }
        fail("Délai dépassé en attendant : $quoi")
    }

    /** Écoule les boucles encore vivantes, dont celle de lecture de l'ExoPlayer. */
    private fun ecouler() {
        ShadowLooper.getAllLoopers()
            .filter { it.thread.isAlive }
            .forEach { boucle -> runCatching { shadowOf(boucle).idle() } }
    }

    private companion object {
        /**
         * Le profil *Wi-Fi* réglé sur *Haute qualité* plutôt que sur l'original :
         * un transcodage porte sa durée dans le catalogue, ce dont la règle Q8
         * a besoin sur un lecteur qui ne décode rien.
         */
        val WIFI = StreamQuality.Haute.rendering
        val MOBILE = StreamQuality.Economie.rendering

        const val DUREE_MS = 200_000L
        const val TAILLE_MELANGE = 8
        const val SEMENCE = 86L

        /**
         * Une semence sous laquelle l'ordre par défaut glisse une piste
         * réécrite juste derrière la courante.
         */
        const val SEMENCE_COUPURE = 81L
        const val COURANTE = 3
        const val TIMEOUT_S = 15L
        const val PAUSE_MS = 5L
    }
}
