package app.waveflow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import app.waveflow.model.ThemeChoice

/*
 * Les deux matières sur les rôles Material 3.
 *
 * Le principe des deux documents : **un seul accent**, dépensé avec soin — sur
 * l'action principale, ce qui est allumé, l'anneau du focus. Tout le reste est
 * de l'encre sur un fond. D'où `secondary` et `tertiary` égaux à l'accent, et
 * leurs conteneurs pris dans les tons : un composant Material qui chercherait
 * une deuxième couleur n'en trouve pas. L'indicateur de la barre de navigation
 * (`secondaryContainer`) devient ainsi un ton, et non une pastille verte.
 *
 * Les états — survol, appui, focus — sont les calques d'état de Material, la
 * couleur du contenu posée en transparence : du blanc sur le sombre, de l'encre
 * sur le clair, jamais une nouvelle couleur. Material les dessine à 8 % au
 * survol, 10 % à l'appui et au focus, 16 % au glisser ; les documents posent
 * 4 % au survol et 6 % à l'appui — c'est [WaveFlowRippleAlpha], fourni par
 * [WaveFlowTheme].
 * Pour la même raison `surfaceTint` est blanc : une élévation tonale éclaircit
 * d'un ton, elle ne verdit pas.
 *
 * Les tableaux qui suivent disent ce que lisent les composants de material3
 * 1.5, relevé dans ses jetons, pour ceux que l'application emploie. Un seul
 * lit un rôle qui ne lui convient pas, et l'écran le corrige à l'appel : le
 * menu déroulant prend `surfaceContainer`, le niveau des barres, et la forme
 * `extraSmall`, celle du champ ; les documents le posent au niveau flottant,
 * celui des dialogues (`surfaceContainerHigh`, forme `extraLarge`).
 *
 * Non couverts par les documents, laissés aux valeurs de Material : les rôles
 * d'erreur et le voile (`scrim`).
 */

/**
 * Fluid Editorial. Trois fonds — page, surface, seconde surface —, trois
 * encres, l'accent au palier 400 sous une encre sombre.
 *
 * | Rôle                      | Ce qui le lit                                   | Jeton du document          |
 * |---------------------------|-------------------------------------------------|----------------------------|
 * | `background`, `surface`   | la page, `TopAppBar`, `PrimaryTabRow`           | `bg`                       |
 * | `surfaceContainerLow`     | `ModalBottomSheet`                              | `surface`                  |
 * | `surfaceContainer`        | `NavigationBar`                                 | `surface` (les barres)     |
 * | `surfaceContainerHigh`    | `AlertDialog` ; le menu, à l'appel              | `surface-2` (flottant)     |
 * | `surfaceContainerHighest` | `Card`                                          | `surface` (les cartes)     |
 * | `surfaceVariant`          | la carte de reprise, une pochette absente       | `surface-2`                |
 * | `secondaryContainer`      | indicateur de navigation, pistes de progression | un ton (blanc 8 %)         |
 * | `onSurface`               | le texte                                        | `ink`                      |
 * | `onSurfaceVariant`        | la seconde ligne                                | `ink-2`                    |
 * | `outline`, `outlineVariant` | anneaux, séparateurs                          | `hairline-strong`, `hairline` |
 *
 * `surfaceContainerHighest` n'est pas la marche la plus claire, comme Material
 * l'entend en sombre : c'est le rôle de la carte, et une carte de Fluid est la
 * `surface` — un ton au-dessus de la page, et sous le ton de sa piste.
 */
private val FluidColorScheme: ColorScheme = darkColorScheme(
    primary = Emerald400,
    onPrimary = FluidAccentInk,
    primaryContainer = FluidTone,
    onPrimaryContainer = FluidInk,
    inversePrimary = Emerald600,
    secondary = Emerald400,
    onSecondary = FluidAccentInk,
    secondaryContainer = FluidTone,
    onSecondaryContainer = FluidInk,
    tertiary = Emerald400,
    onTertiary = FluidAccentInk,
    tertiaryContainer = FluidTone,
    onTertiaryContainer = FluidInk,
    background = FluidBg,
    onBackground = FluidInk,
    surface = FluidBg,
    onSurface = FluidInk,
    surfaceVariant = FluidSurface2,
    onSurfaceVariant = FluidInk2,
    surfaceTint = Color.White,
    inverseSurface = FluidInk,
    inverseOnSurface = FluidBg,
    outline = FluidHairlineStrong,
    outlineVariant = FluidHairline,
    surfaceBright = FluidTone,
    surfaceDim = FluidBg,
    surfaceContainerLowest = FluidBg,
    surfaceContainerLow = FluidSurface,
    surfaceContainer = FluidSurface,
    surfaceContainerHigh = FluidSurface2,
    surfaceContainerHighest = FluidSurface,
)

