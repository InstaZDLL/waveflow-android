package app.waveflow.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** La vitesse telle qu'elle se dit, et telle qu'on la garde présentable. */
class PlaybackSpeedTest {

    @Test
    fun `les zeros inutiles ne s'ecrivent pas`() {
        // « ×1,00 » suggérerait une précision au centième que le réglage
        // n'offre pas, et allongerait un bouton qui doit tenir dans 48 dp.
        assertEquals("×1", PlaybackSpeed.format(1f))
        assertEquals("×2", PlaybackSpeed.format(2f))
        assertEquals("×1,5", PlaybackSpeed.format(1.5f))
    }

    @Test
    fun `le separateur decimal est la virgule, en toute locale`() {
        // `String.format` suivrait la locale de l'appareil : « ×1.5 » sur un
        // téléphone en anglais, au milieu d'une interface en français.
        val defaut = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.US)
            assertEquals("×1,25", PlaybackSpeed.format(1.25f))
        } finally {
            java.util.Locale.setDefault(defaut)
        }
    }

    @Test
    fun `un centieme non nul garde son zero de tete`() {
        // Sans lui, ×1,05 s'écrirait « ×1,5 » — quatre fois plus rapide que
        // demandé, et rigoureusement impossible à repérer à l'écran.
        assertEquals("×1,05", PlaybackSpeed.format(1.05f))
    }

    @Test
    fun `toutes les vitesses proposees s'ecrivent`() {
        // Le bouton les affiche telles quelles : aucune ne doit rendre une
        // forme inattendue, et toutes tiennent en cinq caractères.
        assertEquals(
            listOf("×0,5", "×0,75", "×1", "×1,25", "×1,5", "×1,75", "×2"),
            PlaybackSpeed.PROPOSEES.map(PlaybackSpeed::format),
        )
    }

    @Test
    fun `une vitesse hors bornes est ramenee dedans`() {
        // La valeur vient du disque : une version future aux bornes plus larges,
        // ou un fichier abîmé. Une vitesse nulle figerait la lecture sans rien
        // dire de ce qui se passe.
        assertEquals(PlaybackSpeed.MAX, PlaybackSpeed.borner(8f), 0f)
        assertEquals(PlaybackSpeed.MIN, PlaybackSpeed.borner(0.1f), 0f)
        assertEquals(PlaybackSpeed.MIN, PlaybackSpeed.borner(0f), 0f)
        assertEquals(PlaybackSpeed.MIN, PlaybackSpeed.borner(-1f), 0f)
    }

    @Test
    fun `ce qui n'est pas un nombre vaut la vitesse normale`() {
        // `coerceIn` laisse passer NaN tel quel, et Media3 lève en le recevant
        // — loin de l'endroit où il a été lu, donc difficile à rattacher au
        // fichier de préférences qui l'a produit.
        assertEquals(PlaybackSpeed.NORMALE, PlaybackSpeed.borner(Float.NaN), 0f)
        assertEquals(PlaybackSpeed.NORMALE, PlaybackSpeed.borner(Float.POSITIVE_INFINITY), 0f)
        assertEquals(PlaybackSpeed.NORMALE, PlaybackSpeed.borner(Float.NEGATIVE_INFINITY), 0f)
    }
}
