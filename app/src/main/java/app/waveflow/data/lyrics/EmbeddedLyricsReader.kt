package app.waveflow.data.lyrics

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import androidx.media3.inspector.MetadataRetriever
import app.waveflow.model.Lyrics
import app.waveflow.model.SyncedLine
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Les paroles embarquées dans un fichier de l'appareil (`docs/paroles.md`, P2).
 *
 * Lues par l'extracteur de Media3, celui-là même qui joue le fichier : aucune
 * bibliothèque de tags. [MetadataRetriever] prépare le fichier sans le décoder
 * et rend les métadonnées de chaque piste ; [lyricsOf] y cherche les paroles.
 */
class EmbeddedLyricsReader(private val context: Context) {

    /**
     * Les paroles du fichier [uri], ou `null` s'il n'en porte pas.
     *
     * Un fichier que l'extracteur ne sait pas ouvrir n'a pas de paroles : ce
     * n'est pas une panne à montrer, le lecteur dira lui-même s'il ne le joue
     * pas. L'annulation, elle, remonte : la recherche a été abandonnée, pas
     * manquée.
     */
    suspend fun read(uri: Uri): Lyrics? {
        val groupes = try {
            MetadataRetriever.Builder(context, MediaItem.fromUri(uri)).build().use { retriever ->
                retriever.retrieveTrackGroups().attendre()
            }
        } catch (annulation: CancellationException) {
            throw annulation
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") illisible: Exception) {
            return null
        }
        return lyricsOf(groupes.entrees())
    }
}

/** Toutes les entrées de métadonnées de toutes les pistes du fichier. */
private fun TrackGroupArray.entrees(): List<Metadata.Entry> = buildList {
    val groupes = this@entrees
    for (g in 0 until groupes.length) {
        val groupe = groupes[g]
        for (f in 0 until groupe.length) {
            val metadata = groupe.getFormat(f).metadata ?: continue
            for (e in 0 until metadata.length()) add(metadata[e])
        }
    }
}

/**
 * Attend le futur sans bloquer de fil, et l'annule si l'attente l'est.
 *
 * Sa panne remonte telle quelle, débarrassée de l'enveloppe
 * [ExecutionException].
 */
private suspend fun <T> ListenableFuture<T>.attendre(): T = suspendCancellableCoroutine { suite ->
    addListener(
        {
            try {
                suite.resume(get())
            } catch (enveloppe: ExecutionException) {
                suite.resumeWithException(enveloppe.cause ?: enveloppe)
            } catch (annule: CancellationException) {
                suite.cancel(annule)
            }
        },
        MoreExecutors.directExecutor(),
    )
    suite.invokeOnCancellation { cancel(true) }
}

/**
 * Les paroles que portent ces entrées de métadonnées, telles que Media3 1.11.1
 * les rend :
 * - ID3 `SYLT` et `USLT` (`SLT` et `ULT` en ID3v2.2) : un [BinaryFrame] brut, décodé ici ([decodeSylt],
 *   [decodeUslt]) ;
 * - commentaire Vorbis `LYRICS` ou `UNSYNCEDLYRICS` (FLAC, Ogg, Opus) : un
 *   [VorbisComment] ;
 * - atome MP4 `©lyr` : un [TextInformationFrame] d'identifiant `USLT`.
 *
 * Des paroles synchronisées passent devant des simples (P2), quel que soit
 * l'ordre des tags : un texte qui se lit comme un LRC compte comme
 * synchronisé.
 */
internal fun lyricsOf(entries: List<Metadata.Entry>): Lyrics? {
    val trouvees = entries.mapNotNull { entree ->
        when (entree) {
            is BinaryFrame -> when (entree.id) {
                // ID3v2.2 nomme ses cadres en trois lettres, et Media3 les garde.
                "SYLT", "SLT" -> decodeSylt(entree.data)?.let(Lyrics::Synced)
                "USLT", "ULT" -> decodeUslt(entree.data)?.let(Lrc::parse)
                else -> null
            }
            is VorbisComment ->
                entree.value.takeIf { entree.key.uppercase() in CLES_VORBIS }?.let(Lrc::parse)
            is TextInformationFrame ->
                entree.values.takeIf { entree.id == "USLT" }?.joinToString("\n")?.let(Lrc::parse)
            else -> null
        }
    }
    return trouvees.firstOrNull { it is Lyrics.Synced } ?: trouvees.firstOrNull()
}

private val CLES_VORBIS = setOf("LYRICS", "UNSYNCEDLYRICS")

/**
 * Le texte d'un cadre ID3 `USLT` : encodage (1 octet), langue (3), descripteur
 * terminé, puis le texte. `null` si le cadre est tronqué ou le texte vide.
 */
internal fun decodeUslt(data: ByteArray): String? {
    if (data.size < ENTETE_USLT) return null
    val encodage = data[0].toInt()
    val apresDescripteur = finDeChaine(data, ENTETE_USLT, encodage) ?: return null
    return decoder(data, apresDescripteur.debutSuivant, data.size, encodage)
        .trimEnd('\u0000')
        .takeIf { it.isNotBlank() }
}

