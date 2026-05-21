package net.rafkos.neuroshima.editor.app

import net.rafkos.neuroshima.editor.i18n.LocaleService
import net.rafkos.neuroshima.editor.ui.MainFrame
import java.nio.file.Paths
import javax.swing.SwingUtilities
import javax.swing.UIManager

fun main() {
    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())

    val bundledRoot = AppDirs.assetsRoot.toPath()
    val userRoot = Paths.get(System.getProperty("user.home"))
        .resolve(".neuroshima-editor").resolve("content")
    val prefsFile = Paths.get(System.getProperty("user.home"))
        .resolve(".neuroshima-editor").resolve("prefs.json")

    val ctx = AppContext(bundledRoot = bundledRoot, userRoot = userRoot, prefsFile = prefsFile)
    applyFileChooserLocale(ctx.locale)
    SwingUtilities.invokeLater {
        val frame = MainFrame(ctx)
        frame.isVisible = true
        Runtime.getRuntime().addShutdownHook(Thread { ctx.savePrefs() })
    }
}

private fun applyFileChooserLocale(locale: LocaleService) {
    UIManager.put("FileChooser.fileNameLabelText", locale.t("chooser.label.fileName"))
    UIManager.put("FileChooser.filesOfTypeLabelText", locale.t("chooser.label.filesOfType"))
    UIManager.put("FileChooser.lookInLabelText", locale.t("chooser.label.lookIn"))
    UIManager.put("FileChooser.saveInLabelText", locale.t("chooser.label.saveIn"))
    UIManager.put("FileChooser.folderNameLabelText", locale.t("chooser.label.folderName"))
    UIManager.put("FileChooser.saveButtonText", locale.t("chooser.button.save"))
    UIManager.put("FileChooser.saveButtonToolTipText", locale.t("chooser.button.save"))
    UIManager.put("FileChooser.cancelButtonText", locale.t("chooser.button.cancel"))
    UIManager.put("FileChooser.cancelButtonToolTipText", locale.t("chooser.button.cancel"))
    UIManager.put("FileChooser.openButtonText", locale.t("chooser.button.open"))
    UIManager.put("FileChooser.openButtonToolTipText", locale.t("chooser.button.open"))
    UIManager.put("FileChooser.acceptAllFileFilterText", locale.t("chooser.filter.allFiles"))
}
