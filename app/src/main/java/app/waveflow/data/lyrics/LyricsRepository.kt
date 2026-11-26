package app.waveflow.data.lyrics

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import app.waveflow.data.remote.ServerException
import app.waveflow.model.Lyrics
import app.waveflow.playback.REMOTE_PREFIX
import app.waveflow.playback.localSongIdOf

/** Ce que la recherche des paroles d'un morceau a donné. */
sealed interface LyricsResult {

    /** Le morceau a des paroles. */
    data class Found(val lyrics: Lyrics) : LyricsResult

    /** Le morceau n'en a pas : ni dans son fichier, ou le serveur n'en connaît pas. */
    data object None : LyricsResult

    /** Le serveur n'a pas pu répondre : on ne sait pas, et on peut réessayer. */
    data object Unavailable : LyricsResult
}

/**
 * Les paroles d'un morceau, d'où qu'il vienne (`docs/paroles.md`, P1 à P8).
 *
 * Une piste de l'appareil ne prend ses paroles que dans son fichier — tags
 * d'abord, `.lrc` voisin ensuite —, une piste du serveur que du serveur :
 * aucune n'est rapprochée de l'autre (RFC-003), il n'y a donc pas de source à
 * préférer. Les écrans ne parlent qu'au dépôt ; lui seul touche au réseau et
 * aux fichiers.
 *
 * Les sources sont des fonctions pour s'éprouver sans fichier ni serveur.
 *
 * @param embedded les paroles des tags du fichier dont on donne l'URI.
 * @param sidecar celles du `.lrc` voisin de ce même fichier.
 * @param server celles que le serveur connaît pour cet identifiant de piste ;
 *   lève [ServerException] quand il ne répond pas.
 */
class LyricsRepository(
    private val embedded: suspend (Uri) -> Lyrics?,
    private val sidecar: suspend (Uri) -> Lyrics?,
    private val server: suspend (String) -> Lyrics?,
) {

    /**
     * Ce qui a été trouvé, et ce qui n'a pas été trouvé, pour les derniers
     * morceaux (P7). Les pannes n'y entrent pas : la prochaine demande réessaie.
     */
    private val gardes = object : LinkedHashMap<String, LyricsResult>(CACHE_SIZE, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LyricsResult>): Boolean =
            size > CACHE_SIZE
    }

    /**
     * Les paroles du morceau [mediaId], tel que le lecteur le nomme.
     *
     * Deux demandes simultanées du même morceau cherchent chacune de leur
     * côté : elles trouvent la même chose, et la seconde écrase la première
     * dans le cache par une valeur égale.
     */
    suspend fun lyricsFor(mediaId: String): LyricsResult {
        synchronized(gardes) { gardes[mediaId] }?.let { return it }

        val resultat = chercher(mediaId)
        if (resultat != LyricsResult.Unavailable) {
            synchronized(gardes) { gardes[mediaId] = resultat }
        }
        return resultat
    }

    private suspend fun chercher(mediaId: String): LyricsResult {
        localSongIdOf(mediaId)?.let { songId ->
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
            return (embedded(uri) ?: sidecar(uri)).trouve()
        }

        val trackId = mediaId.takeIf { it.startsWith(REMOTE_PREFIX) }?.removePrefix(REMOTE_PREFIX)
            ?.takeIf { it.isNotEmpty() }
            ?: return LyricsResult.None
        return try {
            server(trackId).trouve()
        } catch (_: ServerException) {
            LyricsResult.Unavailable
        }
    }

    private fun Lyrics?.trouve(): LyricsResult = this?.let(LyricsResult::Found) ?: LyricsResult.None

    private companion object {
        const val CACHE_SIZE = 32
        const val LOAD_FACTOR = 0.75f
    }
}
