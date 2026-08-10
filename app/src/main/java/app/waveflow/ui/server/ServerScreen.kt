package app.waveflow.ui.server

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.waveflow.model.ServerSession

/**
 * Onglet Serveur : connexion, puis compte connecté.
 *
 * Le serveur est une source à part de la bibliothèque de l'appareil ; rien ici
 * ne dépend de la permission audio ni du MediaStore.
 */
@Composable
fun ServerScreen(
    state: ServerUiState,
    onConnect: (serverUrl: String, username: String, password: String) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 32.dp, bottom = bottomPadding + 32.dp),
    ) {
        when (val connected = state.connected) {
            null -> ConnectionForm(state = state, onConnect = onConnect)
            else -> ConnectedAccount(session = connected, onDisconnect = onDisconnect)
        }
    }
}

@Composable
private fun ConnectionForm(
    state: ServerUiState,
    onConnect: (serverUrl: String, username: String, password: String) -> Unit,
) {
    // `rememberSaveable` : une rotation ne doit pas vider un formulaire à
    // moitié rempli. Le mot de passe en est exclu — il ne part pas dans l'état
    // sauvegardé, qui survit au processus.
    var serverUrl by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    fun submit() {
        if (!state.isConnecting) onConnect(serverUrl, username, password)
    }

    Icon(
        imageVector = Icons.Filled.Cloud,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(56.dp),
    )
    Spacer(Modifier.height(16.dp))
    Text("Se connecter à un serveur", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Votre bibliothèque locale reste inchangée.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(32.dp))

    OutlinedTextField(
        value = serverUrl,
        onValueChange = { serverUrl = it },
        label = { Text("Adresse du serveur") },
        placeholder = { Text("musique.exemple.fr") },
        singleLine = true,
        enabled = !state.isConnecting,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Next,
            autoCorrectEnabled = false,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = username,
        onValueChange = { username = it },
        label = { Text("Identifiant") },
        singleLine = true,
        enabled = !state.isConnecting,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            imeAction = ImeAction.Next,
            autoCorrectEnabled = false,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Mot de passe") },
        singleLine = true,
        enabled = !state.isConnecting,
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        // « Terminé » doit valider : proposer l'action puis ne rien en faire
        // oblige à revenir chercher le bouton.
        keyboardActions = KeyboardActions(onDone = { submit() }),
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    imageVector = if (passwordVisible) {
                        Icons.Filled.VisibilityOff
                    } else {
                        Icons.Filled.Visibility
                    },
                    contentDescription = if (passwordVisible) {
                        "Masquer le mot de passe"
                    } else {
                        "Afficher le mot de passe"
                    },
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )

    state.errorMessage?.let { message ->
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxWidth()
                // Le message apparaît sans que le foyer bouge : sans région
                // active, TalkBack ne dirait rien de l'échec.
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }

    Spacer(Modifier.height(24.dp))
    Button(
        onClick = { submit() },
        enabled = !state.isConnecting,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.isConnecting) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text("Connexion…")
        } else {
            Text("Se connecter")
        }
    }

    Spacer(Modifier.height(16.dp))
    Text(
        text = "Sans schéma, l'adresse est jointe en HTTPS.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ConnectedAccount(
    session: ServerSession.Connected,
    onDisconnect: () -> Unit,
) {
    Icon(
        imageVector = Icons.Filled.CloudDone,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(56.dp),
    )
    Spacer(Modifier.height(16.dp))
    Text(session.username, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(4.dp))
    Text(
        text = session.serverUrl,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(32.dp))
    Text(
        text = "Le catalogue du serveur arrive dans une prochaine version. " +
            "Cette connexion ne fait encore rien d'autre.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(32.dp))
    OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
        Text("Se déconnecter")
    }
}
