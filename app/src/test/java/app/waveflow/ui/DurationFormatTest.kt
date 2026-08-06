package app.waveflow.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pas d'API Android ici : test JVM pur, sans Robolectric. */
class DurationFormatTest {

    @Test
    fun `sous la minute les secondes restent sur deux chiffres`() {
        assertEquals("0:00", formatDuration(0L))
        assertEquals("0:07", formatDuration(7_000L))
        assertEquals("0:59", formatDuration(59_999L))
    }

    @Test
    fun `au dela de la minute le format est m ss`() {
        assertEquals("1:00", formatDuration(60_000L))
        assertEquals("3:45", formatDuration(225_000L))
        assertEquals("59:59", formatDuration(3_599_000L))
    }

    @Test
    fun `au dela de l'heure le format gagne un champ`() {
        assertEquals("1:00:00", formatDuration(3_600_000L))
        assertEquals("1:01:01", formatDuration(3_661_000L))
    }

    @Test
    fun `une duree negative est ramenee a zero`() {
        assertEquals("0:00", formatDuration(-1L))
    }
}
