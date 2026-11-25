package app.waveflow.ui.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import app.waveflow.ui.theme.Emerald400
import app.waveflow.ui.theme.Emerald600
import app.waveflow.ui.theme.FluidBg
import app.waveflow.ui.theme.FluidInk
import app.waveflow.ui.theme.FluidInk2
import app.waveflow.ui.theme.LiquidGround
import app.waveflow.ui.theme.LiquidInk
import app.waveflow.ui.theme.LiquidInk2
import app.waveflow.ui.theme.LiquidInk3
import app.waveflow.ui.theme.WaveFlowMaterial
import app.waveflow.ui.theme.contrast
import app.waveflow.ui.theme.oklchOf
import app.waveflow.ui.theme.oklchToColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * L'accent et la lumière que le lecteur tire d'une pochette.
 *
 * L'exigence est celle de l'accent fixe dans `WaveFlowThemeTest` : 3:1 contre
 * son encre et ses fonds pour l'accent, 4,5:1 pour les trois encres. Elle se
 * vérifie ici sur tout le cercle des teintes, à plusieurs vivacités et
 * clartés de pochette, plutôt que sur quelques couleurs choisies : c'est le
 * cas qu'on n'a pas pensé à essayer qui tombe.
 */
class CoverColorsTest {

    @Test
    fun `une pochette grise ne donne pas d'accent, celui du theme reste`() {
        val gris = 0xFF808080.toInt()

        assertNull(coverAccent(gris, WaveFlowMaterial.Fluid))
        assertEquals(Emerald400, playerLightFor(CoverSample(gris, gris), WaveFlowMaterial.Fluid).accent)
        assertEquals(Emerald600, playerLightFor(CoverSample(gris, gris), WaveFlowMaterial.Liquid).accent)
    }

    @Test
    fun `sans pochette, la lumiere est celle des documents`() {
        val sombre = playerLightFor(null, WaveFlowMaterial.Fluid)
        assertEquals(Emerald400, sombre.accent)
        assertEquals(Color(0xFF1F5A48), sombre.glow)
        assertEquals(Color(0xFF0F2B22), sombre.glowDeep)

        val clair = playerLightFor(null, WaveFlowMaterial.Liquid)
        assertEquals(Emerald600, clair.accent)
        assertEquals(Color.White, clair.onAccent)
        assertEquals(Color(0xFF9FB8EE), clair.glow)
    }

    @Test
    fun `l'accent du theme passe la garde sans changer`() {
        // « Si la pochette n'en donne pas, l'accent fixe reprend » : tel quel,
        // pas une version retouchée par la garde.
        assertEquals(Emerald400, garantirContraste(Emerald400, WaveFlowMaterial.Fluid))
        assertEquals(Emerald600, garantirContraste(Emerald600, WaveFlowMaterial.Liquid))
    }

    @Test
    fun `l'accent garde la teinte de la pochette`() {
        val bleu = 0xFF2F6FD6.toInt()
        val source = oklchOf(bleu)

        for (matiere in WaveFlowMaterial.entries) {
            val accent = checkNotNull(coverAccent(bleu, matiere))
            assertEquals("teinte en $matiere", source.h, oklchOf(accent.toArgb()).h, ECART_TEINTE)
        }
    }

    @Test
    fun `l'accent prend la clarte du palier de la matiere`() {
        // Un bleu tenu au palier 400 en sombre, 600 en clair : la règle du
        // bureau, que la garde n'a pas eu à retoucher pour cette teinte.
        val bleu = 0xFF2F6FD6.toInt()
        assertEquals(0.765f, oklchOf(checkNotNull(coverAccent(bleu, WaveFlowMaterial.Fluid)).toArgb()).l, 0.01f)
        assertEquals(0.596f, oklchOf(checkNotNull(coverAccent(bleu, WaveFlowMaterial.Liquid)).toArgb()).l, 0.01f)
    }

    @Test
    fun `l'accent tire se lit en sombre, sur tout le cercle des teintes`() {
        assertAccentsLisibles(WaveFlowMaterial.Fluid)
    }

    @Test
    fun `l'accent tire se lit en clair, sur tout le cercle des teintes`() {
        assertAccentsLisibles(WaveFlowMaterial.Liquid)
    }

