package app.waveflow.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * La minuterie de veille.
 *
 * L'horloge est celle de `runTest` : sans cela, le temps virtuel où s'écoulent
 * les `delay` et le temps que la minuterie mesure divergeraient, et les tests
 * ne prouveraient rien de leurs échéances.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {

    @Test
    fun `une minuterie armee annonce son echeance`() = runTest {
        val timer = SleepTimer(backgroundScope) { testScheduler.currentTime }

        timer.start(durationMs = 30_000L)

        assertEquals(30_000L, timer.endsAtMs.value)
        assertEquals(30_000L, timer.remainingMs())
    }

    @Test
    fun `l'echeance atteinte, la minuterie previent puis s'eteint`() = runTest {
        val timer = SleepTimer(backgroundScope) { testScheduler.currentTime }
        val recues = mutableListOf<Unit>()
        backgroundScope.launch { timer.expirations.toList(recues) }
        runCurrent()

        timer.start(durationMs = 30_000L)
        advanceTimeBy(30_001L)

        assertEquals("une seule expiration", 1, recues.size)
        assertNull("la minuterie ne court plus", timer.endsAtMs.value)
    }

    @Test
    fun `avant l'echeance rien n'est annonce`() = runTest {
        val timer = SleepTimer(backgroundScope) { testScheduler.currentTime }
        val recues = mutableListOf<Unit>()
        backgroundScope.launch { timer.expirations.toList(recues) }
        runCurrent()

        timer.start(durationMs = 30_000L)
        advanceTimeBy(29_000L)

        assertEquals(emptyList<Unit>(), recues)
        assertEquals(1_000L, timer.remainingMs())
    }

    @Test
    fun `une minuterie annulee ne previent jamais`() = runTest {
        // C'est la distinction que porte `expirations` : annuler et arriver à
        // échéance vident tous deux l'état, mais un seul doit arrêter la
        // lecture.
        val timer = SleepTimer(backgroundScope) { testScheduler.currentTime }
        val recues = mutableListOf<Unit>()
        backgroundScope.launch { timer.expirations.toList(recues) }
        runCurrent()

        timer.start(durationMs = 30_000L)
        advanceTimeBy(10_000L)
        timer.cancel()
        advanceTimeBy(60_000L)

        assertEquals("annuler n'arrête pas la lecture", emptyList<Unit>(), recues)
        assertNull(timer.endsAtMs.value)
        assertNull(timer.remainingMs())
    }

    @Test
    fun `rearmer remplace l'echeance precedente`() = runTest {
        // Régler « 60 min » après « 15 min » doit donner soixante minutes, et
        // surtout ne pas laisser la première minuterie courir en sourdine.
        val timer = SleepTimer(backgroundScope) { testScheduler.currentTime }
        val recues = mutableListOf<Unit>()
        backgroundScope.launch { timer.expirations.toList(recues) }
        runCurrent()

        timer.start(durationMs = 15_000L)
        advanceTimeBy(5_000L)
        timer.start(durationMs = 60_000L)

        assertEquals(65_000L, timer.endsAtMs.value)

        // L'ancienne échéance passe sans rien déclencher.
        advanceTimeBy(11_000L)
        assertEquals("l'ancienne minuterie ne doit plus courir", emptyList<Unit>(), recues)

        advanceTimeBy(50_000L)
        assertEquals(1, recues.size)
    }

    @Test
    fun `une duree nulle ou negative eteint la minuterie`() = runTest {
        // Personne ne demande d'arrêter la lecture sur-le-champ en réglant une
        // minuterie : c'est une annulation, pas une échéance immédiate.
        val timer = SleepTimer(backgroundScope) { testScheduler.currentTime }
        val recues = mutableListOf<Unit>()
        backgroundScope.launch { timer.expirations.toList(recues) }
        runCurrent()

        timer.start(durationMs = 30_000L)
        timer.start(durationMs = 0L)
        advanceTimeBy(60_000L)

        assertNull(timer.endsAtMs.value)
        assertEquals(emptyList<Unit>(), recues)
    }

    @Test
    fun `le temps restant ne devient jamais negatif`() = runTest {
        // Entre l'échéance et le réveil de la coroutine, il s'écoule un
        // instant : un décompte y afficherait un temps à rebours.
        val timer = SleepTimer(backgroundScope) { testScheduler.currentTime }

        timer.start(durationMs = 30_000L)
        advanceTimeBy(29_999L)

        assertEquals(1L, timer.remainingMs())
    }

    @Test
    fun `sans minuterie il n'y a pas de temps restant`() = runTest {
        val timer = SleepTimer(backgroundScope) { testScheduler.currentTime }

        assertNull(timer.endsAtMs.value)
        assertNull(timer.remainingMs())
    }
}
