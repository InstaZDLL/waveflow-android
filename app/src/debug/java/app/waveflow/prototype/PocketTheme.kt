package app.waveflow.prototype

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.waveflow.R

/*
 * Les jetons de WaveFlow Pocket — Sonic Editorial, la direction mobile
 * prototypée (`docs/design/sonic-editorial.md`, qui fait foi et donne les
 * contrastes mesurés).
 *
 * Deux apparences d'une seule identité : elles partagent l'accent (à son
 * palier), la teinte des neutres — un gris très légèrement vert-bleu — et les
 * règles. Le clair n'est pas une inversion : ce qui se lève y est plus blanc
 * que la page, et sa surface de contenu est une menthe.
 */

/**
 * Les couleurs de Pocket, y compris celles que Material 3 n'a pas.
 *
 * @property canvas la page, ce qui défile.
 * @property raised la barre de navigation.
 * @property surface une surface de contenu : le mini-lecteur, une mise en avant.
 * @property floating feuilles, menus, dialogues.
 * @property ink les titres.
 * @property ink2 la seconde ligne, le corps.
 * @property ink3 étiquettes, durées, icônes au repos. Jamais sur [floating]
 *   en sombre (4,0:1) : une feuille écrit sa troisième ligne en [ink2].
 * @property accent l'action principale, ce qui est allumé. Jamais décoratif.
 * @property onAccent l'encre posée sur l'accent.
 * @property hairline un séparateur, au besoin.
 * @property atmosphere la nappe par défaut, quand la pochette n'en donne pas.
 * @property dark l'apparence sombre est en vigueur.
 */
@Immutable
@Suppress("LongParameterList") // Un jeu de jetons : un paramètre par jeton nommé du document.
class PocketColors(
    val canvas: Color,
    val raised: Color,
    val surface: Color,
    val floating: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val accent: Color,
    val onAccent: Color,
    val hairline: Color,
    val atmosphere: Color,
    val dark: Boolean,
)

/** Le sombre, la direction principale. */
internal val PocketDark = PocketColors(
    canvas = Color(0xFF0B1214),
    raised = Color(0xFF142023),
    surface = Color(0xFF1A282B),
    floating = Color(0xFF22333A),
    ink = Color(0xFFF1F6F4),
    ink2 = Color(0xFFA4B3B4),
    ink3 = Color(0xFF8A9C9E),
    accent = Color(0xFF20D39B),
    onAccent = Color(0xFF06231A),
    hairline = Color(0x14FFFFFF),
    atmosphere = Color(0xFF123A2E),
    dark = true,
)

/** Le clair : un blanc cassé neutre, une menthe, l'émeraude au palier 700. */
internal val PocketLight = PocketColors(
    canvas = Color(0xFFF5F6F2),
    raised = Color(0xFFFFFFFF),
    surface = Color(0xFFE4EEE9),
    floating = Color(0xFFFFFFFF),
    ink = Color(0xFF142023),
    ink2 = Color(0xFF46585B),
    ink3 = Color(0xFF586A6D),
    accent = Color(0xFF047857),
    onAccent = Color(0xFFFFFFFF),
    hairline = Color(0x14142023),
    atmosphere = Color(0xFFCDE9DB),
    dark = false,
)

/**
 * Les jetons sur les rôles de Material 3, que ses composants lisent seuls —
 * y compris ceux que le prototype réemploie de la production (la file, les
 * paroles, les feuilles de la minuterie et de la vitesse).
 *
 * Même règle que le thème de production : `secondary` et `tertiary` sont
 * l'accent, leurs conteneurs des tons, pour qu'aucun composant ne trouve de
 * seconde couleur. `surfaceContainerHigh` est le niveau flottant : c'est celui
 * que `WaveFlowSheet` donne aux feuilles.
 */
internal fun PocketColors.toColorScheme(): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = surface,
        onPrimaryContainer = ink,
        inversePrimary = accent,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = surface,
        onSecondaryContainer = ink,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = surface,
        onTertiaryContainer = ink,
        background = canvas,
        onBackground = ink,
        surface = canvas,
        onSurface = ink,
        surfaceVariant = surface,
        onSurfaceVariant = ink2,
        surfaceTint = Color.Transparent,
        inverseSurface = ink,
        inverseOnSurface = canvas,
        outline = ink3,
        outlineVariant = hairline,
        surfaceBright = surface,
        surfaceDim = canvas,
        surfaceContainerLowest = canvas,
        surfaceContainerLow = raised,
        surfaceContainer = raised,
        surfaceContainerHigh = floating,
        surfaceContainerHighest = surface,
    )
}

/**
 * Les formes de Pocket : plus l'objet est grand, plus son rayon l'est, sans
 * jamais l'arrondir en galet.
 */
