package app.waveflow.suggestion

/**
 * Où en est la suggestion du jour : le jour du tirage, les appuis sur « Une autre
 * suggestion » depuis, et la suggestion qu'il a donnée (S9 de
 * `docs/suggestion-du-jour.md`).
 *
 * @property jour les jours depuis l'époque de la date locale (S2).
 * @property essais positif ou nul.
 */
data class TirageDuJour(
    val jour: Long,
    val essais: Int = 0,
    val epinglee: SuggestionEpinglee? = null,
)

/**
 * Le tirage qui vaut pour [jour] : celui-ci s'il en est, sinon le premier du jour,
 * sans appui ni épinglée — un tirage d'un autre jour est périmé (S10).
 */
fun TirageDuJour?.pour(jour: Long): TirageDuJour =
    if (this != null && this.jour == jour) this else TirageDuJour(jour)

/** Après un appui sur « Une autre suggestion » ce jour-là : un appui de plus, rien d'épinglé (S11). */
fun TirageDuJour?.apresUnAppui(jour: Long): TirageDuJour {
    val courant = pour(jour)
    val essais = if (courant.essais < Int.MAX_VALUE) courant.essais + 1 else courant.essais
    return TirageDuJour(jour, essais, epinglee = null)
}

/**
 * Le tirage, une fois [nouvelle] épinglée — à condition que ce qui est enregistré
 * soit encore [depuis], l'état d'après lequel elle a été tirée (S11). Sinon, rien
 * ne change : un appui arrivé entre le tirage et son écriture n'est pas écrasé,
 * pas plus qu'un tirage du lendemain par celui de la veille.
 */
fun TirageDuJour?.epingler(depuis: TirageDuJour, nouvelle: SuggestionEpinglee): TirageDuJour? = when {
    // [pour] tiendrait pour périmé un tirage du lendemain comme un tirage d'hier.
    this != null && jour > depuis.jour -> this
    pour(depuis.jour) == depuis -> depuis.copy(epinglee = nouvelle)
    else -> this
}
