package app.waveflow.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Le contenu de l'application, surmonté du mini-lecteur flottant.
 *
 * La réserve laissée sous les listes est **mesurée sur le mini-lecteur**, jamais
 * devinée. Une constante ne pouvait pas être juste : la carte occupe 82 dp aux
 * réglages par défaut et 103,5 dp à polices doublées, alors que les 76 dp
 * réservés jusqu'ici masquaient le bas de la dernière ligne — de 6 dp en temps
 * ordinaire, de 27 pour qui agrandit les caractères. Une mesure suit aussi la
 * moindre retouche du mini-lecteur, ce qu'un nombre écrit ailleurs ne fait pas.
 *
 * @param showMiniPlayer `false` sous le lecteur plein écran, qui le recouvre
 *   entièrement : inutile de le composer, et la liste n'a alors rien à réserver.
 * @param content reçoit la réserve à passer en `contentPadding` de ses listes.
 */
@Composable
fun MiniPlayerHost(
    state: PlayerUiState,
    showMiniPlayer: Boolean,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (bottomPadding: Dp) -> Unit,
) {
    val density = LocalDensity.current
    var mesure by remember { mutableStateOf(MiniPlayerSpaceEstimate) }

    // Le mini-lecteur ne s'affiche pas sans morceau : la liste n'a alors rien à
    // réserver, et la mesure retenue attendra le prochain.
    val visible = showMiniPlayer && state.track != null

    Box(modifier = modifier) {
        content(if (visible) mesure else 0.dp)

        if (visible) {
            MiniPlayer(
                state = state,
                onExpand = onExpand,
                onTogglePlayPause = onTogglePlayPause,
                onSkipNext = onSkipNext,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { mesure = with(density) { it.height.toDp() } },
            )
        }
    }
}

/**
 * Ce que vaut la réserve avant la première mesure.
 *
 * La carte fait 82 dp aux réglages par défaut : cette valeur n'a qu'à éviter que
 * la liste ne saute entre la première image et la suivante.
 */
private val MiniPlayerSpaceEstimate = 82.dp