@Immutable
object PocketShapes {
    /** Une pochette de 40 à 56 dp dans une ligne. */
    val thumbnail: Shape = RoundedCornerShape(6.dp)

    /** Une pochette de grille ou de carrousel. */
    val tile: Shape = RoundedCornerShape(10.dp)

    /** Le mini-lecteur, une mise en avant. */
    val surface: Shape = RoundedCornerShape(14.dp)

    /** La grande pochette : le lecteur, l'en-tête d'album. */
    val cover: Shape = RoundedCornerShape(16.dp)

    /** Boutons, pastilles, disques d'action. */
    val action: Shape = CircleShape
}

/**
 * Les rôles de Material, que lisent les composants réemployés : le champ, le
 * menu et la feuille — celle-ci à 24 dp, le rayon des feuilles du bas.
 */
private val PocketMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Les graisses de l'échelle de Pocket. */
private val PocketWeights = listOf(480, 500, 560, 600, 620, 640)

/**
 * DM Sans — la police variable déjà embarquée — réglée pour un texte de
 * [size] : la taille optique suit la taille affichée.
 */
private fun dmSans(size: TextUnit): FontFamily = FontFamily(
    PocketWeights.map { weight ->
        Font(
            resId = R.font.dm_sans,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.opticalSizing(size),
            ),
        )
    },
)

private fun sans(
    size: TextUnit,
    weight: Int,
    lineHeight: TextUnit,
    tracking: TextUnit = 0.em,
    features: String? = null,
) = TextStyle(
    fontFamily = dmSans(size),
    fontWeight = FontWeight(weight),
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = tracking,
    fontFeatureSettings = features,
)

/**
 * L'échelle de Pocket sur les rôles de Material 3 : DM Sans seule, la
 * hiérarchie par la graisse et la taille. Rien sous 12 sp.
 *
 * - `displaySmall` — le titre d'un écran (32 sp) ;
 * - `headlineSmall` — la mise en avant : le titre du lecteur (26 sp) ;
 * - `titleLarge` — une section (20 sp) ;
 * - `titleMedium` — un titre de piste (16 sp), `titleSmall` celui du
 *   mini-lecteur (15 sp) ;
 * - `body*` — l'artiste, la seconde ligne ;
 * - `labelMedium` — les chiffres, tabulaires ;
 * - `labelSmall` — l'étiquette en capitales, espacée de 0,06 em seulement :
 *   à 12 sp sur un téléphone, les 0,1 em du bureau dispersent le mot.
 */
internal val PocketTypography = Typography(
    displaySmall = sans(32.sp, 640, 36.sp, (-0.03).em),
    headlineSmall = sans(26.sp, 620, 31.sp, (-0.025).em),
    titleLarge = sans(20.sp, 600, 26.sp, (-0.015).em),
    titleMedium = sans(16.sp, 560, 22.sp),
    titleSmall = sans(15.sp, 560, 20.sp),
    bodyLarge = sans(16.sp, 480, 24.sp),
    bodyMedium = sans(14.sp, 480, 20.sp),
    bodySmall = sans(13.sp, 480, 18.sp),
    labelLarge = sans(14.sp, 560, 20.sp),
    labelMedium = sans(13.sp, 500, 18.sp, features = "tnum"),
    labelSmall = sans(12.sp, 620, 16.sp, 0.06.em),
)

/**
 * La ligne éditoriale : Playfair Display italique, **une seule ligne** de
 * l'application — le salut de l'accueil —, dans les deux apparences
 * (décision du 28/11). Jamais pour du texte courant.
 */
internal val PocketEditorial = TextStyle(
    fontFamily = FontFamily(
        Font(
            resId = R.font.playfair_display_italic,
            weight = FontWeight.Normal,
            style = FontStyle.Italic,
            variationSettings = FontVariation.Settings(FontVariation.weight(400)),
        ),
    ),
    fontWeight = FontWeight.Normal,
    fontStyle = FontStyle.Italic,
    fontSize = 30.sp,
    lineHeight = 38.sp,
    letterSpacing = (-0.01).em,
)

private val LocalPocketColors = staticCompositionLocalOf { PocketDark }

/**
 * Habille le prototype. Il se pose **sous** `WaveFlowTheme`, qui fournit
 * toujours la détection du mouvement réduit et ce que lisent les composants
 * réemployés de la production ; Material 3 reçoit ici les jetons de Pocket.
 *
 * @param dark l'apparence ; par défaut celle du système.
 */
@Composable
fun PocketTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) PocketDark else PocketLight
    CompositionLocalProvider(LocalPocketColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(),
            shapes = PocketMaterialShapes,
            typography = PocketTypography,
            content = content,
        )
    }
}

/** Les jetons de Pocket que Material ne porte pas, lus comme les siens. */
object Pocket {
    val colors: PocketColors
        @Composable @ReadOnlyComposable
        get() = LocalPocketColors.current
}
