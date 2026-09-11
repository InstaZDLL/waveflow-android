package app.waveflow.playback

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.Util
import app.waveflow.model.StreamRendering
import app.waveflow.testing.remoteSong
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * L'enveloppe face à un lecteur factice dont on pilote l'état.
 *
 * Robolectric ne décode pas l'Opus : aucune piste n'y joue réellement. Ce qui
 * s'éprouve ici, c'est ce que l'enveloppe fait de ce que le lecteur enveloppé
 * lui dit — et ce qu'elle lui demande. Les renvois R1 à R13 pointent vers
 * `docs/deplacement-dans-un-transcodage.md`.
 */
@RunWith(RobolectricTestRunner::class)
class TranscodeSeekingPlayerTest {

    private val faux = FauxLecteur()
    private val lecteur = TranscodeSeekingPlayer(faux)

    private fun transcode(id: String = "a") =
        remoteSong(id = id, durationMs = DUREE_MS).toMediaItem().withRendering(StreamRendering("opus", 96))

    private fun original(id: String = "b") = remoteSong(id = id, durationMs = DUREE_MS).toMediaItem()

    private fun jouer(vararg items: MediaItem, index: Int = 0) {
        lecteur.setMediaItems(items.toList(), index, 0L)
        ecouler()
    }

    private fun ecouler() = shadowOf(Looper.getMainLooper()).idle()

    private val Player.fenetreCourante: Timeline.Window
        get() = currentTimeline.getWindow(currentMediaItemIndex, Timeline.Window())

    private val FauxLecteur.dernierDecalage: Long
        get() = streamOffsetOfRemoteUri(remplacements.last().second.localConfiguration!!.uri)

    @Test
    fun `un transcodage sans duree annonce celle du catalogue`() {
        // R3 : sans elle, le curseur de l'écran reste désactivé.
        jouer(transcode())

        assertEquals(DUREE_MS, lecteur.duration)
    }

