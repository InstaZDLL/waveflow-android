package app.waveflow.data.remote

import app.waveflow.model.Lyrics
import app.waveflow.model.SyncedLine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Paroles ----------------------------------------------------------------
//
// `GET /api/v2/tracks/{id}/lyrics` rend toutes les feuilles de la piste, déjà
// rangées par le serveur (RFC-012, décision 6) : la première est celle à
// montrer. Les noms sont en camelCase, contrairement au reste de l'API — c'est
// la forme de la réponse Subsonic `getLyricsBySongId`, reprise telle quelle.

@Serializable
internal data class LyricsListResponse(
    @SerialName("structuredLyrics") val structuredLyrics: List<StructuredLyricsResponse> = emptyList(),
)

@Serializable
internal data class StructuredLyricsResponse(
    val synced: Boolean = false,
    @SerialName("line") val lines: List<LyricsLineResponse> = emptyList(),
)

/** Une ligne : son début en millisecondes, absent pour des paroles simples. */
@Serializable
internal data class LyricsLineResponse(
    val start: Long? = null,
    val value: String = "",
)

/**
 * La feuille à montrer, ou `null` si la piste n'en a pas (`docs/paroles.md`,
 * P4). Une feuille dite synchronisée dont une ligne n'a pas de début se montre
 * en paroles simples : suivre la position supposerait d'inventer l'instant qui
 * manque.
 */
internal fun LyricsListResponse.toModel(): Lyrics? {
    val feuille = structuredLyrics.firstOrNull { sheet -> sheet.lines.any { it.value.isNotBlank() } }
        ?: return null
    val debuts = feuille.lines.mapNotNull { it.start }
    return if (feuille.synced && debuts.size == feuille.lines.size) {
        Lyrics.Synced(
            feuille.lines
                .zip(debuts) { ligne, debut -> SyncedLine(debut.coerceAtLeast(0L), ligne.value.trim()) }
                .sortedBy { it.startMs },
        )
    } else {
        val texte = feuille.lines.map { it.value.trimEnd() }
            .dropWhile { it.isBlank() }
            .dropLastWhile { it.isBlank() }
        Lyrics.Plain(texte)
    }
}
