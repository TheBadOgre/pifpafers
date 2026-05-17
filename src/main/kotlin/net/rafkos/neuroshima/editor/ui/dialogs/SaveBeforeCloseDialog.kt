package net.rafkos.neuroshima.editor.ui.dialogs

import java.awt.Component
import javax.swing.JOptionPane

object SaveBeforeCloseDialog {
    enum class Result { SAVE, DISCARD, CANCEL }

    fun ask(parent: Component): Result {
        val choice = JOptionPane.showOptionDialog(
            parent,
            "You have unsaved changes. Save before closing?",
            "Unsaved changes",
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            arrayOf("Save", "Discard", "Cancel"),
            "Save",
        )
        return when (choice) {
            0 -> Result.SAVE
            1 -> Result.DISCARD
            else -> Result.CANCEL
        }
    }
}
