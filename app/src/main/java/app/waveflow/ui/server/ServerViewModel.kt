package app.waveflow.ui.server

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.waveflow.WaveFlowApp
import app.waveflow.data.remote.ServerException
import app.waveflow.data.remote.ServerSessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Connexion au serveur WaveFlow : saisie, état, déconnexion. */
class ServerViewModel(
    private val sessionRepository: ServerSessionRepository,
) : ViewModel() {

    /** Ce que l'écran ajoute à la session : progression et dernier échec. */
    private val local = MutableStateFlow(ServerUiState())

    private val _state = MutableStateFlow(ServerUiState())
    val state: StateFlow<ServerUiState> = _state.asStateFlow()

    init {
        combine(sessionRepository.session, local) { session, local ->
            local.copy(session = session)
        }.onEach { _state.value = it }.launchIn(viewModelScope)
    }

    fun connect(serverUrl: String, username: String, password: String) {
        if (local.value.isConnecting) return

        val url = serverUrl.trim()
        val user = username.trim()
        if (url.isEmpty() || user.isEmpty() || password.isEmpty()) {
            local.value = local.value.copy(
                errorMessage = "Adresse, identifiant et mot de passe sont requis.",
            )
            return
        }

        local.value = local.value.copy(isConnecting = true, errorMessage = null)
        viewModelScope.launch {
            try {
                sessionRepository.connect(url, user, password)
                local.value = ServerUiState()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: ServerException) {
                // Le message du serveur est repris tel quel pour les refus
                // d'identifiants ; le reste est reformulé, « connection reset »
                // ne disant rien à personne.
                local.value = local.value.copy(
                    isConnecting = false,
                    errorMessage = error.toMessage(),
                )
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            try {
                sessionRepository.disconnect()
                local.value = ServerUiState()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // Le dépôt tolère déjà un serveur injoignable, mais l'effacement
                // sur disque peut échouer. Sans ce filet, l'exception quitterait
                // `viewModelScope` et ferait tomber l'application.
                Log.w(TAG, "Déconnexion incomplète", error)
                local.value = local.value.copy(
                    errorMessage = "La déconnexion n'a pas pu être enregistrée.",
                )
            }
        }
    }

    fun dismissError() {
        local.value = local.value.copy(errorMessage = null)
    }

    private fun ServerException.toMessage(): String = when (this) {
        is ServerException.Unauthorized -> "Identifiant ou mot de passe refusé."
        is ServerException.Rejected -> message ?: "Requête refusée par le serveur."
        is ServerException.Unreachable -> "Serveur injoignable. Vérifiez l'adresse et le réseau."
        is ServerException.Unexpected -> "Le serveur a répondu de façon inattendue."
    }

    companion object {
        private const val TAG = "ServerViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as WaveFlowApp
                ServerViewModel(sessionRepository = app.container.serverSessionRepository)
            }
        }
    }
}
