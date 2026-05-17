package net.rafkos.neuroshima.editor.model

import java.util.UUID

data class Layer(
    val id: UUID,
    val assetPath: AssetPath,
    val props: LayerProperties = LayerProperties(),
) {
    companion object {
        fun create(assetPath: AssetPath, props: LayerProperties = LayerProperties()): Layer =
            Layer(UUID.randomUUID(), assetPath, props)
    }
}
