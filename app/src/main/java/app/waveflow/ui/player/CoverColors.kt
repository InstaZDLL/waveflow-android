package app.waveflow.ui.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import app.waveflow.ui.theme.Emerald400
import app.waveflow.ui.theme.Emerald600
import app.waveflow.ui.theme.FluidAccentInk
import app.waveflow.ui.theme.FluidBg
import app.waveflow.ui.theme.FluidGlow
import app.waveflow.ui.theme.FluidGlowDeep
import app.waveflow.ui.theme.LiquidGlow
import app.waveflow.ui.theme.LiquidGlowDeep
import app.waveflow.ui.theme.LiquidGround
import app.waveflow.ui.theme.LiquidIce
import app.waveflow.ui.theme.LiquidLavender
import app.waveflow.ui.theme.LiquidMint
import app.waveflow.ui.theme.MAX_CHROMA
import app.waveflow.ui.theme.WaveFlowMaterial
import app.waveflow.ui.theme.contrast
import app.waveflow.ui.theme.greyOfLuminance
import app.waveflow.ui.theme.luma
import app.waveflow.ui.theme.maxChroma
import app.waveflow.ui.theme.oklchOf
import app.waveflow.ui.theme.oklchToColor
import kotlin.math.max
import kotlin.math.min

/*
 * Les couleurs que le lecteur tire d'une pochette : son accent et sa lumière.
 *
 * Décision du 25/11 : l'accent de la pochette vaut **dans le lecteur seul** —
 * le plein écran et le mini-lecteur, leur progression, le W et leurs
 * commandes. Le reste de l'application garde l'accent fixe du thème.
 *
 * Tout est pur, sans Android : ce qui se calcule ici s'éprouve sur la JVM, et
 * les écrans n'ont qu'à le lire.
 */

/**
 * Ce que Palette a lu d'une pochette, avant toute décision de thème.
 *
 * @property vivid la teinte la plus caractéristique, d'où vient l'accent.
 * @property average le ton d'ensemble, d'où vient la lumière.
 */
data class CoverSample(val vivid: Int?, val average: Int?)

/**
 * La lumière du lecteur pour une pochette et une matière.
 *
 * @property accent l'accent tiré de la pochette, ou celui du thème quand elle
 *   n'a pas de couleur à donner. Toujours lisible : voir [garantirContraste].
 * @property onAccent l'encre posée sur l'accent — sombre en Fluid, blanche en
 *   Liquid, comme l'accent fixe.
 * @property glow la lumière de la pochette, la première nappe.
 * @property glowDeep la seconde nappe, plus sombre (Fluid seulement).
 */
data class PlayerLight(
    val accent: Color,
    val onAccent: Color,
    val glow: Color,
    val glowDeep: Color,
)

/** La lumière par défaut, celle d'une pochette absente ou sans couleur. */
fun defaultPlayerLight(material: WaveFlowMaterial): PlayerLight = when (material) {
    WaveFlowMaterial.Fluid -> PlayerLight(Emerald400, FluidAccentInk, FluidGlow, FluidGlowDeep)
    WaveFlowMaterial.Liquid -> PlayerLight(Emerald600, Color.White, LiquidGlow, LiquidGlowDeep)
}

/**
 * La lumière du lecteur pour [sample], ou la lumière par défaut s'il est nul.
 *
 * L'accent et la lumière se décident séparément : une pochette grise n'a pas
 * d'accent à donner (celui du thème reste) mais garde sa lumière, et
 * inversement Palette peut trouver une teinte vive sans ton d'ensemble.
 */
fun playerLightFor(sample: CoverSample?, material: WaveFlowMaterial): PlayerLight {
    val defaut = defaultPlayerLight(material)
    if (sample == null) return defaut
    val accent = sample.vivid?.let { coverAccent(it, material) } ?: defaut.accent
    val glow = sample.average?.let { shade(it, GLOW_SHADE) } ?: defaut.glow
    val glowDeep = sample.average?.let { shade(it, GLOW_DEEP_SHADE) } ?: defaut.glowDeep
    return defaut.copy(accent = accent, glow = glow, glowDeep = glowDeep)
}

// ── L'accent : la règle du palier ───────────────────────────────────────────