    @Test
    fun `un transcodage est annonce deplacable, commande de saut comprise`() {
        // R4 : sans la commande, la notification et la voiture ne proposent
        // aucun saut, et celui d'un contrôleur est ignoré.
        jouer(transcode())

        assertTrue(lecteur.isCurrentMediaItemSeekable)
        assertTrue(lecteur.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
    }

    @Test
    fun `l'ordre aleatoire reste celui du lecteur enveloppe`() {
        // R1 : reconstruire la timeline au lieu de l'envelopper perdrait l'ordre
        // aléatoire — « suivant » suivrait l'ordre de la file.
        faux.ordreAleatoire = listOf(0, 2, 1)
        jouer(transcode("a"), transcode("b"), transcode("c"))

        lecteur.shuffleModeEnabled = true
        ecouler()

        assertEquals(2, lecteur.nextMediaItemIndex)
    }

    @Test
    fun `un original se deplace nativement`() {
        faux.deplacable = { true }
        jouer(original())

        lecteur.seekTo(30_000L)
        ecouler()

        assertEquals(listOf(0 to 30_000L), faux.sauts)
        assertEquals(0, faux.remplacements.size)
    }

    @Test
    fun `un transcodage entier en cache se deplace nativement`() {
        // R7 : relancer ce qui se déplace déjà rouvrirait un transcodage pour
        // rien, et contournerait le cache.
        faux.deplacable = { true }
        jouer(transcode())

        lecteur.seekTo(30_000L)
        ecouler()

        assertEquals(listOf(0 to 30_000L), faux.sauts)
        assertEquals(0, faux.remplacements.size)
    }

    @Test
    fun `un transcodage non deplacable se relance au decalage demande`() {
        jouer(transcode())

        lecteur.seekTo(133_000L)
        ecouler()

        val (index, item) = faux.remplacements.single()
        assertEquals(0, index)
        assertEquals(133_000L, streamOffsetOfRemoteUri(item.localConfiguration!!.uri))
        assertEquals(StreamRendering("opus", 96), renderingOfRemoteUri(item.localConfiguration!!.uri))
    }

    @Test
    fun `pendant la relance, la position est deja la cible`() {
        // R6 : le flux décalé n'a encore rien rendu, le curseur ne doit pas
        // revenir en arrière en l'attendant.
        jouer(transcode())

        lecteur.seekTo(133_000L)
        ecouler()

        assertEquals(133_000L, lecteur.currentPosition)
    }

    @Test
    fun `la position est le decalage plus la position dans le flux`() {
        // R2.
        jouer(transcode())
        lecteur.seekTo(133_000L)
        ecouler()

        faux.avancer(5_000L)

        assertEquals(138_000L, lecteur.currentPosition)
    }

    @Test
    fun `un segment annonce la duree du morceau entier`() {
        // R3 : le segment ne connaît que ce qui reste.
        faux.dureeUs = { item ->
            if (streamOffsetOfRemoteUri(item.localConfiguration!!.uri) > 0L) Util.msToUs(112_000L) else C.TIME_UNSET
        }
        jouer(transcode())

        lecteur.seekTo(133_000L)
        ecouler()

        assertEquals(DUREE_MS, lecteur.duration)
    }

    @Test
    fun `une fois un segment en cours, tout saut est une relance`() {
        // R7 : la timeline du segment commence au décalage ; un saut natif y
        // atterrirait d'autant plus loin.
        jouer(transcode())
        lecteur.seekTo(133_000L)
        ecouler()
        faux.deplacable = { true }

        lecteur.seekTo(60_000L)
        ecouler()

        assertEquals(2, faux.remplacements.size)
        assertEquals(60_000L, faux.dernierDecalage)
        assertEquals(0, faux.sauts.size)
    }

    @Test
    fun `un saut au-dela de la fin est borne au morceau`() {
        // R8 : le serveur refuse un décalage au-delà de la durée.
        jouer(transcode())

        lecteur.seekTo(DUREE_MS + 60_000L)
        ecouler()

        assertEquals(DUREE_MS - 1L, faux.dernierDecalage)
    }

    @Test
    fun `une relance n'est pas un changement de piste`() {
        // R5 : l'historique compterait deux fois une piste déjà écoutée.
        jouer(transcode())
        val transitions = mutableListOf<Int>()
        lecteur.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    transitions += reason
                }
            },
        )
        val avant = lecteur.fenetreCourante.uid

        lecteur.seekTo(133_000L)
        ecouler()

        assertEquals("la relance a bien eu lieu", 1, faux.remplacements.size)
        assertEquals(emptyList<Int>(), transitions)
        assertEquals(avant, lecteur.fenetreCourante.uid)
    }

    @Test
    fun `une relance s'annonce comme un saut a la position demandee`() {
        // R5 : le lecteur enveloppé, lui, annonce une piste retirée.
        jouer(transcode())
        val discontinuites = mutableListOf<Pair<Int, Long>>()
        lecteur.addListener(
            object : Player.Listener {
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    discontinuites += reason to newPosition.positionMs
                }
            },
        )

        lecteur.seekTo(133_000L)
        ecouler()

        assertEquals(listOf(Player.DISCONTINUITY_REASON_SEEK to 133_000L), discontinuites)
    }

    @Test
    fun `la piste exposee reste le marqueur sans decalage`() {
        // R10 : le décalage ne regarde que la chaîne de lecture.
        jouer(transcode())

        lecteur.seekTo(133_000L)
        ecouler()

        assertEquals(transcode(), lecteur.currentMediaItem)
    }

    @Test
    fun `precedent, passe le debut d'un segment, revient au debut du morceau`() {
        // R13 : à 2 s d'un segment commencé à 2:10, on est à 2:12. L'ExoPlayer,
        // sur sa position brute, reculerait d'une piste.
        jouer(original(), transcode(), index = 1)
        lecteur.seekTo(130_000L)
        ecouler()
        faux.avancer(2_000L)

        lecteur.seekToPrevious()
        ecouler()

        assertEquals("la piste courante ne change pas", 1, faux.currentMediaItemIndex)
        assertEquals(0L, faux.dernierDecalage)
    }

    @Test
    fun `precedent, au tout debut, recule d'une piste`() {
        // Le pendant du précédent : la réécriture ne doit pas empêcher de
        // reculer quand on est bien au début.
        jouer(original(), transcode(), index = 1)

        lecteur.seekToPrevious()
        ecouler()

        assertEquals(0, faux.currentMediaItemIndex)
    }

    @Test
    fun `reculer se calcule sur la position logique`() {
        // R13 : 2:18 moins cinq secondes, c'est 2:13 — pas le début du segment.
        jouer(transcode())
        lecteur.seekTo(130_000L)
        ecouler()
        faux.avancer(8_000L)

        lecteur.seekBack()
        ecouler()

        assertEquals(133_000L, faux.dernierDecalage)
    }

    @Test
    fun `repeter un titre joue depuis un segment repart du debut du morceau`() {
        // R12 : laisser l'ExoPlayer répéter le flux rejouerait les dernières
        // secondes du morceau sans fin.
        faux.dureeUs = { item ->
            if (streamOffsetOfRemoteUri(item.localConfiguration!!.uri) > 0L) Util.msToUs(112_000L) else C.TIME_UNSET
        }
        jouer(transcode())
        lecteur.repeatMode = Player.REPEAT_MODE_ONE
        lecteur.seekTo(133_000L)
        ecouler()

        faux.finirLeFlux()

        assertEquals(0L, faux.dernierDecalage)
        assertEquals(0L, lecteur.currentPosition)
    }

    @Test
    fun `repeter un titre joue depuis le debut ne relance rien`() {
        // Le pendant du précédent : sans segment, la répétition de l'ExoPlayer
        // est la bonne, et la remplacer couperait le son pour rien.
        faux.dureeUs = { Util.msToUs(DUREE_MS) }
        jouer(transcode())
        lecteur.repeatMode = Player.REPEAT_MODE_ONE

        faux.finirLeFlux()

        assertEquals(0, faux.remplacements.size)
    }

    @Test
    fun `une piste relancee qu'on quitte retrouve son marqueur nu`() {
        // R14 : sinon, y revenir — piste suivante, file rejouée — la ferait
        // repartir en plein milieu.
        faux.dureeUs = { item ->
            if (streamOffsetOfRemoteUri(item.localConfiguration!!.uri) > 0L) Util.msToUs(112_000L) else C.TIME_UNSET
        }
        jouer(transcode("a"), transcode("b"))
        lecteur.seekTo(133_000L)
        ecouler()

        faux.finirLeFlux()

        assertEquals("on est passé au morceau suivant", 1, faux.currentMediaItemIndex)
        assertEquals(0, faux.remplacements.last().first)
        assertEquals(0L, faux.dernierDecalage)
    }

    @Test
    fun `rejouer la piste courante depuis la file repart du debut du morceau`() {
        // `seekToDefaultPosition` : ce que fait un appui sur la ligne en cours
        // dans la file. Laissé à l'ExoPlayer, il revient au début du flux — le
        // milieu du morceau — en gardant le décalage.
        jouer(transcode())
        lecteur.seekTo(133_000L)
        ecouler()

        lecteur.seekToDefaultPosition(0)
        ecouler()

        assertEquals(0L, faux.dernierDecalage)
        assertEquals(0L, lecteur.currentPosition)
    }

    @Test
    fun `une position inconnue reste inconnue`() {
        // R2 : lui ajouter le décalage publierait un instant plausible et faux.
        assertEquals(C.TIME_UNSET, logicalPositionMs(C.TIME_UNSET, 133_000L, DUREE_MS))
    }

    @Test
    fun `une position logique ne depasse pas la fin du morceau`() {
        // R2 : un flux transcodé peut durer un peu plus que le catalogue.
        assertEquals(DUREE_MS, logicalPositionMs(112_400L, 133_000L, DUREE_MS))
    }

    private companion object {
        const val DUREE_MS = 245_000L
    }
}

