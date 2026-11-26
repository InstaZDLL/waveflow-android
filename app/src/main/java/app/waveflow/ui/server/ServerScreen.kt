package app.waveflow.ui.server

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.waveflow.model.ServerSession
import app.waveflow.model.StreamNetwork
import app.waveflow.model.StreamQuality
import app.waveflow.ui.cache.CacheSection
import app.waveflow.ui.cache.CacheUiState
import app.waveflow.ui.components.AccentPill
import app.waveflow.ui.components.GhostPill
import app.waveflow.ui.components.Kicker
import app.waveflow.ui.quality.StreamQualitySection
import app.waveflow.ui.quality.StreamQualityUiState
import app.waveflow.ui.theme.WaveFlowTheme

/**
 * Ouverture d'une session serveur.
 *
 * Le serveur est une source à part de la bibliothèque de l'appareil ; rien ici
 * ne dépend de la permission audio ni du MediaStore.
 *
 * Séparé du compte depuis l'arrivée du catalogue : une fois connecté, l'onglet
 * affiche ce catalogue, et le compte devient un écran qu'on ouvre.
 */
@Composable
fun ServerSignInScreen(
    state: ServerUiState,
    onConnect: (serverUrl: String, username: String, password: String) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    ScrollableColumn(modifier = modifier, bottomPadding = bottomPadding) {
        ConnectionForm(state = state, onConnect = onConnect)
    }
}

/**
 * Le compte connecté, la sortie, la qualité de lecture et le cache des pistes
 * qu'il a servies.
 *
 * La qualité et le cache trouvent leur place ici plutôt que dans les réglages
 * généraux : ils ne concernent que les pistes du serveur. La qualité vient avant
 * le cache, parce qu'elle décide de ce qu'il contiendra.
 */
@Composable
fun ServerAccountScreen(
    session: ServerSession.Connected,
    quality: StreamQualityUiState,
    cache: CacheUiState,
    onDisconnect: () -> Unit,
    onChooseQuality: (StreamQuality) -> Unit,
    onChooseAutomaticQuality: () -> Unit,
    onChooseQualityOn: (StreamNetwork, StreamQuality) -> Unit,
    onClearCache: () -> Unit,
    onDismissCacheError: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    ScrollableColumn(modifier = modifier, bottomPadding = bottomPadding) {
        ConnectedAccount(session = session, onDisconnect = onDisconnect)

        Spacer(Modifier.height(24.dp))

        StreamQualitySection(
            state = quality,
            onChoose = onChooseQuality,
            onChooseAutomatic = onChooseAutomaticQuality,
            onChooseOn = onChooseQualityOn,
        )

        Spacer(Modifier.height(16.dp))

        CacheSection(
            state = cache,
            onClear = onClearCache,
            onDismissError = onDismissCacheError,
        )
    }
}

/**
 * La page, alignée à gauche comme les autres : sa marge, et sous elle la
 * réserve du mini-lecteur.
 */
@Composable
private fun ScrollableColumn(
    modifier: Modifier,
    bottomPadding: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp, bottom = bottomPadding + 32.dp),
        content = content,
    )
}

/**
 * L'en-tête d'une page du compte : le surtitre, le titre en affichage, une
 * ligne de méta. L'icône de nuage dans sa pastille est partie : la direction
 * retire les boîtes d'icônes colorées, le surtitre et le titre portent la page.
 */
@Composable
private fun AccountHeader(kicker: String, title: String, meta: String) {
    Kicker(kicker)
    Spacer(Modifier.height(10.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(14.dp))
    Text(
        text = meta,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ConnectionForm(
    state: ServerUiState,
    onConnect: (serverUrl: String, username: String, password: String) -> Unit,
) {
    // `rememberSaveable` : une rotation ne doit pas vider un formulaire à
    // moitié rempli. Le mot de passe en est exclu — il ne part pas dans l'état
    // sauvegardé, qui survit au processus : son état est un simple `remember`,
    // et non `rememberTextFieldState`, qui le sauvegarderait.
    var serverUrl by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    val password = remember { TextFieldState() }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    fun submit() {
        if (!state.isConnecting) onConnect(serverUrl, username, password.text.toString())
    }

    AccountHeader(
        kicker = "Serveur",
        title = "Se connecter à un serveur",
        meta = "Votre bibliothèque locale reste inchangée.",
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

    // Le champ sécurisé de Compose remplace `PasswordVisualTransformation`,
    // déprécié par Compose 1.13. Masqué, il ne montre aucun caractère, pas même
    // le dernier tapé : rien ne change pour l'utilisateur.
    OutlinedSecureTextField(
        state = password,
        label = { Text("Mot de passe") },
        enabled = !state.isConnecting,
        textObfuscationMode = if (passwordVisible) {
            TextObfuscationMode.Visible
        } else {
            TextObfuscationMode.Hidden
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        // « Terminé » doit valider : proposer l'action puis ne rien en faire
        // oblige à revenir chercher le bouton.
        onKeyboardAction = { submit() },
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

    Spacer(Modifier.height(28.dp))
    AccentPill(
        text = if (state.isConnecting) "Connexion…" else "Se connecter",
        onClick = { submit() },
        enabled = !state.isConnecting,
        trailing = if (state.isConnecting) {
            {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(16.dp))
    Text(
        text = "Sans schéma, l'adresse est jointe en HTTPS.",
        style = MaterialTheme.typography.bodySmall,
        color = WaveFlowTheme.colors.ink3,
    )
}

@Composable
private fun ConnectedAccount(
    session: ServerSession.Connected,
    onDisconnect: () -> Unit,
) {
    AccountHeader(kicker = "Compte", title = session.username, meta = session.serverUrl)

    Spacer(Modifier.height(20.dp))
    Text(
        text = "Le catalogue de ce serveur est consultable depuis l'onglet Serveur, " +
            "et ses pistes se lisent comme celles de l'appareil.",
        style = MaterialTheme.typography.bodyMedium,
        color = WaveFlowTheme.colors.ink3,
    )

    Spacer(Modifier.height(24.dp))
    GhostPill(text = "Se déconnecter", onClick = onDisconnect)
}
