package app.waveflow.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import app.waveflow.model.ThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Quelle matière habille quel thème, et ce que chacune garantit à la lecture.
 *
 * La décision du 24/11 : **sombre = Fluid Editorial**, **clair = Liquid Aurora
 * Glass**. Ces tests la tiennent au point où elle se prend — la traduction du
 * choix de l'utilisateur dans [WaveFlowTheme] — en lisant ce qu'un écran lit :
 * `MaterialTheme` et `WaveFlowTheme`.
 */
@RunWith(RobolectricTestRunner::class)
class WaveFlowThemeTest {

    @get:Rule
    val compose = createComposeRule()

    private class Habit(
        val scheme: ColorScheme,
        val colors: WaveFlowColors,
        val shapes: WaveFlowShapes,
        val materialShapes: Shapes,
        val editorial: TextStyle,
        val ripple: RippleAlpha?,
        val material: WaveFlowMaterial,
    )

    // `rippleAlpha` est déprécié sans remplaçant ; voir WaveFlowRippleAlpha.
    @Suppress("DEPRECATION")
    private fun habiller(choix: ThemeChoice): Habit {
        var habit: Habit? = null
        compose.setContent {
            WaveFlowTheme(theme = choix) {
                habit = Habit(
                    scheme = MaterialTheme.colorScheme,
                    colors = WaveFlowTheme.colors,
                    shapes = WaveFlowTheme.shapes,
                    materialShapes = MaterialTheme.shapes,
                    editorial = WaveFlowTheme.editorial,
                    ripple = LocalRippleConfiguration.current?.rippleAlpha,
                    material = WaveFlowTheme.material,
                )
            }
        }
        compose.waitForIdle()
        return checkNotNull(habit)
    }

    @Test
    fun `le sombre est Fluid Editorial`() {
        val habit = habiller(ThemeChoice.Dark)

        assertEquals(FluidBg, habit.scheme.background)
        assertEquals(FluidInk, habit.scheme.onSurface)
        assertEquals(Emerald400, habit.scheme.primary)
        assertEquals(FluidAccentInk, habit.scheme.onPrimary)
        assertSame(FluidShapes, habit.shapes)
        assertEquals(WaveFlowMaterial.Fluid, habit.material)
        // La ligne éditoriale de Fluid est la seule à porter l'empattement.
        assertEquals(FontStyle.Italic, habit.editorial.fontStyle)
    }

    @Test
    fun `le clair est Liquid Aurora Glass`() {
        val habit = habiller(ThemeChoice.Light)

        assertEquals(LiquidGround, habit.scheme.background)
        assertEquals(LiquidInk, habit.scheme.onSurface)
        assertEquals(Emerald600, habit.scheme.primary)
        assertEquals(Color.White, habit.scheme.onPrimary)
        assertSame(LiquidShapes, habit.shapes)
        assertEquals(WaveFlowMaterial.Liquid, habit.material)
        assertNotEquals(FontStyle.Italic, habit.editorial.fontStyle)
    }

    @Test
    @Config(qualifiers = "night")
    fun `le choix systeme suit un systeme sombre`() {
        assertEquals(FluidBg, habiller(ThemeChoice.System).scheme.background)
    }

    @Test
    @Config(qualifiers = "notnight")
    fun `le choix systeme suit un systeme clair`() {
        assertEquals(LiquidGround, habiller(ThemeChoice.System).scheme.background)
    }

    /**
     * Les valeurs elles-mêmes, recopiées des documents, et non les constantes
     * qui les portent : une constante modifiée, ou les deux matières
     * échangées sur un rôle, doivent faire tomber quelque chose.
     */
    @Test
    fun `les valeurs du sombre sont celles de Fluid Editorial`() {
        val habit = habiller(ThemeChoice.Dark)

        assertEquals(Color(0xFF0B0B0E), habit.scheme.background)
        assertEquals(Color(0xFF34D399), habit.scheme.primary)
        // La carte est la `surface` ; menus et dialogues, `surface-2`.
        assertEquals(Color(0xFF121216), habit.scheme.surfaceContainerHighest)
        assertEquals(Color(0xFF17171D), habit.scheme.surfaceContainerHigh)
        assertEquals(RoundedCornerShape(14.dp), habit.materialShapes.medium)
        assertEquals(RoundedCornerShape(14.dp), habit.materialShapes.extraLarge)
    }

    @Test
    fun `les valeurs du clair sont celles de Liquid Aurora Glass`() {
        val habit = habiller(ThemeChoice.Light)

        assertEquals(Color(0xFFECEEF5), habit.scheme.background)
        assertEquals(Color(0xFF059669), habit.scheme.primary)
        // Carte et overlay : le blanc 90 % posé sur le fond, plus clair que lui.
        assertEquals(Color(0xFFFDFDFE), habit.scheme.surfaceContainerHighest)
        assertEquals(Color(0xFFFDFDFE), habit.scheme.surfaceContainerHigh)
        assertEquals(RoundedCornerShape(20.dp), habit.materialShapes.medium)
        assertEquals(RoundedCornerShape(20.dp), habit.materialShapes.extraLarge)
    }

