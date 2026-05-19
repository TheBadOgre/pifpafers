package net.rafkos.neuroshima.editor.render.color

import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Perceptual colorize pipeline.
 *
 * Replaces the naive HSB hue/sat replacement with a luminance-preserving recolor in OKLab:
 *  sRGB -> linear RGB -> OKLab/OKLCh -> adjust C, H, L -> mild tone curve -> linear -> sRGB.
 *
 * Slider semantics (kept compatible with existing .box files):
 *  - targetHue:  0..1 normalized hue (0 = red, 1/3 = green, etc.)
 *  - satMult:    chroma amount. 0 = no chroma, ~2 = the tool's "full" recolor;
 *                higher boosts further but soft-clamps at the OKLab chroma ceiling.
 *  - brightness: perceptual-L multiplier. 1.0 = unchanged, 0.0 = black, >1 brightens
 *                until L saturates near white.
 *
 * Brightness is applied on the OKLab L channel (linear-light, perceptual), not on sRGB
 * RGB channels — so a colorize-mode brightness keeps chroma proportional and avoids the
 * desaturation artefact of multiplying sRGB channels.
 */
object ColorizePipeline {

    private const val CHROMA_MAX = 0.4f
    private const val S_CURVE_AMP = 0.03f
    private const val TWO_PI = (PI * 2.0).toFloat()

    fun apply(
        source: BufferedImage,
        targetHue: Float,
        satMult: Float,
        brightness: Float = 1f,
    ): BufferedImage {
        val w = source.width
        val h = source.height
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val srcPixels = source.getRGB(0, 0, w, h, null, 0, w)
        val outPixels = IntArray(srcPixels.size)

        val hueRad = (((targetHue % 1f) + 1f) % 1f) * TWO_PI
        val cosH = cos(hueRad.toDouble()).toFloat()
        val sinH = sin(hueRad.toDouble()).toFloat()
        val satNorm = satMult / 2f  // tool default satMult=2 -> 1.0 amount
        val bMult = max(0f, brightness)

        for (i in srcPixels.indices) {
            val argb = srcPixels[i]
            val a = (argb ushr 24) and 0xff
            if (a == 0) {
                outPixels[i] = 0
                continue
            }
            val rLin = OkLab.srgb8ToLinear((argb ushr 16) and 0xff)
            val gLin = OkLab.srgb8ToLinear((argb ushr 8) and 0xff)
            val bLin = OkLab.srgb8ToLinear(argb and 0xff)

            val lab = OkLab.linearRgbToOkLab(rLin, gLin, bLin)
            val cSrc = kotlin.math.sqrt(lab.a * lab.a + lab.b * lab.b)
            val L = lab.L

            // Brightness shifts perceptual L first; chroma masks then follow the shifted L
            // so chroma stays consistent with the new luminance.
            val Lb = (L * bMult).coerceIn(0f, 1f)

            val lumBell = max(0f, 1f - sq(2f * Lb - 1f))
            val baseC = satNorm * CHROMA_MAX * lumBell
            val softC = CHROMA_MAX * tanh((baseC / CHROMA_MAX).toDouble()).toFloat()
            val material = 1f + 0.5f * min(1f, cSrc / CHROMA_MAX)
            val finalC = softC * material

            val finalL = sCurve(Lb)

            val newA = finalC * cosH
            val newB = finalC * sinH
            val rgb = OkLab.okLabToLinearRgb(finalL, newA, newB)

            val r8 = OkLab.linearToSrgb8(rgb[0])
            val g8 = OkLab.linearToSrgb8(rgb[1])
            val b8 = OkLab.linearToSrgb8(rgb[2])
            outPixels[i] = (a shl 24) or (r8 shl 16) or (g8 shl 8) or b8
        }
        out.setRGB(0, 0, w, h, outPixels, 0, w)
        return out
    }

    private fun sCurve(L: Float): Float {
        // Mild midtone-contrast curve: pushes shadows darker, highlights brighter.
        val curved = L - S_CURVE_AMP * sin((TWO_PI * L).toDouble()).toFloat()
        return curved.coerceIn(0f, 1f)
    }

    private fun sq(x: Float): Float = x * x
}
