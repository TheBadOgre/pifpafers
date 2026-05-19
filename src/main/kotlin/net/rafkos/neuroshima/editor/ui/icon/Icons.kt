package net.rafkos.neuroshima.editor.ui.icon

import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.RenderingHints
import java.awt.image.BaseMultiResolutionImage
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.ImageIcon

object Icons {
    private const val SIZE = 24

    private class SmoothIcon(image: Image) : ImageIcon(image) {
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g as? Graphics2D
            g2?.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2?.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g2?.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            super.paintIcon(c, g, x, y)
        }
    }

    private fun load(name: String): ImageIcon {
        val stream = Icons::class.java.classLoader.getResourceAsStream("icons/$name.png")
            ?: return ImageIcon()
        val src = ImageIO.read(stream) ?: return ImageIcon()
        val img1x = downscale(src, SIZE, SIZE)
        val img2x = downscale(src, SIZE * 2, SIZE * 2)
        return SmoothIcon(BaseMultiResolutionImage(img1x, img2x))
    }

    private fun downscale(src: BufferedImage, targetW: Int, targetH: Int): BufferedImage {
        if (src.width == targetW && src.height == targetH) return src
        var cur = src
        while (cur.width > targetW * 2 || cur.height > targetH * 2) {
            val w = maxOf(cur.width / 2, targetW)
            val h = maxOf(cur.height / 2, targetH)
            cur = render(cur, w, h, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        }
        return render(cur, targetW, targetH, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
    }

    private fun render(src: BufferedImage, w: Int, h: Int, interpolation: Any): BufferedImage {
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.drawImage(src, 0, 0, w, h, null)
        g.dispose()
        return out
    }

    val appIcon: List<Image> = run {
        val stream = Icons::class.java.classLoader.getResourceAsStream("icons/app_icon.png")
        val src = stream?.let { ImageIO.read(it) }
        if (src != null) listOf(16, 32, 48, 64, 128, 256).map { sz -> downscale(src, sz, sz) }
        else emptyList()
    }

    val toolSelect     = load("tool_select")
    val toolMove       = load("tool_move")
    val toolRotate     = load("tool_rotate")
    val toolScale      = load("tool_scale")
    val toolOpacity    = load("tool_opacity")
    val toolColorize   = load("tool_colorize")
    val layerUp        = load("layer_up")
    val layerDown      = load("layer_down")
    val layerRemove    = load("layer_remove")
    val layerDuplicate = load("layer_duplicate")
    val tokenAddUnit   = load("token_add_unit")
    val tokenAddMod    = load("token_add_modifier")
    val tokenDelete    = load("token_delete")
    val tokenDuplicate = load("token_duplicate")
    val assetsRefresh  = load("assets_refresh")
}
