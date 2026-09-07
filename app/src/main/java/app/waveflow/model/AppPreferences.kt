package app.waveflow.model

/**
 * Ce que l'utilisateur a choisi, et qui survit à la fermeture de l'application.
 *
 * @property playbackSpeed la vitesse de lecture. Elle se persiste parce qu'elle
 *   décrit une habitude et non un geste : qui écoute ses podcasts à ×1,5 ne
 *   veut pas le redire à chaque lancement. Voir [PlaybackSpeed] pour ses bornes.
 */
data class AppPreferences(
    val theme: ThemeChoice = ThemeChoice.System,
    val playbackSpeed: Float = PlaybackSpeed.NORMALE,
)

/**
 * Le thème demandé.
 *
 * [System] est le défaut et non un troisième choix de politesse : tant que
 * l'utilisateur n'a rien dit, suivre l'appareil est ce qu'il attend.
 */
enum class ThemeChoice(val label: String) {
    System("Système"),
    Light("Clair"),
    Dark("Sombre"),
}
