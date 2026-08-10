package app.waveflow.ui.server

import app.waveflow.model.ServerSession

/**
 * Ce que l'onglet Serveur affiche.
 *
 * [session] vient du dépôt, le reste appartient à l'écran : une connexion en
 * cours et le message du dernier échec n'ont pas à survivre à la fermeture de
 * l'app.
 */
data class ServerUiState(
    val session: ServerSession = ServerSession.Disconnected,
    val isConnecting: Boolean = false,
    val errorMessage: String? = null,
) {
    val isConnected: Boolean get() = session is ServerSession.Connected

    val connected: ServerSession.Connected? get() = session as? ServerSession.Connected
}
