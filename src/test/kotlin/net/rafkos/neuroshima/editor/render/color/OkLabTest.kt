package net.rafkos.neuroshima.editor.render.color

import org.junit.jupiter.api.Test
import kotlin.math.abs

class OkLabTest {

    @Test
    fun `sRGB byte round-trip through linear is exact within 1 of 255`() {
        for (i in 0..255) {
            val lin = OkLab.srgb8ToLinear(i)
            val back = OkLab.linearToSrgb8(lin)
            assert(abs(back - i) <= 1) { "delta ${back - i} at byte $i" }
        }
    }

    @Test
    fun `linear black and white round-trip through OKLab`() {
        for (g in floatArrayOf(0f, 0.18f, 0.5f, 1f)) {
            val lab = OkLab.linearRgbToOkLab(g, g, g)
            val rgb = OkLab.okLabToLinearRgb(lab.L, lab.a, lab.b)
            for (i in 0..2) {
                assert(abs(rgb[i] - g) < 1e-4f) { "gray $g channel $i -> ${rgb[i]}" }
            }
        }
    }

    @Test
    fun `gray pixels have near-zero chroma in OKLCh`() {
        for (g in floatArrayOf(0.1f, 0.5f, 0.9f)) {
            val lab = OkLab.linearRgbToOkLab(g, g, g)
            val lch = OkLab.labToLCh(lab.L, lab.a, lab.b)
            assert(lch.C < 1e-4f) { "gray $g produced chroma ${lch.C}" }
        }
    }

    @Test
    fun `pure red has L below pure green in OKLab`() {
        val red = OkLab.linearRgbToOkLab(1f, 0f, 0f)
        val green = OkLab.linearRgbToOkLab(0f, 1f, 0f)
        assert(red.L < green.L) { "red.L=${red.L} green.L=${green.L}" }
    }
}