/** Une piste du faux lecteur, avec ce que le vrai en saurait. */
private class Piste(val item: MediaItem, val deplacable: Boolean, val dureeUs: Long) {
    /** Neufs à chaque piste, comme ceux que l'ExoPlayer donne à une piste remplacée. */
    val fenetre = Any()
    val periode = Any()
}

/**
 * Un lecteur enveloppé dont on pilote l'état, comme l'ExoPlayer le tiendrait.
 *
 * Il se comporte comme le vrai là où l'enveloppe en dépend : une piste sans
 * longueur n'est pas déplaçable et perd ses commandes de saut, une piste
 * remplacée reçoit un **nouvel** identifiant, et sa timeline porte son propre
 * ordre aléatoire.
 */
private class FauxLecteur : SimpleBasePlayer(Looper.getMainLooper()) {

    /** Déplaçable si le vrai lecteur connaissait sa longueur ; un transcodage en direct ne l'est pas. */
    var deplacable: (MediaItem) -> Boolean = { !it.isRemoteTranscode() }
    var dureeUs: (MediaItem) -> Long = { C.TIME_UNSET }

    /** L'ordre de lecture aléatoire, par rangs ; celui de la file s'il n'est pas donné. */
    var ordreAleatoire: List<Int>? = null

    val sauts = mutableListOf<Pair<Int, Long>>()
    val remplacements = mutableListOf<Pair<Int, MediaItem>>()

