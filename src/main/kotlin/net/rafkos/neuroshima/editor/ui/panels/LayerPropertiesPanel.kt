package net.rafkos.neuroshima.editor.ui.panels

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.command.ColorizeCommand
import net.rafkos.neuroshima.editor.command.LayerProperty
import net.rafkos.neuroshima.editor.command.SetLayerPropertyCommand
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.LayerProperties
import net.rafkos.neuroshima.editor.model.ModelEvent
import net.rafkos.neuroshima.editor.model.TokenSide
import java.awt.BorderLayout
import java.awt.GridLayout
import java.util.UUID
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class LayerPropertiesPanel(private val ctx: AppContext) : JPanel() {

    private val content = JPanel(GridLayout(0, 2, 4, 2))
    private val spinnerMap = mutableMapOf<LayerProperty, JSpinner>()
    private var colorizeCb: JCheckBox? = null
    private var displayedLayerId: UUID? = null
    private var suppressChange = false
    private val resetButton = JButton(ctx.locale.t("button.reset.defaults"))

    init {
        layout = BorderLayout()
        border = BorderFactory.createTitledBorder(ctx.locale.t("panel.properties"))
        val contentWrapper = JPanel(BorderLayout()).apply { add(content, BorderLayout.NORTH) }
        add(JScrollPane(contentWrapper).apply { border = null }, BorderLayout.CENTER)
        val resetHolder = JPanel(BorderLayout()).apply {
            isOpaque = true
            border = BorderFactory.createEmptyBorder(8, 4, 4, 4)
        }
        resetHolder.add(resetButton, BorderLayout.CENTER)
        add(resetHolder, BorderLayout.SOUTH)
        resetButton.addActionListener { resetToDefaults() }
        ctx.addBagListener { event ->
            when (event) {
                is ModelEvent.LayerAdded,
                is ModelEvent.LayerRemoved,
                is ModelEvent.LayerReordered,
                is ModelEvent.LayerPropsChanged -> rebuild()
                else -> {}
            }
        }
        ctx.viewState.addListener { rebuild() }
        rebuild()
    }

    private fun activeLayer(): Triple<UUID, TokenSide, Layer>? {
        if (ctx.viewState.selectedLayers.size != 1) return null
        val tokenId = ctx.viewState.activeTokenId ?: return null
        val layerId = ctx.viewState.selectedLayers.first()
        val token = ctx.bag.findToken(tokenId) ?: return null
        val side = ctx.viewState.activeSide
        val layer = token.findLayer(side, layerId) ?: return null
        return Triple(tokenId, side, layer)
    }

    private fun rebuild() {
        val pair = activeLayer()
        if (pair == null) {
            val msgKey = if (ctx.viewState.selectedLayers.size > 1) "label.multi.layer" else "label.no.layer"
            if (displayedLayerId == null && content.componentCount > 0 &&
                (content.getComponent(0) as? JLabel)?.text == ctx.locale.t(msgKey)) return
            displayedLayerId = null
            spinnerMap.clear()
            colorizeCb = null
            content.removeAll()
            content.add(JLabel(ctx.locale.t(msgKey)))
            content.add(JLabel(""))
            content.revalidate(); content.repaint()
            resetButton.isEnabled = false
            return
        }
        val (tokenId, side, layer) = pair
        if (layer.id == displayedLayerId) {
            for ((prop, spinner) in spinnerMap) {
                val v = propValue(layer, prop)
                if ((spinner.value as Number).toDouble() != v) {
                    suppressChange = true
                    spinner.value = v
                    suppressChange = false
                }
            }
            colorizeCb?.let { cb ->
                if (cb.isSelected != layer.props.colorize) {
                    suppressChange = true
                    cb.isSelected = layer.props.colorize
                    suppressChange = false
                }
            }
            return
        }
        displayedLayerId = layer.id
        spinnerMap.clear()
        colorizeCb = null
        content.removeAll()
        val props = listOf(
            LayerProperty.OFFSET_X,
            LayerProperty.OFFSET_Y,
            LayerProperty.ROTATION,
            LayerProperty.SCALE,
            LayerProperty.OPACITY,
            LayerProperty.HUE,
            LayerProperty.SATURATION,
            LayerProperty.BRIGHTNESS,
        )
        for (prop in props) {
            val value = propValue(layer, prop)
            content.add(JLabel(ctx.locale.t(prop.labelKey())))
            val (min, max, step) = bounds(prop)
            val model = SpinnerNumberModel(value, min, max, step)
            val spinner = JSpinner(model)
            spinner.addChangeListener {
                if (suppressChange) return@addChangeListener
                val newValue = (spinner.value as Number).toDouble()
                val currentLayer = ctx.bag.findToken(tokenId)?.findLayer(side, layer.id) ?: return@addChangeListener
                val oldValue = propValue(currentLayer, prop)
                ctx.history.execute(ctx.bag, SetLayerPropertyCommand(
                    tokenId = tokenId,
                    side = side,
                    layerId = layer.id,
                    property = prop,
                    oldValue = oldValue,
                    newValue = newValue,
                ))
            }
            spinnerMap[prop] = spinner
            content.add(spinner)
        }
        content.add(JLabel(ctx.locale.t("prop.colorize")))
        val cb = JCheckBox()
        cb.isSelected = layer.props.colorize
        cb.addActionListener {
            if (suppressChange) return@addActionListener
            val currentLayer = ctx.bag.findToken(tokenId)?.findLayer(side, layer.id) ?: return@addActionListener
            val currentProps = currentLayer.props
            ctx.history.execute(ctx.bag, ColorizeCommand(
                tokenId = tokenId,
                side = side,
                changes = listOf(ColorizeCommand.LayerChange(layer.id, currentProps, currentProps.copy(colorize = cb.isSelected)))
            ))
        }
        colorizeCb = cb
        content.add(cb)
        content.revalidate(); content.repaint()
        resetButton.isEnabled = true
    }

    private fun propValue(layer: Layer, prop: LayerProperty): Double = when (prop) {
        LayerProperty.OFFSET_X -> layer.props.offsetX.toDouble()
        LayerProperty.OFFSET_Y -> layer.props.offsetY.toDouble()
        LayerProperty.ROTATION -> layer.props.rotation.toDouble()
        LayerProperty.SCALE -> layer.props.scale.toDouble()
        LayerProperty.OPACITY -> layer.props.opacity.toDouble()
        LayerProperty.HUE -> layer.props.hue.toDouble()
        LayerProperty.SATURATION -> layer.props.saturation.toDouble()
        LayerProperty.BRIGHTNESS -> layer.props.brightness.toDouble()
    }

    private fun bounds(p: LayerProperty): Triple<Double, Double, Double> = when (p) {
        LayerProperty.OFFSET_X, LayerProperty.OFFSET_Y -> Triple(-1000.0, 1000.0, 1.0)
        LayerProperty.ROTATION -> Triple(-360.0, 360.0, 60.0)
        LayerProperty.SCALE -> Triple(0.01, 10.0, 0.05)
        LayerProperty.OPACITY -> Triple(0.0, 1.0, 0.05)
        LayerProperty.HUE -> Triple(0.0, 1.0, 0.01)
        LayerProperty.SATURATION -> Triple(0.0, 2.0, 0.05)
        LayerProperty.BRIGHTNESS -> Triple(0.0, 2.0, 0.05)
    }

    private fun LayerProperty.labelKey(): String = when (this) {
        LayerProperty.OFFSET_X -> "prop.offsetX"
        LayerProperty.OFFSET_Y -> "prop.offsetY"
        LayerProperty.ROTATION -> "prop.rotation"
        LayerProperty.SCALE -> "prop.scale"
        LayerProperty.OPACITY -> "prop.opacity"
        LayerProperty.HUE -> "prop.hue"
        LayerProperty.SATURATION -> "prop.saturation"
        LayerProperty.BRIGHTNESS -> "prop.brightness"
    }

    private fun resetToDefaults() {
        val pair = activeLayer() ?: return
        val (tokenId, side, layer) = pair
        val currentProps = layer.props
        val defaults = LayerProperties()
        if (currentProps != defaults) {
            ctx.history.execute(ctx.bag, ColorizeCommand(
                tokenId = tokenId,
                side = side,
                changes = listOf(ColorizeCommand.LayerChange(layer.id, currentProps, defaults))
            ))
        }
    }
}
