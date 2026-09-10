package app.waveflow.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        positionMs = { position },
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
