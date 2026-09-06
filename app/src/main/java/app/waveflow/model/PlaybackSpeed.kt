package app.waveflow.model

/**
 * La vitesse de lecture, et ce qui la tient dans des limites raisonnables.
 *
 * Un nombre et non une énumération comme [ThemeChoice] : la vitesse est une
 * grandeur continue, pas un choix parmi des noms. En faire un jeu de constantes
 * obligerait à baptiser chaque palier, et interdirait d'ajouter un réglage plus
 * fin sans réécrire ce qui est déjà sur le disque. [PROPOSEES] dit ce que
 * l'interface offre aujourd'hui ; le format persisté, lui, ne s'y limite pas.
 */
object PlaybackSpeed {

    /** La vitesse d'origine, celle du disque tel qu'il a été gravé. */
    const val NORMALE = 1f

    /**
     * Les bornes du réglage.
     *
     * En dessous de la moitié, l'étirement temporel de Media3 rend une bouillie
     * plutôt qu'un ralenti ; au-delà du double, plus personne ne suit une
     * parole, et la musique n'y survit pas du tout.
     */
    const val MIN = 0.5f
    const val MAX = 2f

    /**
     * Ce que la feuille de réglage propose.
     *
     * Des quarts entre l'unité et le double, là où se joue l'écoute d'une
     * parole ; la moitié et les trois quarts en dessous, utiles pour repiquer
     * un passage à l'instrument. Un pas plus fin ajouterait des lignes sans
     * ajouter de choix : personne ne distingue ×1,4 de ×1,45.
     */
    val PROPOSEES = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    /**
     * Ramène [speed] dans les bornes, et rend [NORMALE] de ce qui n'est pas un
     * nombre.
     *
     * Ce n'est pas une précaution de style : la valeur vient du disque, donc
     * potentiellement d'une version future qui aurait élargi les bornes, ou
     * d'un fichier abîmé. Une vitesse nulle figerait la lecture sans rien dire,
     * et un `NaN` — que `coerceIn` laisse passer tel quel — ferait lever Media3
     * au moment de l'appliquer, loin de l'endroit où il a été lu.
     */
    fun borner(speed: Float): Float =
        if (speed.isFinite()) speed.coerceIn(MIN, MAX) else NORMALE

    /**
     * La vitesse telle qu'elle s'écrit : « ×1 », « ×1,5 », « ×0,75 ».
     *
     * Formatée à la main plutôt qu'avec `String.format` : celui-ci suit la
     * locale de l'appareil, qui décide aussi bien du séparateur décimal que du
     * jeu de chiffres. L'interface est en français, la virgule y est donc
     * constante — et les chiffres doivent le rester.
     *
     * Les zéros inutiles tombent : « ×1 » et non « ×1,00 », qui suggérerait une
     * précision que le réglage n'offre pas.
     */
    fun format(speed: Float): String {
        val centiemes = Math.round(borner(speed) * CENTIEMES)
        val entier = centiemes / CENTIEMES
        val reste = centiemes % CENTIEMES

        val decimales = when {
            reste == 0 -> ""
            // ×1,50 s'écrit « ×1,5 » : le zéro final ne dit rien de plus.
            reste % DIXIEMES == 0 -> ",${reste / DIXIEMES}"
            // ×1,05 garde le sien, sans quoi il se lirait « ×1,5 ».
            reste < DIXIEMES -> ",0$reste"
            else -> ",$reste"
        }

        return "×$entier$decimales"
    }

    private const val CENTIEMES = 100
    private const val DIXIEMES = 10
}
