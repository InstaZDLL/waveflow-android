package app.waveflow.playback

/**
 * Ce que les réglages ont besoin de savoir du cache de lecture.
 *
 * Volontairement plus étroit que [RemoteMediaCache] : l'écran n'a que faire de
 * la chaîne de sources, et le réduire ainsi le rend éprouvable sans ouvrir de
 * vrai répertoire de cache.
 */
interface PlaybackCache {

    /** Plafond au-delà duquel les pistes les plus anciennes sont évincées. */
    val maxBytes: Long

    /** Place occupée, à l'octet près. */
    suspend fun usedBytes(): Long

    /** Retire tout ce que le cache contient. */
    suspend fun clear()
}
