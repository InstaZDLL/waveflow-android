package app.waveflow.model

/**
 * Lien avec un serveur WaveFlow.
 *
 * Le serveur est une source à part : rien ici ne touche à la bibliothèque de
 * l'appareil, et une session absente ne dégrade en rien le reste de l'app.
 */
sealed interface ServerSession {

    /** Aucun serveur configuré, ou déconnexion demandée. */
    data object Disconnected : ServerSession

    /**
     * Session ouverte.
     *
     * [accessToken] est de courte durée — un quart d'heure côté serveur — et se
     * renouvelle avec [refreshToken]. Ce dernier tourne à chaque usage : le
     * serveur invalide l'ancien dès qu'il en émet un nouveau.
     *
     * @property deviceId appareil enregistré à la connexion. Le serveur s'en
     *   sert pour attribuer les mutations et pour l'accusé de synchronisation ;
     *   il rejette un appareil appartenant à un autre compte.
     * @property accessExpiresAtMs échéance absolue, calculée à la réception à
     *   partir du `expires_in` relatif renvoyé par le serveur.
     */
    data class Connected(
        val serverUrl: String,
        val username: String,
        val accessToken: String,
        val refreshToken: String,
        val deviceId: String,
        val accessExpiresAtMs: Long,
    ) : ServerSession
}
