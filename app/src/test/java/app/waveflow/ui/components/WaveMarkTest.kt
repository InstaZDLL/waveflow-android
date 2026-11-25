package app.waveflow.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Le motif de l'onde tient entier dans la boîte qu'on lui donne, sans
 * déformation. Robolectric : le fichier du motif construit un `Path` Android.
 */
@RunWith(RobolectricTestRunner::class)
class WaveMarkTest {

    @Test
    fun `le motif prend l'echelle du cote qui le borne`() {
        // Aux proportions du motif, 40 × 10.
        assertEquals(2f, markScale(width = 80f, height = 20f), 0f)
        // Élargi sans être grandi : la hauteur borne, le motif ne déborde pas.
        assertEquals(1f, markScale(width = 80f, height = 10f), 0f)
        // Grandi sans être élargi : la largeur borne.
        assertEquals(1f, markScale(width = 40f, height = 30f), 0f)
    }
}
