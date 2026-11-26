package app.waveflow.ui.player

import app.waveflow.model.SyncedLine
import org.junit.Assert.assertEquals
import org.junit.Test

/** Le suivi de la ligne chantée (`docs/paroles.md`, P12 et P13). */
class LyricsFollowTest {

    private val lignes = listOf(
        SyncedLine(1_000, "une"),
        SyncedLine(4_000, "deux"),
        SyncedLine(4_000, "deux bis"),
        SyncedLine(9_000, "trois"),
    )

    @Test
    fun `avant la premiere ligne, aucune n'est chantee`() {
        assertEquals(-1, currentLineIndex(lignes, 0L))
        assertEquals(-1, currentLineIndex(lignes, 999L))
    }

    @Test
    fun `une ligne est chantee des son debut et jusqu'a la suivante`() {
        assertEquals(0, currentLineIndex(lignes, 1_000L))
        assertEquals(0, currentLineIndex(lignes, 3_999L))
        assertEquals(3, currentLineIndex(lignes, 9_000L))
        assertEquals(3, currentLineIndex(lignes, 600_000L))
    }

    @Test
    fun `entre deux lignes du meme instant, la derniere l'emporte`() {
        assertEquals(2, currentLineIndex(lignes, 4_000L))
    }

    @Test
    fun `sans lignes, rien n'est chante`() {
        assertEquals(-1, currentLineIndex(emptyList(), 5_000L))
    }

    @Test
    fun `entre deux tics la position avance a la vitesse de lecture`() {
        assertEquals(10_200L, extrapolatedPositionMs(10_000L, elapsedMs = 200L, speed = 1f))
        assertEquals(10_300L, extrapolatedPositionMs(10_000L, elapsedMs = 200L, speed = 1.5f))
    }

    @Test
    fun `la position ne depasse jamais d'un tic la derniere recue`() {
        // Un lecteur qui cale sans le dire : plus aucun tic n'arrive.
        assertEquals(10_000L + POSITION_TICK_MS, extrapolatedPositionMs(10_000L, elapsedMs = 60_000L, speed = 1f))
        // À ×2, un tic couvre une seconde du morceau : c'est le temps écoulé qui
        // est borné, pas l'avance.
        assertEquals(10_800L, extrapolatedPositionMs(10_000L, elapsedMs = 400L, speed = 2f))
        assertEquals(11_000L, extrapolatedPositionMs(10_000L, elapsedMs = 60_000L, speed = 2f))
    }

    @Test
    fun `une horloge qui recule ne fait pas reculer la position`() {
        assertEquals(10_000L, extrapolatedPositionMs(10_000L, elapsedMs = -50L, speed = 1f))
    }
}
