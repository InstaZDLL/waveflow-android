package app.waveflow.data.remote

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import app.waveflow.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser

/**
 * La configuration de sécurité réseau que l'app déclare : celle qu'Android lit
 * au démarrage, et les autorités qu'elle fait croire.
 *
 * Robolectric n'applique pas cette configuration aux connexions — aucune
 * poignée de main TLS n'est éprouvée ici. Le test vérifie ce qu'Android
 * recevra : le manifeste fusionné qui la désigne, et son contenu.
 */
@RunWith(RobolectricTestRunner::class)
class NetworkSecurityConfigTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `le manifeste fusionne designe la configuration de l'app`() {
        // Champ caché de l'API : il porte l'attribut
        // `android:networkSecurityConfig` tel que le système l'a lu.
        val reference = ApplicationInfo::class.java
            .getField("networkSecurityConfigRes")
            .getInt(context.applicationInfo)

        assertEquals(R.xml.network_security_config, reference)
    }

    @Test
    fun `les autorites du systeme et celles de l'utilisateur sont crues`() {
        val sources = mutableListOf<String>()
        context.resources.getXml(R.xml.network_security_config).use { xml ->
            var dansBase = false
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                when {
                    xml.eventType == XmlPullParser.START_TAG && xml.name == "base-config" -> dansBase = true
                    xml.eventType == XmlPullParser.END_TAG && xml.name == "base-config" -> dansBase = false
                    xml.eventType == XmlPullParser.START_TAG && xml.name == "certificates" && dansBase ->
                        sources += xml.getAttributeValue(null, "src")
                }
            }
        }

        assertEquals(listOf("system", "user"), sources)
    }
}