/**
 * L'accent tiré de [argb] pour [material], ou `null` quand la couleur est un
 * gris : un accent neutre aurait l'air d'un défaut plutôt que d'un choix, et
 * l'accent du thème reste alors.
 *
 * C'est la règle du bureau (`WaveFlow/src/lib/accent.ts`), qui vaut pour
 * toutes les plateformes : on garde la **clarté** du palier de l'émeraude que
 * la matière emploie — 400 en sombre, 600 en clair — et l'on n'emprunte à la
 * pochette que sa teinte et sa vivacité, relevée à un plancher pour qu'une
 * pochette terne donne tout de même un accent. Chaque matière ayant été réglée
 * sur ces clartés, n'importe quelle teinte s'y lit à peu près comme l'émeraude.
 *
 * « À peu près » : à clarté perçue égale, la luminance varie avec la teinte.
 * Le bureau s'en remet à la règle ; ici le résultat passe encore par
 * [garantirContraste]. Sur le cercle des teintes que `CoverColorsTest`
 * parcourt, aucune n'en a besoin aujourd'hui — retirer la garde n'y fait rien
 * tomber. Elle reste en filet : pour le jour où une limite de fond bougerait,
 * et pour les mélanges du fondu, qu'elle reprend à chaque image.
 */
fun coverAccent(argb: Int, material: WaveFlowMaterial): Color? {
    val couleur = oklchOf(argb)
    if (couleur.c < COVER_MIN_CHROMA) return null
    val pic = max(couleur.c, COVER_FLOOR_CHROMA).coerceAtMost(MAX_CHROMA)
    val (clarte, chromaPalier) = when (material) {
        WaveFlowMaterial.Fluid -> STOP_400
        WaveFlowMaterial.Liquid -> STOP_600
    }
    val voulue = chromaPalier / TEMPLATE_PEAK * pic
    val chroma = min(voulue, maxChroma(clarte, couleur.h))
    return garantirContraste(oklchToColor(clarte, chroma, couleur.h), material)
}

/**
 * [accent], ramené au besoin vers un palier où il se lit.
 *
 * L'exigence est celle que les tests de contraste du thème posent à l'accent
 * fixe (`WaveFlowThemeTest`) : **3:1**, le seuil des composants et des grands
 * textes — contre son encre ([PlayerLight.onAccent]), puisqu'un bouton plein
 * porte une icône, et contre chaque fond sur lequel le lecteur le pose : la
 * page, et le fond le plus marqué que sa lumière puisse faire ([groundLimit]).
 *
 * Le pas suit la clarté perçue, à teinte et vivacité gardées : vers le clair
 * en Fluid, où l'encre et les fonds sont sombres ; vers le sombre en Liquid,
 * où l'encre est blanche et les fonds clairs. Dans les deux cas le pas
 * améliore les deux contrastes à la fois, si bien que la recherche s'arrête au
 * premier palier qui convient — un accent déjà lisible, l'émeraude du thème
 * notamment, ressort inchangé.
 */
fun garantirContraste(accent: Color, material: WaveFlowMaterial): Color {
    val encre = defaultPlayerLight(material).onAccent
    val fonds = when (material) {
        WaveFlowMaterial.Fluid -> listOf(FluidBg, groundLimit(material))
        WaveFlowMaterial.Liquid -> listOf(LiquidGround, groundLimit(material))
    }
    fun lisible(c: Color) = contrast(encre, c) >= ACCENT_CONTRAST && fonds.all { contrast(c, it) >= ACCENT_CONTRAST }
    if (lisible(accent)) return accent

    val depart = oklchOf(accent.toArgb())
    val sens = if (material == WaveFlowMaterial.Fluid) 1f else -1f
    var clarte = depart.l
    while (clarte in 0f..1f) {
        clarte += sens * LIGHTNESS_STEP
        val l = clarte.coerceIn(0f, 1f)
        val candidat = oklchToColor(l, min(depart.c, maxChroma(l, depart.h)), depart.h)
        if (lisible(candidat)) return candidat
    }
    // Inatteignable : au bout du pas, le blanc pur ou le noir pur passent les
    // deux seuils contre ces encres et ces fonds.
    return if (material == WaveFlowMaterial.Fluid) Color.White else Color.Black
}

// ── La lumière : les nappes derrière le lecteur ─────────────────────────────

