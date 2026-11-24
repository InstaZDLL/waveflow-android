package app.waveflow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
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
 * Les états — survol, appui, focus — sont les calques d'état de Material : la
 * couleur du contenu à 8-10 %, c'est-à-dire du blanc sur le sombre et de
 * l'encre sur le clair. Exactement les tons des documents (« never a new
 * colour »). Pour la même raison `surfaceTint` est blanc : une élévation tonale
 * éclaircit d'un ton, elle ne verdit pas.
 *
 * Non couverts par les documents, laissés aux valeurs de Material : les rôles
 * d'erreur et le voile (`scrim`).
 */

/**
 * Fluid Editorial. Trois fonds — page, surface, seconde surface —, trois
 * encres, l'accent au palier 400 sous une encre sombre.
 *
 * | Rôle                                         | Jeton du document                 |
 * |----------------------------------------------|-----------------------------------|
 * | `background`, `surface`                      | `bg`, la page                     |
 * | `surfaceContainerLow`, `surfaceContainer`    | `surface` (barres, feuilles)      |
 * | `surfaceVariant`, `surfaceContainerHigh`     | `surface-2` (menus, dialogues)    |
 * | `surfaceContainerHighest`, les conteneurs    | un ton (blanc 8 %) sur `surface`  |
 * | `onSurface`                                  | `ink`                             |
 * | `onSurfaceVariant`                           | `ink-2` (la seconde ligne)        |
 * | `outline` / `outlineVariant`                 | `hairline-strong` / `hairline`    |
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
    surfaceContainerHighest = FluidTone,
)

/**
 * Liquid Aurora Glass, en clair. Le fond n'est jamais blanc ; les niveaux de
 * verre, rendus opaques sur lui, sont des blancs de plus en plus denses. Une
 * surface relevée y est **plus blanche**, jamais plus sombre ; la marche
 * enfoncée est un ton d'encre. L'accent au palier 600 sous une encre blanche.
 *
 * | Rôle                                         | Niveau du document                |
 * |----------------------------------------------|-----------------------------------|
 * | `background`, `surface`                      | `ground`, la page                 |
 * | `surfaceContainerLowest`                     | `canvas` (blanc 90 %)             |
 * | `surfaceVariant`, `surfaceContainerLow`      | `elevated` (blanc 74 %)           |
 * | `surfaceContainer`                           | `structural` (blanc 58 %)         |
 * | `surfaceContainerHigh`                       | `tone` (blanc 42 %)               |
 * | `surfaceContainerHighest`, les conteneurs    | un ton d'encre (8 %) sur le fond  |
 * | `onSurface` / `onSurfaceVariant`             | encre de titre / du corps         |
 * | `outline` / `outlineVariant`                 | `hairline-strong` / `hairline`    |
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
    surfaceContainerHigh = LiquidTone,
    surfaceContainerHighest = LiquidToneDeep,
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
            content = content,
        )
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
