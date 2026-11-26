package app.waveflow.data.lyrics

import android.app.Application
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import app.waveflow.model.Lyrics
import app.waveflow.model.SyncedLine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Le `.lrc` voisin d'un morceau de l'appareil (`docs/paroles.md`, P3). */
@RunWith(RobolectricTestRunner::class)
class SidecarLyricsReaderTest {

    @get:Rule
    val dossier = TemporaryFolder()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val morceau: Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, 7L)

    @Before
    fun installer() {
        CheminsDuMediaStore.chemin = null
        Robolectric.setupContentProvider(CheminsDuMediaStore::class.java, MediaStore.AUTHORITY)
    }

    private fun lire(): Lyrics? = runBlocking { SidecarLyricsReader(app.contentResolver).read(morceau) }

    private fun chanson(nom: String = "Chanson.flac"): File =
        File(dossier.root, nom).apply { writeBytes(ByteArray(8)) }.also { CheminsDuMediaStore.chemin = it.path }

    @Test
    fun `le lrc voisin se lit, meme nom et meme dossier`() {
        val audio = chanson()
        File(audio.parentFile, "Chanson.lrc").writeText("[00:01.00]Première\n[00:03.00]Seconde")

        assertEquals(
            Lyrics.Synced(listOf(SyncedLine(1_000, "Première"), SyncedLine(3_000, "Seconde"))),
            lire(),
        )
    }

    @Test
    fun `sans lrc voisin, pas de paroles`() {
        chanson()
        File(dossier.root, "Autre.lrc").writeText("[00:01.00]Pas la sienne")

        assertNull(lire())
    }

    @Test
    fun `un lrc de plus d'un mio est ignore`() {
        val audio = chanson()
        File(audio.parentFile, "Chanson.lrc").writeText("[00:01.00]" + "a".repeat((SIDECAR_MAX_BYTES).toInt()))

        assertNull(lire())
    }

    @Test
    fun `un morceau que le MediaStore ne situe pas n'a pas de voisin`() {
        CheminsDuMediaStore.chemin = null

        assertNull(lire())
    }

    @Test
    fun `les voisins possibles d'un chemin`() {
        assertEquals(
            listOf("/sdcard/Music/A b.lrc", "/sdcard/Music/A b.LRC"),
            sidecarCandidates("/sdcard/Music/A b.flac"),
        )
        assertEquals(listOf("/m/v1.2/x.y.lrc", "/m/v1.2/x.y.LRC"), sidecarCandidates("/m/v1.2/x.y.mp3"))
        // Sans extension, pas de « même nom » : un point dans le dossier ne
        // compte pas, ni celui d'un fichier caché.
        assertEquals(emptyList<String>(), sidecarCandidates("/m/v1.2/chanson"))
        assertEquals(emptyList<String>(), sidecarCandidates("/m/.cache"))
    }

    @Test
    fun `l'encodage suit la marque d'ordre d'octets, sinon UTF-8, sinon Windows-1252`() {
        val texte = "Été ♪"
        assertEquals(
            texte,
            decodeLyricsBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + texte.toByteArray()),
        )
        assertEquals(
            texte,
            decodeLyricsBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + texte.toByteArray(Charsets.UTF_16LE)),
        )
        assertEquals(
            texte,
            decodeLyricsBytes(byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + texte.toByteArray(Charsets.UTF_16BE)),
        )
        assertEquals(texte, decodeLyricsBytes(texte.toByteArray(Charsets.UTF_8)))
        // « l’été » écrit sous Windows : 0x92 et 0xE9 ne sont pas de l'UTF-8.
        assertEquals("l’été", decodeLyricsBytes(byteArrayOf(0x6C, 0x92.toByte(), 0xE9.toByte(), 0x74, 0xE9.toByte())))
    }

    /**
     * Le MediaStore, réduit à ce que le lecteur de `.lrc` lui demande : le
     * chemin du morceau, dans la colonne `DATA`.
     */
    class CheminsDuMediaStore : ContentProvider() {
        override fun onCreate(): Boolean = true

        @Suppress("DEPRECATION")
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor = MatrixCursor(arrayOf(MediaStore.Audio.Media.DATA)).apply {
            chemin?.let { addRow(arrayOf<Any?>(it)) }
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0

        companion object {
            @Volatile
            var chemin: String? = null
        }
    }
}