/**
 * Liquid Aurora Glass, en clair. Le fond n'est jamais blanc ; les niveaux de
 * verre, rendus opaques sur lui, sont des blancs de plus en plus denses. Une
 * surface relevée y est **plus blanche**, jamais plus sombre ; la marche
 * enfoncée est un ton d'encre. L'accent au palier 600 sous une encre blanche.
 *
 * | Rôle                      | Ce qui le lit                                   | Niveau du document           |
 * |---------------------------|-------------------------------------------------|------------------------------|
 * | `background`, `surface`   | la page, `TopAppBar`, `PrimaryTabRow`           | `ground`                     |
 * | `surfaceContainerLow`     | `ModalBottomSheet`                              | `elevated` (blanc 74 %)      |
 * | `surfaceContainer`        | `NavigationBar`                                 | `structural` (blanc 58 %)    |
 * | `surfaceContainerHigh`    | `AlertDialog` ; le menu, à l'appel              | overlay (blanc 90 %)         |
 * | `surfaceContainerHighest` | `Card`                                          | `canvas` (blanc 90 %)        |
 * | `surfaceVariant`          | la carte de reprise, une pochette absente       | `elevated`                   |
 * | `secondaryContainer`      | indicateur de navigation, pistes de progression | un ton d'encre (8 %)         |
 * | `onSurface`               | le texte                                        | encre de titre               |
 * | `onSurfaceVariant`        | la seconde ligne                                | encre du corps               |
 * | `outline`, `outlineVariant` | anneaux, séparateurs                          | `hairline-strong`, `hairline` |
 *
 * Le niveau `tone` (blanc 42 %) est celui des puces et des pilules, que
 * l'application ne dessine pas encore : aucun rôle ne le porte.
 */
private val LiquidColorScheme: ColorScheme = lightColorScheme(
    primary = Emerald600,
    onPrimary = Color.White,
    primaryContainer = LiquidToneDeep,
    onPrimaryContainer = LiquidInk,
    inversePrimary = Emerald400,
    secondary = Emerald600,
    onSecondary = Color.White,
    secondaryContainer = LiquidToneDeep,
    onSecondaryContainer = LiquidInk,
    tertiary = Emerald600,
    onTertiary = Color.White,
    tertiaryContainer = LiquidToneDeep,
    onTertiaryContainer = LiquidInk,
    background = LiquidGround,
    onBackground = LiquidInk,
    surface = LiquidGround,
    onSurface = LiquidInk,
    surfaceVariant = LiquidElevated,
    onSurfaceVariant = LiquidInk2,
    surfaceTint = Color.White,
    inverseSurface = LiquidInk,
    inverseOnSurface = LiquidGround,
    outline = LiquidHairlineStrong,
    outlineVariant = LiquidHairline,
    surfaceBright = LiquidCanvas,
    surfaceDim = LiquidToneDeep,
    surfaceContainerLowest = LiquidCanvas,
    surfaceContainerLow = LiquidElevated,
    surfaceContainer = LiquidStructural,
    surfaceContainerHigh = LiquidCanvas,
    surfaceContainerHighest = LiquidCanvas,
)

/**
 * Ce que les documents nomment et que Material 3 n'a pas.
 *
 * @property ink3 la troisième encre : étiquettes, compteurs, horodatages,
 *   icônes au repos.
 * @property hairline le filet : un séparateur, l'anneau d'un champ au repos.
 * @property hairlineStrong le filet appuyé : l'anneau d'une puce, d'un champ.
 * @property glow la lumière de la pochette, à sa valeur par défaut tant que
 *   rien n'a été échantillonné.
 * @property glowDeep la seconde nappe de cette lumière.
 */
@Immutable
class WaveFlowColors(
    val ink3: Color,
    val hairline: Color,
    val hairlineStrong: Color,
    val glow: Color,
    val glowDeep: Color,
)

private val FluidColors = WaveFlowColors(
    ink3 = FluidInk3,
    hairline = FluidHairline,
    hairlineStrong = FluidHairlineStrong,
    glow = FluidGlow,
    glowDeep = FluidGlowDeep,
)

