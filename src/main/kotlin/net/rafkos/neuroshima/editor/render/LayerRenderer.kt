package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.model.LayerProperties
import java.awt.Color
import java.awt.image.BufferedImage
import java.awt.image.RescaleOp

object LayerRenderer {

    fun applyPixelOps(source: BufferedImage, props: LayerProperties): BufferedImage {
        val identity = props.opacity == 1f && props.hue == 0f &&
            props.saturation == 1f && props.brightness == 1f
        if (identity) return source

        var img = ensureArgb(source)
        if (props.hue != 0f) img = hueShift(img, props.hue)
        if (props.saturation != 1f) img = applySaturationPerPixel(img, props.saturation)
        if (props.brightness != 1f) {
            val scales = floatArrayOf(props.brightness, props.brightness, props.brightness, 1f)
            val offsets = floatArrayOf(0f, 0f, 0f, 0f)
            img = RescaleOp(scales, offsets, null).filter(img, null)
        }
        if (props.opacity != 1f) {
            val scales = floatArrayOf(1f, 1f, 1f, props.opacity)
            val offsets = floatArrayOf(0f, 0f, 0f, 0f)
            img = RescaleOp(scales, offsets, null).filter(img, null)
        }
        return img
    }

    private fun ensureArgb(source: BufferedImage): BufferedImage {
        if (source.type == BufferedImage.TYPE_INT_ARGB) return source
        val copy = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
        val g = copy.createGraphics()
        try { g.drawImage(source, 0, 0, null) } finally { g.dispose() }
        return copy
    }

    // Per-pixel for saturation: RescaleOp can't do saturation in RGB space.
    private fun applySaturationPerPixel(src: BufferedImage, saturation: Float): BufferedImage {
        val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
        val hsb = FloatArray(3)
        for (y in 0 until src.height) for (x in 0 until src.width) {
            val argb = src.getRGB(x, y)
            val a = (argb ushr 24) and 0xff
            val r = (argb ushr 16) and 0xff
            val g = (argb ushr 8) and 0xff
            val b = argb and 0xff
            Color.RGBtoHSB(r, g, b, hsb)
            val newSat = (hsb[1] * saturation).coerceIn(0f, 1f)
            val rgb = Color.HSBtoRGB(hsb[0], newSat, hsb[2]) and 0x00ffffff
            out.setRGB(x, y, (a shl 24) or rgb)
        }
        return out
    }

    private fun hueShift(src: BufferedImage, hueShift: Float): BufferedImage {
        val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
        val hsb = FloatArray(3)
        for (y in 0 until src.height) for (x in 0 until src.width) {
            val argb = src.getRGB(x, y)
            val a = (argb ushr 24) and 0xff
            val r = (argb ushr 16) and 0xff
            val g = (argb ushr 8) and 0xff
            val b = argb and 0xff
            Color.RGBtoHSB(r, g, b, hsb)
            val newHue = ((hsb[0] + hueShift) % 1f + 1f) % 1f
            val rgb = Color.HSBtoRGB(newHue, hsb[1], hsb[2]) and 0x00ffffff
            out.setRGB(x, y, (a shl 24) or rgb)
        }
        return out
    }
}
