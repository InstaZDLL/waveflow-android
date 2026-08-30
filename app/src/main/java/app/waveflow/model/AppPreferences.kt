package app.waveflow.model

/** Ce que l'utilisateur a choisi, et qui survit à la fermeture de l'application. */
data class AppPreferences(
    val theme: ThemeChoice = ThemeChoice.System,
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
