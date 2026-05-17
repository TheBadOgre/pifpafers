package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.model.LayerProperties
import java.awt.Color
import java.awt.image.BufferedImage

object LayerRendererLegacy {
    fun applyPixelOps(source: BufferedImage, props: LayerProperties): BufferedImage {
        val identity = props.opacity == 1f && props.hue == 0f &&
            props.saturation == 1f && props.brightness == 1f
        if (identity) return source
        val out = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
        val hsb = FloatArray(3)
        for (y in 0 until source.height) for (x in 0 until source.width) {
            val argb = source.getRGB(x, y)
            val a = (argb ushr 24) and 0xff
            val r = (argb ushr 16) and 0xff
            val g = (argb ushr 8) and 0xff
            val b = argb and 0xff
            Color.RGBtoHSB(r, g, b, hsb)
            val newHue = ((hsb[0] + props.hue) % 1f + 1f) % 1f
            val newSat = (hsb[1] * props.saturation).coerceIn(0f, 1f)
            val newBri = (hsb[2] * props.brightness).coerceIn(0f, 1f)
            val rgb = Color.HSBtoRGB(newHue, newSat, newBri) and 0x00ffffff
            val newAlpha = (a * props.opacity).toInt().coerceIn(0, 255)
            out.setRGB(x, y, (newAlpha shl 24) or rgb)
        }
        return out
    }
}