    @Test
    fun `la garde ramene un accent trop pale vers un palier lisible`() {
        val jaunePale = Color(0xFFFFF59D)
        val ramene = garantirContraste(jaunePale, WaveFlowMaterial.Liquid)

        assertTrue(contrast(Color.White, ramene) >= ACCENT_CONTRAST)
        assertTrue(contrast(ramene, LiquidGround) >= ACCENT_CONTRAST)
        // Toujours un jaune, simplement plus profond.
        assertEquals(oklchOf(jaunePale.toArgb()).h, oklchOf(ramene.toArgb()).h, ECART_TEINTE)
    }

    @Test
    fun `les nappes du sombre laissent lire les encres et l'accent`() {
        // `ink-3` n'est pas dans la liste : le lecteur n'écrit rien à la
        // troisième encre sur sa lumière, ce qui laisse celle-ci se voir.
        assertNappesLisibles(WaveFlowMaterial.Fluid, FluidBg, listOf(FluidInk, FluidInk2))
    }

    @Test
    fun `en sombre, la lumiere d'une pochette claire n'est plus rabattue`() {
        // Le pendant du précédent : la limite cale la lumière sur la deuxième
        // encre, et non plus sur la troisième, qui rabattait la nappe d'une
        // pochette claire (blanche à 32 %, jaune ou rose à 37 %). Elle se pose
        // désormais à l'opacité de `fluid.css`, comme celle des autres.
        for (pochette in listOf(0xFFFFFFFF, 0xFFFFEB3B, 0xFFEC4899)) {
            val echantillon = CoverSample(pochette.toInt(), pochette.toInt())
            val nappes =
                lightPools(playerLightFor(echantillon, WaveFlowMaterial.Fluid), WaveFlowMaterial.Fluid, FluidBg)
            assertEquals("pochette ${hex(pochette.toInt())}", FLUID_GLOW_VOULUE, nappes.first().alpha, 0.005f)
        }
    }

    @Test
    fun `les nappes du clair laissent lire les encres et l'accent`() {
        assertNappesLisibles(WaveFlowMaterial.Liquid, LiquidGround, listOf(LiquidInk, LiquidInk2, LiquidInk3))
    }

    @Test
    fun `les nappes du clair restent visibles avec la lumiere par defaut`() {
        // Le pendant du précédent : une borne qui éteindrait toute nappe le
        // passerait aussi. Le clair n'est jamais blanc : il garde ses teintes.
        val nappes = lightPools(defaultPlayerLight(WaveFlowMaterial.Liquid), WaveFlowMaterial.Liquid, LiquidGround)
        assertTrue(nappes.all { it.alpha > NAPPE_VISIBLE })
    }

    @Test
    fun `une pochette blanche ne delave pas la page`() {
        // La lumière du bureau tire plus fort vers le noir une pochette claire.
        val blanche = shade(0xFFFFFFFF.toInt(), 0.62f)
        assertTrue(blanche.red < 0.4f)
    }

    private fun assertAccentsLisibles(matiere: WaveFlowMaterial) {
        val encre = defaultPlayerLight(matiere).onAccent
        val fonds = when (matiere) {
            WaveFlowMaterial.Fluid -> listOf(FluidBg, groundLimit(matiere))
            WaveFlowMaterial.Liquid -> listOf(LiquidGround, groundLimit(matiere))
        }
        var essais = 0
        for (pochette in pochettes()) {
            val accent = coverAccent(pochette, matiere) ?: continue
            essais++
            val avecEncre = contrast(encre, accent)
            assertTrue(
                "encre sur ${hex(accent)} (pochette ${hex(pochette)}) : $avecEncre",
                avecEncre >= ACCENT_CONTRAST,
            )
            for (fond in fonds) {
                val c = contrast(accent, fond)
                assertTrue("${hex(accent)} sur ${hex(fond)} (pochette ${hex(pochette)}) : $c", c >= ACCENT_CONTRAST)
            }
        }
        // Le cercle a bien été parcouru, et non écarté comme gris.
        assertTrue("seulement $essais accents", essais > 100)
    }