/**
 * Le fond le plus marqué que la lumière de la pochette a le droit de faire :
 * une limite de luminance relative, au-delà de laquelle chaque nappe s'efface.
 *
 * Les encres et l'accent sont posés sur ces nappes ; c'est contre cette limite
 * qu'ils se mesurent. Dans le lecteur, rien ne s'écrit à la troisième encre
 * (`ink-3`) : les durées, l'étiquette « EN LECTURE » et les lettres de la
 * boucle prennent la deuxième (`ink-2`, `onSurfaceVariant`), la moins
 * contrastée qui y reste. C'est elle qui fixe la limite, à 4,5:1.
 *
 * En Fluid la lumière éclaircit le fond, jusqu'à 0,058 : `ink-2` y tient
 * encore 4,5:1 (décision du 25/11). Calée sur `ink-3`, elle s'arrêtait à
 * 0,018 et rabattait la nappe d'une pochette claire. Elle ne borne plus
 * aucune des pochettes essayées : la lumière du sombre reste discrète par sa
 * recette même, celle du bureau — la couleur assombrie à 62 %, posée à 40 %.
 * En Liquid elle l'assombrit, jusqu'à 0,82 ; l'aurore du clair reste celle
 * qui a été validée.
 */
fun groundLimit(material: WaveFlowMaterial): Color = when (material) {
    WaveFlowMaterial.Fluid -> greyOfLuminance(FLUID_GROUND_LIMIT)
    WaveFlowMaterial.Liquid -> greyOfLuminance(LIQUID_GROUND_LIMIT)
}

/**
 * Une nappe de lumière : un disque flou de [color] à son centre, posé à
 * [alpha] au plus, centré en ([x], [y]) sur l'écran (fractions de sa largeur
 * et de sa hauteur), de demi-axes [rx] et [ry] (mêmes fractions).
 */
data class LightPool(
    val color: Color,
    val alpha: Float,
    val x: Float,
    val y: Float,
    val rx: Float,
    val ry: Float,
)

/**
 * Les nappes du lecteur, reprises des feuilles de style du bureau.
 *
 * Fluid (`fluid.css`, `body::before`) : deux nappes, la lumière à 40 % en
 * haut à droite, la lumière profonde à 70 % en bas à gauche. Liquid
 * (`liquid.css`, l'aurore) : trois nappes pastel, lavande et glace mêlées à la
 * lumière (74/26), menthe (70/30), en haut à gauche, en haut à droite, en bas.
 *
 * Chaque nappe est bornée par [groundLimit] : son opacité est rabattue jusqu'à
 * ce que, posée pleine sur [ground], elle n'y dépasse pas la limite. Une
 * pochette très claire ou très sombre éclaire donc moins, et les encres comme
 * l'accent restent lisibles par-dessus. Immobiles : rien ne dérive, si bien
 * qu'il n'y a rien à couper quand les animations sont supprimées.
 */
fun lightPools(light: PlayerLight, material: WaveFlowMaterial, ground: Color): List<LightPool> {
    val brutes = when (material) {
        WaveFlowMaterial.Fluid -> listOf(
            nappe(light.glow, FLUID_GLOW_ALPHA, FluidGlowPlace),
            nappe(light.glowDeep, FLUID_GLOW_DEEP_ALPHA, FluidGlowDeepPlace),
        )
        WaveFlowMaterial.Liquid -> listOf(
            nappe(lerp(LiquidLavender, light.glow, LIQUID_POOL_MIX), 1f, LiquidLavenderPlace),
            nappe(lerp(LiquidIce, light.glow, LIQUID_POOL_MIX), 1f, LiquidIcePlace),
            nappe(lerp(LiquidMint, light.glow, LIQUID_MINT_MIX), 1f, LiquidMintPlace),
        )
    }
    // Les nappes du clair se chevauchent, mais là où elles se rencontrent
    // chacune n'est plus qu'à une fraction de sa force : bornées une à une,
    // elles tiennent encore ensemble. CoverColorsTest parcourt la page pour
    // le vérifier, plutôt qu'une seconde borne ne le suppose.
    return brutes.map { it.copy(alpha = boundedGlowAlpha(it.color, it.alpha, ground, material)) }
}

/**
 * L'opacité, au plus [voulue], à laquelle une lumière [glow] peut se poser
 * sur [ground] sans y franchir [groundLimit] : les nappes du plein écran, le
 * dégradé du haut de la plaque du mini-lecteur.
 */
fun boundedGlowAlpha(glow: Color, voulue: Float, ground: Color, material: WaveFlowMaterial): Float =
    boundedAlpha(glow, voulue, ground, groundLimit(material).luminance(), eclaire = material == WaveFlowMaterial.Fluid)

/**
 * L'opacité la plus forte, au plus [voulue], à laquelle [nappe] posée sur
 * [ground] reste du bon côté de [limite] : sous elle quand la lumière
 * éclaircit ([eclaire]), au-dessus sinon. Recherche par dichotomie, la
 * luminance d'un mélange sRGB n'étant pas linéaire en l'opacité.
 */
