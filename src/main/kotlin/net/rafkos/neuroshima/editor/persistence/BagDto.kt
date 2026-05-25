package net.rafkos.neuroshima.editor.persistence

import kotlinx.serialization.Serializable

@Serializable
internal data class BagDto(
    val schemaVersion: Int,
    val name: String,
    val printSettings: PublishSettingsDto = PublishSettingsDto(),
    val tokens: List<TokenDto>,
)

@Serializable
internal data class PublishSettingsDto(
    val dpi: Int = 300,
    val pageFormat: String = "A4",
    val invertBackSide: Boolean = false,
    val renderOverlay: Boolean = true,
    val marginMm: Double = 25.0,
)

@Serializable
internal data class TokenDto(
    val id: String,
    val kind: String,
    val maskId: Int = 0,
    val sameSides: Boolean = false,
    val front: List<LayerDto>,
    val back: List<LayerDto>,
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
