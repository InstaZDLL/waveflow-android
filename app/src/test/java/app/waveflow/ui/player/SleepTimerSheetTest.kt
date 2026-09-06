package app.waveflow.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** Le décompte tel qu'il se dit, à l'écran comme au lecteur d'écran. */
class SleepTimerSheetTest {

    @Test
    fun `le temps restant s'arrondit a la minute superieure`() {
        // Vers le haut : « 29 minutes » alors qu'il en reste 29 et demie
        // ferait croire que la minuterie a déjà mangé une minute.
        assertEquals("30 minutes", formatRemaining(29 * 60_000L + 30_000L))
        assertEquals("30 minutes", formatRemaining(30 * 60_000L))
    }

    @Test
    fun `les dernieres secondes se disent une minute, jamais zero`() {
        // « 0 minute » ferait croire que la minuterie est passée sans rien
        // faire, alors qu'elle est sur le point d'arrêter la lecture.
        assertEquals("1 minute", formatRemaining(1_000L))
        assertEquals("1 minute", formatRemaining(0L))
    }

    @Test
    fun `le singulier est respecte`() {
        assertEquals("1 minute", formatRemaining(60_000L))
        assertEquals("2 minutes", formatRemaining(61_000L))
    }
}