internal fun boundedAlpha(nappe: Color, voulue: Float, ground: Color, limite: Float, eclaire: Boolean): Float {
    fun tient(alpha: Float): Boolean {
        val l = nappe.copy(alpha = alpha).compositeOver(ground).luminance()
        return if (eclaire) l <= limite else l >= limite
    }
    if (tient(voulue)) return voulue
    var bas = 0f
    var haut = voulue
    repeat(BISECTION_STEPS) {
        val milieu = (bas + haut) / 2f
        if (tient(milieu)) bas = milieu else haut = milieu
    }
    return bas
}

/**
 * La lumière du bureau (`useCoverGlow`, `shade`) : la couleur ramenée vers le
 * noir par [k], plus fort pour une pochette claire, un peu moins pour une très
 * sombre — une pochette blanche ne délave pas la page, une noire s'y voit.
 */
internal fun shade(argb: Int, k: Float): Color {
    val couleur = Color(argb)
    val clarte = luma(couleur)
    val facteur = when {
        clarte > SHADE_BRIGHT -> k * SHADE_BRIGHT_FACTOR
        clarte < SHADE_DARK -> k * SHADE_DARK_FACTOR
        else -> k
    }
    fun canal(v: Float) = (v * facteur).coerceIn(0f, 1f)
    return Color(canal(couleur.red), canal(couleur.green), canal(couleur.blue))
}

/**
 * Où se pose une nappe : son centre et ses demi-axes, en part de la largeur
 * et de la hauteur de l'écran — les ellipses des feuilles de style du bureau.
 */
private class Placement(val x: Float, val y: Float, val rx: Float, val ry: Float)

/** Fluid : la lumière en haut à droite, la lumière profonde en bas à gauche. */
private val FluidGlowPlace = Placement(x = 0.72f, y = 0.08f, rx = 0.62f, ry = 0.38f)
private val FluidGlowDeepPlace = Placement(x = 0.18f, y = 0.92f, rx = 0.56f, ry = 0.30f)

/** Liquid : la lavande en haut à gauche, la glace en haut à droite, la menthe en bas. */
private val LiquidLavenderPlace = Placement(x = 0.12f, y = 0.10f, rx = 0.66f, ry = 0.42f)
private val LiquidIcePlace = Placement(x = 0.88f, y = 0.24f, rx = 0.68f, ry = 0.44f)
private val LiquidMintPlace = Placement(x = 0.38f, y = 1.06f, rx = 0.74f, ry = 0.46f)

private fun nappe(color: Color, alpha: Float, place: Placement) =
    LightPool(color, alpha, place.x, place.y, place.rx, place.ry)

/** Clarté et vivacité des paliers 400 et 600 de l'émeraude (`accent.ts`). */
private val STOP_400 = 0.765f to 0.177f
private val STOP_600 = 0.596f to 0.145f

/** La vivacité du palier 500, le sommet de la courbe de l'émeraude. */
private const val TEMPLATE_PEAK = 0.17f

/** En deçà, la pochette se lit grise : pas d'accent. */
internal const val COVER_MIN_CHROMA = 0.05f

/** La vivacité minimale d'un accent tiré d'une pochette terne. */
private const val COVER_FLOOR_CHROMA = 0.12f

/** Le seuil des composants et des grands textes, celui de l'accent fixe. */
internal const val ACCENT_CONTRAST = 3f

private const val LIGHTNESS_STEP = 0.01f
private const val BISECTION_STEPS = 12

/** Où une nappe s'éteint, en part de son demi-axe (« transparent 70 % » du bureau). */
const val POOL_FADE = 0.7f

private const val FLUID_GROUND_LIMIT = 0.058f
private const val LIQUID_GROUND_LIMIT = 0.82f

private const val FLUID_GLOW_ALPHA = 0.40f
private const val FLUID_GLOW_DEEP_ALPHA = 0.70f
private const val LIQUID_POOL_MIX = 0.26f
private const val LIQUID_MINT_MIX = 0.30f

/** `glow` et `glow-deep` : la pochette ramenée à 62 % et à 30 %. */
private const val GLOW_SHADE = 0.62f
private const val GLOW_DEEP_SHADE = 0.30f
private const val SHADE_BRIGHT = 0.6f
private const val SHADE_BRIGHT_FACTOR = 0.55f
private const val SHADE_DARK = 0.12f
private const val SHADE_DARK_FACTOR = 1.8f
