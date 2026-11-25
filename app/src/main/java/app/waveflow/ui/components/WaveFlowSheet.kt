package app.waveflow.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Une feuille modale de WaveFlow : au niveau flottant, celui des menus.
 *
 * `ModalBottomSheet` lit `surfaceContainerLow`, le niveau des panneaux ; les
 * documents posent menus et feuilles flottantes sur `surface-2` en sombre et
 * sur l'overlay en clair — `surfaceContainerHigh`, celui des dialogues. La
 * forme reste `extraLarge`, celle du flottant dans les deux matières.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaveFlowSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        content = content,
    )
}

/**
 * Le titre d'une feuille, souligné du motif de l'onde, et sa ligne
 * d'explication.
 */
@Composable
fun SheetHeader(title: String, supporting: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = SHEET_GUTTER.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        WaveMark()
        if (supporting != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Une rangée d'option dans une feuille : son libellé, et à droite ce qui la
 * marque.
 *
 * Les rangées des menus du document ont leur propre rayon (8 px) et prennent
 * un ton sous le doigt : c'est la forme sur laquelle le calque d'état se
 * découpe. Elle remplace le `ListItem` de Material, déprécié par Compose 1.13,
 * dont la mise en page ne servait ici qu'à poser un texte et une icône.
 *
 * @param interaction le geste de la rangée — `clickable`, ou `selectable` pour
 *   les valeurs d'un même réglage. Il est appliqué **après** le découpage,
 *   pour que le calque d'état épouse le rayon.
 */
@Composable
fun SheetOption(
    text: String,
    interaction: Modifier,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ROW_INSET.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ROW_RADIUS.dp))
                .then(interaction)
                .heightIn(min = ROW_MIN_HEIGHT.dp)
                .padding(horizontal = (SHEET_GUTTER - ROW_INSET).dp, vertical = 8.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
    }
}

/** La marge des feuilles, celle du texte de leur titre. */
private const val SHEET_GUTTER = 24

/** Le retrait d'une rangée dans la feuille : son ton ne touche pas les bords. */
private const val ROW_INSET = 8
private const val ROW_RADIUS = 8

/** La hauteur d'une rangée d'une ligne, celle que Material donnait. */
private const val ROW_MIN_HEIGHT = 56
