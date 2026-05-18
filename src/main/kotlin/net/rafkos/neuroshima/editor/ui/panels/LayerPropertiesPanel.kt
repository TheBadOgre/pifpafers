package net.rafkos.neuroshima.editor.ui.panels

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.command.LayerProperty
import net.rafkos.neuroshima.editor.command.SetLayerPropertyCommand
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.LayerProperties
import net.rafkos.neuroshima.editor.model.ModelEvent
import java.awt.BorderLayout
import java.awt.GridLayout
import java.util.UUID
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class LayerPropertiesPanel(private val ctx: AppContext) : JPanel() {

    private val content = JPanel(GridLayout(0, 2, 4, 2))
    private val spinnerMap = mutableMapOf<LayerProperty, JSpinner>()
    private var displayedLayerId: UUID? = null
    private var suppressChange = false
    private val resetButton = JButton(ctx.locale.t("button.reset.defaults"))

    init {
        layout = BorderLayout()
        border = BorderFactory.createTitledBorder(ctx.locale.t("panel.properties"))
        add(content, BorderLayout.NORTH)
        add(resetButton, BorderLayout.SOUTH)
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

    private fun activeLayer(): Pair<UUID, Layer>? {
        if (ctx.viewState.selectedLayers.size != 1) return null
        val tokenId = ctx.viewState.activeTokenId ?: return null
        val layerId = ctx.viewState.selectedLayers.first()
        val token = ctx.bag.findToken(tokenId) ?: return null
        val layer = token.findLayer(layerId) ?: return null
        return tokenId to layer
    }

    private fun rebuild() {
        val pair = activeLayer()
        if (pair == null) {
            val msgKey = if (ctx.viewState.selectedLayers.size > 1) "label.multi.layer" else "label.no.layer"
            if (displayedLayerId == null && content.componentCount > 0 &&
                (content.getComponent(0) as? JLabel)?.text == ctx.locale.t(msgKey)) return
            displayedLayerId = null
            spinnerMap.clear()
            content.removeAll()
            content.add(JLabel(ctx.locale.t(msgKey)))
            content.add(JLabel(""))
            content.revalidate(); content.repaint()
            resetButton.isEnabled = false
            return
        }
        val (tokenId, layer) = pair
        if (layer.id == displayedLayerId) {
            for ((prop, spinner) in spinnerMap) {
                val v = propValue(layer, prop)
                if ((spinner.value as Number).toDouble() != v) {
                    suppressChange = true
                    spinner.value = v
                    suppressChange = false
                }
            }
            return
        }
        displayedLayerId = layer.id
        spinnerMap.clear()
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
            val capturedProp = prop
            spinner.addChangeListener {
                if (suppressChange) return@addChangeListener
                val newValue = (spinner.value as Number).toDouble()
                val currentLayer = ctx.bag.findToken(tokenId)?.findLayer(layer.id) ?: return@addChangeListener
                val oldValue = propValue(currentLayer, capturedProp)
                ctx.history.execute(ctx.bag, SetLayerPropertyCommand(
                    tokenId = tokenId,
                    layerId = layer.id,
                    property = capturedProp,
                    oldValue = oldValue,
                    newValue = newValue,
                ))
            }
            spinnerMap[prop] = spinner
            content.add(spinner)
        }
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
        val (tokenId, layer) = pair
        val defaults = LayerProperties()
        val props = listOf(
            LayerProperty.OFFSET_X to defaults.offsetX.toDouble(),
            LayerProperty.OFFSET_Y to defaults.offsetY.toDouble(),
            LayerProperty.ROTATION to defaults.rotation.toDouble(),
            LayerProperty.SCALE    to defaults.scale.toDouble(),
            LayerProperty.OPACITY  to defaults.opacity.toDouble(),
            LayerProperty.HUE      to defaults.hue.toDouble(),
            LayerProperty.SATURATION to defaults.saturation.toDouble(),
            LayerProperty.BRIGHTNESS to defaults.brightness.toDouble(),
        )
        for ((prop, newValue) in props) {
            val currentLayer = ctx.bag.findToken(tokenId)?.findLayer(layer.id) ?: continue
            val oldValue = propValue(currentLayer, prop)
            if (oldValue != newValue) {
                ctx.history.execute(ctx.bag, SetLayerPropertyCommand(
                    tokenId = tokenId,
                    layerId = layer.id,
                    property = prop,
                    oldValue = oldValue,
                    newValue = newValue,
                ))
            }
        }
    }
}
