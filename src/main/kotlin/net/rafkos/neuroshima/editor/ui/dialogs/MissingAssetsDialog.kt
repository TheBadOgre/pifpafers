package net.rafkos.neuroshima.editor.ui.dialogs

import net.rafkos.neuroshima.editor.persistence.MissingAssetsException
import java.awt.Component
import javax.swing.JOptionPane

object MissingAssetsDialog {
    fun show(parent: Component, title: String, header: String, ex: MissingAssetsException) {
        val body = StringBuilder(header).append("\n\n")
        for (m in ex.missing) body.append(m.uri).append('\n')
        JOptionPane.showMessageDialog(parent, body.toString(), title, JOptionPane.ERROR_MESSAGE)
    }
}