    @Test
    fun `les calques d'etat sont les tons des documents`() {
        val alpha = checkNotNull(habiller(ThemeChoice.Dark).ripple)

        assertEquals(0.04f, alpha.hoveredAlpha)
        assertEquals(0.06f, alpha.pressedAlpha)
        assertEquals(0.06f, alpha.focusedAlpha)
        assertEquals(0.08f, alpha.draggedAlpha)
    }

    @Test
    fun `une piste se voit sur la carte et sur la barre du sombre`() {
        assertPistesVisibles(habiller(ThemeChoice.Dark).scheme)
    }

    @Test
    fun `une piste se voit sur la carte et sur la barre du clair`() {
        assertPistesVisibles(habiller(ThemeChoice.Light).scheme)
    }

    @Test
    fun `un seul accent dans le sombre`() {
        assertUnSeulAccent(habiller(ThemeChoice.Dark).scheme)
    }

    @Test
    fun `un seul accent dans le clair`() {
        assertUnSeulAccent(habiller(ThemeChoice.Light).scheme)
    }

    @Test
    fun `les encres du sombre se lisent sur tous ses fonds`() {
        assertEncresLisibles(habiller(ThemeChoice.Dark))
    }

    @Test
    fun `les encres du clair se lisent sur tous ses fonds`() {
        assertEncresLisibles(habiller(ThemeChoice.Light))
    }

    /**
     * Un composant Material qui cherche une deuxième couleur — l'indicateur de
     * la barre de navigation prend `secondaryContainer` — ne doit pas en
     * trouver : les deux documents n'ont qu'un accent, et ses conteneurs sont
     * des tons, sans teinte.
     */
    private fun assertUnSeulAccent(scheme: ColorScheme) {
        assertEquals(scheme.primary, scheme.secondary)
        assertEquals(scheme.primary, scheme.tertiary)
        listOf(scheme.primaryContainer, scheme.secondaryContainer, scheme.tertiaryContainer).forEach {
            assertTrue("conteneur teinté : $it", estUnGris(it))
        }
    }

    /**
     * Material pose un ton (`secondaryContainer`) sur une surface : la piste
     * d'une barre de progression sur la carte (`surfaceContainerHighest`) —
     * celle du cache, dans les réglages —, l'indicateur de l'onglet ouvert sur
     * la barre de navigation (`surfaceContainer`). Le ton doit s'en détacher
     * d'au moins une marche : le blanc 8 % que Fluid pose sur sa `surface`
     * donne 1,22:1, et c'est le seuil.
     */
    private fun assertPistesVisibles(scheme: ColorScheme) {
        assertContraste(scheme.secondaryContainer, scheme.surfaceContainerHighest, MARCHE)
        assertContraste(scheme.secondaryContainer, scheme.surfaceContainer, MARCHE)
    }

    /**
     * Le seuil du texte courant (WCAG AA, 4,5:1) pour les trois encres, sur
     * chaque fond où un écran pose du texte. Une exception, mesurée et voulue :
     * l'encre posée sur l'accent ne tient que le seuil des grands textes et des
     * composants (3:1) en clair — le palier 600 sous une encre blanche donne
     * 3,77, comme le bureau (suivi dans #78).
     *
     * Les tons (`secondaryContainer`) ne sont pas des fonds de texte : la
     * troisième encre n'y tient pas (4,49 en sombre, 4,05 en clair), et une
     * étiquette ne se pose pas sur une piste ou un indicateur.
     */
    private fun assertEncresLisibles(habit: Habit) {
        val s = habit.scheme
        val fonds = listOf(
            s.background,
            s.surface,
            s.surfaceVariant,
            s.surfaceContainerLowest,
            s.surfaceContainerLow,
            s.surfaceContainer,
            s.surfaceContainerHigh,
            s.surfaceContainerHighest,
        )
        for (fond in fonds) {
            assertContraste(s.onSurface, fond, TEXTE)
            assertContraste(s.onSurfaceVariant, fond, TEXTE)
            assertContraste(habit.colors.ink3, fond, TEXTE)
        }
        assertContraste(s.onPrimary, s.primary, COMPOSANT)
    }

    private fun assertContraste(encre: Color, fond: Color, seuil: Float) {
        val clair = maxOf(encre.luminance(), fond.luminance())
        val sombre = minOf(encre.luminance(), fond.luminance())
        val rapport = (clair + 0.05f) / (sombre + 0.05f)
        assertTrue("$encre sur $fond : $rapport < $seuil", rapport >= seuil)
    }

    private fun estUnGris(couleur: Color): Boolean {
        val canaux = listOf(couleur.red, couleur.green, couleur.blue)
        return canaux.max() - canaux.min() < ECART_GRIS
    }

    private companion object {
        const val TEXTE = 4.5f
        const val COMPOSANT = 3f

        /** Une marche de ton : blanc 8 % sur la `surface` de Fluid, 1,22:1. */
        const val MARCHE = 1.2f

        /** Les tons des documents sont des gris froids : `#252529`, `#DCDEE6`. */
        const val ECART_GRIS = 0.05f
    }
}
