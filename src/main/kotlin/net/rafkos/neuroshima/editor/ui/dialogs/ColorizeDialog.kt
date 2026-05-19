package net.rafkos.neuroshima.editor.ui.dialogs

import net.rafkos.neuroshima.editor.i18n.LocaleService
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import javax.swing.*
import javax.swing.event.ChangeListener
import kotlin.math.roundToInt

class ColorizeDialog private constructor(
    owner: Window?,
    private val locale: LocaleService,
    initialHue: Float,
    initialSat: Float,
    initialBright: Float,
    private val onPreview: (hue: Float, sat: Float, bright: Float) -> Unit,
) : JDialog(owner, ModalityType.APPLICATION_MODAL) {

    var accepted = false

    var hue: Float = initialHue
        private set
    var saturation: Float = initialSat
        private set
    var brightness: Float = initialBright
        private set

    private val square = ColorSquarePanel(initialHue, initialSat, initialBright)
    private val hSlider = JSlider(0, 360, (initialHue * 360).roundToInt())
    private val sSlider = JSlider(0, 100, (initialSat * 100).roundToInt())
    private val bSlider = JSlider(0, 100, (initialBright * 100).roundToInt())

    private var updating = false

    init {
        title = locale.t("tool.colorize")
        defaultCloseOperation = DISPOSE_ON_CLOSE
        isResizable = false

        val content = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
        }

        square.border = BorderFactory.createLineBorder(Color.DARK_GRAY)
        content.add(square, BorderLayout.NORTH)

        val slidersPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply { insets = Insets(3, 0, 3, 6) }

        fun addRow(row: Int, labelKey: String, slider: JSlider) {
            gbc.gridy = row
            gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
            slidersPanel.add(JLabel(locale.t(labelKey)), gbc)
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
            slidersPanel.add(slider, gbc)
        }
        addRow(0, "prop.hue", hSlider)
        addRow(1, "prop.saturation", sSlider)
        addRow(2, "prop.brightness", bSlider)
        content.add(slidersPanel, BorderLayout.CENTER)

        val okBtn = JButton(locale.t("dialog.ok")).apply {
            addActionListener { accepted = true; dispose() }
        }
        val cancelBtn = JButton(locale.t("dialog.cancel")).apply {
            addActionListener { dispose() }
        }
        val btnPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0))
        btnPanel.add(cancelBtn)
        btnPanel.add(okBtn)
        content.add(btnPanel, BorderLayout.SOUTH)

        contentPane = content

        val listener = ChangeListener { if (!updating) syncFromSliders() }
        hSlider.addChangeListener(listener)
        sSlider.addChangeListener(listener)
        bSlider.addChangeListener(listener)

        getRootPane().defaultButton = okBtn
        pack()
    }

    private fun syncFromSliders() {
        hue = hSlider.value / 360f
        saturation = sSlider.value / 100f
        brightness = bSlider.value / 100f
        updating = true
        square.update(hue, saturation, brightness)
        updating = false
        onPreview(hue, saturation, brightness)
    }

    private fun syncFromSquare(newSat: Float, newBright: Float) {
        saturation = newSat
        brightness = newBright
        updating = true
        sSlider.value = (newSat * 100).roundToInt()
        bSlider.value = (newBright * 100).roundToInt()
        updating = false
        onPreview(hue, saturation, brightness)
    }

    private inner class ColorSquarePanel(h: Float, s: Float, b: Float) : JPanel() {
        private var pHue = h
        private var pSat = s
        private var pBright = b

        init {
            preferredSize = Dimension(240, 240)
            cursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
            val ml = object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) = pick(e)
                override fun mouseDragged(e: MouseEvent) = pick(e)
            }
            addMouseListener(ml)
            addMouseMotionListener(ml)
        }

        private fun pick(e: MouseEvent) {
            val s = (e.x.toFloat() / width).coerceIn(0f, 1f)
            val b = (1f - e.y.toFloat() / height).coerceIn(0f, 1f)
            pSat = s; pBright = b
            repaint()
            syncFromSquare(s, b)
        }

        fun update(h: Float, s: Float, b: Float) {
            pHue = h; pSat = s; pBright = b
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val w = width; val h = height

            // Saturation axis (x): white → fully saturated hue color
            g2.paint = GradientPaint(0f, 0f, Color.WHITE, w.toFloat(), 0f, Color.getHSBColor(pHue, 1f, 1f))
            g2.fillRect(0, 0, w, h)

            // Brightness axis (y): transparent → black
            g2.paint = GradientPaint(0f, 0f, Color(0, 0, 0, 0), 0f, h.toFloat(), Color.BLACK)
            g2.fillRect(0, 0, w, h)

            // Crosshair — black outer ring, white inner ring for visibility on any background
            val cx = (pSat * w).toInt().coerceIn(0, w - 1)
            val cy = ((1f - pBright) * h).toInt().coerceIn(0, h - 1)
            g2.color = Color.BLACK
            g2.draw(Ellipse2D.Float(cx - 7f, cy - 7f, 14f, 14f))
            g2.color = Color.WHITE
            g2.draw(Ellipse2D.Float(cx - 5f, cy - 5f, 10f, 10f))
        }
    }

    companion object {
        fun show(
            parent: Component?,
            locale: LocaleService,
            initialHue: Float,
            initialSat: Float,
            initialBright: Float,
            onPreview: (Float, Float, Float) -> Unit,
        ): ColorizeDialog {
            val owner = parent?.let { SwingUtilities.getWindowAncestor(it) }
            return ColorizeDialog(owner, locale, initialHue, initialSat, initialBright, onPreview).also {
                it.setLocationRelativeTo(parent)
                it.isVisible = true
            }
        }
    }
}
