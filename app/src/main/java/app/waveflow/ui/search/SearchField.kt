package app.waveflow.ui.search

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction

/**
 * Champ de recherche logé dans la barre du haut, à la place du titre.
 *
 * Sans conteneur ni soulignement : il doit se lire comme la barre elle-même,
 * pas comme un formulaire posé dessus.
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * `false` pour un champ permanent, qui fait déjà partie de l'écran.
     * Prendre le focus n'a de sens que pour une barre qu'on vient d'ouvrir —
     * sinon le clavier monte à chaque visite de l'onglet.
     */
    autoFocus: Boolean = true,
) {
    val focusRequester = remember { FocusRequester() }

    // Le champ s'ouvre prêt à recevoir la frappe : sans ça, ouvrir la
    // recherche demanderait un second appui pour faire monter le clavier.
    LaunchedEffect(autoFocus) { if (autoFocus) focusRequester.requestFocus() }

    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Titre, album ou artiste") },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
    )
}
