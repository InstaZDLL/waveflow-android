// Les nombres de ce fichier sont des définitions publiées — les matrices
// d'OKLab (Björn Ottosson, 2020), la courbe du sRGB (IEC 61966-2-1), le
// contraste du WCAG 2 — recopiées telles quelles : les nommer un à un
// n'apprendrait rien de plus que leur source, citée à chaque fonction.
@file:Suppress("MagicNumber")

package app.waveflow.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Une couleur en OKLCH : [l] la clarté perçue (0 à 1), [c] la vivacité, [h]
 * la teinte en degrés (sans objet quand [c] est nul).
 *
 * C'est l'espace où le bureau tient sa règle d'accent (`WaveFlow/src/lib/accent.ts`) :
 * à clarté égale, deux teintes s'y lisent à peu près aussi claires l'une que
 * l'autre, ce que ni le RGB ni le HSL ne donnent.
 */
data class Oklch(val l: Float, val c: Float, val h: Float)

/** [argb] (canaux sRGB sur 8 bits) en OKLCH. */
fun oklchOf(argb: Int): Oklch {
    val couleur = Color(argb)
    val lr = versLineaire(couleur.red)
    val lg = versLineaire(couleur.green)
    val lb = versLineaire(couleur.blue)
    val lp = cbrt(0.4122214708f * lr + 0.5363325363f * lg + 0.0514459929f * lb)
    val mp = cbrt(0.2119034982f * lr + 0.6806995451f * lg + 0.1073969566f * lb)
    val sp = cbrt(0.0883024619f * lr + 0.2817188376f * lg + 0.6299787005f * lb)
    val l = 0.2104542553f * lp + 0.793617785f * mp - 0.0040720468f * sp
    val a = 1.9779984951f * lp - 2.428592205f * mp + 0.4505937099f * sp
    val b = 0.0259040371f * lp + 0.7827717662f * mp - 0.808675766f * sp
    val h = (Math.toDegrees(atan2(b, a).toDouble()).toFloat() + 360f) % 360f
    return Oklch(l, hypot(a, b), h)
}

/** La couleur sRGB de `oklch(l c h)`, chaque canal ramené dans la gamme. */
fun oklchToColor(l: Float, c: Float, h: Float): Color {
    val (r, g, b) = lineaire(l, c, h).map { versGamma(it.coerceIn(0f, 1f)) }
    return Color(r, g, b)
}

/**
 * La vivacité la plus forte que la teinte [h] peut porter à la clarté [l]
 * sans sortir du sRGB. La garder en deçà, plutôt que laisser l'écran rogner
 * un canal, évite que la teinte ne glisse en chemin.
 */
fun maxChroma(l: Float, h: Float): Float {
    var bas = 0f
    var haut = MAX_CHROMA
    repeat(24) {
        val milieu = (bas + haut) / 2f
        if (dansLaGamme(l, milieu, h)) bas = milieu else haut = milieu
    }
    return bas
}

/** Le rapport de contraste du WCAG 2 entre deux couleurs opaques. */
fun contrast(a: Color, b: Color): Float {
    val clair = max(a.luminance(), b.luminance())
    val sombre = min(a.luminance(), b.luminance())
    return (clair + 0.05f) / (sombre + 0.05f)
}

/** Le gris neutre dont la luminance relative vaut [luminance]. */
fun greyOfLuminance(luminance: Float): Color {
    val v = versGamma(luminance)
    return Color(v, v, v)
}

/**
 * La luma de [couleur] — la pondération du Rec. 709 sur ses canaux encodés,
 * et non la luminance relative : c'est la mesure rapide que le bureau prend
 * pour juger une pochette claire ou sombre (`useCoverGlow`).
 */
fun luma(couleur: Color): Float = 0.2126f * couleur.red + 0.7152f * couleur.green + 0.0722f * couleur.blue

/** Au-delà, aucune couleur sRGB n'a plus de vivacité, quelle que soit sa clarté. */
const val MAX_CHROMA = 0.37f

private fun versLineaire(v: Float): Float = if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)

private fun versGamma(v: Float): Float = if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f

/** Les canaux sRGB linéaires de `oklch(l c h)`, hors gamme compris. */
private fun lineaire(l: Float, c: Float, h: Float): FloatArray {
    val rad = Math.toRadians(h.toDouble())
    val a = c * cos(rad).toFloat()
    val b = c * sin(rad).toFloat()
    val l3 = (l + 0.3963377774f * a + 0.2158037573f * b).pow(3)
    val m3 = (l - 0.1055613458f * a - 0.0638541728f * b).pow(3)
    val s3 = (l - 0.0894841775f * a - 1.291485548f * b).pow(3)
    return floatArrayOf(
        4.0767416621f * l3 - 3.3077115913f * m3 + 0.2309699292f * s3,
        -1.2684380046f * l3 + 2.6097574011f * m3 - 0.3413193965f * s3,
        -0.0041960863f * l3 - 0.7034186147f * m3 + 1.707614701f * s3,
    )
}

private fun dansLaGamme(l: Float, c: Float, h: Float): Boolean =
    lineaire(l, c, h).all { it >= -1e-4f && it <= 1f + 1e-4f }
