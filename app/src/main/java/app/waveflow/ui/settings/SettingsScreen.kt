package app.waveflow.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.waveflow.model.AppPreferences
import app.waveflow.model.ThemeChoice

/**
 * Les réglages de l'application.
 *
 * Un seul endroit pour ce qui se décide une fois : l'apparence, le serveur, et
 * ce que l'application dit d'elle-même. Le serveur y figure comme un réglage
 * parmi d'autres et non comme une section de la bibliothèque — on écoute de la
 * musique, on n'administre pas une source.
 *
 * Chaque réglage exposé ici agit réellement. Un interrupteur qui ne commande
 * rien encore n'aurait pas sa place : il se lit comme une panne.
 */
@Composable
fun SettingsScreen(
    preferences: AppPreferences,
    isServerConnected: Boolean,
    serverSummary: String?,
    appVersion: String,
    onThemeChange: (ThemeChoice) -> Unit,
    onOpenServer: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = bottomPadding + 32.dp),
    ) {
        SectionTitle("Apparence")
        ThemeChooser(current = preferences.theme, onChange = onThemeChange)

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        SectionTitle("Serveur")
        SettingsRow(
            title = if (isServerConnected) "Compte" else "Se connecter à un serveur",
            // Connecté, l'adresse dit à quoi ; sinon la ligne dit ce qu'on y
            // gagne, plutôt que de rester muette.
            summary = serverSummary ?: "Écouter la bibliothèque d'un serveur WaveFlow",
            onClick = onOpenServer,
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        SectionTitle("À propos")
        SettingsRow(title = "WaveFlow", summary = "Version $appVersion", onClick = null)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
    )
}

/**
 * Le choix du thème.
 *
 * `selectableGroup` et `Role.RadioButton` plutôt que trois lignes cliquables :
 * c'est ce qui fait annoncer « 2 sur 3 » par le lecteur d'écran. Le
 * `RadioButton` reçoit `onClick = null` parce que c'est la ligne entière qui
 * porte le geste — le bouton n'est plus qu'un témoin, et la surface de frappe
 * fait toute la largeur.
 */
@Composable
private fun ThemeChooser(current: ThemeChoice, onChange: (ThemeChoice) -> Unit) {
    Column(Modifier.selectableGroup()) {
        ThemeChoice.entries.forEach { choice ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = choice == current,
                        role = Role.RadioButton,
                        onClick = { onChange(choice) },
                    )
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            ) {
                RadioButton(selected = choice == current, onClick = null)
                Text(
                    text = choice.label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
    }
}

/**
 * Une ligne de réglage qui mène ailleurs, ou qui se contente d'informer.
 *
 * [onClick] nul retire le chevron **et** le clic : une ligne qui invite à
 * appuyer sans rien faire est pire qu'une ligne inerte assumée.
 */
@Composable
private fun SettingsRow(title: String, summary: String?, onClick: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            summary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
