package app.waveflow.prototype

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import app.waveflow.ui.theme.contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * La nappe de la pochette : elle peut prendre n'importe quelle teinte, mais la
 * seconde encre doit rester lisible en son cœur, dans les deux apparences.
 * Robolectric pour `HSVToColor`, qui parcourt le cercle des teintes.
 */
@RunWith(RobolectricTestRunner::class)
class AtmosphereTest {

    /** Le cercle des teintes, plus le blanc et le noir : les pochettes extrêmes. */
    private val pochettes: List<Int> = (0 until 360 step 15).map { teinte ->
        android.graphics.Color.HSVToColor(floatArrayOf(teinte.toFloat(), 0.9f, 0.95f))
    } + listOf(Color.White.toArgb(), Color.Black.toArgb(), 0xFF808080.toInt())

    @Test
    fun `la seconde encre garde 4,5 contre 1 au coeur de toute nappe`() {
        for (colors in listOf(PocketDark, PocketLight)) {
            // Les pochettes, et la nappe par défaut d'une pochette absente.
            for (argb in pochettes + listOf<Int?>(null)) {
                val nappe = poolColor(argb, colors.dark, colors.atmosphere)
                val fond = nappe.copy(alpha = POOL_ALPHA).compositeOver(colors.canvas)
                assertTrue(
                    "pochette ${argb?.let(Integer::toHexString)} en ${if (colors.dark) "sombre" else "clair"} : " +
                        "${contrast(colors.ink2, fond)}",
                    contrast(colors.ink2, fond) >= 4.5f,
                )
            }
        }
    }

    @Test
    fun `sans couleur de pochette la nappe par defaut reste`() {
        assertEquals(PocketDark.atmosphere, poolColor(null, dark = true, fallback = PocketDark.atmosphere))
    }
}
