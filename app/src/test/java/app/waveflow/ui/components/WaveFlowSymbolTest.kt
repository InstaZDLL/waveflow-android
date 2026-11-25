package app.waveflow.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La respiration du W, éprouvée sur ses fonctions pures.
 *
 * Le W lui-même tourne sur l'horloge des animations infinies, que l'outil de
 * test de Compose interrompt : un test d'écran passerait que le W respire ou
 * non. Ce qui décide de la respiration — quand elle a lieu, jusqu'où elle
 * descend, comment elle retombe — se vérifie donc ici.
 */
class WaveFlowSymbolTest {

    @Test
    fun `le W respire pendant la lecture`() {
        assertTrue(breathes(playing = true, reduced = false))
    }

    @Test
    fun `le W se tient immobile en pause`() {
        assertFalse(breathes(playing = false, reduced = false))
    }

    @Test
    fun `le W se tient immobile quand les animations sont supprimees`() {
        assertFalse(breathes(playing = true, reduced = true))
    }

    @Test
    fun `chaque extreme descend jusqu'a 73 pour cent de sa hauteur, pas plus bas`() {
        val hauteurs = (0L until 3_000L step 10L).map { breathTargets(it) }
        val toutes = hauteurs.flatMap { listOf(it.trough1, it.crest, it.trough2) }

        assertEquals(W_AMP - BREATH_DEPTH, toutes.min(), 0.05f)
        assertEquals(W_AMP, toutes.max(), 0.05f)
        // 44 sur 60 : la part que le document donne.
        assertEquals(0.73f, toutes.min() / W_AMP, 0.01f)
    }

    @Test
    fun `les trois extremes respirent a tour de role`() {
        val instant = breathTargets(400L)

        assertNotEquals(instant.trough1, instant.crest)
        assertNotEquals(instant.crest, instant.trough2)
    }

    @Test
    fun `une pause ramene le W a son dessin, sans le figer en pleine respiration`() {
        var hauteurs = breathTargets(500L)
        assertFalse(hauteurs.isAtRest())

        // Une seconde d'images à trente par seconde : chaque image comble un
        // peu plus du quart de l'écart, le W est au repos en six dixièmes.
        repeat(30) { hauteurs = settleTowards(hauteurs, WRest, 33L) }

        assertTrue(hauteurs.isAtRest())
    }
}
