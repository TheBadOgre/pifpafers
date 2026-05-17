package net.rafkos.neuroshima.editor.ui.panels

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.command.LayerProperty
import net.rafkos.neuroshima.editor.command.SetLayerPropertyCommand
import net.rafkos.neuroshima.editor.model.Layer
import java.awt.BorderLayout
import java.awt.GridLayout
import java.util.UUID
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.event.ChangeListener

class LayerPropertiesPanel(private val ctx: AppContext) : JPanel() {

    private val content = JPanel(GridLayout(0, 2, 4, 2))

    init {
        layout = BorderLayout()
        border = BorderFactory.createTitledBorder(ctx.locale.t("panel.properties"))
        add(content, BorderLayout.NORTH)
        ctx.bag.addListener { rebuild() }
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
        content.removeAll()
        val pair = activeLayer()
        if (pair == null) {
            content.add(JLabel("No single layer selected"))
            content.add(JLabel(""))
            content.revalidate(); content.repaint()
            return
        }
        val (tokenId, layer) = pair
        val rows = listOf(
            LayerProperty.OFFSET_X to layer.props.offsetX.toDouble(),
            LayerProperty.OFFSET_Y to layer.props.offsetY.toDouble(),
            LayerProperty.ROTATION to layer.props.rotation.toDouble(),
            LayerProperty.SCALE to layer.props.scale.toDouble(),
            LayerProperty.OPACITY to layer.props.opacity.toDouble(),
            LayerProperty.HUE to layer.props.hue.toDouble(),
            LayerProperty.SATURATION to layer.props.saturation.toDouble(),
            LayerProperty.BRIGHTNESS to layer.props.brightness.toDouble(),
        )
        for ((prop, value) in rows) {
            content.add(JLabel(ctx.locale.t(prop.labelKey())))
            val (min, max, step) = bounds(prop)
            val model = SpinnerNumberModel(value, min, max, step)
            val spinner = JSpinner(model)
            spinner.addChangeListener(ChangeListener {
                val newValue = (spinner.value as Number).toDouble()
                ctx.history.execute(ctx.bag, SetLayerPropertyCommand(
                    tokenId = tokenId,
                    layerId = layer.id,
                    property = prop,
                    oldValue = value,
                    newValue = newValue,
                ))
            })
            content.add(spinner)
        }
        content.revalidate(); content.repaint()
    }

    private fun bounds(p: LayerProperty): Triple<Double, Double, Double> = when (p) {
        LayerProperty.OFFSET_X, LayerProperty.OFFSET_Y -> Triple(-1000.0, 1000.0, 1.0)
        LayerProperty.ROTATION -> Triple(-360.0, 360.0, 1.0)
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
}
