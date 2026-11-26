package app.waveflow.model

/**
 * Les paroles d'un morceau, prêtes à afficher — quelle que soit leur source.
 *
 * Voir `docs/paroles.md` : d'où elles viennent (P1 à P5), comment le format LRC
 * se lit (P9, P10).
 */
sealed interface Lyrics {

    /**
     * Des paroles synchronisées : chaque ligne sait quand elle se chante.
     *
     * @property lines jamais vide, rangée par instant croissant — l'ordre du
     *   texte départage les égalités. Une ligne au texte vide marque la fin de
     *   la précédente (un pont instrumental).
     */
    data class Synced(val lines: List<SyncedLine>) : Lyrics

    /**
     * Des paroles sans instants : un texte, ligne à ligne.
     *
     * @property lines jamais vide, sans ligne vide en tête ni en queue ; celles
     *   du milieu séparent les strophes.
     */
    data class Plain(val lines: List<String>) : Lyrics
}

/** Une ligne de paroles synchronisées : son début dans le morceau, et son texte. */
data class SyncedLine(val startMs: Long, val text: String)
