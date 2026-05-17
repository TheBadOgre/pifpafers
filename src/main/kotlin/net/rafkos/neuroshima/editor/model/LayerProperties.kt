package net.rafkos.neuroshima.editor.model

data class LayerProperties(
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val rotation: Float = 0f,
    val scale: Float = 1f,
    val opacity: Float = 1f,
    val hue: Float = 0f,
    val saturation: Float = 1f,
    val brightness: Float = 1f,
    val colorize: Boolean = false,
) {
    fun normalized(): LayerProperties =
        copy(rotation = ((rotation % 360f) + 360f) % 360f)
}
