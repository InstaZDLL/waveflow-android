package app.waveflow.data.lyrics

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import app.waveflow.model.Lyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Le `.lrc` posé à côté d'un fichier de l'appareil (`docs/paroles.md`, P3).
 *
 * Le chemin du morceau vient du MediaStore, qui le tient dans sa colonne
 * `DATA` — dépréciée pour l'écriture, toujours remplie en lecture.
 */
class SidecarLyricsReader(private val resolver: ContentResolver) {

    /**
     * Les paroles du `.lrc` voisin du morceau [songUri], ou `null` s'il n'y en
     * a pas, s'il est trop gros ou si le système refuse de l'ouvrir.
     */
    suspend fun read(songUri: Uri): Lyrics? = withContext(Dispatchers.IO) {
        val chemin = cheminDe(songUri) ?: return@withContext null
        sidecarCandidates(chemin).firstNotNullOfOrNull { candidat ->
            lireBorne(File(candidat))?.let { Lrc.parse(decodeLyricsBytes(it)) }
        }
    }

    @Suppress("DEPRECATION") // DATA ne se lit plus que par là, et se lit toujours.
    private fun cheminDe(songUri: Uri): String? = try {
        resolver.query(songUri, arrayOf(MediaStore.Audio.Media.DATA), null, null, null)?.use { curseur ->
            if (curseur.moveToFirst()) curseur.getString(0) else null
        }
    } catch (_: SecurityException) {
        null
    }

    /**
     * Le contenu du fichier s'il tient dans la limite. La limite borne les octets
     * lus, pas la taille annoncée : un fichier qui grossit entre les deux ne la
     * franchit pas.
     */
    private fun lireBorne(fichier: File): ByteArray? = try {
        fichier.takeIf { it.isFile }?.inputStream()?.use { it.lireAuPlus(SIDECAR_MAX_BYTES.toInt()) }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }
}

/**
 * Au plus [limite] octets du flux, ou `null` s'il en a davantage. Un octet de
 * plus que la limite est demandé : s'il arrive, le flux la passe.
 */
private fun InputStream.lireAuPlus(limite: Int): ByteArray? {
    val tampon = ByteArray(limite + 1)
    var lus = 0
    var n = 0
    while (lus < tampon.size && n >= 0) {
        n = read(tampon, lus, tampon.size - lus)
        if (n > 0) lus += n
    }
    return tampon.copyOf(lus).takeIf { lus <= limite }
}

/**
 * Les `.lrc` voisins possibles d'un morceau, dans l'ordre où les essayer : même
 * dossier, même nom, extension remplacée — en minuscules, puis en capitales.
 * Rien pour un chemin sans extension, qui n'a pas de « même nom ».
 */
internal fun sidecarCandidates(audioPath: String): List<String> {
    val nom = audioPath.substringAfterLast('/')
    val point = nom.lastIndexOf('.')
    if (point <= 0) return emptyList()
    val base = audioPath.substring(0, audioPath.length - nom.length + point)
    return listOf("$base.lrc", "$base.LRC")
}

/**
 * Le texte d'un fichier de paroles : l'encodage que nomme sa marque d'ordre
 * d'octets, sinon UTF-8 s'il en est, sinon Windows-1252 — l'encodage des
 * `.lrc` écrits sous Windows, et qui sait décoder n'importe quel octet.
 */
internal fun decodeLyricsBytes(bytes: ByteArray): String {
    val marque = Char(BOM_CODE).toString()
    fun octet(i: Int) = bytes.getOrNull(i)?.toUByte()?.toInt() ?: -1
    fun mot(fort: Int, faible: Int) = (octet(fort) shl Byte.SIZE_BITS) or octet(faible)
    fun apresMarque(jeu: Charset) = String(bytes, Char.SIZE_BYTES, bytes.size - Char.SIZE_BYTES, jeu)

    return when {
        bytes.size >= Char.SIZE_BYTES && mot(0, 1) == BOM_CODE -> apresMarque(Charsets.UTF_16BE)
        bytes.size >= Char.SIZE_BYTES && mot(1, 0) == BOM_CODE -> apresMarque(Charsets.UTF_16LE)
        // En UTF-8, la marque se décode en U+FEFF : elle tombe avec le texte.
        else -> try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
                .removePrefix(marque)
        } catch (_: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1252"))
        }
    }
}

/** La limite du serveur pour un fichier voisin : au-delà, ce ne sont pas des paroles. */
internal const val SIDECAR_MAX_BYTES = 1L shl 20
