package app.waveflow.model

/**
 * Ce que l'utilisateur a choisi, et qui survit à la fermeture de l'application.
 *
 * @property playbackSpeed la vitesse de lecture. Elle se persiste parce qu'elle
 *   décrit une habitude et non un geste : qui écoute ses podcasts à ×1,5 ne
 *   veut pas le redire à chaque lancement. Voir [PlaybackSpeed] pour ses bornes.
 * @property streamQuality ce qu'on demande au serveur pour chaque piste. Elle
 *   ne dit rien des fichiers de l'appareil, qui ne passent pas par lui. Sous
 *   [automaticQuality], c'est le profil fixe qu'on retrouvera en le quittant.
 * @property automaticQuality vrai quand la qualité suit le réseau : le profil
 *   vient alors de [qualityOn]. Voir `docs/qualite-automatique.md`, Q1 et Q2.
 * @property unmeteredQuality le profil *Wi-Fi* de la qualité automatique.
 * @property meteredQuality le profil *Données mobiles* de la qualité
 *   automatique : Économie par défaut, puisque c'est le forfait qu'on ménage.
 */
data class AppPreferences(
    val theme: ThemeChoice = ThemeChoice.System,
    val playbackSpeed: Float = PlaybackSpeed.NORMALE,
    val streamQuality: StreamQuality = StreamQuality.Original,
    val automaticQuality: Boolean = false,
    val unmeteredQuality: StreamQuality = StreamQuality.Original,
    val meteredQuality: StreamQuality = StreamQuality.Economie,
) {

    /** Le profil que la qualité automatique prend sur ce réseau. */
    fun qualityOn(network: StreamNetwork): StreamQuality = when (network) {
        StreamNetwork.Unmetered -> unmeteredQuality
        StreamNetwork.Metered -> meteredQuality
    }

    /**
     * Le rendu à demander au serveur sur ce réseau.
     *
     * Le réseau ne compte que sous [automaticQuality] : un profil fixe vaut
     * partout.
     */
    fun renderingOn(network: StreamNetwork): StreamRendering =
        if (automaticQuality) qualityOn(network).rendering else streamQuality.rendering
}

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
