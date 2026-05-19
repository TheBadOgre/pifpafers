package net.rafkos.neuroshima.editor.render.color

import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import kotlin.math.abs

class ColorizePipelineTest {

    private fun solid(rgb: Int, alpha: Int = 0xff, w: Int = 4, h: Int = 4): BufferedImage {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until h) for (x in 0 until w) {
            img.setRGB(x, y, (alpha shl 24) or (rgb and 0xffffff))
        }
        return img
    }

    @Test
    fun `fully transparent pixels stay transparent`() {
        val src = solid(0x808080, alpha = 0)
        val out = ColorizePipeline.apply(src, targetHue = 0f, satMult = 2f)
        for (y in 0 until out.height) for (x in 0 until out.width) {
            assert((out.getRGB(x, y) ushr 24) and 0xff == 0)
        }
    }

    @Test
    fun `satMult of zero produces neutral output (no chroma)`() {
        val src = solid(0x808080)
        val out = ColorizePipeline.apply(src, targetHue = 0f, satMult = 0f)
        val argb = out.getRGB(0, 0)
        val r = (argb ushr 16) and 0xff
        val g = (argb ushr 8) and 0xff
        val b = argb and 0xff
        assert(abs(r - g) <= 2 && abs(g - b) <= 2) { "expected gray, got $r,$g,$b" }
    }

    @Test
    fun `red target hue makes mid-gray reddish`() {
        val src = solid(0x808080)
        val out = ColorizePipeline.apply(src, targetHue = 0f, satMult = 2f)
        val argb = out.getRGB(0, 0)
        val r = (argb ushr 16) and 0xff
        val g = (argb ushr 8) and 0xff
        val b = argb and 0xff
        assert(r > g && r > b) { "expected r dominant, got $r,$g,$b" }
    }

    @Test
    fun `blue target hue makes mid-gray bluish`() {
        val src = solid(0x808080)
        val out = ColorizePipeline.apply(src, targetHue = 2f / 3f, satMult = 2f)
        val argb = out.getRGB(0, 0)
        val r = (argb ushr 16) and 0xff
        val g = (argb ushr 8) and 0xff
        val b = argb and 0xff
        assert(b > r) { "expected blue dominant, got $r,$g,$b" }
    }

    @Test
    fun `near-white tints less than mid-gray (lumBell attenuates highlights)`() {
        val white = solid(0xf2f2f2)
        val gray = solid(0x808080)
        val whiteOut = ColorizePipeline.apply(white, targetHue = 0f, satMult = 2f)
        val grayOut = ColorizePipeline.apply(gray, targetHue = 0f, satMult = 2f)

        fun chromaProxy(argb: Int): Int {
            val r = (argb ushr 16) and 0xff
            val g = (argb ushr 8) and 0xff
            val b = argb and 0xff
            return kotlin.math.max(r, kotlin.math.max(g, b)) - kotlin.math.min(r, kotlin.math.min(g, b))
        }
        val cWhite = chromaProxy(whiteOut.getRGB(0, 0))
        val cGray = chromaProxy(grayOut.getRGB(0, 0))
        assert(cWhite < cGray) { "expected white chroma ($cWhite) < gray chroma ($cGray)" }
    }

    @Test
    fun `mid-gray input keeps perceptual luminance roughly intact`() {
        val src = solid(0x808080)
        val out = ColorizePipeline.apply(src, targetHue = 0.33f, satMult = 2f)
        val inLab = labOf(0x80, 0x80, 0x80)
        val pixel = out.getRGB(0, 0)
        val outLab = labOf((pixel ushr 16) and 0xff, (pixel ushr 8) and 0xff, pixel and 0xff)
        // S-curve allowed to nudge L; total drift should stay small.
        assert(abs(outLab.L - inLab.L) < 0.08f) { "L drift ${outLab.L - inLab.L}" }
    }

    private fun labOf(r: Int, g: Int, b: Int): OkLab.Lab {
        val rLin = OkLab.srgb8ToLinear(r)
        val gLin = OkLab.srgb8ToLinear(g)
        val bLin = OkLab.srgb8ToLinear(b)
        return OkLab.linearRgbToOkLab(rLin, gLin, bLin)
    }
}