    @Test
    fun `les nappes du clair qui se chevauchent laissent encore lire`() {
        // L'aurore se chevauche : chaque nappe dans la limite ne garantit
        // rien de deux nappes ensemble. La page est parcourue sur une grille
        // plus fine que celle du code (61 × 61 contre 21 × 21), les nappes
        // posées dans l'ordre du dessin, chacune à son opacité en ce point.
        val encres = listOf(LiquidInk, LiquidInk2, LiquidInk3)
        val pas = 60
        val points = (0..pas).flatMap { i -> (0..pas).map { j -> i.toFloat() / pas to j.toFloat() / pas } }
        for (echantillon in echantillons()) {
            val lumiere = playerLightFor(echantillon, WaveFlowMaterial.Liquid)
            val nappes = lightPools(lumiere, WaveFlowMaterial.Liquid, LiquidGround)
            for ((x, y) in points) {
                val dessous = nappes.fold(LiquidGround) { fond, n ->
                    n.color.copy(alpha = poolAlphaAt(n, x, y)).compositeOver(fond)
                }
                val pire = (encres + lumiere.accent).minOf { contrast(it, dessous) / seuil(it, lumiere.accent) }
                assertTrue("en ($x, $y) ($echantillon) : ${pire * 100} % du seuil", pire >= 1f)
            }
        }
    }

    /**
     * L'opacité d'une nappe au point (x, y) de l'écran : pleine au centre,
     * éteinte à [POOL_FADE] de son demi-axe, linéaire entre les deux — le
     * dégradé radial que dessine `playerLight`.
     */
    private fun poolAlphaAt(pool: LightPool, x: Float, y: Float): Float {
        val dx = (x - pool.x) / pool.rx
        val dy = (y - pool.y) / pool.ry
        return pool.alpha * (1f - sqrt(dx * dx + dy * dy) / POOL_FADE).coerceIn(0f, 1f)
    }

    /** Le seuil d'une couleur posée sur la page : 3:1 pour l'accent, 4,5:1 pour une encre. */
    private fun seuil(couleur: Color, accent: Color) = if (couleur == accent) ACCENT_CONTRAST else TEXTE

    private fun echantillons(): List<CoverSample?> = listOf(
        0xFF000000,
        0xFFFFFFFF,
        0xFFE53935,
        0xFF1E3A8A,
        0xFFFFEB3B,
        0xFF0F172A,
        0xFF7C3AED,
        0xFF22C55E,
    ).map { CoverSample(it.toInt(), it.toInt()) } + listOf(null)

    private fun assertNappesLisibles(matiere: WaveFlowMaterial, fond: Color, encres: List<Color>) {
        for (echantillon in echantillons()) {
            val lumiere = playerLightFor(echantillon, matiere)
            for (nappe in lightPools(lumiere, matiere, fond)) {
                // Le point le plus marqué de la nappe : son centre, posé plein.
                val dessous = nappe.color.copy(alpha = nappe.alpha).compositeOver(fond)
                encres.forEach { encre ->
                    val c = contrast(encre, dessous)
                    assertTrue("${hex(encre)} sur ${hex(dessous)} ($echantillon) : $c", c >= TEXTE)
                }
                val c = contrast(lumiere.accent, dessous)
                assertTrue("accent ${hex(lumiere.accent)} sur ${hex(dessous)} : $c", c >= ACCENT_CONTRAST)
            }
        }
    }

    /** Des pochettes sur tout le cercle des teintes, à trois vivacités et trois clartés. */
    private fun pochettes(): List<Int> = buildList {
        for (teinte in 0 until 360 step 5) {
            for (vivacite in listOf(0.06f, 0.12f, 0.25f)) {
                for (clarte in listOf(0.35f, 0.6f, 0.85f)) {
                    add(oklchToColor(clarte, vivacite, teinte.toFloat()).toArgb())
                }
            }
        }
    }

    private fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)
    private fun hex(c: Int) = "#%06X".format(c and 0xFFFFFF)

    private fun assertEquals(message: String, attendu: Float, obtenu: Float, ecart: Float) {
        // Une teinte est un angle : 359° et 1° sont voisins.
        val d = abs(attendu - obtenu) % 360f
        assertTrue("$message : $attendu contre $obtenu", min(d, 360f - d) <= ecart)
    }

    private fun assertEquals(attendu: Float, obtenu: Float, ecart: Float) = assertEquals(
        "teinte",
        attendu,
        obtenu,
        ecart,
    )

    private companion object {
        const val TEXTE = 4.5f
        const val ECART_TEINTE = 3f
        const val NAPPE_VISIBLE = 0.08f

        /** L'opacité de la lumière du sombre, `fluid.css` : `--fe-glow` 40 %. */
        const val FLUID_GLOW_VOULUE = 0.40f
    }
}
