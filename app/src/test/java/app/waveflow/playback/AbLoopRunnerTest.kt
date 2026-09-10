package app.waveflow.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le rembobinage, éprouvé sans lecteur.
 *
 * La position et le `seek` sont reçus par [AbLoopRunner] plutôt que pris sur un
 * `Player` : c'est ce qui rend la boucle mesurable ici. Sous Robolectric, faute
 * de codec, la position n'avance pas — un test qui passerait par un vrai lecteur
 * ne prouverait que sa propre immobilité.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AbLoopRunnerTest {

    private val loop = AbLoop()

    /** Les rembobinages demandés, dans l'ordre. */
    private val seeks = mutableListOf<Long>()

    /** La position de lecture, que le test avance à la main. */
    private var position = 0L

    /** La lecture avance, sauf quand un test dit le contraire. */
    private val enLecture = MutableStateFlow(true)

    /**
     * Combien de fois la position a été demandée.
     *
     * C'est la seule trace observable des réveils : à la pause, ce qu'on veut
     * n'est pas « aucun rembobinage » — ce serait vrai d'une boucle qui tourne
     * à vide — mais **plus aucun échantillon**.
     */
    private var lectures = 0

    /**
     * Le rembobinage **déplace la position**, comme le ferait le lecteur.
     *
     * Ce n'est pas un détail de confort : un faux qui note le `seek` sans
     * bouger laisserait la position au-delà de B, et la surveillance
     * rembobinerait sans fin. Le test compterait alors des tours que le vrai
     * lecteur ne fait pas.
     */
    private fun TestScope.runner() = AbLoopRunner(
        scope = backgroundScope,
        loop = loop,
        isPlaying = enLecture,
        positionMs = {
            lectures++
            position
        },
        seekTo = {
            seeks += it
            position = it
        },
    ).also { it.start() }

    private fun armer(startMs: Long, endMs: Long) {
        loop.mark(PISTE, startMs)
        loop.mark(PISTE, endMs)
    }

    @Test
    fun `depasser B ramene en A`() = runTest {
        runner()
        armer(10_000L, 25_000L)
        runCurrent()

        position = 25_000L
        advanceTimeBy(600L)

        assertEquals(listOf(10_000L), seeks)
    }

    @Test
    fun `tant qu'on est avant B rien ne bouge`() = runTest {
        runner()
        armer(10_000L, 25_000L)
        runCurrent()

        position = 24_000L
        advanceTimeBy(5_000L)

        assertTrue("rembobiner avant B couperait le passage en cours", seeks.isEmpty())
    }

    @Test
    fun `la boucle tourne plusieurs fois`() = runTest {
        // Un seul rembobinage suffirait à un test qui ne regarde que le premier
        // tour ; ce qu'on veut est qu'elle *tourne*.
        runner()
        armer(10_000L, 25_000L)
        runCurrent()

        // Chaque tour : la lecture court jusqu'à B, on la laisse rembobiner.
        // Le faux `seekTo` la ramène en A de lui-même, comme le lecteur.
        repeat(3) {
            position = 25_000L
            advanceTimeBy(600L)
        }

        assertEquals(listOf(10_000L, 10_000L, 10_000L), seeks)
    }

    @Test
    fun `effacer la boucle arrete le rembobinage`() = runTest {
        runner()
        armer(10_000L, 25_000L)
        runCurrent()

        loop.clear()
        runCurrent()
        position = 30_000L
        advanceTimeBy(5_000L)

        assertTrue("une boucle effacée ne doit plus retenir la lecture", seeks.isEmpty())
    }

    @Test
    fun `changer de piste arrete le rembobinage`() = runTest {
        runner()
        armer(10_000L, 25_000L)
        runCurrent()

        loop.clearIfOtherTrack(AUTRE_PISTE)
        runCurrent()
        position = 30_000L
        advanceTimeBy(5_000L)

        assertTrue(seeks.isEmpty())
    }

    @Test
    fun `A pose sans B ne retient pas la lecture`() = runTest {
        // La pose est en deux temps : tant que B manque, il n'y a pas
        // d'intervalle, et la lecture doit courir jusqu'au bout du morceau.
        runner()
        loop.mark(PISTE, 10_000L)
        runCurrent()

        position = 300_000L
        advanceTimeBy(5_000L)

        assertTrue(seeks.isEmpty())
    }

    @Test
    fun `en pause, plus rien n'est echantillonne`() = runTest {
        // Une position à l'arrêt reste éternellement sous B. Sans cette garde,
        // la surveillance réveillerait le service toutes les 500 ms — et toutes
        // les 50 ms si l'on met en pause juste avant la borne — pour constater
        // à chaque fois que rien n'a bougé. Une pause dure ce que dure une
        // pause ; le coût, lui, ne s'arrêterait pas.
        runner()
        armer(10_000L, 25_000L)
        runCurrent()
        position = 24_000L
        advanceTimeBy(2_000L)

        enLecture.value = false
        runCurrent()
        val avantLaPause = lectures
        advanceTimeBy(60_000L)

        assertEquals("une minute de pause ne doit coûter aucun réveil", avantLaPause, lectures)
    }

    @Test
    fun `la reprise remet la boucle en marche`() = runTest {
        // Le pendant du test précédent : une garde qui ne se rouvrirait jamais
        // arrêterait aussi bien l'échantillonnage, et la boucle avec.
        runner()
        armer(10_000L, 25_000L)
        runCurrent()

        enLecture.value = false
        runCurrent()
        position = 30_000L
        advanceTimeBy(5_000L)
        assertTrue("en pause, la lecture n'est pas ramenée en A", seeks.isEmpty())

        enLecture.value = true
        advanceTimeBy(600L)

        assertEquals(listOf(10_000L), seeks)
    }

    @Test
    fun `sur une longue boucle le rembobinage ne se fait pas attendre`() = runTest {
        // Le sommeil se règle sur ce qui reste avant B, mais reste plafonné :
        // sans ce plafond, une boucle de dix minutes ferait dormir dix minutes,
        // et un saut de position — un `seek` de l'utilisateur — resterait sans
        // effet tout ce temps.
        runner()
        armer(0L, 600_000L)
        runCurrent()

        position = 600_000L
        advanceTimeBy(600L)

        assertEquals(listOf(0L), seeks)
    }

    // Il n'y a pas de test pour la relecture d'état qui suit chaque sommeil
    // dans `surveiller`. Un test écrit pour elle passait le retrait de la
    // garde : sous `runTest`, l'ordonnanceur est mono-fil, et c'est
    // `collectLatest` qui annule la surveillance avant qu'elle ne se réveille.
    // La course que la garde ferme — l'annulation qui arrive après que la
    // continuation du `delay` a été résumée — ne s'y reproduit pas. Le test a
    // été retiré plutôt que de laisser croire la zone couverte ; c'est la même
    // conclusion que pour `SleepTimer`.

    private companion object {
        const val PISTE = "piste-1"
        const val AUTRE_PISTE = "piste-2"
    }
}
