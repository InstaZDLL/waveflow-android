package app.waveflow.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Les formes de WaveFlow, nommées par leur **intention** plutôt que par leur
 * taille : un écran demande « une vignette », pas « 10 dp ».
 *
 * Chaque matière a ses rayons. Fluid Editorial (sombre) reste serré ; Liquid
 * Aurora Glass (clair) les arrondit un peu plus, comme du verre poli. Les
 * deux ne dessinent ni bordure ni ombre portée — sauf sous une pochette.
 *
 * @property thumbnail une petite pochette dans une ligne ou une carte (40 à
 *   64 dp), un carré d'action de même taille.
 * @property tile la pochette d'une tuile de grille.
 * @property card une carte posée sur la page.
 * @property cover la grande pochette : le lecteur, l'en-tête d'un album.
 * @property plate le lecteur réduit, posé au pied de l'écran.
 */
@Immutable
class WaveFlowShapes(
    val thumbnail: Shape,
    val tile: Shape,
    val card: Shape,
    val cover: Shape,
    val plate: Shape,
) {
    /** Un artiste : toujours un disque, dans les deux matières. */
    val portrait: Shape = CircleShape

    /** Un bouton, une puce, une recherche : bouts entièrement ronds, partout. */
    val pill: Shape = CircleShape
}

/**
 * Fluid Editorial : petite tuile et champ 0,625 rem, carte et tuile 0,875 rem,
 * grande pochette 1,25 rem. Le lecteur réduit y est une carte.
 */
internal val FluidShapes = WaveFlowShapes(
    thumbnail = RoundedCornerShape(10.dp),
    tile = RoundedCornerShape(14.dp),
    card = RoundedCornerShape(14.dp),
    cover = RoundedCornerShape(20.dp),
    plate = RoundedCornerShape(14.dp),
)

/**
 * Liquid Aurora Glass : la tuile 1 rem, la feuille de verre 1,25 rem, la
 * plaque du lecteur 1,75 rem. La vignette et la grande pochette gardent les
 * rayons de Fluid, que Liquid ne redéfinit pas.
 */
internal val LiquidShapes = WaveFlowShapes(
    thumbnail = RoundedCornerShape(10.dp),
    tile = RoundedCornerShape(16.dp),
    card = RoundedCornerShape(20.dp),
    cover = RoundedCornerShape(20.dp),
    plate = RoundedCornerShape(28.dp),
)

/*
 * Les rôles de Material 3, que ses composants lisent seuls. Chaque rôle reçoit
 * la valeur que le document donne à la même intention. Ce qui le lit est
 * relevé dans les jetons de material3 1.5, pour les composants employés :
 *
 * | Rôle         | Ce qui le lit                               | Sombre | Clair | Intention                   |
 * |--------------|---------------------------------------------|--------|-------|-----------------------------|
 * | `extraSmall` | `OutlinedTextField`, `Snackbar`             | 10     | 10    | le champ                    |
 * | `small`      | les puces, que l'application n'emploie pas  | 10     | 12    | la petite tuile ; le module |
 * | `medium`     | `Card`                                      | 14     | 20    | la carte ; la feuille       |
 * | `large`      | aucun des composants employés               | 20     | 20    | la grande pochette          |
 * | `extraLarge` | `AlertDialog`, `ModalBottomSheet` ; le menu | 14     | 20    | le flottant                 |
 *
 * `DropdownMenu` lit `extraSmall`, la forme du champ : l'écran qui l'ouvre lui
 * donne `extraLarge`, celle du flottant.
 *
 * `extraLarge` est plus petit que `large` en sombre, et c'est voulu : Fluid
 * donne 14 px à ses menus et fenêtres flottantes et réserve son plus grand
 * rayon à la pochette. Material ne demande pas que l'échelle croisse.
 */

internal val FluidMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(14.dp),
)

internal val LiquidMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(20.dp),
)
