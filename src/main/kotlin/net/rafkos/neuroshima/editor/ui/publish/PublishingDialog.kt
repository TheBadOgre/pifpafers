package net.rafkos.neuroshima.editor.ui.publish

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.model.PageFormat
import net.rafkos.neuroshima.editor.model.PublishSettings
import net.rafkos.neuroshima.editor.publish.MaskRenderer
import net.rafkos.neuroshima.editor.publish.PageLayoutPlanner
import net.rafkos.neuroshima.editor.publish.PageRasterizer
import net.rafkos.neuroshima.editor.publish.PageRenderer
import net.rafkos.neuroshima.editor.publish.PdfExporter
import net.rafkos.neuroshima.editor.publish.PngExporter
import java.awt.BorderLayout
import java.awt.Dialog
import java.awt.Dimension
import java.awt.Frame
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.swing.ButtonGroup
import javax.swing.JCheckBoxMenuItem
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JRadioButtonMenuItem
import javax.swing.JScrollPane
import javax.swing.SwingWorker
import javax.swing.filechooser.FileNameExtensionFilter

class PublishingDialog(
    private val ctx: AppContext,
    owner: Frame,
) : JDialog(owner, ctx.locale.t("dialog.publish.title"), Dialog.ModalityType.APPLICATION_MODAL) {

    private val preview = PagePreviewPanel()

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        layout = BorderLayout()
        val scroll = JScrollPane(preview).apply {
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            getViewport().background = preview.background
        }
        add(scroll, BorderLayout.CENTER)
        jMenuBar = buildMenuBar()
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) { dispose() }
        })
        size = Dimension(960, 700)
        setLocationRelativeTo(owner)
        rebuildPreview()
    }

    private fun buildMenuBar(): JMenuBar {
        val bar = JMenuBar()
        bar.add(settingsMenu())
        bar.add(exportMenu())
        return bar
    }

    private fun settingsMenu(): JMenu {
        val m = JMenu(ctx.locale.t("menu.publish.settings"))
        val dpiGroup = ButtonGroup()
        for ((dpi, key) in listOf(150 to "menu.publish.dpi.150", 300 to "menu.publish.dpi.300", 600 to "menu.publish.dpi.600")) {
            val mi = JRadioButtonMenuItem(ctx.locale.t(key), ctx.bag.printSettings.dpi == dpi)
            mi.addActionListener { applySettings(ctx.bag.printSettings.copy(dpi = dpi)) }
            dpiGroup.add(mi); m.add(mi)
        }
        m.addSeparator()
        val fmtGroup = ButtonGroup()
        for ((fmt, key) in listOf(
            PageFormat.A3 to "menu.publish.format.a3",
            PageFormat.A4 to "menu.publish.format.a4",
            PageFormat.A5 to "menu.publish.format.a5",
        )) {
            val mi = JRadioButtonMenuItem(ctx.locale.t(key), ctx.bag.printSettings.pageFormat == fmt)
            mi.addActionListener { applySettings(ctx.bag.printSettings.copy(pageFormat = fmt)) }
            fmtGroup.add(mi); m.add(mi)
        }
        m.addSeparator()
        m.add(JCheckBoxMenuItem(ctx.locale.t("menu.publish.invert.back"), ctx.bag.printSettings.invertBackSide).apply {
            addActionListener { applySettings(ctx.bag.printSettings.copy(invertBackSide = isSelected)) }
        })
        m.add(JCheckBoxMenuItem(ctx.locale.t("menu.publish.render.overlay"), ctx.bag.printSettings.renderOverlay).apply {
            addActionListener { applySettings(ctx.bag.printSettings.copy(renderOverlay = isSelected)) }
        })
        return m
    }

    private fun exportMenu(): JMenu {
        val m = JMenu(ctx.locale.t("menu.publish.export"))
        m.add(JMenuItem(ctx.locale.t("menu.publish.images")).apply {
            isEnabled = ctx.bag.tokens.isNotEmpty()
            addActionListener { exportPng() }
        })
        m.add(JMenuItem(ctx.locale.t("menu.publish.printable")).apply {
            isEnabled = ctx.bag.tokens.isNotEmpty()
            addActionListener { exportPdf() }
        })
        return m
    }

    private fun applySettings(s: PublishSettings) {
        ctx.bag.updatePrintSettings(s)
        rebuildPreview()
    }

    private fun rebuildPreview() {
        val settings = ctx.bag.printSettings
        val plans = PageLayoutPlanner(settings, ctx.bag.tokens).plan()
        val raster = PageRasterizer(PageRenderer(ctx.imageCache))
        val thumbs = plans.map { plan ->
            val scale = 240.0 / plan.widthPx
            val low = BufferedImage(240, (plan.heightPx * scale).toInt(), BufferedImage.TYPE_INT_RGB)
            val g = low.createGraphics()
            try {
                val full = raster.rasterizePage(plan, settings)
                g.drawImage(full, 0, 0, low.width, low.height, null)
            } finally {
                g.dispose()
            }
            low
        }
        preview.setPages(thumbs)
    }

    private fun exportPng() {
        val dir = chooseDirectory() ?: return
        val baseName = JOptionPane.showInputDialog(
            this,
            ctx.locale.t("dialog.publish.basename.prompt"),
            ctx.bag.name.ifBlank { "army" },
        ) ?: return
        runExport {
            val plans = PageLayoutPlanner(ctx.bag.printSettings, ctx.bag.tokens).plan()
            val rasterizer = PageRasterizer(PageRenderer(ctx.imageCache))
            val maskRenderer = MaskRenderer()
            val exporter = PngExporter(rasterizer, maskRenderer)
            exporter.export(ctx.bag, plans, dir, baseName, overwrite = true)
        }
    }

    private fun exportPdf() {
        val target = choosePdfFile() ?: return
        runExport {
            val plans = PageLayoutPlanner(ctx.bag.printSettings, ctx.bag.tokens).plan()
            val rasterizer = PageRasterizer(PageRenderer(ctx.imageCache))
            PdfExporter(rasterizer).export(ctx.bag, plans, target)
        }
    }

    private fun runExport(work: () -> Unit) {
        val worker = object : SwingWorker<Unit, Unit>() {
            override fun doInBackground() { work() }
            override fun done() {}
        }
        worker.execute()
    }

    private fun chooseDirectory(): Path? {
        val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return null
        return chooser.selectedFile.toPath()
    }

    private fun choosePdfFile(): Path? {
        val chooser = JFileChooser().apply {
            fileFilter = FileNameExtensionFilter("PDF", "pdf")
        }
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return null
        val raw = chooser.selectedFile
        val withExt = if (raw.extension.equals("pdf", ignoreCase = true)) raw else java.io.File("${raw.absolutePath}.pdf")
        return withExt.toPath()
    }
}
