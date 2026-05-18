package net.rafkos.neuroshima.editor.ui.dialogs

import net.rafkos.neuroshima.editor.i18n.LocaleService
import java.awt.Component
import javax.swing.JOptionPane

object SaveBeforeCloseDialog {
    enum class Result { SAVE, DISCARD, CANCEL }

    fun ask(parent: Component, locale: LocaleService): Result {
        val saveLabel = locale.t("dialog.unsaved.save")
        val choice = JOptionPane.showOptionDialog(
            parent,
            locale.t("dialog.unsaved.message"),
            locale.t("dialog.unsaved.title"),
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            arrayOf(saveLabel, locale.t("dialog.unsaved.discard"), locale.t("dialog.unsaved.cancel")),
            saveLabel,
        )
        return when (choice) {
            0 -> Result.SAVE
            1 -> Result.DISCARD
            else -> Result.CANCEL
        }
    }
}
