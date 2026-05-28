package net.rafkos.neuroshima.editor.model

import java.util.UUID

sealed class ModelEvent {
    data object NameChanged : ModelEvent()
    data class TokenAdded(val tokenId: UUID, val index: Int) : ModelEvent()
    data class TokenRemoved(val tokenId: UUID) : ModelEvent()
    data class LayerAdded(val tokenId: UUID, val side: TokenSide, val layerId: UUID, val index: Int) : ModelEvent()
    data class LayerRemoved(val tokenId: UUID, val side: TokenSide, val layerId: UUID) : ModelEvent()
    data class LayerReordered(val tokenId: UUID, val side: TokenSide, val layerId: UUID, val newIndex: Int) : ModelEvent()
    data class LayerPropsChanged(val tokenId: UUID, val side: TokenSide, val layerId: UUID) : ModelEvent()
    data class MaskIdAssigned(val tokenId: UUID, val maskId: Int) : ModelEvent()
    data class SameSidesChanged(val tokenId: UUID) : ModelEvent()
    data object PrintSettingsChanged : ModelEvent()
}