    private var pistes = listOf<Piste>()
    private var index = 0
    private var positionMs = 0L
    private var melange = false

    /**
     * Avance la lecture, et laisse l'enveloppe l'apprendre.
     *
     * Un saut de position se signale en deux temps : la discontinuité tout de
     * suite, `onEvents` au passage suivant de la boucle. Entre les deux,
     * l'enveloppe tient encore des positions figées à l'ancienne valeur.
     */
    fun avancer(ms: Long) {
        positionMs = ms
        invalidateState()
        shadowOf(Looper.getMainLooper()).idle()
    }

    /**
     * Joue le flux courant jusqu'au bout, puis enchaîne comme le vrai lecteur :
     * la même piste si elle se répète, la suivante sinon.
     *
     * C'est le dépassement de la durée qui fait qualifier la discontinuité de
     * transition automatique, et non un drapeau posé à la main.
     */
    fun finirLeFlux() {
        val courante = pistes[index]
        positionMs = Util.usToMs(courante.dureeUs)
        invalidateState()
        if (repetition != Player.REPEAT_MODE_ONE) index += 1
        positionMs = 0L
        invalidateState()
        shadowOf(Looper.getMainLooper()).idle()
    }

    override fun getState(): State {
        val courante = pistes.getOrNull(index)
        val commandes = Player.Commands.Builder().addAllCommands().apply {
            if (courante?.deplacable != true) {
                removeAll(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_BACK, COMMAND_SEEK_FORWARD)
            }
        }.build()
        return State.Builder()
            .setAvailableCommands(commandes)
            .setPlaylist(TimelineDeTest(pistes, ordreAleatoire ?: pistes.indices.toList()), Tracks.EMPTY, null)
            .setCurrentMediaItemIndex(index)
            .setContentPositionMs(positionMs)
            .setPlaybackState(if (pistes.isEmpty()) STATE_IDLE else STATE_READY)
            .setPlayWhenReady(true, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setShuffleModeEnabled(melange)
            .setRepeatMode(repetition)
            .setMaxSeekToPreviousPositionMs(3_000L)
            .setSeekBackIncrementMs(5_000L)
            .build()
    }

    private var repetition = Player.REPEAT_MODE_OFF

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        repetition = repeatMode
        return Futures.immediateVoidFuture()
    }

