package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.LayerProperties

enum class LayerProperty {
    OFFSET_X, OFFSET_Y, ROTATION, SCALE, OPACITY, HUE, SATURATION, BRIGHTNESS;

    fun apply(props: LayerProperties, value: Double): LayerProperties = when (this) {
        OFFSET_X -> props.copy(offsetX = value.toInt())
        OFFSET_Y -> props.copy(offsetY = value.toInt())
        ROTATION -> props.copy(rotation = value.toFloat())
        SCALE -> props.copy(scale = value.toFloat())
        OPACITY -> props.copy(opacity = value.toFloat())
        HUE -> props.copy(hue = value.toFloat())
        SATURATION -> props.copy(saturation = value.toFloat())
        BRIGHTNESS -> props.copy(brightness = value.toFloat())
    }
}
