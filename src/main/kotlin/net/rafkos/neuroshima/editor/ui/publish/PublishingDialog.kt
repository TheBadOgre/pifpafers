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
import javax.swing.BorderFactory
import javax.swing.ButtonGroup
import javax.swing.JCheckBoxMenuItem
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JRadioButtonMenuItem
import javax.swing.JScrollPane
import javax.swing.SwingWorker
import javax.swing.filechooser.FileNameExtensionFilter

class PublishingDialog(
    private val ctx: AppContext,
    owner: Frame,
) : JDialog(owner, ctx.locale.t("dialog.publish.title"), ModalityType.APPLICATION_MODAL) {

    private val preview = PagePreviewPanel()
    private var previewWorker: SwingWorker<*, *>? = null

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

    private fun buildProgressDialog(message: String): JDialog {
        val pd = JDialog(this, ctx.locale.t("dialog.progress.title"), ModalityType.APPLICATION_MODAL)
        pd.defaultCloseOperation = DO_NOTHING_ON_CLOSE
        pd.isResizable = false
        val panel = JPanel(BorderLayout(0, 10))
        panel.border = BorderFactory.createEmptyBorder(20, 30, 20, 30)
        panel.add(JLabel(message), BorderLayout.NORTH)
        val bar = JProgressBar().apply {
            isIndeterminate = true
            preferredSize = Dimension(300, preferredSize.height)
        }
        panel.add(bar, BorderLayout.CENTER)
        pd.contentPane = panel
        pd.pack()
        pd.setLocationRelativeTo(this)
        return pd
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
        val marginGroup = ButtonGroup()
        for ((marginMm, key) in listOf(
            15.0 to "menu.publish.margin.15",
            25.0 to "menu.publish.margin.25",
            35.0 to "menu.publish.margin.35",
        )) {
            val mi = JRadioButtonMenuItem(ctx.locale.t(key), ctx.bag.printSettings.marginMm == marginMm)
            mi.addActionListener { applySettings(ctx.bag.printSettings.copy(marginMm = marginMm)) }
            marginGroup.add(mi); m.add(mi)
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
        previewWorker?.cancel(true)
        val settings = ctx.bag.printSettings
        val tokens = ctx.bag.tokens.toList()
        val imageCache = ctx.imageCache
        val progressDialog = buildProgressDialog(ctx.locale.t("dialog.progress.preview"))

        val worker = object : SwingWorker<List<BufferedImage>, Unit>() {
            override fun doInBackground(): List<BufferedImage> {
                val plans = PageLayoutPlanner(settings, tokens).plan()
                val raster = PageRasterizer(PageRenderer(imageCache))
                return plans.mapNotNull { plan ->
                    if (isCancelled) return@mapNotNull null
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
            }

            override fun done() {
                progressDialog.dispose()
                if (!isCancelled) {
                    try { preview.setPages(get()) } catch (_: Exception) {}
                }
            }
        }
        previewWorker = worker
        worker.execute()
        progressDialog.isVisible = true
    }

    private fun exportPng() {
        val dir = chooseDirectory() ?: return
        val baseName = JOptionPane.showInputDialog(
            this,
            ctx.locale.t("dialog.publish.basename.prompt"),
            ctx.locale.t("dialog.publish.basename.title"),
            JOptionPane.PLAIN_MESSAGE,
            null,
            null,
            ctx.bag.name.ifBlank { "army" },
        ) as? String ?: return
        val progressDialog = buildProgressDialog(ctx.locale.t("dialog.progress.export.png"))
        val worker = object : SwingWorker<Unit, Unit>() {
            override fun doInBackground() {
                val plans = PageLayoutPlanner(ctx.bag.printSettings, ctx.bag.tokens).plan()
                val rasterizer = PageRasterizer(PageRenderer(ctx.imageCache))
                val exporter = PngExporter(rasterizer, MaskRenderer())
                exporter.export(ctx.bag, plans, dir, baseName, overwrite = true)
            }
            override fun done() { progressDialog.dispose() }
        }
        worker.execute()
        progressDialog.isVisible = true
    }

    private fun exportPdf() {
        val target = choosePdfFile() ?: return
        val progressDialog = buildProgressDialog(ctx.locale.t("dialog.progress.export.pdf"))
        val worker = object : SwingWorker<Unit, Unit>() {
            override fun doInBackground() {
                val plans = PageLayoutPlanner(ctx.bag.printSettings, ctx.bag.tokens).plan()
                val rasterizer = PageRasterizer(PageRenderer(ctx.imageCache))
                PdfExporter(rasterizer).export(ctx.bag, plans, target)
            }
            override fun done() { progressDialog.dispose() }
        }
        worker.execute()
        progressDialog.isVisible = true
    }

    private fun chooseDirectory(): Path? {
        val chooser = JFileChooser().apply {
            dialogTitle = ctx.locale.t("chooser.export.images.title")
            approveButtonText = ctx.locale.t("chooser.export.images.approve")
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            ctx.lastImagesDir?.let { currentDirectory = java.io.File(it) }
        }
        if (chooser.showDialog(this, null) != JFileChooser.APPROVE_OPTION) return null
        val path = chooser.selectedFile.toPath()
        ctx.lastImagesDir = path.toString()
        ctx.savePrefs()
        return path
    }

    private fun choosePdfFile(): Path? {
        val chooser = JFileChooser().apply {
            dialogTitle = ctx.locale.t("chooser.export.pdf.title")
            fileFilter = FileNameExtensionFilter(ctx.locale.t("filter.pdf.description"), "pdf")
            ctx.lastPdfDir?.let { currentDirectory = java.io.File(it) }
        }
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return null
        val raw = chooser.selectedFile
        val withExt = if (raw.extension.equals("pdf", ignoreCase = true)) raw else java.io.File("${raw.absolutePath}.pdf")
        if (withExt.exists()) {
            val result = JOptionPane.showOptionDialog(
                this,
                ctx.locale.t("dialog.overwrite.message", withExt.name),
                ctx.locale.t("dialog.overwrite.title"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                arrayOf(ctx.locale.t("dialog.overwrite"), ctx.locale.t("dialog.cancel")),
                ctx.locale.t("dialog.cancel"),
            )
            if (result != 0) return null
        }
        ctx.lastPdfDir = withExt.parentFile?.absolutePath
        ctx.savePrefs()
        return withExt.toPath()
    }
}
