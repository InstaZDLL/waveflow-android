package app.waveflow.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * L'échelle typographique de WaveFlow.
 *
 * Les dix styles définis ici sont ceux que l'application emploie réellement.
 * Ce qui les précédait était le gabarit d'Android Studio : un seul style écrit,
 * tous les autres laissés en commentaire, et un `letterSpacing` de 0,5 sp hérité
 * de Material 2 qui délaçait le corps de texte.
 *
 * Trois familles, trois traitements, parce qu'un lecteur de musique n'affiche
 * pas trois fois la même chose :
 *
 * - **Titres** — noms d'albums, d'artistes, de playlists. Resserrés et plus
 *   gras que le défaut : ce sont des noms propres, ils doivent se lire comme un
 *   bloc et non comme une phrase.
 * - **Corps** — titres de morceaux dans les listes, où l'œil descend vite.
 *   L'interlettrage tombe à zéro : espacer aide à déchiffrer un mot isolé, pas
 *   à parcourir cinquante lignes.
 * - **Étiquettes** — onglets, boutons, métadonnées. Elles gardent leur
 *   interlettrage et leur graisse moyenne : petites, souvent en majuscules,
 *   elles ont besoin d'air pour rester lisibles.
 *
 * La famille reste celle du système. Embarquer une police n'apporterait rien
 * tant que le bureau habille la sienne par thème : l'identité WaveFlow tient à
 * sa couleur et à son logo, pas encore à sa lettre.
 */
val Typography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)