private val LiquidColors = WaveFlowColors(
    ink3 = LiquidInk3,
    hairline = LiquidHairline,
    hairlineStrong = LiquidHairlineStrong,
    glow = LiquidGlow,
    glowDeep = LiquidGlowDeep,
)

/**
 * Les calques d'état, pris aux tons des documents (« white at 4 %, 6 %, 8 % » :
 * le survol, l'appui, le fond d'une tuile). Le focus prend l'opacité de
 * l'appui, comme dans Material ; le glisser, que les documents ne nomment pas,
 * la marche du dessus, 8 %.
 *
 * material3 1.5 déprécie le choix de ces opacités sans rien offrir à la place
 * (`RippleConfiguration(rippleAlpha = …)`) ; ses nœuds de ripple les lisent
 * pourtant toujours. Quand le paramètre disparaîtra, l'application retombera
 * sur les opacités de Material — plus appuyées, sans autre dégât.
 */
internal val WaveFlowRippleAlpha = RippleAlpha(
    draggedAlpha = 0.08f,
    focusedAlpha = 0.06f,
    hoveredAlpha = 0.04f,
    pressedAlpha = 0.06f,
)

@Suppress("DEPRECATION")
private val WaveFlowRipple = RippleConfiguration(rippleAlpha = WaveFlowRippleAlpha)

private val LocalWaveFlowColors = staticCompositionLocalOf { FluidColors }
private val LocalWaveFlowShapes = staticCompositionLocalOf { FluidShapes }
private val LocalWaveFlowMotion = staticCompositionLocalOf { WaveFlowMotion(reduced = false) }
private val LocalEditorialStyle = staticCompositionLocalOf { FluidEditorial }

/**
 * Habille l'application : **sombre = Fluid Editorial**, **clair = Liquid
 * Aurora Glass**. Couleurs, formes et typographie passent par
 * [MaterialTheme] ; ce que Material n'a pas, par [WaveFlowTheme].
 *
 * Les couleurs du système (Material You) ne sont pas proposées : la direction
 * n'a qu'un accent, et c'est le sien — puis, plus tard, celui de la pochette.
 *
 * @param theme ce que l'utilisateur a choisi. La traduction en clair ou sombre
 *   se fait ici et nulle part ailleurs : un appelant qui la referait pourrait
 *   la faire autrement.
 */
@Composable
fun WaveFlowTheme(
    theme: ThemeChoice = ThemeChoice.System,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (theme) {
        ThemeChoice.System -> isSystemInDarkTheme()
        ThemeChoice.Light -> false
        ThemeChoice.Dark -> true
    }
    val reducedMotion = rememberReducedMotion()
    val motion = remember(reducedMotion) { WaveFlowMotion(reducedMotion) }

    CompositionLocalProvider(
        LocalWaveFlowColors provides if (darkTheme) FluidColors else LiquidColors,
        LocalWaveFlowShapes provides if (darkTheme) FluidShapes else LiquidShapes,
        LocalWaveFlowMotion provides motion,
        LocalEditorialStyle provides if (darkTheme) FluidEditorial else LiquidEditorial,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) FluidColorScheme else LiquidColorScheme,
            shapes = if (darkTheme) FluidMaterialShapes else LiquidMaterialShapes,
            typography = Typography,
        ) {
            // Sous MaterialTheme, pour qu'aucune valeur posée par lui ne passe
            // devant.
            CompositionLocalProvider(LocalRippleConfiguration provides WaveFlowRipple, content = content)
        }
    }
}

/** Les jetons de WaveFlow que [MaterialTheme] ne porte pas, lus comme les siens. */
object WaveFlowTheme {

    /** Les couleurs nommées par les documents et absentes de Material. */
    val colors: WaveFlowColors
        @Composable @ReadOnlyComposable
        get() = LocalWaveFlowColors.current

    /** Les formes par intention : vignette, tuile, carte, pochette… */
    val shapes: WaveFlowShapes
        @Composable @ReadOnlyComposable
        get() = LocalWaveFlowShapes.current

    /** La courbe, les durées, et si l'utilisateur a retiré les animations. */
    val motion: WaveFlowMotion
        @Composable @ReadOnlyComposable
        get() = LocalWaveFlowMotion.current

    /**
     * La ligne éditoriale, une par page au plus : Playfair italique en sombre,
     * DM Sans légère en clair.
     */
    val editorial: TextStyle
        @Composable @ReadOnlyComposable
        get() = LocalEditorialStyle.current
}
