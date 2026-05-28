package net.rafkos.neuroshima.editor.render.color

import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.atan2

/**
 * sRGB <-> linear RGB <-> OKLab / OKLCh conversions.
 *
 * Constants follow Bjorn Ottosson's OKLab (https://bottosson.github.io/posts/oklab/).
 * sRGB transfer functions are IEC 61966-2-1.
 *
 * Channels:
 *  - sRGB:    0..255 ints (8-bit) or 0..1 floats
 *  - linear:  0..1 floats
 *  - OKLab:   (L, a, b) — L in roughly 0..1, a/b unbounded but small
 *  - OKLCh:   (L, C, H) — H in radians
 */
object OkLab {

    // 8-bit sRGB -> linear LUT (256 entries).
    private val SRGB_TO_LINEAR: FloatArray = FloatArray(256) { i ->
        val c = i / 255f
        if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    }

    fun srgb8ToLinear(c: Int): Float = SRGB_TO_LINEAR[c and 0xff]

    fun linearToSrgb8(c: Float): Int {
        val v = if (c.isNaN()) 0f else c.coerceIn(0f, 1f)
        val s = if (v <= 0.0031308f) 12.92f * v else 1.055f * v.toDouble().pow(1.0 / 2.4).toFloat() - 0.055f
        return (s * 255f + 0.5f).toInt().coerceIn(0, 255)
    }

    data class Lab(val L: Float, val a: Float, val b: Float)
    data class LCh(val L: Float, val C: Float, val H: Float)

    fun linearRgbToOkLab(r: Float, g: Float, b: Float): Lab {
        val l = 0.41222146f * r + 0.53633255f * g + 0.051445995f * b
        val m = 0.2119035f * r + 0.6806995f * g + 0.10739696f * b
        val s = 0.08830246f * r + 0.28171885f * g + 0.6299787f * b
        val l_ = cbrt(l.toDouble()).toFloat()
        val m_ = cbrt(m.toDouble()).toFloat()
        val s_ = cbrt(s.toDouble()).toFloat()
        return Lab(
            L = 0.21045426f * l_ + 0.7936178f * m_ - 0.004072047f * s_,
            a = 1.9779985f * l_ - 2.4285922f * m_ + 0.4505937f * s_,
            b = 0.025904037f * l_ + 0.78277177f * m_ - 0.80867577f * s_,
        )
    }

    /** Returns linear RGB triple as a FloatArray of size 3. */
    fun okLabToLinearRgb(L: Float, a: Float, b: Float): FloatArray {
        val l_ = L + 0.39633778f * a + 0.21580376f * b
        val m_ = L - 0.105561346f * a - 0.06385417f * b
        val s_ = L - 0.08948418f * a - 1.2914855f * b
        val l = l_ * l_ * l_
        val m = m_ * m_ * m_
        val s = s_ * s_ * s_
        return floatArrayOf(
            4.0767417f * l - 3.3077116f * m + 0.23096994f * s,
            -1.268438f * l + 2.6097574f * m - 0.34131938f * s,
            -0.0041960864f * l - 0.7034186f * m + 1.7076147f * s,
        )
    }

    fun labToLCh(L: Float, a: Float, b: Float): LCh {
        val C = sqrt(a * a + b * b)
        val H = atan2(b, a)
        return LCh(L, C, H)
    }

}
