package app.waveflow.playback

import androidx.media3.common.C
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo
import kotlin.random.Random

/**
 * Retente un transcodage refusé quand le serveur le demande, et pas autrement.
 *
 * Le serveur borne les transcodages simultanés, par compte et pour lui-même.
 * Au-delà, la requête du flux répond `429` avec `Retry-After`, avant le premier
 * octet. C'est la source HTTP de Media3 qui le reçoit — le ticket, lui, n'est
 * jamais refusé —, et c'est donc ici, là où Media3 décide de retenter un
 * chargement, qu'il se traite (R17 de `docs/deplacement-dans-un-transcodage.md`).
 *
 * La politique par défaut retentait déjà un 429, mais sans lire l'en-tête :
 * aussitôt, puis à 1 s et 2 s. Revenir avant l'heure, c'est se faire refuser à
 * coup sûr. On attend donc le délai annoncé (R18), plus une gigue (R19), et
 * l'on cesse après [RELANCES_MAX] relances (R20). Ce qui suit l'abandon — le
 * repli sur l'original — se décide plus haut, voir [TranscodeRefusalFallback].
 *
 * Toute autre erreur garde le comportement de Media3, délais et abandon compris.
 *
 * @param hasard la source de la gigue ; remplaçable pour qu'un test en
 *   connaisse les bornes.
 */
internal class TranscodeRefusalPolicy(
    private val hasard: Random = Random.Default,
) : DefaultLoadErrorHandlingPolicy() {

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorInfo): Long {
        val refus = loadErrorInfo.exception.refusDeTranscodage()
            ?: return super.getRetryDelayMsFor(loadErrorInfo)
        // Media3 rendrait de toute façon l'erreur au lecteur à ce compte-là ;
        // dire qu'on renonce évite de laisser une relance en suspens derrière.
        if (loadErrorInfo.errorCount > RELANCES_MAX) return C.TIME_UNSET

        val delaiMs = refus.delaiAnnonceMs()
        return delaiMs + hasard.nextLong(delaiMs / 2 + 1)
    }

    companion object {
        /**
         * Trois relances, quatre demandes : de 3 à 4,5 s avec `Retry-After: 1`.
         *
         * Le seuil même où Media3 rend une erreur de chargement au lecteur
         * (`getMinimumLoadableRetryCount`), et ce n'est pas un hasard : le
         * relever pour le 429 le relèverait pour toute panne, et changerait le
         * moment où une coupure réseau devient fatale — ce compte n'est remis à
         * zéro qu'après la relance d'un flux qui avait progressé.
         */
        const val RELANCES_MAX = DefaultLoadErrorHandlingPolicy.DEFAULT_MIN_LOADABLE_RETRY_COUNT

        /** Ce que le serveur envoie toujours, et ce qu'on suppose s'il ne dit rien. */
        const val DELAI_PAR_DEFAUT_MS = 1_000L

        /** Garde-fou : au-delà, l'application paraîtrait figée. */
        const val DELAI_MAX_MS = 30_000L
    }
}

/**
 * Le refus de transcodage que porte cette erreur, s'il y en a un.
 *
 * Cherché sous les couches d'emballage : le lecteur range l'erreur de la source
 * dans une `PlaybackException`, et la chaîne de cache peut l'avoir enveloppée.
 */
internal fun Throwable.refusDeTranscodage(): InvalidResponseCodeException? =
    generateSequence(this) { it.cause.takeIf { suivante -> suivante !== it } }
        .take(PROFONDEUR_MAX)
        .filterIsInstance<InvalidResponseCodeException>()
        .firstOrNull { it.responseCode == HTTP_TOO_MANY_REQUESTS }

/**
 * Le délai que le serveur a annoncé, en millisecondes (R18).
 *
 * `Retry-After` en secondes. La forme date de l'en-tête, absente du serveur, se
 * lit comme un en-tête illisible : une seconde. Les noms d'en-tête ne sont pas
 * sensibles à la casse, et la pile HTTP les rend comme elle les a reçus.
 *
 * `HttpURLConnection` range la ligne de statut sous une clé `null`, que le type
 * Kotlin de la carte ne laisse pas voir : chaque nom se lit donc comme nullable.
 */
internal fun InvalidResponseCodeException.delaiAnnonceMs(): Long {
    val valeur = headerFields.entries
        .firstOrNull { entree -> (entree.key as String?)?.equals(RETRY_AFTER, ignoreCase = true) == true }
        ?.value?.firstOrNull()
    val secondes = valeur?.trim()?.toLongOrNull()?.takeIf { it >= 0L }
        ?: return TranscodeRefusalPolicy.DELAI_PAR_DEFAUT_MS
    // Borné avant la multiplication : un en-tête aberrant la ferait déborder.
    return secondes.coerceAtMost(TranscodeRefusalPolicy.DELAI_MAX_MS / MS_PAR_SECONDE) * MS_PAR_SECONDE
}

private const val HTTP_TOO_MANY_REQUESTS = 429
private const val RETRY_AFTER = "Retry-After"
private const val MS_PAR_SECONDE = 1_000L

/** Garde-fou : une chaîne de causes circulaire ne doit pas figer le lecteur. */
private const val PROFONDEUR_MAX = 10
