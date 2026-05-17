package net.rafkos.neuroshima.editor.persistence

import kotlinx.serialization.Serializable

@Serializable
internal data class BagDto(
    val schemaVersion: Int,
    val name: String,
    val tokens: List<TokenDto>,
)

@Serializable
internal data class TokenDto(
    val id: String,
    val kind: String,
    val layers: List<LayerDto>,
)

@Serializable
internal data class LayerDto(
    val id: String,
    val asset: String,
    val props: PropsDto,
)

@Serializable
internal data class PropsDto(
    val offsetX: Int,
    val offsetY: Int,
    val rotation: Float,
    val scale: Float,
    val opacity: Float,
    val hue: Float,
    val saturation: Float,
    val brightness: Float,
    val colorize: Boolean = false,
)
