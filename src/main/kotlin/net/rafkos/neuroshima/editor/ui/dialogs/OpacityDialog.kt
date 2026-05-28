package net.rafkos.neuroshima.editor.ui.dialogs

import net.rafkos.neuroshima.editor.i18n.LocaleService
import java.awt.*
import javax.swing.*
import javax.swing.event.ChangeListener
import kotlin.math.roundToInt

class OpacityDialog private constructor(
    owner: Window?,
    locale: LocaleService,
    initialOpacity: Float,
    private val onPreview: (Float) -> Unit,
) : JDialog(owner, ModalityType.APPLICATION_MODAL) {

    var accepted = false
    var opacityValue: Float = initialOpacity
        private set

    private val slider = JSlider(0, 100, (initialOpacity * 100).roundToInt())
    private val pctLabel = JLabel("${(initialOpacity * 100).roundToInt()}%").apply {
        preferredSize = Dimension(36, preferredSize.height)
        horizontalAlignment = SwingConstants.RIGHT
    }

    init {
        title = locale.t("tool.opacity")
        defaultCloseOperation = DISPOSE_ON_CLOSE
        isResizable = false

        val content = JPanel(BorderLayout(8, 8)).apply {
            border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
        }

        val row = JPanel(BorderLayout(8, 0))
        row.add(JLabel(locale.t("prop.opacity")), BorderLayout.WEST)
        row.add(slider, BorderLayout.CENTER)
        row.add(pctLabel, BorderLayout.EAST)
        content.add(row, BorderLayout.CENTER)

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

        slider.addChangeListener {
            opacityValue = slider.value / 100f
            pctLabel.text = "${slider.value}%"
            onPreview(opacityValue)
        }

        getRootPane().defaultButton = okBtn
        pack()
    }

    companion object {
        fun show(
            parent: Component?,
            locale: LocaleService,
            initialOpacity: Float,
            onPreview: (Float) -> Unit,
        ): OpacityDialog {
            val owner = parent?.let { SwingUtilities.getWindowAncestor(it) }
            return OpacityDialog(owner, locale, initialOpacity, onPreview).also {
                it.setLocationRelativeTo(parent)
                it.isVisible = true
            }
        }
    }
}