    private fun piste(item: MediaItem) = Piste(item, deplacable(item), dureeUs(item))

    override fun handleSetMediaItems(
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<*> {
        pistes = mediaItems.map(::piste)
        index = startIndex.coerceAtLeast(0)
        positionMs = 0L
        return Futures.immediateVoidFuture()
    }

    override fun handleReplaceMediaItems(
        fromIndex: Int,
        toIndex: Int,
        mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<*> {
        remplacements += fromIndex to mediaItems.single()
        pistes = pistes.toMutableList().apply { this[fromIndex] = piste(mediaItems.single()) }
        if (fromIndex == index) positionMs = 0L
        return Futures.immediateVoidFuture()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        melange = shuffleModeEnabled
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        if (seekCommand == COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) {
            index = (index - 1).coerceAtLeast(0)
            this.positionMs = 0L
        } else {
            sauts += mediaItemIndex to positionMs
            index = mediaItemIndex
            this.positionMs = positionMs.takeIf { it != C.TIME_UNSET } ?: 0L
        }
        return Futures.immediateVoidFuture()
    }
}

/** La timeline du faux lecteur : une période par piste, et l'ordre aléatoire qu'on lui donne. */
private class TimelineDeTest(private val pistes: List<Piste>, private val ordre: List<Int>) : Timeline() {

    override fun getWindowCount(): Int = pistes.size

    override fun getPeriodCount(): Int = pistes.size

    override fun getNextWindowIndex(windowIndex: Int, repeatMode: Int, shuffleModeEnabled: Boolean): Int =
        if (shuffleModeEnabled && repeatMode == Player.REPEAT_MODE_OFF) {
            ordre.getOrNull(ordre.indexOf(windowIndex) + 1) ?: C.INDEX_UNSET
        } else {
            super.getNextWindowIndex(windowIndex, repeatMode, shuffleModeEnabled)
        }

    override fun getPreviousWindowIndex(windowIndex: Int, repeatMode: Int, shuffleModeEnabled: Boolean): Int =
        if (shuffleModeEnabled && repeatMode == Player.REPEAT_MODE_OFF) {
            ordre.getOrNull(ordre.indexOf(windowIndex) - 1) ?: C.INDEX_UNSET
        } else {
            super.getPreviousWindowIndex(windowIndex, repeatMode, shuffleModeEnabled)
        }

    override fun getFirstWindowIndex(shuffleModeEnabled: Boolean): Int =
        if (shuffleModeEnabled && ordre.isNotEmpty()) ordre.first() else super.getFirstWindowIndex(false)

    override fun getLastWindowIndex(shuffleModeEnabled: Boolean): Int =
        if (shuffleModeEnabled && ordre.isNotEmpty()) ordre.last() else super.getLastWindowIndex(false)

    override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
        val piste = pistes[windowIndex]
        return window.set(
            /* uid = */ piste.fenetre,
            /* mediaItem = */ piste.item,
            /* manifest = */ null,
            /* presentationStartTimeMs = */ C.TIME_UNSET,
            /* windowStartTimeMs = */ C.TIME_UNSET,
            /* elapsedRealtimeEpochOffsetMs = */ C.TIME_UNSET,
            /* isSeekable = */ piste.deplacable,
            /* isDynamic = */ false,
            /* liveConfiguration = */ null,
            /* defaultPositionUs = */ 0L,
            /* durationUs = */ piste.dureeUs,
            /* firstPeriodIndex = */ windowIndex,
            /* lastPeriodIndex = */ windowIndex,
            /* positionInFirstPeriodUs = */ 0L,
        )
    }

    override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
        val piste = pistes[periodIndex]
        return period.set(piste.periode, piste.periode, periodIndex, piste.dureeUs, 0L)
    }

    override fun getIndexOfPeriod(uid: Any): Int = pistes.indexOfFirst { it.periode == uid }

    override fun getUidOfPeriod(periodIndex: Int): Any = pistes[periodIndex].periode
}
