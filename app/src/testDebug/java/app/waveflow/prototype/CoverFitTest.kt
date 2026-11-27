package app.waveflow.prototype

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La place que Now Playing donne à la pochette : tout ce que le reste laisse,
 * plafonné à la largeur et à 520 dp, et une vignette quand il ne reste presque
 * rien.
 */
class CoverFitTest {

    @Test
    fun `sur un Pixel 8 la pochette prend toute la largeur`() {
        // 412 dp moins deux gouttières de 24 ; 780 dp de haut sous la barre.
        val ajustement = fitCover(width = 364.dp, height = 780.dp, details = 300.dp, gap = 24.dp)

        assertEquals(CoverFit.Full(364.dp), ajustement)
    }

    @Test
    fun `quand la hauteur manque la pochette cede a la hauteur et non au titre`() {
        val ajustement = fitCover(width = 364.dp, height = 600.dp, details = 300.dp, gap = 24.dp)

        assertEquals(CoverFit.Full(276.dp), ajustement)
    }

    @Test
    fun `sur une tablette la pochette est plafonnee`() {
        val ajustement = fitCover(width = 752.dp, height = 1200.dp, details = 300.dp, gap = 24.dp)

        assertEquals(CoverFit.Full(COVER_CAP), ajustement)
    }

    @Test
    fun `sous cent vingt dp l'ecran passe en disposition compacte`() {
        // Une fenêtre basse à police doublée : le reste prend presque tout.
        assertEquals(CoverFit.Compact, fitCover(width = 320.dp, height = 504.dp, details = 420.dp, gap = 24.dp))
        // Juste au seuil, la pochette reste en grand.
        assertEquals(CoverFit.Full(120.dp), fitCover(width = 320.dp, height = 444.dp, details = 300.dp, gap = 24.dp))
        // Un petit téléphone (360 × 640 dp) garde sa pochette en grand.
        assertEquals(CoverFit.Full(164.dp), fitCover(width = 320.dp, height = 512.dp, details = 324.dp, gap = 24.dp))
    }

    @Test
    fun `deux volets seulement en paysage assez large`() {
        assertTrue(usesTwoPanes(width = 891.dp, height = 411.dp))
        assertFalse(usesTwoPanes(width = 411.dp, height = 891.dp))
        // Un paysage étroit — une fenêtre partagée — garde une colonne.
        assertFalse(usesTwoPanes(width = 520.dp, height = 360.dp))
    }
}
