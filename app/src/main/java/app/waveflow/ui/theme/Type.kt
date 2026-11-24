package app.waveflow.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.waveflow.R

/*
 * La typographie de WaveFlow 2.0, commune aux deux matières : **DM Sans**
 * partout, **Playfair Display italique** pour une ligne par page au plus.
 *
 * Les deux polices sont embarquées (`res/font`, licence OFL, voir
 * `licences/polices/`) : le document le demande, la direction tenant à la
 * géométrie peu contrastée de DM Sans, que la police du système n'a pas.
 *
 * DM Sans est une police variable à deux axes, la graisse (`wght`) et la taille
 * optique (`opsz`, de 9 à 40). Le bureau laisse le navigateur régler la taille
 * optique sur la taille du texte (`font-optical-sizing: auto`) ; Android ne le
 * fait pas seul, d'où une famille par taille, qui la règle elle-même. Ses
 * graisses fines — 480, 540, 560 — n'existent qu'en variable : elles viennent
 * de l'axe, pas d'un fichier par graisse. Une famille déclare toutes les
 * graisses de l'échelle, pour qu'un `fontWeight` posé à l'appel trouve la
 * sienne ; seule celle qu'on affiche est chargée.
 */

/** Les graisses de l'échelle, plus le gras qu'un écran peut demander à l'appel. */
private val DmSansWeights = listOf(380, 400, 480, 500, 540, 560, 580, 600, 700)

/**
 * DM Sans réglée pour un texte de [size] : la taille optique suit la taille
 * affichée, l'échelle de police de l'utilisateur comprise.
 */
private fun dmSans(size: TextUnit): FontFamily = FontFamily(
    DmSansWeights.map { weight ->
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

/** Playfair Display italique, à sa graisse régulière : la ligne éditoriale. */
private val PlayfairItalic = FontFamily(
    Font(
        resId = R.font.playfair_display_italic,
        weight = FontWeight.Normal,
        style = FontStyle.Italic,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
)

/** Un style DM Sans : la famille suit la taille, pour la taille optique. */
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
 * L'échelle Material 3, remplie avec les rôles du document. Les tailles du
 * bureau s'y transposent à raison de 1 rem pour 16 sp.
 *
 * - **Affichage** (`display*`) — le grand titre d'un canevas : 2 à 3,25 rem,
 *   graisse 560-580, resserré de −0,035 à −0,04 em, interligne 0,98. Deux
 *   lignes au plus. Aucun écran ne l'emploie encore.
 * - **Titres** (`headline*`, `title*`) — titres de section (1,5 rem), de bande
 *   (1,625 rem), noms d'albums et de morceaux : graisse 540, resserrés de
 *   −0,022 em. Le resserrement décroît sous 22 sp — à 14 sp, DM Sans a besoin
 *   de son air, ce que sa taille optique dit aussi.
 * - **Corps** (`body*`) — 480 : un rien plus nourri que le régulier, comme sur
 *   le bureau. Interlettrage nul.
 * - **Étiquettes** — `labelSmall` est l'étiquette du document : 540, +0,1 em,
 *   à écrire en capitales (Compose n'a pas de `text-transform` : c'est le
 *   texte qui les porte). `labelMedium` porte les **chiffres** — durées,
 *   compteurs — en chiffres tabulaires (`tnum`), pour qu'une durée qui défile
 *   ne fasse pas danser la ligne. `labelLarge` est le libellé des boutons.
 */
val Typography = Typography(
    displayLarge = sans(52.sp, 580, 0.98.em, (-0.04).em),
    displayMedium = sans(44.sp, 580, 0.98.em, (-0.035).em),
    displaySmall = sans(32.sp, 560, 0.98.em, (-0.035).em),
    headlineLarge = sans(28.sp, 540, 34.sp, (-0.022).em),
    headlineMedium = sans(26.sp, 540, 32.sp, (-0.022).em),
    headlineSmall = sans(24.sp, 540, 30.sp, (-0.022).em),
    titleLarge = sans(22.sp, 540, 28.sp, (-0.022).em),
    titleMedium = sans(16.sp, 540, 22.sp, (-0.011).em),
    titleSmall = sans(14.sp, 540, 20.sp),
    bodyLarge = sans(16.sp, 480, 24.sp),
    bodyMedium = sans(14.sp, 480, 20.sp),
    bodySmall = sans(12.sp, 480, 16.sp),
    labelLarge = sans(14.sp, 540, 20.sp),
    labelMedium = sans(12.sp, 500, 16.sp, features = "tnum"),
    labelSmall = sans(11.sp, 540, 16.sp, 0.1.em),
)

/**
 * La ligne éditoriale de Fluid Editorial : Playfair Display italique, 400,
 * resserrée de −0,01 em. Une par page au plus — le salut, la nature d'une
 * suggestion —, jamais pour du texte courant.
 */
internal val FluidEditorial = TextStyle(
    fontFamily = PlayfairItalic,
    fontWeight = FontWeight.Normal,
    fontStyle = FontStyle.Italic,
    fontSize = 24.sp,
    lineHeight = 30.sp,
    letterSpacing = (-0.01).em,
)

/**
 * La même ligne dans Liquid Aurora Glass : DM Sans à graisse légère (380),
 * resserrée de −0,028 em. Le document l'explique : un empattement posé sur du
 * verre se lit comme un autocollant.
 */
internal val LiquidEditorial = sans(24.sp, 380, 30.sp, (-0.028).em)
