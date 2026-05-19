package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.model.TokenSide
import java.util.UUID

class SetLayerPropertyCommand(
    val tokenId: UUID,
    val side: TokenSide,
    val layerId: UUID,
    val property: LayerProperty,
    val oldValue: Double,
    val newValue: Double,
) : Command {
    override val label: String = "Set ${property.name.lowercase()}"

    override fun execute(bag: TokenBag) {
        val token = bag.findToken(tokenId) ?: throw NoSuchElementException("Token $tokenId")
        val layer = token.findLayer(side, layerId) ?: throw NoSuchElementException("Layer $layerId on $side")
        bag.updateLayerProps(tokenId, side, layerId, property.apply(layer.props, newValue))
    }

    override fun undo(bag: TokenBag) {
        val token = bag.findToken(tokenId) ?: return
        val layer = token.findLayer(side, layerId) ?: return
        bag.updateLayerProps(tokenId, side, layerId, property.apply(layer.props, oldValue))
    }

    override fun mergeWith(next: Command): Command? {
        if (next !is SetLayerPropertyCommand) return null
        if (next.tokenId != tokenId || next.side != side || next.layerId != layerId || next.property != property) return null
        return SetLayerPropertyCommand(
            tokenId, side, layerId, property,
            oldValue = oldValue,
            newValue = next.newValue,
        )
    }
}