/**
 * Les lignes d'un cadre ID3 `SYLT` : encodage (1 octet), langue (3), format des
 * instants (1), type de contenu (1), descripteur terminé, puis des paires
 * « texte terminé, instant sur 4 octets ».
 *
 * Seuls les instants en millisecondes (format 2) se lisent : en trames MPEG
 * (format 1), il faudrait la cadence du flux, que les métadonnées ne donnent
 * pas. Seuls les paroles (type 1) et la transcription (type 2) en sont : des
 * accords ou des liens horodatés ne se montrent pas comme des paroles.
 *
 * Un `SYLT` peut horodater des syllabes plutôt que des lignes : le saut de
 * ligne est alors porté en tête du fragment qui ouvre la ligne suivante. Les
 * fragments se regroupent dans ce cas en lignes, chacune au début de son
 * premier fragment ; sinon chaque fragment est une ligne. Les lignes sont
 * rangées par instant ; `null` si aucune n'a de texte.
 */
internal fun decodeSylt(data: ByteArray): List<SyncedLine>? {
    val lisible = data.size >= ENTETE_SYLT &&
        data[OCTET_FORMAT_SYLT].toInt() == SYLT_EN_MILLISECONDES &&
        data[OCTET_CONTENU_SYLT].toInt() in SYLT_CONTENUS_PAROLES
    val encodage = data.firstOrNull()?.toInt() ?: 0
    var position = if (lisible) finDeChaine(data, ENTETE_SYLT, encodage)?.debutSuivant else null

    val fragments = mutableListOf<SyncedLine>()
    while (position != null) {
        val fin = finDeChaine(data, position, encodage)
        position = if (fin != null && fin.debutSuivant + Int.SIZE_BYTES <= data.size) {
            val texte = decoder(data, position, fin.finTexte, encodage)
            val instant = ByteBuffer.wrap(data, fin.debutSuivant, Int.SIZE_BYTES).int.toUInt().toLong()
            fragments += SyncedLine(instant, texte)
            fin.debutSuivant + Int.SIZE_BYTES
        } else {
            null
        }
    }
    return enLignes(fragments).sortedBy { it.startMs }.takeIf { liste -> liste.any { it.text.isNotEmpty() } }
}

/**
 * Des fragments `SYLT` aux lignes : regroupés quand un fragment après le premier
 * ouvre sa ligne par un saut de ligne, un par ligne sinon.
 *
 * Regroupés, un fragment sans texte (ou fait de sauts de ligne seuls) est une
 * ligne à lui seul, comme une ligne horodatée vide d'un LRC : il dit, à son
 * instant, que la ligne précédente est finie. Le fragment qui le suit ouvre une
 * nouvelle ligne, saut de ligne ou non.
 */
private fun enLignes(fragments: List<SyncedLine>): List<SyncedLine> {
    val syllabique = fragments.drop(1).any { it.text.startsWith('\n') || it.text.startsWith('\r') }
    if (!syllabique) return fragments.map { it.copy(text = it.text.trim()) }

    val lignes = mutableListOf<SyncedLine>()
    var fermee = true
    for (fragment in fragments) {
        val marqueur = fragment.text.all { it == '\n' || it == '\r' }
        val ouvre = fermee || marqueur || fragment.text.startsWith('\n') || fragment.text.startsWith('\r')
        if (ouvre) {
            lignes += fragment
        } else {
            lignes[lignes.lastIndex] = lignes.last().let { it.copy(text = it.text + fragment.text) }
        }
        fermee = marqueur
    }
    return lignes.map { it.copy(text = it.text.trim()) }
}

/** Où s'arrête une chaîne terminée, et où commence ce qui la suit. */
private class FinDeChaine(val finTexte: Int, val debutSuivant: Int)

/**
 * La fin de la chaîne qui commence à [debut] : un octet nul en Latin-1 et en
 * UTF-8, deux octets nuls alignés en UTF-16. `null` sans terminaison.
 */
private fun finDeChaine(data: ByteArray, debut: Int, encodage: Int): FinDeChaine? {
    val largeur = if (encodage == ID3_UTF16 || encodage == ID3_UTF16BE) 2 else 1
    val fin = (debut..data.size - largeur step largeur).firstOrNull { i ->
        (0 until largeur).all { data[i + it].toInt() == 0 }
    }
    return fin?.let { FinDeChaine(it, it + largeur) }
}

private fun decoder(data: ByteArray, debut: Int, fin: Int, encodage: Int): String {
    if (fin <= debut) return ""
    val jeu: Charset = when (encodage) {
        ID3_UTF16 -> Charsets.UTF_16 // la marque d'ordre d'octets décide
        ID3_UTF16BE -> Charsets.UTF_16BE
        ID3_UTF8 -> Charsets.UTF_8
        else -> Charsets.ISO_8859_1
    }
    return String(data, debut, fin - debut, jeu).removePrefix(Char(BOM_CODE).toString())
}

private const val ID3_UTF16 = 1
private const val ID3_UTF16BE = 2
private const val ID3_UTF8 = 3

/** Encodage et langue. */
private const val ENTETE_USLT = 4

/** Encodage, langue, format des instants et type de contenu. */
private const val ENTETE_SYLT = 6

/** Le format des instants suit l'encodage et la langue. */
private const val OCTET_FORMAT_SYLT = 4

private const val SYLT_EN_MILLISECONDES = 2

/** Le type de contenu suit le format des instants. */
private const val OCTET_CONTENU_SYLT = 5

/** Paroles (1) et transcription (2) : les autres types ne se chantent pas. */
private val SYLT_CONTENUS_PAROLES = setOf(1, 2)
