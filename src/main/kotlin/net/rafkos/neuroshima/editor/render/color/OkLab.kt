package net.rafkos.neuroshima.editor.render.color

import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
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
        val l = 0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b
        val m = 0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b
        val s = 0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b
        val l_ = cbrt(l.toDouble()).toFloat()
        val m_ = cbrt(m.toDouble()).toFloat()
        val s_ = cbrt(s.toDouble()).toFloat()
        return Lab(
            L = 0.2104542553f * l_ + 0.7936177850f * m_ - 0.0040720468f * s_,
            a = 1.9779984951f * l_ - 2.4285922050f * m_ + 0.4505937099f * s_,
            b = 0.0259040371f * l_ + 0.7827717662f * m_ - 0.8086757660f * s_,
        )
    }

    /** Returns linear RGB triple as a FloatArray of size 3. */
    fun okLabToLinearRgb(L: Float, a: Float, b: Float): FloatArray {
        val l_ = L + 0.3963377774f * a + 0.2158037573f * b
        val m_ = L - 0.1055613458f * a - 0.0638541728f * b
        val s_ = L - 0.0894841775f * a - 1.2914855480f * b
        val l = l_ * l_ * l_
        val m = m_ * m_ * m_
        val s = s_ * s_ * s_
        return floatArrayOf(
            4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s,
            -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s,
            -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s,
        )
    }

    fun labToLCh(L: Float, a: Float, b: Float): LCh {
        val C = sqrt(a * a + b * b)
        val H = atan2(b, a)
        return LCh(L, C, H)
    }

    fun lChToLab(L: Float, C: Float, H: Float): Lab = Lab(L, C * cos(H), C * sin(H))
}
