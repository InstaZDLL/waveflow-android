package app.waveflow.ui.theme

import android.content.Context
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Le mouvement commun, et la promesse faite à qui retire les animations :
 * plus rien ne se déplace, il ne reste que l'opacité.
 *
 * Android n'a pas de « mouvement réduit » distinct : *Supprimer les
 * animations* met l'échelle des animations à zéro, et c'est elle qu'on lit.
 */
@RunWith(RobolectricTestRunner::class)
class WaveFlowMotionTest {

    @get:Rule
    val compose = createComposeRule()

    private val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver

    private var motion: WaveFlowMotion? = null

    private fun composer() {
        compose.setContent {
            WaveFlowTheme { motion = WaveFlowTheme.motion }
        }
        compose.waitForIdle()
    }

    private fun echelle(valeur: Float) {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, valeur)
    }

    @Test
    fun `sans reglage le mouvement est entier`() {
        composer()
        val m = checkNotNull(motion)

        assertFalse(m.reduced)
        val forme = m.shape<Float>() as TweenSpec
        assertEquals(320, forme.durationMillis)
        assertEquals(WaveFlowMotion.Ease, forme.easing)
        assertEquals(10.dp, m.revealOffset)
        // Le quatrième bloc d'une page part trois décalages après le premier.
        assertEquals(210, (m.reveal<Float>(index = 3) as TweenSpec).delay)
    }

    @Test
    fun `animations supprimees il ne reste que l'opacite`() {
        echelle(0f)
        composer()
        val m = checkNotNull(motion)

        assertTrue(m.reduced)
        // Ce qui se déplace saute à sa place ; l'apparition ne monte plus et
        // ne se décale plus d'un bloc à l'autre.
        assertTrue(m.shape<Float>() is SnapSpec)
        assertEquals(0.dp, m.revealOffset)
        assertEquals(0, (m.reveal<Float>(index = 3) as TweenSpec).delay)
        // Les fondus restent : ils ne déplacent rien.
        assertEquals(180, (m.tone<Float>() as TweenSpec).durationMillis)
    }

    @Test
    fun `supprimer les animations application ouverte s'applique sans relancer`() {
        composer()
        assertFalse(checkNotNull(motion).reduced)

        echelle(0f)
        // Ce que le fournisseur des réglages fait après une écriture.
        resolver.notifyChange(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), null)
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()

        assertTrue(checkNotNull(motion).reduced)
    }
}
